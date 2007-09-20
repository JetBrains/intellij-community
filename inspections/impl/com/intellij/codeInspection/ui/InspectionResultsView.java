package com.intellij.codeInspection.ui;

import com.intellij.CommonBundle;
import com.intellij.analysis.AnalysisScope;
import com.intellij.analysis.AnalysisUIOptions;
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
import com.intellij.codeInspection.reference.RefManagerImpl;
import com.intellij.codeInspection.ui.actions.ExportHTMLAction;
import com.intellij.codeInspection.ui.actions.InspectionsOptionsToolbarAction;
import com.intellij.codeInspection.ui.actions.InvokeQuickFixAction;
import com.intellij.ide.*;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.pom.Navigatable;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiElement;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SmartExpander;
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
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.*;
import java.util.List;

/**
 * @author max
 */
public class InspectionResultsView extends JPanel implements Disposable, OccurenceNavigator, DataProvider {
  public static final RefElement[] EMPTY_ELEMENTS_ARRAY = new RefElement[0];
  public static final ProblemDescriptor[] EMPTY_DESCRIPTORS = new ProblemDescriptor[0];
  private Project myProject;
  private InspectionTree myTree;
  private Browser myBrowser;
  private Map<HighlightDisplayLevel, Map<String, InspectionGroupNode>> myGroups = null;
  private OccurenceNavigator myOccurenceNavigator;
  private InspectionProfile myInspectionProfile;
  private AnalysisScope myScope;
  @NonNls
  public static final String HELP_ID = "reference.toolWindows.inspections";
  public final Map<HighlightDisplayLevel, InspectionSeverityGroupNode> mySeverityGroupNodes = new HashMap<HighlightDisplayLevel, InspectionSeverityGroupNode>();

  private Splitter mySplitter;
  private GlobalInspectionContextImpl myGlobalInspectionContext;
  private boolean myRerun = false;

  private InspectionRVContentProvider myProvider;
  private AnAction myIncludeAction;
  private AnAction myExcludeAction;

  public InspectionResultsView(final Project project,
                               final InspectionProfile inspectionProfile,
                               final AnalysisScope scope,
                               final GlobalInspectionContextImpl globalInspectionContext,
                               final InspectionRVContentProvider provider) {
    setLayout(new BorderLayout());

    myProject = project;
    myInspectionProfile = inspectionProfile;
    myScope = scope;
    myGlobalInspectionContext = globalInspectionContext;
    myProvider = provider;

    myTree = new InspectionTree(project);
    initTreeListeners();

    myOccurenceNavigator = initOccurenceNavigator();

    myBrowser = new Browser(this);

    mySplitter = new Splitter(false, AnalysisUIOptions.getInstance(myProject).SPLITTER_PROPORTION);

    mySplitter.setFirstComponent(ScrollPaneFactory.createScrollPane(myTree));
    mySplitter.setSecondComponent(myBrowser);

    mySplitter.addPropertyChangeListener(new PropertyChangeListener() {
      public void propertyChange(PropertyChangeEvent evt) {
        if (Splitter.PROP_PROPORTION.equals(evt.getPropertyName())) {
          myGlobalInspectionContext.setSplitterProportion(((Float)evt.getNewValue()).floatValue());
        }
      }
    });
    add(mySplitter, BorderLayout.CENTER);

    myBrowser.addClickListener(new Browser.ClickListener() {
      public void referenceClicked(final Browser.ClickEvent e) {
        if (e.getEventType() == Browser.ClickEvent.REF_ELEMENT) {
          final RefElement refElement = e.getClickedElement();
          final OpenFileDescriptor descriptor = getOpenFileDescriptor(refElement);
          if (descriptor != null) {
            FileEditorManager.getInstance(project).openTextEditor(descriptor, false);
          }
        }
        else if (e.getEventType() == Browser.ClickEvent.FILE_OFFSET) {
          final VirtualFile file = e.getFile();
          final OpenFileDescriptor descriptor = new OpenFileDescriptor(project, file, e.getStartOffset());
          final Editor editor = FileEditorManager.getInstance(project).openTextEditor(descriptor, true);
          if (editor != null) {
            final TextAttributes selectionAttributes =
              EditorColorsManager.getInstance().getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);
            HighlightManager.getInstance(project)
            .addRangeHighlight(editor, e.getStartOffset(), e.getEndOffset(), selectionAttributes, true, null);
          }
        }
      }
    });

