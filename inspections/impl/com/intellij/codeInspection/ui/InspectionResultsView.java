package com.intellij.codeInspection.ui;

import com.intellij.CommonBundle;
import com.intellij.analysis.AnalysisScope;
import com.intellij.codeEditor.printing.ExportToHTMLSettings;
import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.impl.AddNoInspectionDocTagAction;
import com.intellij.codeInsight.daemon.impl.AddSuppressWarningsAnnotationAction;
import com.intellij.codeInsight.daemon.impl.EditInspectionToolsSettingsAction;
import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.deadCode.DeadCodeInspection;
import com.intellij.codeInspection.deadCode.DummyEntryPointsTool;
import com.intellij.codeInspection.ex.*;
import com.intellij.codeInspection.export.ExportToHTMLDialog;
import com.intellij.codeInspection.export.HTMLExportFrameMaker;
import com.intellij.codeInspection.export.HTMLExporter;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.reference.RefImplicitConstructor;
import com.intellij.codeInspection.util.RefEntityAlphabeticalComparator;
import com.intellij.ide.*;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.impl.ModuleUtil;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.pom.Navigatable;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SmartExpander;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.OpenSourceUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.*;
import java.util.List;

/**
 * @author max
 */
public class InspectionResultsView extends JPanel implements OccurenceNavigator, DataProvider {
  public static final RefElement[] EMPTY_ELEMENTS_ARRAY = new RefElement[0];
  public static final ProblemDescriptor[] EMPTY_DESCRIPTORS = new ProblemDescriptor[0];
  private Project myProject;
  private InspectionTree myTree;
  private Browser myBrowser;
  private Map<HighlightDisplayLevel, Map<String, InspectionGroupNode>> myGroups = null;
  private OccurenceNavigator myOccurenceNavigator;
  @Nullable private InspectionProfile myInspectionProfile;
  private AnalysisScope myScope;
  @NonNls
  public static final String HELP_ID = "codeInspection";
  public final Map<HighlightDisplayLevel, InspectionSeverityGroupNode> mySeverityGroupNodes = new HashMap<HighlightDisplayLevel, InspectionSeverityGroupNode>();
  private static final Logger LOG = Logger.getInstance("com.intellij.codeInspection.ui.InspectionResultsView");

  private Splitter mySplitter;

