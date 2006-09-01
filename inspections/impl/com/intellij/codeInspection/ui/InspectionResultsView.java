package com.intellij.codeInspection.ui;

import com.intellij.CommonBundle;
import com.intellij.analysis.AnalysisScope;
import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.deadCode.DeadCodeInspection;
import com.intellij.codeInspection.deadCode.DummyEntryPointsTool;
import com.intellij.codeInspection.ex.*;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.reference.RefImplicitConstructor;
import com.intellij.ide.*;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.pom.Navigatable;
import com.intellij.profile.Profile;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiElement;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SmartExpander;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.ArrayUtil;
import com.intellij.util.OpenSourceUtil;
import com.intellij.util.ui.tree.TreeUtil;
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
import java.util.*;
import java.util.List;

/**
 * @author max
 */
public class InspectionResultsView extends JPanel implements OccurenceNavigator, DataProvider {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.ui.InspectionResultsView");

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

  private Splitter mySplitter;
  private GlobalInspectionContextImpl myGlobalInspectionContext;
  private boolean myRerun = false;

  public InspectionResultsView(final Project project, InspectionProfile inspectionProfile, AnalysisScope scope, GlobalInspectionContextImpl globalInspectionContext) {
    setLayout(new BorderLayout());

    myProject = project;
    myInspectionProfile = inspectionProfile;
    myScope = scope;
    myGlobalInspectionContext = globalInspectionContext;
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

      @Nullable
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
          myGlobalInspectionContext.setSplitterProportion(((Float)evt.getNewValue()).floatValue());
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
    group.add(new RerunAction(this));
    group.add(new CloseAction());
    final TreeExpander treeExpander = new TreeExpander() {
      public void expandAll() {
        TreeUtil.expandAll(myTree);
      }

      public boolean canExpand() {
        return true;
      }

      public void collapseAll() {
        TreeUtil.collapseAll(myTree, 0);
      }

      public boolean canCollapse() {
        return true;
      }
    };
    group.add(actionsManager.createExpandAllAction(treeExpander, myTree));
    group.add(actionsManager.createCollapseAllAction(treeExpander, myTree));
    group.add(actionsManager.createPrevOccurenceAction(getOccurenceNavigator()));
    group.add(actionsManager.createNextOccurenceAction(getOccurenceNavigator()));
    group.add(myGlobalInspectionContext.createToggleAutoscrollAction());
    group.add(new ExportHTMLAction(this));
    group.add(new HelpAction());

    JPanel westPanel = new JPanel(new BorderLayout());
    westPanel.add(ActionManager.getInstance().createActionToolbar(ActionPlaces.CODE_INSPECTION, group, false).getComponent(), BorderLayout.WEST);

    DefaultActionGroup specialGroup = new DefaultActionGroup();
    specialGroup.add(myGlobalInspectionContext.getUIOptions().createGroupBySeverityAction(this));
    specialGroup.add(myGlobalInspectionContext.getUIOptions().createFilterResolvedItemsAction(this));
    specialGroup.add(myGlobalInspectionContext.getUIOptions().createShowOutdatedProblemsAction(this));
    specialGroup.add(myGlobalInspectionContext.getUIOptions().createShowDiffOnlyAction(this));
    specialGroup.add(new EditSettingsAction());
    specialGroup.add(new DisableInspectionAction());
    final InvokeQuickFixAction invokeQuickFixAction = new InvokeQuickFixAction(this);
    specialGroup.add(invokeQuickFixAction);
    specialGroup.add(new SuppressInspectionToolbarAction(this));
    final JComponent toolbarComponent = ActionManager.getInstance()
      .createActionToolbar(ActionPlaces.CODE_INSPECTION, specialGroup, false).getComponent();
    westPanel.add(toolbarComponent, BorderLayout.EAST);
    add(westPanel, BorderLayout.WEST);
    final Component actionButton = toolbarComponent.getComponent(ArrayUtil.find(specialGroup.getChildren(null), invokeQuickFixAction));
    invokeQuickFixAction.setupPopupCoordinates(new RelativePoint(actionButton, new Point(0, actionButton.getHeight())));
  }

  public void dispose(){
    mySplitter.dispose();
    myBrowser.dispose();
    myTree = null;
    myOccurenceNavigator = null;
  }


  public GlobalInspectionContextImpl getInspectionContext() {
    return myGlobalInspectionContext;
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
    return myGlobalInspectionContext.getUIOptions().AUTOSCROLL_TO_SOURCE &&
           (activeToolWindowId == null || activeToolWindowId.equals(ToolWindowId.INSPECTION));
  }