    createActionsToolbar();
  }

  private void initTreeListeners() {
    myTree.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
      public void valueChanged(TreeSelectionEvent e) {
        syncBrowser();
        if (isAutoScrollMode()) {
          OpenSourceUtil.openSourcesFrom(DataManager.getInstance().getDataContext(InspectionResultsView.this), false);
        }
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
  }

  private OccurenceNavigatorSupport initOccurenceNavigator(){
    return new OccurenceNavigatorSupport(myTree) {
      @Nullable
      protected Navigatable createDescriptorForNode(DefaultMutableTreeNode node) {
        if (node instanceof RefElementNode) {
          final RefElementNode refNode = (RefElementNode)node;
          if (refNode.hasDescriptorsUnder()) return null;
          final RefEntity element = refNode.getElement();
          if (element == null || !element.isValid()) return null;
          final CommonProblemDescriptor problem = refNode.getProblem();
          if (problem != null) {
            return navigate(problem);
          }
          if (element instanceof RefElement) {
            return getOpenFileDescriptor((RefElement)element);
          }
        }
        else if (node instanceof ProblemDescriptionNode) {
          if (!((ProblemDescriptionNode)node).isValid()) return null;
          return navigate(((ProblemDescriptionNode)node).getDescriptor());
        }
        return null;
      }

      @Nullable
      private Navigatable navigate(final CommonProblemDescriptor descriptor) {
        return getSelectedNavigatable(descriptor);
      }

      public String getNextOccurenceActionName() {
        return InspectionsBundle.message("inspection.action.go.next");
      }

      public String getPreviousOccurenceActionName() {
        return InspectionsBundle.message("inspection.actiongo.prev");
      }
    };
  }

  private void createActionsToolbar() {
    final JComponent leftActionsToolbar = createLeftActionsToolbar();
    final JComponent rightActionsToolbar = createRightActionsToolbar();

    JPanel westPanel = new JPanel(new BorderLayout());
    westPanel.add(leftActionsToolbar, BorderLayout.WEST);
    westPanel.add(rightActionsToolbar, BorderLayout.EAST);
    add(westPanel, BorderLayout.WEST);
  }

  @SuppressWarnings({"NonStaticInitializer"})
  private JComponent createRightActionsToolbar() {
    myIncludeAction = new AnAction(InspectionsBundle.message("inspections.result.view.include.action.text")){
      {
        registerCustomShortcutSet(CommonShortcuts.INSERT, myTree);
      }

      public void actionPerformed(AnActionEvent e) {
        ((InspectionTreeNode)myTree.getSelectionPath().getLastPathComponent()).amnesty();
        updateView(false);
      }

      public void update(final AnActionEvent e) {
        final TreePath path = myTree.getSelectionPath();
        e.getPresentation().setEnabled(path != null && !myGlobalInspectionContext.getUIOptions().FILTER_RESOLVED_ITEMS);
      }
    };
                                             
    myExcludeAction = new AnAction(InspectionsBundle.message("inspections.result.view.exclude.action.text")) {
      {
        registerCustomShortcutSet(CommonShortcuts.DELETE, myTree);
      }

      public void actionPerformed(final AnActionEvent e) {
        ((InspectionTreeNode)myTree.getSelectionPath().getLastPathComponent()).ignoreElement();
        updateView(false);
      }

      public void update(final AnActionEvent e) {
        final TreePath path = myTree.getSelectionPath();
        e.getPresentation().setEnabled(path != null);
      }
    };

    DefaultActionGroup specialGroup = new DefaultActionGroup();
    specialGroup.add(myGlobalInspectionContext.getUIOptions().createGroupBySeverityAction(this));
    specialGroup.add(myGlobalInspectionContext.getUIOptions().createGroupByDirectoryAction(this));
    specialGroup.add(myGlobalInspectionContext.getUIOptions().createFilterResolvedItemsAction(this));
    specialGroup.add(myGlobalInspectionContext.getUIOptions().createShowOutdatedProblemsAction(this));
    specialGroup.add(myGlobalInspectionContext.getUIOptions().createShowDiffOnlyAction(this));
    specialGroup.add(new EditSettingsAction());
    specialGroup.add(new InvokeQuickFixAction(this));
    specialGroup.add(new InspectionsOptionsToolbarAction(this));
    return createToolbar(specialGroup);
  }

  private JComponent createLeftActionsToolbar() {
    final CommonActionsManager actionsManager = CommonActionsManager.getInstance();
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

    return createToolbar(group);
  }

  private static JComponent createToolbar(final DefaultActionGroup specialGroup) {
    return ActionManager.getInstance()
      .createActionToolbar(ActionPlaces.CODE_INSPECTION, specialGroup, false).getComponent();
  }

  public void dispose(){
    mySplitter.dispose();
    myBrowser.dispose();
    myTree = null;
    myOccurenceNavigator = null;
  }


  private boolean isAutoScrollMode() {
    String activeToolWindowId = ToolWindowManager.getInstance(myProject).getActiveToolWindowId();
    return myGlobalInspectionContext.getUIOptions().AUTOSCROLL_TO_SOURCE &&
           (activeToolWindowId == null || activeToolWindowId.equals(ToolWindowId.INSPECTION));
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
          final RefEntity refSelected = refElementNode.getElement();
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

  private void showInBrowser(InspectionTool tool) {
    Cursor currentCursor = getCursor();
    setCursor(new Cursor(Cursor.WAIT_CURSOR));
    myBrowser.showDescription(tool);
    setCursor(currentCursor);
  }

  private void showInBrowser(final RefEntity refEntity, CommonProblemDescriptor descriptor) {
    Cursor currentCursor = getCursor();
    setCursor(new Cursor(Cursor.WAIT_CURSOR));
    myBrowser.showPageFor(refEntity, descriptor);
    setCursor(currentCursor);
  }

  private void addTool(InspectionTool tool, HighlightDisplayLevel errorLevel, boolean groupedBySeverity) {
    final InspectionTreeNode parentNode = getToolParentNode(tool.getGroupDisplayName().length() > 0 ? tool.getGroupDisplayName() : GroupNames.GENERAL_GROUP_NAME, errorLevel, groupedBySeverity);
    final InspectionNode toolNode = new InspectionNode(tool);
    myProvider.appendToolNodeContent(toolNode, parentNode, myGlobalInspectionContext.getUIOptions().SHOW_STRUCTURE);
    if (tool instanceof DeadCodeInspection) {
      final DummyEntryPointsTool entryPoints = new DummyEntryPointsTool((DeadCodeInspection)tool);
      entryPoints.updateContent();
      myProvider.appendToolNodeContent(new EntryPointsNode(entryPoints), toolNode, myGlobalInspectionContext.getUIOptions().SHOW_STRUCTURE);
    }
    regsisterActionShortcuts(tool);
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

  @Nullable
  public String getCurrentProfileName() {
    return myInspectionProfile != null ? myInspectionProfile.getDisplayName() : null;
  }

  public InspectionProfile getCurrentProfile() {
    return myInspectionProfile;
  }

  public boolean update(){
    return updateView(true);
  }

  public boolean updateView(boolean strict) {
    if (!strict && !myGlobalInspectionContext.getUIOptions().FILTER_RESOLVED_ITEMS){
      myTree.repaint();
      return false;
    }
    clearTree();
    boolean resultsFound = buildTree();
    myTree.sort();
    myTree.restoreExpantionAndSelection();
    return resultsFound;
  }

  private boolean buildTree() {
    final InspectionProfile profile = myInspectionProfile;
    final boolean isGroupedBySeverity = myGlobalInspectionContext.getUIOptions().GROUP_BY_SEVERITY;
    myGroups = new HashMap<HighlightDisplayLevel, Map<String, InspectionGroupNode>>();
    final Map<String, Set<Pair<InspectionTool, InspectionProfile>>> tools = myGlobalInspectionContext.getTools();
    boolean resultsFound = false;
    for (Set<Pair<InspectionTool, InspectionProfile>> toolsInsideProfile : tools.values()) {
      for (Pair<InspectionTool, InspectionProfile> toolWithProfile : toolsInsideProfile) {
        final InspectionTool tool = toolWithProfile.first;
        final HighlightDisplayKey key = HighlightDisplayKey.find(tool.getShortName());
        if (profile != null && !profile.isToolEnabled(key)) {
          break; //exclude disabled inspections from view
        }
        if (myProvider.checkReportedProblems(tool)) {
          addTool(tool, toolWithProfile.second.getErrorLevel(key), isGroupedBySeverity);
          resultsFound = true;
        }
      }
    }
    return resultsFound;
  }

  private InspectionTreeNode getToolParentNode(String groupName, HighlightDisplayLevel errorLevel, boolean groupedBySeverity) {
    if (groupName == null || groupName.length() == 0) {
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
        final InspectionSeverityGroupNode severityGroupNode = new InspectionSeverityGroupNode(myProject, level);
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
    if (dataId.equals(DataConstants.HELP_ID)) return HELP_ID;
    if (dataId.equals(DataConstantsEx.INSPECTION_VIEW)) return this;
    if (myTree == null) return null;
    TreePath[] paths = myTree.getSelectionPaths();

    if (paths == null) return null;

    if (paths.length > 1) {
      if (DataConstants.PSI_ELEMENT_ARRAY.equals(dataId)) {
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
      RefEntity refElement = refElementNode.getElement();
      final RefEntity item;
      if (refElement instanceof RefImplicitConstructor) {
        item = ((RefImplicitConstructor)refElement).getOwnerClass();
      }
      else {
        item = refElement;
      }

      if (item == null || !item.isValid()) return null;

      PsiElement psiElement = item instanceof RefElement ? ((RefElement)item).getElement() : null;
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
        return getSelectedNavigatable(problem, psiElement);
      }
      else if (DataConstants.PSI_ELEMENT.equals(dataId)) {
        return psiElement;
      }
    }
    else if (selectedNode instanceof ProblemDescriptionNode && DataConstants.NAVIGATABLE.equals(dataId)) {
      return getSelectedNavigatable(((ProblemDescriptionNode)selectedNode).getDescriptor());
    }

    return null;
  }

  @Nullable
  private Navigatable getSelectedNavigatable(final CommonProblemDescriptor descriptor) {
    return getSelectedNavigatable(descriptor,
                                  descriptor instanceof ProblemDescriptor ? ((ProblemDescriptor)descriptor).getPsiElement() : null);
  }

  @Nullable
  private Navigatable getSelectedNavigatable(final CommonProblemDescriptor descriptor, final PsiElement psiElement) {
    if (descriptor instanceof ProblemDescriptorImpl) {
      Navigatable navigatable = ((ProblemDescriptorImpl) descriptor).getNavigatable();
      if (navigatable != null) {
        return navigatable;
      }
    }
    if (psiElement == null || !psiElement.isValid()) return null;
    final VirtualFile virtualFile = psiElement.getContainingFile().getVirtualFile();
    if (virtualFile != null) {
      int startOffset = psiElement.getTextOffset();
      if (descriptor instanceof ProblemDescriptorImpl) {
        startOffset = ((ProblemDescriptorImpl)descriptor).getTextRange().getStartOffset();
      }
      return new OpenFileDescriptor(myProject, virtualFile, startOffset);
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
    final TreePath path;
    if (myTree.hasFocus()) {
      path = myTree.getLeadSelectionPath();
    }
    else {
      path = null;
    }

    if (path == null) return;

    final DefaultActionGroup actions = new DefaultActionGroup();
    final ActionManager actionManager = ActionManager.getInstance();
    actions.add(actionManager.getAction(IdeActions.ACTION_EDIT_SOURCE));
    actions.add(actionManager.getAction(IdeActions.ACTION_FIND_USAGES));

    actions.add(myIncludeAction);
    actions.add(myExcludeAction);

    actions.addSeparator();

    final InspectionTool tool = myTree.getSelectedTool();
    if (tool != null) {
      final QuickFixAction[] quickFixes = myProvider.getQuickFixes(tool, myTree);
      if (quickFixes != null) {
        for (QuickFixAction quickFixe : quickFixes) {
          actions.add(quickFixe);
        }
      }
      final HighlightDisplayKey key = HighlightDisplayKey.find(tool.getShortName());
      if (key == null) return; //e.g. DummyEntryPointsTool

      //options
      actions.addSeparator();
      actions.add(new EditSettingsAction());
      final List<AnAction> options = new InspectionsOptionsToolbarAction(this).createActions();
      for (AnAction action : options) {
        actions.add(action);
      }
    }

    actions.addSeparator();
    actions.add(actionManager.getAction(IdeActions.GROUP_VERSION_CONTROLS));

    final ActionPopupMenu menu = actionManager.createActionPopupMenu(ActionPlaces.CODE_INSPECTION, actions);
    menu.getComponent().show(component, x, y);
  }

  @NotNull public InspectionTree getTree(){
    return myTree;
  }

  protected RefManagerImpl getRefManager() {
    return (RefManagerImpl)myGlobalInspectionContext.getRefManager();
  }

  public GlobalInspectionContextImpl getGlobalInspectionContext() {
    return myGlobalInspectionContext;
  }

  public InspectionRVContentProvider getProvider() {
    return myProvider;
  }

  public boolean isSingleToolInSelection() {
    return myTree.getSelectedTool() != null;
  }

  public boolean isRerun() {
    boolean rerun = myRerun;
    myRerun = false;
    return rerun;
  }

  private InspectionProfile guessProfileToSelect(final InspectionProjectProfileManager profileManager) {
    final Set<InspectionProfile> profiles = new HashSet<InspectionProfile>();
    final RefEntity[] selectedElements = myTree.getSelectedElements();
    for (RefEntity selectedElement : selectedElements) {
      if (selectedElement instanceof RefElement) {
        final RefElement refElement = (RefElement)selectedElement;
        final PsiElement element = refElement.getElement();
        if (element != null) {
          profiles.add(profileManager.getInspectionProfile(element));
        }
      }
    }
    if (!profiles.isEmpty()) {
      return profiles.iterator().next();
    }
    else {
      return (InspectionProfile)profileManager.getProjectProfileImpl();
    }
  }

  public boolean isProfileDefined() {
    return myInspectionProfile != null && myInspectionProfile.isEditable();
  }

  public static void showPopup(AnActionEvent e, JBPopup popup) {
    final InputEvent event = e.getInputEvent();
    if (event instanceof MouseEvent) {
      popup.showUnderneathOf(event.getComponent());
    } else {
      popup.showInBestPositionFor(e.getDataContext());
    }
  }

  public AnalysisScope getScope() {
    return myScope;
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
      final InspectionProjectProfileManager profileManager = InspectionProjectProfileManager.getInstance(myProject);
      final InspectionTool tool = myTree.getSelectedTool();
      InspectionProfile inspectionProfile = myInspectionProfile;
      final boolean profileIsDefined = isProfileDefined();
      if (!profileIsDefined) {
        inspectionProfile = guessProfileToSelect(profileManager);
      }

      if (tool != null) {
        final HighlightDisplayKey key = HighlightDisplayKey.find(tool.getShortName()); //do not search for dead code entry point tool
        if (key != null){
          if (new EditInspectionToolsSettingsAction(key).editToolSettings(myProject, (InspectionProfileImpl)inspectionProfile, profileIsDefined)
              && profileIsDefined){
            updateCurrentProfile();
          }
          return;
        }
      }
      if (EditInspectionToolsSettingsAction.editToolSettings(myProject, inspectionProfile, profileIsDefined, null) && profileIsDefined) {
        updateCurrentProfile();
      }
    }
 }

  public void updateCurrentProfile() {
    final String name = myInspectionProfile.getName();
    myInspectionProfile = (InspectionProfile)myInspectionProfile.getProfileManager().getProfile(name);
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
        myGlobalInspectionContext.doInspections(myScope, InspectionManager.getInstance(myProject));
      }
    }
  }
}