  public InspectionResultsView(final Project project, InspectionProfile inspectionProfile, AnalysisScope scope) {
    setLayout(new BorderLayout());

    myProject = project;
    myInspectionProfile = inspectionProfile;
    myScope = scope;
    myTree = new InspectionTree(project);
    myOccurenceNavigator = new OccurenceNavigatorSupport(myTree) {
      @Nullable
      protected Navigatable createDescriptorForNode(DefaultMutableTreeNode node) {
        if (node instanceof RefElementNode) {
          final RefElementNode refNode = (RefElementNode)node;
          if (refNode.hasDescriptorsUnder()) return null;
          final RefElement element = refNode.getElement();
          if (element == null || !element.isValid()) return null;
          final CommonProblemDescriptor problem = refNode.getProblem();
          if (problem != null) {
            return navigate(problem);
          }
          return getOpenFileDescriptor(element);
        }
        else if (node instanceof ProblemDescriptionNode) {
          if (!((ProblemDescriptionNode)node).isValid()) return null;
          final CommonProblemDescriptor descriptor = ((ProblemDescriptionNode)node).getDescriptor();
          return navigate(descriptor);
        }
        return null;
      }

      private Navigatable navigate(final CommonProblemDescriptor descriptor) {
        final PsiElement psiElement = descriptor instanceof ProblemDescriptor ? ((ProblemDescriptor)descriptor).getPsiElement() : null;
        if (psiElement == null || !psiElement.isValid()) return null;
        return getOpenFileDescriptor(psiElement);
      }

      public String getNextOccurenceActionName() {
        return InspectionsBundle.message("inspection.action.go.next");
      }

      public String getPreviousOccurenceActionName() {
        return InspectionsBundle.message("inspection.actiongo.prev");
      }
    };

    myBrowser = new Browser(this);

    final InspectionManagerEx manager = (InspectionManagerEx)InspectionManager.getInstance(project);
    mySplitter = new Splitter(false, manager.getUIOptions().SPLITTER_PROPORTION);

    mySplitter.setFirstComponent(ScrollPaneFactory.createScrollPane(myTree));
    mySplitter.setSecondComponent(myBrowser);

    mySplitter.addPropertyChangeListener(new PropertyChangeListener() {
      public void propertyChange(PropertyChangeEvent evt) {
        if (Splitter.PROP_PROPORTION.equals(evt.getPropertyName())) {
          final InspectionManagerEx manager = (InspectionManagerEx)InspectionManager.getInstance(project);
          manager.setSplitterProportion(((Float)evt.getNewValue()).floatValue());
        }
      }
    });

    myTree.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
      public void valueChanged(TreeSelectionEvent e) {
        syncBrowser();
        syncSource();
      }
    });

    myTree.addMouseListener(new MouseAdapter() {
      public void mouseClicked(MouseEvent e) {
        if (!e.isPopupTrigger() && e.getClickCount() == 2) {
          OpenSourceUtil.openSourcesFrom(DataManager.getInstance().getDataContext(InspectionResultsView.this), true);
        }
      }
    });

    myTree.addKeyListener(new KeyAdapter() {
      public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ENTER) {
          OpenSourceUtil.openSourcesFrom(DataManager.getInstance().getDataContext(InspectionResultsView.this), false);
        }
      }
    });

    myTree.addMouseListener(new PopupHandler() {
      public void invokePopup(Component comp, int x, int y) {
        popupInvoked(comp, x, y);
      }
    });

    SmartExpander.installOn(myTree);

    myBrowser.addClickListener(new Browser.ClickListener() {
      public void referenceClicked(final Browser.ClickEvent e) {
        if (e.getEventType() == Browser.ClickEvent.REF_ELEMENT) {
          showSource(e.getClickedElement());
        }
        else if (e.getEventType() == Browser.ClickEvent.FILE_OFFSET) {
          final VirtualFile file = e.getFile();
          OpenFileDescriptor descriptor = new OpenFileDescriptor(project, file, e.getStartOffset());
          Editor editor = FileEditorManager.getInstance(project).openTextEditor(descriptor, true);
          TextAttributes selectionAttributes = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(
            EditorColors.SEARCH_RESULT_ATTRIBUTES);
          HighlightManager.getInstance(project).addRangeHighlight(editor, e.getStartOffset(), e.getEndOffset(),
                                                                  selectionAttributes, true, null);
        }
      }
    });

    final CommonActionsManager actionsManager = CommonActionsManager.getInstance();
    add(mySplitter, BorderLayout.CENTER);
    DefaultActionGroup group = new DefaultActionGroup();
    group.add(new CloseAction());
    group.add(new RerunAction(this));
    group.add(manager.createToggleAutoscrollAction());
    group.add(manager.createGroupBySeverityAction());
    group.add(actionsManager.createPrevOccurenceAction(getOccurenceNavigator()));
    group.add(actionsManager.createNextOccurenceAction(getOccurenceNavigator()));
    group.add(new ExportHTMLAction());
    group.add(new EditSettingsAction());
    group.add(new HelpAction());
    group.add(new InvokeQuickFixAction());
    group.add(new SuppressInspectionToolbarAction());

    ActionToolbar actionToolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.CODE_INSPECTION,
                                                                                  group, false);
    add(actionToolbar.getComponent(), BorderLayout.WEST);
  }

  public void dispose(){
    mySplitter.dispose();
    myBrowser.dispose();
    myTree = null;
    myOccurenceNavigator = null;
  }

  private void hectorCorrections(final Set<String> profiles){
    final Map<VirtualFile, String> hectorAssignments = InspectionProjectProfileManager.getInstance(myProject).getHectorAssignments();
    for (VirtualFile vFile : hectorAssignments.keySet()) {
      if (myScope.contains(vFile)){
        profiles.add(hectorAssignments.get(vFile));
      }
    }
  }

  private static OpenFileDescriptor getOpenFileDescriptor(PsiElement psiElement) {
    return new OpenFileDescriptor(psiElement.getProject(), psiElement.getContainingFile().getVirtualFile(), psiElement.getTextOffset());
  }

  private void syncSource() {
    if (isAutoScrollMode()) {
      OpenSourceUtil.openSourcesFrom(DataManager.getInstance().getDataContext(this), false);
    }
  }

  private boolean isAutoScrollMode() {
    String activeToolWindowId = ToolWindowManager.getInstance(myProject).getActiveToolWindowId();
    final InspectionManagerEx manager = (InspectionManagerEx)InspectionManager.getInstance(myProject);
    return manager.getUIOptions().AUTOSCROLL_TO_SOURCE &&
           (activeToolWindowId == null || activeToolWindowId.equals(ToolWindowId.INSPECTION));
  }

  private static void showSource(final RefElement refElement) {
    OpenFileDescriptor descriptor = getOpenFileDescriptor(refElement);
    if (descriptor != null) {
      Project project = refElement.getRefManager().getProject();
      FileEditorManager.getInstance(project).openTextEditor(descriptor, false);
    }
  }

  private class CloseAction extends AnAction {
    private CloseAction() {
      super(CommonBundle.message("action.close"), null, IconLoader.getIcon("/actions/cancel.png"));
    }

    public void actionPerformed(AnActionEvent e) {
      final InspectionManagerEx managerEx = ((InspectionManagerEx)InspectionManagerEx.getInstance(myProject));
      managerEx.close();
      //may differ from CurrentProfile in case of editor set up
      if (myInspectionProfile != null) {
        myInspectionProfile.cleanup();
      } else {
        final Set<String> profiles = myScope.getActiveInspectionProfiles();
        hectorCorrections(profiles);
        InspectionProjectProfileManager inspectionProfileManager = InspectionProjectProfileManager.getInstance(myProject);
        for (String profileName : profiles) {
          ((InspectionProfileImpl)inspectionProfileManager.getProfile(profileName)).cleanup();
        }
      }
    }
  }

  private class EditSettingsAction extends AnAction {
    private EditSettingsAction() {
      super(InspectionsBundle.message("inspection.action.edit.settings"), InspectionsBundle.message("inspection.action.edit.settings"), IconLoader.getIcon("/general/ideOptions.png"));
    }

    public void actionPerformed(AnActionEvent e) {
      if (EditInspectionToolsSettingsAction.editToolSettings(myProject, (InspectionProfileImpl) myInspectionProfile, false, null, InspectionProjectProfileManager.getInstance(myProject))){
        InspectionResultsView.this.update();
      }
    }
  }

  private void exportHTML() {
    ExportToHTMLDialog exportToHTMLDialog = new ExportToHTMLDialog(myProject);
    final ExportToHTMLSettings exportToHTMLSettings = ExportToHTMLSettings.getInstance(myProject);
    if (exportToHTMLSettings.OUTPUT_DIRECTORY == null) {
      exportToHTMLSettings.OUTPUT_DIRECTORY = PathManager.getHomePath() + File.separator + "exportToHTML";
    }
    exportToHTMLDialog.reset();
    exportToHTMLDialog.show();
    if (!exportToHTMLDialog.isOK()) {
      return;
    }
    exportToHTMLDialog.apply();

    final String outputDirectoryName = exportToHTMLSettings.OUTPUT_DIRECTORY;
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        final Runnable exportRunnable = new Runnable() {
          public void run() {
            HTMLExportFrameMaker maker = new HTMLExportFrameMaker(outputDirectoryName, myProject);
            maker.start();
            try {
              exportHTML(maker);
            }
            catch (ProcessCanceledException e) {
              // Do nothing here.
            }

            maker.done();
          }
        };

        if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(exportRunnable, InspectionsBundle.message(
          "inspection.generating.html.progress.title"), true, myProject)) {
          return;
        }

        if (exportToHTMLSettings.OPEN_IN_BROWSER) {
          BrowserUtil.launchBrowser(exportToHTMLSettings.OUTPUT_DIRECTORY + File.separator + "index.html");
        }
      }
    });
  }

  private static class HelpAction extends AnAction {
    private HelpAction() {
      super(CommonBundle.message("action.help"), null, IconLoader.getIcon("/actions/help.png"));
    }

    public void actionPerformed(AnActionEvent event) {
      HelpManager.getInstance().invokeHelp(HELP_ID);
    }
  }

  private class ExportHTMLAction extends AnAction {
    public ExportHTMLAction() {
      super(InspectionsBundle.message("inspection.action.export.html"), null, IconLoader.getIcon("/actions/export.png"));
    }

    public void actionPerformed(AnActionEvent e) {
      exportHTML();
    }
  }


  @Nullable
  private static OpenFileDescriptor getOpenFileDescriptor(final RefElement refElement) {
    final VirtualFile[] file = new VirtualFile[1];
    final int[] offset = new int[1];

    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        PsiElement psiElement = refElement.getElement();
        if (psiElement != null) {
          file[0] = psiElement.getContainingFile().getVirtualFile();
          offset[0] = psiElement.getTextOffset();
        }
        else {
          file[0] = null;
        }
      }
    });

    if (file[0] != null && file[0].isValid()) {
      return new OpenFileDescriptor(refElement.getRefManager().getProject(), file[0], offset[0]);
    }
    return null;
  }

  public void syncBrowser() {
    if (myTree.getSelectionModel().getSelectionCount() != 1) {
      myBrowser.showEmpty();
    }
    else {
      TreePath pathSelected = myTree.getSelectionModel().getLeadSelectionPath();
      if (pathSelected != null) {
        final InspectionTreeNode node = (InspectionTreeNode)pathSelected.getLastPathComponent();
        if (node instanceof RefElementNode) {
          final RefElementNode refElementNode = (RefElementNode)node;
          final CommonProblemDescriptor problem = refElementNode.getProblem();
          RefElement refSelected = refElementNode.getElement();
          if (problem != null) {
            showInBrowser(refSelected, problem);
          }
          else {
            showInBrowser(refSelected);
          }
        }
        else if (node instanceof ProblemDescriptionNode) {
          final ProblemDescriptionNode problemNode = (ProblemDescriptionNode)node;
          showInBrowser(problemNode.getElement(), problemNode.getDescriptor());
        }
        else if (node instanceof InspectionNode) {
          showInBrowser(((InspectionNode)node).getTool());
        }
        else {
          myBrowser.showEmpty();
        }
      }
    }
  }

  public void showInBrowser(final RefEntity refEntity) {
    Cursor currentCursor = getCursor();
    setCursor(new Cursor(Cursor.WAIT_CURSOR));
    myBrowser.showPageFor(refEntity);
    setCursor(currentCursor);
  }

  public void showInBrowser(InspectionTool tool) {
    Cursor currentCursor = getCursor();
    setCursor(new Cursor(Cursor.WAIT_CURSOR));
    myBrowser.showDescription(tool);
    setCursor(currentCursor);
  }

  public void showInBrowser(final RefEntity refEntity, CommonProblemDescriptor descriptor) {
    Cursor currentCursor = getCursor();
    setCursor(new Cursor(Cursor.WAIT_CURSOR));
    myBrowser.showPageFor(refEntity, descriptor);
    setCursor(currentCursor);
  }

  public void addTool(InspectionTool tool, HighlightDisplayLevel errorLevel, boolean groupedBySeverity) {
    tool.updateContent();
    if (tool.hasReportedProblems()) {
      final InspectionTreeNode parentNode = getToolParentNode(tool.getGroupDisplayName().length() > 0 ? tool.getGroupDisplayName() : GroupNames.GENERAL_GROUP_NAME, errorLevel, groupedBySeverity);
      InspectionNode toolNode = new InspectionNode(tool);
      initToolNode(tool, toolNode, parentNode);
      if (tool instanceof DeadCodeInspection) {
        final DummyEntryPointsTool entryPoints = new DummyEntryPointsTool((DeadCodeInspection)tool);
        entryPoints.updateContent();
        initToolNode(entryPoints, new EntryPointsNode(entryPoints), toolNode);
      }
      regsisterActionShortcuts(tool);
    }
  }

  private void initToolNode(InspectionTool tool, InspectionNode toolNode, InspectionTreeNode parentNode) {
    final InspectionTreeNode[] contents = tool.getContents();
    for (InspectionTreeNode content : contents) {
      mergeBranch(content, toolNode);
    }
    mergeBranch(toolNode, parentNode);
  }

  private void mergeBranch(InspectionTreeNode child, InspectionTreeNode parent){
    if (((InspectionManagerEx)InspectionManagerEx.getInstance(myProject)).RUN_WITH_EDITOR_PROFILE){
      for(int i = 0; i < parent.getChildCount(); i++){
        InspectionTreeNode current = (InspectionTreeNode)parent.getChildAt(i);
        if (child.getClass() != current.getClass()){
          continue;
        }
        if (current instanceof InspectionPackageNode){
          if (((InspectionPackageNode)current).getPackageName().compareTo(((InspectionPackageNode)child).getPackageName()) == 0){
            processDepth(child, current);
            return;
          }
        } else if (current instanceof RefElementNode){
          if (((RefElementNode)current).getElement().getName().compareTo(((RefElementNode)child).getElement().getName()) == 0){
            processDepth(child, current);
            return;
          }
        } else if (current instanceof InspectionNode){
          if (((InspectionNode)current).getTool().getShortName().compareTo(
              ((InspectionNode)child).getTool().getShortName()) == 0){
            processDepth(child, current);
            return;
          }
        } else if (current instanceof InspectionModuleNode){
          if (((InspectionModuleNode)current).getName().compareTo(
              ((InspectionModuleNode)child).getName()) == 0){
            processDepth(child, current);
            return;
          }
        } else if (current instanceof ProblemDescriptionNode){
          if (((ProblemDescriptionNode)current).getDescriptor().getDescriptionTemplate().compareTo(
              ((ProblemDescriptionNode)child).getDescriptor().getDescriptionTemplate()) == 0){
            processDepth(child, current);
            return;
          }
        }
      }
    }
    parent.add(child);
  }

  private void processDepth(final InspectionTreeNode child, final InspectionTreeNode current) {
    for (int j = 0; j < child.getChildCount(); j ++){
      mergeBranch((InspectionTreeNode)child.getChildAt(j), current);
    }
  }

  private void regsisterActionShortcuts(InspectionTool tool) {
    final QuickFixAction[] fixes = tool.getQuickFixes(null);
    if (fixes != null) {
      for (QuickFixAction fix : fixes) {
        fix.registerCustomShortcutSet(fix.getShortcutSet(), this);
      }
    }
  }

  private void clearTree() {
    myTree.removeAllNodes();
    mySeverityGroupNodes.clear();
  }

  public String getCurrentProfileName() {
    return myInspectionProfile != null ? myInspectionProfile.getName() : "";
  }

  public boolean update() {
    clearTree();
    boolean resultsFound = false;
    final InspectionManagerEx manager = ((InspectionManagerEx)InspectionManagerEx.getInstance(myProject));
    final boolean isGroupedBySeverity = manager.getUIOptions().GROUP_BY_SEVERITY;
    myGroups = new HashMap<HighlightDisplayLevel, Map<String, InspectionGroupNode>>();
    Map<InspectionTool, HighlightDisplayLevel> tools = new HashMap<InspectionTool, HighlightDisplayLevel>();
    InspectionProjectProfileManager inspectionProfileManager = InspectionProjectProfileManager.getInstance(myProject);
    if (manager.RUN_WITH_EDITOR_PROFILE){
      final Set<String> profiles = myScope.getActiveInspectionProfiles();
      hectorCorrections(profiles);
      for (String profileName : profiles) {
        processProfile((InspectionProfileImpl)inspectionProfileManager.getProfile(profileName), tools);
      }
    } else {
      processProfile(myInspectionProfile, tools);
    }

    for (InspectionTool tool : tools.keySet()) {
      tool.updateContent();
      final boolean hasProblems = tool.hasReportedProblems();
      if (hasProblems) {
        addTool(tool, tools.get(tool), isGroupedBySeverity);
      }
      resultsFound |= hasProblems;
    }
    myTree.sort();
    myTree.restoreExpantionAndSelection();
    return resultsFound;
  }

  private static void processProfile(final InspectionProfile profile, final Map<InspectionTool, HighlightDisplayLevel> tools) {
    final InspectionTool[] inspectionTools = profile.getInspectionTools();
    for (InspectionTool tool : inspectionTools) {
      final HighlightDisplayKey key = HighlightDisplayKey.find(tool.getShortName());
      if (profile.isToolEnabled(key)){
        tools.put(tool, profile.getErrorLevel(key));
      }
    }
  }

  private InspectionTreeNode getToolParentNode(String groupName, HighlightDisplayLevel errorLevel, boolean groupedBySeverity) {
    if ((groupName == null || groupName.length() == 0)) {
      return getRelativeRootNode(groupedBySeverity, errorLevel);
    }
    Map<String, InspectionGroupNode> map = myGroups.get(errorLevel);
    if (map == null) {
      map = new HashMap<String, InspectionGroupNode>();
      myGroups.put(errorLevel, map);
    }
    Map<String, InspectionGroupNode> searchMap = new HashMap<String, InspectionGroupNode>(map);
    if (!groupedBySeverity) {
      for (HighlightDisplayLevel level : myGroups.keySet()) {
        searchMap.putAll(myGroups.get(level));
      }
    }
    InspectionGroupNode group = searchMap.get(groupName);
    if (group == null) {
      group = new InspectionGroupNode(groupName);
      map.put(groupName, group);
      getRelativeRootNode(groupedBySeverity, errorLevel).add(group);
    }
    return group;
  }

  private InspectionTreeNode getRelativeRootNode(boolean isGroupedBySeverity, HighlightDisplayLevel level) {
    if (isGroupedBySeverity) {
      if (mySeverityGroupNodes.containsKey(level)) {
        return mySeverityGroupNodes.get(level);
      }
      else {
        final InspectionSeverityGroupNode severityGroupNode = new InspectionSeverityGroupNode(level);
        mySeverityGroupNodes.put(level, severityGroupNode);
        myTree.getRoot().add(severityGroupNode);
        return severityGroupNode;
      }
    }
    else {
      return myTree.getRoot();
    }
  }

  public void exportHTML(HTMLExportFrameMaker frameMaker) {
    final InspectionTreeNode root = myTree.getRoot();
    final Enumeration children = root.children();
    while (children.hasMoreElements()) {
      InspectionTreeNode node = (InspectionTreeNode)children.nextElement();
      if (node instanceof InspectionNode) {
        exportHTML(frameMaker, (InspectionNode)node);
      }
      else if (node instanceof InspectionGroupNode) {
        final Enumeration groupChildren = node.children();
        while (groupChildren.hasMoreElements()) {
          InspectionNode toolNode = (InspectionNode)groupChildren.nextElement();
          exportHTML(frameMaker, toolNode);
        }
      }
    }
  }

  private void exportHTML(HTMLExportFrameMaker frameMaker, InspectionNode node) {
    InspectionTool tool = node.getTool();
    HTMLExporter exporter = new HTMLExporter(frameMaker.getRootFolder() + "/" + tool.getFolderName(),
                                             tool.getComposer(), myProject);
    frameMaker.startInspection(tool);
    exportHTML(tool, exporter);
    exporter.generateReferencedPages();
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public void exportHTML(InspectionTool tool, HTMLExporter exporter) {
    StringBuffer packageIndex = new StringBuffer();
    packageIndex.append("<html><body>");

    final Map<String, Set<RefElement>> content = tool.getPackageContent();
    ArrayList<String> packageNames = new ArrayList<String>(content.keySet());

    Collections.sort(packageNames, RefEntityAlphabeticalComparator.getInstance());
    for (String packageName : packageNames) {
      appendPackageReference(packageIndex, packageName);
      final ArrayList<RefElement> packageContent = new ArrayList<RefElement>(content.get(packageName));
      Collections.sort(packageContent, RefEntityAlphabeticalComparator.getInstance());
      StringBuffer contentIndex = new StringBuffer();
      contentIndex.append("<html><body>");
      for (RefElement refElement : packageContent) {
        if (refElement instanceof RefImplicitConstructor) {
          //noinspection AssignmentToForLoopParameter
          refElement = ((RefImplicitConstructor)refElement).getOwnerClass();
        }

        contentIndex.append("<a HREF=\"");
        contentIndex.append(exporter.getURL(refElement));
        contentIndex.append("\" target=\"elementFrame\">");
        contentIndex.append(refElement.getName());
        contentIndex.append("</a><br>");

        exporter.createPage(refElement);
      }

      contentIndex.append("</body></html>");
      HTMLExporter.writeFile(exporter.getRootFolder(), packageName + "-index.html", contentIndex, myProject);
    }

    packageIndex.append("</body></html>");

    HTMLExporter.writeFile(exporter.getRootFolder(), "index.html", packageIndex, myProject);
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private static void appendPackageReference(StringBuffer packageIndex, String packageName) {
    packageIndex.append("<a HREF=\"");
    packageIndex.append(packageName);
    packageIndex.append("-index.html\" target=\"packageFrame\">");
    packageIndex.append(packageName);
    packageIndex.append("</a><br>");
  }

  public OccurenceNavigator getOccurenceNavigator() {
    return myOccurenceNavigator;
  }

  public boolean hasNextOccurence() {
    return myOccurenceNavigator.hasNextOccurence();
  }

  public boolean hasPreviousOccurence() {
    return myOccurenceNavigator.hasPreviousOccurence();
  }

  public OccurenceNavigator.OccurenceInfo goNextOccurence() {
    return myOccurenceNavigator.goNextOccurence();
  }

  public OccurenceNavigator.OccurenceInfo goPreviousOccurence() {
    return myOccurenceNavigator.goPreviousOccurence();
  }

  public String getNextOccurenceActionName() {
    return myOccurenceNavigator.getNextOccurenceActionName();
  }

  public String getPreviousOccurenceActionName() {
    return myOccurenceNavigator.getPreviousOccurenceActionName();
  }

  public void invokeLocalFix(int idx) {
    if (myTree.getSelectionCount() != 1) return;
    final InspectionTreeNode node = (InspectionTreeNode)myTree.getSelectionPath().getLastPathComponent();
    if (node instanceof ProblemDescriptionNode) {
      final ProblemDescriptionNode problemNode = (ProblemDescriptionNode)node;
      final CommonProblemDescriptor descriptor = problemNode.getDescriptor();
      final RefEntity element = problemNode.getElement();
      invokeFix(element, descriptor, idx);
    }
    else if (node instanceof RefElementNode) {
      RefElementNode elementNode = (RefElementNode)node;
      RefElement element = elementNode.getElement();
      CommonProblemDescriptor descriptor = elementNode.getProblem();
      if (descriptor != null) {
        invokeFix(element, descriptor, idx);
      }
    }
  }

  private void invokeFix(final RefEntity element, final CommonProblemDescriptor descriptor, final int idx) {
    final QuickFix[] fixes = descriptor.getFixes();
    if (fixes != null && fixes.length > idx && fixes[idx] != null) {
      if (element instanceof RefElement) {
        PsiElement psiElement = ((RefElement)element).getElement();
        if (psiElement != null && psiElement.isValid()) {
          if (!psiElement.isWritable()) {
            final ReadonlyStatusHandler.OperationStatus operationStatus = ReadonlyStatusHandler.getInstance(myProject)
              .ensureFilesWritable(psiElement.getContainingFile().getVirtualFile());
            if (operationStatus.hasReadonlyFiles()) {
              return;
            }
          }

          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            public void run() {
              Runnable command = new Runnable() {
                public void run() {
                  CommandProcessor.getInstance().markCurrentCommandAsComplex(myProject);
                  if (descriptor instanceof ProblemDescriptor){
                    ((LocalQuickFix)fixes[idx]).applyFix(myProject, (ProblemDescriptor)descriptor);
                  } else {
                    ((GlobalQuickFix)fixes[idx]).applyFix();
                  }
                }
              };
              CommandProcessor.getInstance().executeCommand(myProject, command, fixes[idx].getName(), null);
              final DescriptorProviderInspection tool = ((DescriptorProviderInspection)myTree.getSelectedTool());
              if (tool != null) {
                tool.ignoreProblem(element, descriptor, idx);
              }
              update();
            }
          });
        }
      } else {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
            public void run() {
              Runnable command = new Runnable() {
                public void run() {
                  CommandProcessor.getInstance().markCurrentCommandAsComplex(myProject);
                  if (!(descriptor instanceof ProblemDescriptor)){
                    ((GlobalQuickFix)fixes[idx]).applyFix();
                  }
                }
              };
              CommandProcessor.getInstance().executeCommand(myProject, command, fixes[idx].getName(), null);
              final DescriptorProviderInspection tool = ((DescriptorProviderInspection)myTree.getSelectedTool());
              if (tool != null) {
                tool.ignoreProblem(element, descriptor, idx);
              }
              update();
            }
          });
      }
    }
  }

  private class SuppressInspectionToolbarAction extends AnAction{
      public SuppressInspectionToolbarAction() {
        super(InspectionsBundle.message("suppress.inspection.family"), InspectionsBundle.message("suppress.inspection.family"), IconLoader.getIcon("/general/inspectionsOff.png"));
      }

      public void actionPerformed(AnActionEvent e) {
        final InspectionTool selectedTool = myTree.getSelectedTool();
        assert selectedTool != null;
        getSuppressAction(selectedTool, myTree.getSelectionPaths(), HighlightDisplayKey.find(selectedTool.getShortName()).getID()).actionPerformed(e);
      }

    public void update(AnActionEvent e) {
      if (!isSingleToolInSelection()){
        e.getPresentation().setEnabled(false);
        return;
      }
      final InspectionTool selectedTool = myTree.getSelectedTool();
      assert selectedTool != null;
      final HighlightDisplayKey key = HighlightDisplayKey.find(selectedTool.getShortName());
      if (key == null){
        e.getPresentation().setEnabled(false);
        return;
      }
      getSuppressAction(selectedTool, myTree.getSelectionPaths(), HighlightDisplayKey.find(selectedTool.getShortName()).getID()).update(e);
    }
  }

  protected class InvokeQuickFixAction extends AnAction {
    public InvokeQuickFixAction() {
      super(InspectionsBundle.message("inspection.action.apply.quickfix"), InspectionsBundle.message("inspection.action.apply.quickfix.description"), IconLoader.getIcon("/actions/createFromUsage.png"));

      registerCustomShortcutSet(ActionManager.getInstance().getAction(IdeActions.ACTION_SHOW_INTENTION_ACTIONS).getShortcutSet(),
                                myTree);
    }

    public void update(AnActionEvent e) {
      if (!isSingleToolInSelection()) {
        e.getPresentation().setEnabled(false);
        return;
      }

      //noinspection ConstantConditions
      final @NotNull InspectionTool tool = myTree.getSelectedTool();
      List<RefEntity> selectedElements = new ArrayList<RefEntity>();
      final TreePath[] selectionPaths = myTree.getSelectionPaths();
      for (TreePath path : selectionPaths) {
        traverseRefElements((InspectionTreeNode)path.getLastPathComponent(), selectedElements);
      }
      final QuickFixAction[] quickFixes = tool.getQuickFixes(selectedElements.toArray(new RefEntity[selectedElements.size()]));
      if (quickFixes == null || quickFixes.length == 0) {
        e.getPresentation().setEnabled(false);
        return;
      }

      ActionGroup fixes = new ActionGroup() {
        public AnAction[] getChildren(@Nullable AnActionEvent e) {
          List<QuickFixAction> children = new ArrayList<QuickFixAction>();
          for (QuickFixAction fix : quickFixes) {
            if (fix != null) {
              children.add(fix);
            }
          }
          return children.toArray(new AnAction[children.size()]);
        }
      };

      e.getPresentation().setEnabled(!ActionGroupUtil.isGroupEmpty(fixes, e));
    }

    public void actionPerformed(AnActionEvent e) {
      final InspectionTool tool = myTree.getSelectedTool();
      assert tool != null;
      List<RefEntity> selectedElements = new ArrayList<RefEntity>();
      final TreePath[] selectionPaths = myTree.getSelectionPaths();
      for (TreePath path : selectionPaths) {
        traverseRefElements((InspectionTreeNode)path.getLastPathComponent(), selectedElements);
      }
      final QuickFixAction[] quickFixes = tool.getQuickFixes(selectedElements.toArray(new RefElement[selectedElements.size()]));
      ActionGroup fixes = new ActionGroup() {
        public AnAction[] getChildren(@Nullable AnActionEvent e) {
          List<QuickFixAction> children = new ArrayList<QuickFixAction>();
          for (QuickFixAction fix : quickFixes) {
            if (fix != null) {
              children.add(fix);
            }
          }
          return children.toArray(new AnAction[children.size()]);
        }
      };

      DataContext dataContext = e.getDataContext();
      final ListPopup popup = JBPopupFactory.getInstance()
        .createActionGroupPopup(InspectionsBundle.message("inspection.tree.popup.title"),
                                fixes,
                                dataContext,
                                JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
                                false);

      popup.showInBestPositionFor(dataContext);
    }
  }

  public Project getProject() {
    return myProject;
  }

  public Object getData(String dataId) {
    if (dataId.equals(DataConstantsEx.INSPECTION_VIEW)) return this;
    TreePath[] paths = myTree.getSelectionPaths();

    if (paths == null) return null;

    if (paths.length > 1) {
      if (DataConstantsEx.PSI_ELEMENT_ARRAY.equals(dataId)) {
        return collectPsiElements();
      }
      else {
        return null;
      }
    }

    TreePath path = paths[0];

    InspectionTreeNode selectedNode = (InspectionTreeNode)path.getLastPathComponent();

    if (selectedNode instanceof RefElementNode) {
      final RefElementNode refElementNode = (RefElementNode)selectedNode;
      RefElement refElement = refElementNode.getElement();
      final RefElement item;
      if (refElement instanceof RefImplicitConstructor) {
        item = ((RefImplicitConstructor)refElement).getOwnerClass();
      }
      else {
        item = refElement;
      }

      if (!item.isValid()) return null;

      PsiElement psiElement = item.getElement();
      if (psiElement == null) return null;

      final CommonProblemDescriptor problem = refElementNode.getProblem();
      if (problem != null) {
        if (problem instanceof ProblemDescriptor) {
          psiElement = ((ProblemDescriptor)problem).getPsiElement();
          if (psiElement == null) return null;
        }
        else {
          return null;
        }
      }

      if (DataConstants.NAVIGATABLE.equals(dataId)) {
        final VirtualFile virtualFile = psiElement.getContainingFile().getVirtualFile();
        if (virtualFile != null && virtualFile.isValid()) {
          return new OpenFileDescriptor(myProject, virtualFile, psiElement.getTextOffset());
        }
      }
      else if (DataConstants.PSI_ELEMENT.equals(dataId)) {
        return psiElement;
      }
    }
    else if (selectedNode instanceof ProblemDescriptionNode && DataConstants.NAVIGATABLE.equals(dataId)) {
      final CommonProblemDescriptor descriptor = ((ProblemDescriptionNode)selectedNode).getDescriptor();
      PsiElement psiElement = descriptor instanceof ProblemDescriptor ? ((ProblemDescriptor)descriptor).getPsiElement() : null;
      if (psiElement == null || !psiElement.isValid()) return null;
      return new OpenFileDescriptor(myProject, psiElement.getContainingFile().getVirtualFile(), psiElement.getTextOffset());
    }

    return null;
  }

  private PsiElement[] collectPsiElements() {
    RefEntity[] refElements = myTree.getSelectedElements();
    List<PsiElement> psiElements = new ArrayList<PsiElement>();
    for (RefEntity refElement : refElements) {
      PsiElement psiElement = refElement instanceof RefElement ? ((RefElement)refElement).getElement() : null;
      if (psiElement != null && psiElement.isValid()) {
        psiElements.add(psiElement);
      }
    }

    return psiElements.toArray(new PsiElement[psiElements.size()]);
  }

  private void popupInvoked(Component component, int x, int y) {
    if (!isSingleToolInSelection()) return;

    final TreePath path;
    if (myTree.hasFocus()) {
      path = myTree.getLeadSelectionPath();
    }
    else {
      path = null;
    }

    if (path == null) return;

    DefaultActionGroup actions = new DefaultActionGroup();
    actions.add(ActionManager.getInstance().getAction(IdeActions.ACTION_EDIT_SOURCE));
    actions.add(ActionManager.getInstance().getAction(IdeActions.ACTION_FIND_USAGES));

    final InspectionTool tool = myTree.getSelectedTool();
    if (tool == null) return;

    List<RefEntity> selectedElements = new ArrayList<RefEntity>();
    final TreePath[] selectionPaths = myTree.getSelectionPaths();
    for (TreePath selectionPath : selectionPaths) {
      traverseRefElements((InspectionTreeNode)selectionPath.getLastPathComponent(), selectedElements);
    }
    final QuickFixAction[] quickFixes = tool.getQuickFixes(selectedElements.toArray(new RefElement[selectedElements.size()]));
    if (quickFixes != null) {
      for (QuickFixAction quickFixe : quickFixes) {
        actions.add(quickFixe);
      }
    }
    final HighlightDisplayKey key = HighlightDisplayKey.find(tool.getShortName());
    if (key == null) return; //e.g. DummyEntryPointsTool
    actions.add(new AnAction(InspectionsBundle.message("inspection.edit.tool.settings")) {
      public void actionPerformed(AnActionEvent e) {
        if (new EditInspectionToolsSettingsAction(key).editToolSettings(myProject, (InspectionProfileImpl) myInspectionProfile, false)){
          InspectionResultsView.this.update();
        }
      }
    });

    actions.add(getSuppressAction(tool, selectionPaths, key.getID()));
    actions.add(ActionManager.getInstance().getAction(IdeActions.GROUP_VERSION_CONTROLS));

    ActionPopupMenu menu = ActionManager.getInstance().createActionPopupMenu(ActionPlaces.CODE_INSPECTION, actions);
    menu.getComponent().show(component, x, y);
  }

    private AnAction getSuppressAction(final InspectionTool tool, final TreePath[] selectionPaths, final String id) {
        final AnAction suppressAction = new AnAction(InspectionsBundle.message("inspection.quickfix.suppress", tool.getDisplayName())) {
          public void actionPerformed(AnActionEvent e) {
            ApplicationManager.getApplication().invokeLater(new Runnable() {
              public void run() {
                PsiDocumentManager.getInstance(myProject).commitAllDocuments();
                CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
                  public void run() {
                    for (TreePath treePath : selectionPaths) {
                      final InspectionTreeNode node = (InspectionTreeNode)treePath.getLastPathComponent();
                      final List<RefElement> elementsToSuppress = myTree.getElementsToSuppressInSubTree(node);
                      for (final RefElement refElement : elementsToSuppress) {
                        final PsiElement element = refElement.getElement();
                        final IntentionAction action = getCorrectIntentionAction(tool.getDisplayName(), id, element);
                        ApplicationManager.getApplication().runWriteAction(new Runnable() {
                          public void run() {
                            try {
                              action.invoke(myProject, null, refElement.getElement().getContainingFile());
                            }
                            catch (IncorrectOperationException e1) {
                              LOG.error(e1);
                            }
                          }
                        });
                      }
                      final List<RefEntity> elementsToIgnore = new ArrayList<RefEntity>();
                      traverseRefElements(node, elementsToIgnore);
                      for (RefEntity element : elementsToIgnore) {
                        tool.ignoreElement(element);
                      }
                    }
                    InspectionResultsView.this.update();
                  }
                }, InspectionsBundle.message("inspection.quickfix.suppress"), null);
              }
            });
          }

          public void update(AnActionEvent e) {
            e.getPresentation().setEnabled(true);
            for (TreePath treePath : selectionPaths) {
              final InspectionTreeNode node = (InspectionTreeNode)treePath.getLastPathComponent();
              final List<RefElement> elementsToSuppress = myTree.getElementsToSuppressInSubTree(node);
              for (RefElement refElement : elementsToSuppress) {
                final PsiElement element = refElement.getElement();
                if (element instanceof PsiFile) continue;
                if (element == null || !element.isValid()) continue;
                final PsiFile file = element.getContainingFile();
                final IntentionAction action = getCorrectIntentionAction(tool.getDisplayName(), id, element);
                if (action.isAvailable(myProject, null, file)) {
                  e.getPresentation().setEnabled(true);
                  return;
                }
              }
            }
            e.getPresentation().setEnabled(false);
          }
        };
        return suppressAction;
    }

    @Nullable
    public AnAction getSuppressAction( final RefElement refElement,
                                       final InspectionTool tool){
      final HighlightDisplayKey key = HighlightDisplayKey.find(tool.getShortName());
      if (key != null) {
        final IntentionAction action = getCorrectIntentionAction(tool.getDisplayName(), key.getID(), refElement.getElement());
        final PsiFile file = refElement.getElement().getContainingFile();
        if (action.isAvailable(myProject, null, file)) {
          return new AnAction(action.getText()) {
            public void actionPerformed(AnActionEvent e) {
              final List<RefEntity> elementsToSuppress = new ArrayList<RefEntity>();
              traverseRefElements((InspectionTreeNode)myTree.getSelectionPath().getLastPathComponent(), elementsToSuppress);
              invokeSuppressAction(action, refElement, tool, elementsToSuppress);
            }
          };
        }
      }
      return null;
    }

  private static void traverseRefElements(@NotNull InspectionTreeNode node, List<RefEntity> elementsToSuppress){
    if (node instanceof RefElementNode){
      elementsToSuppress.add(((RefElementNode)node).getElement());
    } else if (node instanceof ProblemDescriptionNode){
      elementsToSuppress.add(((ProblemDescriptionNode)node).getElement());
    }
    for (int i = 0; i < node.getChildCount(); i++) {
      final InspectionTreeNode child = (InspectionTreeNode)node.getChildAt(i);
      traverseRefElements(child, elementsToSuppress);
    }
  }

  private void invokeSuppressAction(final IntentionAction action, final RefElement element, final InspectionTool tool, final List<RefEntity> elementsToSuppress) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            PsiDocumentManager.getInstance(myProject).commitAllDocuments();
            CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
              public void run() {
                ApplicationManager.getApplication().runWriteAction(new Runnable() {
                  public void run() {
                    try {
                      action.invoke(myProject, null, element.getElement().getContainingFile());
                      for (RefEntity refElement : elementsToSuppress) {
                        tool.ignoreElement(refElement);
                      }
                      InspectionResultsView.this.update();
                    }
                    catch (IncorrectOperationException e1) {
                      LOG.error(e1);
                    }
                  }
                });
              }
            }, action.getText(), null);
          }
        });
      }
    });
  }

  @NotNull public InspectionTree getTree(){
    return myTree;
  }

  public boolean isSingleToolInSelection() {
    return myTree.getSelectedTool() != null;
  }

  private class RerunAction extends AnAction {
    public RerunAction(JComponent comp) {
      super(InspectionsBundle.message("inspection.action.rerun"), InspectionsBundle.message("inspection.action.rerun"), IconLoader.getIcon("/actions/refreshUsages.png"));
      registerCustomShortcutSet(CommonShortcuts.getRerun(), comp);
    }

    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(myScope.isValid());
    }

    public void actionPerformed(AnActionEvent e) {
      rerun();
    }
  }

  private void rerun() {
    if (myScope.isValid()) {
      if (myInspectionProfile == null) {
        final Set<String> profiles = myScope.getActiveInspectionProfiles();
        hectorCorrections(profiles);
        InspectionProjectProfileManager inspectionProfileManager = InspectionProjectProfileManager.getInstance(myProject);
        for (String profileName : profiles) {
          ((InspectionProfileImpl)inspectionProfileManager.getProfile(profileName)).cleanup();
        }
      }
      final InspectionManagerEx inspectionManagerEx = ((InspectionManagerEx)InspectionManagerEx.getInstance(myProject));
      inspectionManagerEx.setExternalProfile(myInspectionProfile);
      inspectionManagerEx.doInspections(myScope);
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          inspectionManagerEx.setExternalProfile(null);
        }
      });
    }
  }

  private static class SuppressWarningAction extends AddSuppressWarningsAnnotationAction {
    public SuppressWarningAction(final String displayName, final String ID, final PsiElement context) {
      super(displayName, ID, context);
    }

    @Nullable
    protected PsiModifierListOwner getContainer() {
      if (!(myContext.getContainingFile().getLanguage() instanceof JavaLanguage) || myContext instanceof PsiFile) {
        return null;
      }
      PsiElement container = myContext;
      while (container instanceof PsiClassInitializer || container instanceof PsiAnonymousClass ||
             container instanceof PsiTypeParameter){
        container = PsiTreeUtil.getParentOfType(container, PsiMember.class);
      }
      return (PsiModifierListOwner)container;
    }
  }

  private static class SuppressDocCommentAction extends AddNoInspectionDocTagAction{

    public SuppressDocCommentAction(final String displayName, final String ID, final PsiElement context) {
      super(displayName, ID, context);
    }

    @Nullable
    protected PsiDocCommentOwner getContainer() {
      if (!(myContext.getContainingFile().getLanguage() instanceof JavaLanguage) || myContext instanceof PsiFile){
        return null;
      }
      PsiElement container = myContext;
      while (container instanceof PsiTypeParameter) {
        container = PsiTreeUtil.getParentOfType(container, PsiDocCommentOwner.class);
      }
      return (PsiDocCommentOwner)container;
    }
  }

  private static IntentionAction getCorrectIntentionAction(String displayName, String id, PsiElement context){
    boolean isSuppressWarnings = false;
    final Module module = ModuleUtil.findModuleForPsiElement(context);
    if (module != null){
       final ProjectJdk jdk = ModuleRootManager.getInstance(module).getJdk();
       if (jdk != null){
         isSuppressWarnings = DaemonCodeAnalyzerSettings.getInstance().SUPPRESS_WARNINGS &&
                              jdk.getVersionString().indexOf("1.5") > 0 &&
                              LanguageLevel.JDK_1_5.compareTo(context.getManager().getEffectiveLanguageLevel()) <= 0;
       }
    }
    if (isSuppressWarnings){
      return new SuppressWarningAction(displayName, id, context);
    }
    return new SuppressDocCommentAction(displayName, id, context);
  }
}