  private static void showSource(final RefElement refElement) {
    OpenFileDescriptor descriptor = getOpenFileDescriptor(refElement);
    if (descriptor != null) {
      Project project = refElement.getRefManager().getProject();
      FileEditorManager.getInstance(project).openTextEditor(descriptor, false);
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

  @SuppressWarnings({"ConstantConditions"})
  private void mergeBranch(InspectionTreeNode child, InspectionTreeNode parent){
    if (myGlobalInspectionContext.RUN_WITH_EDITOR_PROFILE){
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
    return myInspectionProfile != null ? myInspectionProfile.getDisplayName() : null;
  }

  public boolean update(){
    return updateView(true);
  }

  public boolean updateView(boolean strict) {
    if (!strict && !myGlobalInspectionContext.getUIOptions().FILTER_RESOLVED_ITEMS) return false;
    clearTree();
    boolean resultsFound = false;
    final boolean isGroupedBySeverity = myGlobalInspectionContext.getUIOptions().GROUP_BY_SEVERITY;
    myGroups = new HashMap<HighlightDisplayLevel, Map<String, InspectionGroupNode>>();
    Map<InspectionTool, HighlightDisplayLevel> tools = new HashMap<InspectionTool, HighlightDisplayLevel>();
    InspectionProjectProfileManager inspectionProfileManager = InspectionProjectProfileManager.getInstance(myProject);
    if (myGlobalInspectionContext.RUN_WITH_EDITOR_PROFILE){
      final Set<String> profiles = myScope.getActiveInspectionProfiles();
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

  private void processProfile(final InspectionProfile profile, final Map<InspectionTool, HighlightDisplayLevel> tools) {
    final InspectionTool[] inspectionTools = InspectionProjectProfileManager.getInstance(myProject).getProfileWrapper(profile.getName()).getInspectionTools();
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
          //InspectionResultsView.this.update();
        }
      }

      public void update(AnActionEvent e) {
        e.getPresentation().setEnabled(myInspectionProfile != null && myInspectionProfile.isEditable());
      }

    });

    actions.add(new SuppressInspectionToolbarAction(this));
    actions.add(ActionManager.getInstance().getAction(IdeActions.GROUP_VERSION_CONTROLS));

    ActionPopupMenu menu = ActionManager.getInstance().createActionPopupMenu(ActionPlaces.CODE_INSPECTION, actions);
    menu.getComponent().show(component, x, y);
  }

  public static void traverseRefElements(@NotNull InspectionTreeNode node, List<RefEntity> elementsToSuppress){
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

  @NotNull public InspectionTree getTree(){
    return myTree;
  }

  public boolean isSingleToolInSelection() {
    return myTree.getSelectedTool() != null;
  }

  public boolean isRerun() {
    return myRerun;
  }

  private class CloseAction extends AnAction {
    private CloseAction() {
      super(CommonBundle.message("action.close"), null, IconLoader.getIcon("/actions/cancel.png"));
    }

    public void actionPerformed(AnActionEvent e) {
      myGlobalInspectionContext.close(true);
    }
  }

  private class EditSettingsAction extends AnAction {
    private EditSettingsAction() {
      super(InspectionsBundle.message("inspection.action.edit.settings"), InspectionsBundle.message("inspection.action.edit.settings"), IconLoader.getIcon("/general/ideOptions.png"));
    }

    public void actionPerformed(AnActionEvent e) {
      final InspectionTool tool = myTree.getSelectedTool();
      final InspectionProfile inspectionProfile = myInspectionProfile;
      LOG.assertTrue(inspectionProfile != null);
      if (tool != null) {
        final HighlightDisplayKey key = HighlightDisplayKey.find(tool.getShortName()); //do not search for dead code entry point tool
        if (key != null){
          if (new EditInspectionToolsSettingsAction(key).editToolSettings(myProject, (InspectionProfileImpl)inspectionProfile, false)){
            updateCurrentProfile(inspectionProfile);
          }
          return;
        }
      }
      if (EditInspectionToolsSettingsAction.editToolSettings(myProject, (InspectionProfileImpl)inspectionProfile, false, null, InspectionProjectProfileManager.getInstance(myProject))) {
        updateCurrentProfile(inspectionProfile);
      }
    }

    private void updateCurrentProfile(@NotNull final InspectionProfile inspectionProfile) {
      final Map<String, Profile> projectProfiles = InspectionProjectProfileManager.getInstance(myProject).getProfiles();
      final String name = inspectionProfile.getName();
      myInspectionProfile = (InspectionProfile)(projectProfiles.containsKey(name) ? projectProfiles.get(name) : InspectionProfileManager.getInstance().getProfile(name));
    }

    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(myInspectionProfile != null && myInspectionProfile.isEditable());
    }
  }



  private class DisableInspectionAction extends AnAction {
    private DisableInspectionAction() {
      super(InspectionsBundle.message("disable.inspection.action.name"), InspectionsBundle.message("disable.inspection.action.name"), IconLoader.getIcon("/actions/exclude.png"));
    }

    public void actionPerformed(AnActionEvent e) {
      InspectionProfile inspectionProfile = myInspectionProfile;
      LOG.assertTrue(inspectionProfile != null);
      ModifiableModel model = inspectionProfile.getModifiableModel();
      final InspectionTool tool = myTree.getSelectedTool();
      LOG.assertTrue(tool != null);
      model.disableTool(tool.getShortName());
      model.commit(InspectionProjectProfileManager.getInstance(myProject));
    }

    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(myInspectionProfile != null && myInspectionProfile.isEditable() && myTree.getSelectedTool() != null);
    }
  }

  private static class HelpAction extends AnAction {
    private HelpAction() {
      super(CommonBundle.message("action.help"), null, IconLoader.getIcon("/actions/help.png"));
    }

    public void actionPerformed(AnActionEvent event) {
      HelpManager.getInstance().invokeHelp(HELP_ID);
    }
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
    private void rerun() {
      myRerun = true;
      if (myScope.isValid()) {
        final InspectionProfile profile = myInspectionProfile;
        myGlobalInspectionContext.setExternalProfile(profile);
        myGlobalInspectionContext.doInspections(myScope, InspectionManager.getInstance(myProject));
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            myGlobalInspectionContext.setExternalProfile(null);
            myRerun = false;
          }
        });
      }
    }
  }
}
