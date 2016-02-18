/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.codeInspection.ui;

import com.intellij.CommonBundle;
import com.intellij.analysis.AnalysisScope;
import com.intellij.analysis.AnalysisUIOptions;
import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.*;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.ui.actions.ExportHTMLAction;
import com.intellij.codeInspection.ui.actions.InspectionsOptionsToolbarAction;
import com.intellij.codeInspection.ui.actions.InvokeQuickFixAction;
import com.intellij.icons.AllIcons;
import com.intellij.ide.*;
import com.intellij.ide.actions.ContextHelpAction;
import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.pom.Navigatable;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.ui.*;
import com.intellij.ui.components.JBLabel;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.impl.UsagePreviewPanel;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.EditSourceOnDoubleClickHandler;
import com.intellij.util.OpenSourceUtil;
import com.intellij.util.containers.*;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ConcurrentMap;

/**
 * @author max
 */
public class InspectionResultsView extends JPanel implements Disposable, OccurenceNavigator, DataProvider {
  private static final Logger LOG = Logger.getInstance(InspectionResultsView.class);

  public static final DataKey<InspectionResultsView> DATA_KEY = DataKey.create("inspectionView");
  private static final Key<Boolean> PREVIEW_EDITOR_IS_REUSED_KEY = Key.create("inspection.tool.window.preview.editor.is.reused.");

  private final Project myProject;
  private final InspectionTree myTree;
  private final ConcurrentMap<HighlightDisplayLevel, ConcurrentMap<String, InspectionGroupNode>> myGroups = ContainerUtil.newConcurrentMap();
  private final OccurenceNavigator myOccurenceNavigator;
  private volatile InspectionProfile myInspectionProfile;
  private final AnalysisScope myScope;
  @NonNls
  private static final String HELP_ID = "reference.toolWindows.inspections";
  private final ConcurrentMap<HighlightDisplayLevel, InspectionSeverityGroupNode> mySeverityGroupNodes = ContainerUtil.newConcurrentMap();

  private final Splitter mySplitter;
  @NotNull
  private final GlobalInspectionContextImpl myGlobalInspectionContext;
  private boolean myRerun;
  private volatile boolean myDisposed;

  @NotNull
  private final InspectionRVContentProvider myProvider;
  private AnAction myIncludeAction;
  private AnAction myExcludeAction;
  private Editor myPreviewEditor;

  public InspectionResultsView(@NotNull final Project project,
                               final InspectionProfile inspectionProfile,
                               @NotNull AnalysisScope scope,
                               @NotNull GlobalInspectionContextImpl globalInspectionContext,
                               @NotNull InspectionRVContentProvider provider) {
    setLayout(new BorderLayout());

    myProject = project;
    myInspectionProfile = inspectionProfile;
    myScope = scope;
    myGlobalInspectionContext = globalInspectionContext;
    myProvider = provider;

    myTree = new InspectionTree(project, globalInspectionContext);
    initTreeListeners();

    myOccurenceNavigator = initOccurenceNavigator();

    mySplitter = new OnePixelSplitter(false, AnalysisUIOptions.getInstance(myProject).SPLITTER_PROPORTION);

    mySplitter.setFirstComponent(ScrollPaneFactory.createScrollPane(myTree, SideBorder.LEFT | SideBorder.RIGHT));

    mySplitter.addPropertyChangeListener(evt -> {
      if (Splitter.PROP_PROPORTION.equals(evt.getPropertyName())) {
        myGlobalInspectionContext.setSplitterProportion(((Float)evt.getNewValue()).floatValue());
      }
    });
    add(mySplitter, BorderLayout.CENTER);

    createActionsToolbar();
    TreeUtil.selectFirstNode(myTree);
  }

  private void initTreeListeners() {
    myTree.getSelectionModel().addTreeSelectionListener(e -> {
      syncRightPanel();
      if (isAutoScrollMode()) {
        OpenSourceUtil.openSourcesFrom(DataManager.getInstance().getDataContext(InspectionResultsView.this), false);
      }
    });

    EditSourceOnDoubleClickHandler.install(myTree);

    myTree.addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ENTER) {
          OpenSourceUtil.openSourcesFrom(DataManager.getInstance().getDataContext(InspectionResultsView.this), false);
        }
      }
    });

    myTree.addMouseListener(new PopupHandler() {
      @Override
      public void invokePopup(Component comp, int x, int y) {
        popupInvoked(comp, x, y);
      }
    });

    SmartExpander.installOn(myTree);
  }

  private OccurenceNavigatorSupport initOccurenceNavigator(){
    return new OccurenceNavigatorSupport(myTree) {
      @Override
      @Nullable
      protected Navigatable createDescriptorForNode(DefaultMutableTreeNode node) {
        if (node instanceof InspectionTreeNode && ((InspectionTreeNode)node).isResolved()) {
          return null;
        }
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

      @Override
      public String getNextOccurenceActionName() {
        return InspectionsBundle.message("inspection.action.go.next");
      }

      @Override
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

      @Override
      public void actionPerformed(AnActionEvent e) {
        final TreePath[] paths = myTree.getSelectionPaths();
        if (paths != null) {
          for (TreePath path : paths) {
            ((InspectionTreeNode)path.getLastPathComponent()).amnesty();
          }
        }
        updateView(false);
      }

      @Override
      public void update(final AnActionEvent e) {
        final TreePath[] paths = myTree.getSelectionPaths();
        e.getPresentation().setEnabled(paths != null && paths.length > 0 && 
                                       !myGlobalInspectionContext.getUIOptions().FILTER_RESOLVED_ITEMS);
      }
    };

    myExcludeAction = new AnAction(InspectionsBundle.message("inspections.result.view.exclude.action.text")) {
      {
        registerCustomShortcutSet(CommonShortcuts.getDelete(), myTree);
      }

      @Override
      public void actionPerformed(final AnActionEvent e) {
        final TreePath[] paths = myTree.getSelectionPaths();
        if (paths != null) {
          for (TreePath path : paths) {
            ((InspectionTreeNode)path.getLastPathComponent()).ignoreElement();
          }
        }
        updateView(false);
      }

      @Override
      public void update(final AnActionEvent e) {
        final TreePath[] path = myTree.getSelectionPaths();
        e.getPresentation().setEnabled(path != null && path.length > 0);
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
      @Override
      public void expandAll() {
        TreeUtil.expandAll(myTree);
      }

      @Override
      public boolean canExpand() {
        return true;
      }

      @Override
      public void collapseAll() {
        TreeUtil.collapseAll(myTree, 0);
      }

      @Override
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
    group.add(new ContextHelpAction(HELP_ID));

    return createToolbar(group);
  }

  private static JComponent createToolbar(final DefaultActionGroup specialGroup) {
    return ActionManager.getInstance().createActionToolbar(ActionPlaces.CODE_INSPECTION, specialGroup, false).getComponent();
  }

  @Override
  public void dispose() {
    releaseEditor(myPreviewEditor);
    mySplitter.dispose();
    myInspectionProfile = null;
    myDisposed = true;
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
      @Override
      public void run() {
        PsiElement psiElement = refElement.getElement();
        if (psiElement != null) {
          final PsiFile containingFile = psiElement.getContainingFile();
          if (containingFile != null) {
            file[0] = containingFile.getVirtualFile();
            offset[0] = psiElement.getTextOffset();
          }
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

  private void syncRightPanel() {
    final Editor oldEditor = myPreviewEditor;
    if (myTree.getSelectionModel().getSelectionCount() != 1) {
      if (myTree.getSelectedToolWrapper() == null) {
        final JLabel multipleSelectionLabel = new JBLabel(InspectionViewNavigationPanel.getTitleText(false, false));
        multipleSelectionLabel.setVerticalAlignment(SwingConstants.TOP);
        multipleSelectionLabel.setBorder(IdeBorderFactory.createEmptyBorder(5, 7, 0, 0));
        mySplitter.setSecondComponent(multipleSelectionLabel);
      }
      else {
        showInRightPanel(myTree.getCommonSelectedElement());
      }
    }
    else {
      TreePath pathSelected = myTree.getSelectionModel().getLeadSelectionPath();
      if (pathSelected != null) {
        final InspectionTreeNode node = (InspectionTreeNode)pathSelected.getLastPathComponent();
        if (node instanceof RefElementNode) {
          final RefElementNode refElementNode = (RefElementNode)node;
          final RefEntity refSelected = refElementNode.getElement();
          showInRightPanel(refSelected);
        }
        else if (node instanceof ProblemDescriptionNode) {
          final ProblemDescriptionNode problemNode = (ProblemDescriptionNode)node;
          showInRightPanel(problemNode.getElement());
        }
        else if (node instanceof InspectionNode || node instanceof InspectionPackageNode || node instanceof InspectionModuleNode) {
          showInRightPanel(null);
        }
        else if (node instanceof InspectionRootNode || node instanceof InspectionGroupNode || node instanceof InspectionSeverityGroupNode) {
          mySplitter.setSecondComponent(new InspectionViewNavigationPanel(node, myTree));
        }
        else {
          LOG.error("Unexpected node: " + node.getClass());
        }
      }
    }
    if (oldEditor != null) {
      if (Boolean.TRUE.equals(oldEditor.getUserData(PREVIEW_EDITOR_IS_REUSED_KEY))) {
        oldEditor.putUserData(PREVIEW_EDITOR_IS_REUSED_KEY, null);
      }
      else {
        releaseEditor(oldEditor);
      }
    }
  }

  private void showInRightPanel(@Nullable final RefEntity refEntity) {
    Cursor currentCursor = getCursor();
    try {
      setCursor(new Cursor(Cursor.WAIT_CURSOR));
      final JPanel editorPanel = new JPanel();
      editorPanel.setLayout(new BorderLayout());
      final int problemCount = myTree.getSelectedProblemCount();
      editorPanel.add(createBaseRightComponentFor(problemCount, refEntity), BorderLayout.CENTER);
      if (problemCount > 0) {
        final InspectionToolWrapper tool = myTree.getSelectedToolWrapper();
        LOG.assertTrue(tool != null);
        editorPanel.add(new QuickFixToolbar(myTree,
                                            myProject,
                                            myPreviewEditor,
                                            myProvider.getQuickFixes(tool, myTree)),
                        BorderLayout.NORTH);
      }
      mySplitter.setSecondComponent(editorPanel);
    }
    finally {
      setCursor(currentCursor);
    }
  }

  private boolean reuseEditorFor(Document document) {
    return myPreviewEditor != null && !myPreviewEditor.isDisposed() && myPreviewEditor.getDocument() == document;
  }

  private JComponent createBaseRightComponentFor(int problemCount, RefEntity selectedEntity) {
    if (selectedEntity instanceof RefElement && !(((RefElement)selectedEntity).getElement() instanceof PsiDirectory)) {
      PsiElement selectedElement = ((RefElement)selectedEntity).getElement();
      if (problemCount == 1) {
        CommonProblemDescriptor[] descriptors = myTree.getSelectedDescriptors();
        if (descriptors.length != 0) {
          final CommonProblemDescriptor descriptor = descriptors[0];
          if (descriptor instanceof ProblemDescriptorBase) {
            final PsiElement element = ((ProblemDescriptorBase)descriptor).getPsiElement();
            if (element != null) {
              selectedElement = element;
            }
          }
        }
      }
      final PsiFile file = selectedElement.getContainingFile();
      final Document document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);

      if (reuseEditorFor(document)) {
        myPreviewEditor.putUserData(PREVIEW_EDITOR_IS_REUSED_KEY, true);
      }
      else {
        myPreviewEditor = EditorFactory.getInstance().createEditor(document, myProject, file.getVirtualFile(), true);
        final EditorSettings settings = myPreviewEditor.getSettings();
        settings.setLineNumbersShown(false);
        settings.setLineMarkerAreaShown(false);
        settings.setAdditionalColumnsCount(0);
        settings.setAdditionalLinesCount(0);
        settings.setLeadingWhitespaceShown(true);
        settings.setRightMarginShown(true);
        settings.setRightMargin(60);
        UsagePreviewPanel.highlight(Collections.emptyList(), myPreviewEditor, myProject);
      }

      if (problemCount == 1) {
        final PsiElement finalSelectedElement = selectedElement;
        ApplicationManager.getApplication().invokeLater(() -> {
          if (myPreviewEditor != null && !myPreviewEditor.isDisposed()) {
            PsiDocumentManager.getInstance(myProject).commitAllDocuments();
            myPreviewEditor.getCaretModel().moveToOffset(finalSelectedElement.getTextOffset());
            myPreviewEditor.getScrollingModel().scrollToCaret(ScrollType.CENTER);
          }
        }, ModalityState.NON_MODAL);
      }
      return myPreviewEditor.getComponent();
    }
    else if (selectedEntity == null) {
      return new InspectionNodeInfo(myTree.getSelectedToolWrapper(), myProject);
    }
    return new JPanel();
  }

  @NotNull
  public InspectionNode addTool(@NotNull final InspectionToolWrapper toolWrapper, HighlightDisplayLevel errorLevel, boolean groupedBySeverity) {
    String groupName =
      toolWrapper.getGroupDisplayName().isEmpty() ? InspectionProfileEntry.GENERAL_GROUP_NAME : toolWrapper.getGroupDisplayName();
    InspectionTreeNode parentNode = getToolParentNode(groupName, errorLevel, groupedBySeverity);
    InspectionNode toolNode = new InspectionNode(toolWrapper);
    boolean showStructure = myGlobalInspectionContext.getUIOptions().SHOW_STRUCTURE;
    myProvider.appendToolNodeContent(myGlobalInspectionContext, toolNode, parentNode, showStructure);
    InspectionToolPresentation presentation = myGlobalInspectionContext.getPresentation(toolWrapper);
    toolNode = presentation.createToolNode(myGlobalInspectionContext, toolNode, myProvider, parentNode, showStructure);
    ((DefaultInspectionToolPresentation)presentation).setToolNode(toolNode);

    registerActionShortcuts(presentation);
    return toolNode;
  }

  private void registerActionShortcuts(@NotNull InspectionToolPresentation presentation) {
    final QuickFixAction[] fixes = presentation.getQuickFixes(RefEntity.EMPTY_ELEMENTS_ARRAY, null);
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
    return myInspectionProfile == null ? null : myInspectionProfile.getDisplayName();
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
    myTree.restoreExpansionAndSelection();
    return resultsFound;
  }

  private boolean buildTree() {
    InspectionProfile profile = myInspectionProfile;
    boolean isGroupedBySeverity = myGlobalInspectionContext.getUIOptions().GROUP_BY_SEVERITY;
    myGroups.clear();
    final Map<String, Tools> tools = myGlobalInspectionContext.getTools();
    boolean resultsFound = false;
    for (Tools currentTools : tools.values()) {
      InspectionToolWrapper defaultToolWrapper = currentTools.getDefaultState().getTool();
      final HighlightDisplayKey key = HighlightDisplayKey.find(defaultToolWrapper.getShortName());
      for (ScopeToolState state : myProvider.getTools(currentTools)) {
        InspectionToolWrapper toolWrapper = state.getTool();
        if (myProvider.checkReportedProblems(myGlobalInspectionContext, toolWrapper)) {
          addTool(toolWrapper, ((InspectionProfileImpl)profile).getErrorLevel(key, state.getScope(myProject), myProject), isGroupedBySeverity);
          resultsFound = true;
        }
      }
    }
    return resultsFound;
  }

  @NotNull
  private InspectionTreeNode getToolParentNode(@NotNull String groupName, HighlightDisplayLevel errorLevel, boolean groupedBySeverity) {
    if (groupName.isEmpty()) {
      return getRelativeRootNode(groupedBySeverity, errorLevel);
    }
    ConcurrentMap<String, InspectionGroupNode> map = myGroups.get(errorLevel);
    if (map == null) {
      map = ConcurrencyUtil.cacheOrGet(myGroups, errorLevel, ContainerUtil.<String, InspectionGroupNode>newConcurrentMap());
    }
    InspectionGroupNode group;
    if (groupedBySeverity) {
      group = map.get(groupName);
    }
    else {
      group = null;
      for (Map<String, InspectionGroupNode> groupMap : myGroups.values()) {
        if ((group = groupMap.get(groupName)) != null) break;
      }
    }
    if (group == null) {
      group  = ConcurrencyUtil.cacheOrGet(map, groupName, new InspectionGroupNode(groupName));
      addChildNodeInEDT(getRelativeRootNode(groupedBySeverity, errorLevel), group);
    }
    return group;
  }

  @NotNull
  private InspectionTreeNode getRelativeRootNode(boolean isGroupedBySeverity, HighlightDisplayLevel level) {
    if (isGroupedBySeverity) {
      InspectionSeverityGroupNode severityGroupNode = mySeverityGroupNodes.get(level);
      if (severityGroupNode == null) {
        InspectionSeverityGroupNode newNode = new InspectionSeverityGroupNode(myProject, level);
        severityGroupNode = ConcurrencyUtil.cacheOrGet(mySeverityGroupNodes, level, newNode);
        if (severityGroupNode == newNode) {
          InspectionTreeNode root = myTree.getRoot();
          addChildNodeInEDT(root, severityGroupNode);
        }
      }
      return severityGroupNode;
    }
    return myTree.getRoot();
  }

  private void addChildNodeInEDT(@NotNull final DefaultMutableTreeNode root, @NotNull final MutableTreeNode severityGroupNode) {
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        if (!myDisposed) {
          root.add(severityGroupNode);
        }
      }
    });
  }


  private OccurenceNavigator getOccurenceNavigator() {
    return myOccurenceNavigator;
  }

  @Override
  public boolean hasNextOccurence() {
    return myOccurenceNavigator != null && myOccurenceNavigator.hasNextOccurence();
  }

  @Override
  public boolean hasPreviousOccurence() {
    return myOccurenceNavigator != null && myOccurenceNavigator.hasPreviousOccurence();
  }

  @Override
  public OccurenceInfo goNextOccurence() {
    return myOccurenceNavigator != null ? myOccurenceNavigator.goNextOccurence() : null;
  }

  @Override
  public OccurenceInfo goPreviousOccurence() {
    return myOccurenceNavigator != null ? myOccurenceNavigator.goPreviousOccurence() : null;
  }

  @Override
  public String getNextOccurenceActionName() {
    return myOccurenceNavigator != null ? myOccurenceNavigator.getNextOccurenceActionName() : "";
  }

  @Override
  public String getPreviousOccurenceActionName() {
    return myOccurenceNavigator != null ? myOccurenceNavigator.getPreviousOccurenceActionName() : "";
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  @Override
  public Object getData(String dataId) {
    if (PlatformDataKeys.HELP_ID.is(dataId)) return HELP_ID;
    if (DATA_KEY.is(dataId)) return this;
    if (myTree == null) return null;
    TreePath[] paths = myTree.getSelectionPaths();

    if (paths == null || paths.length == 0) return null;

    if (paths.length > 1) {
      if (LangDataKeys.PSI_ELEMENT_ARRAY.is(dataId)) {
        return collectPsiElements();
      }
      return null;
    }

    TreePath path = paths[0];

    InspectionTreeNode selectedNode = (InspectionTreeNode)path.getLastPathComponent();

    if (selectedNode instanceof RefElementNode) {
      final RefElementNode refElementNode = (RefElementNode)selectedNode;
      RefEntity refElement = refElementNode.getElement();
      if (refElement == null) return null;
      final RefEntity item = refElement.getRefManager().getRefinedElement(refElement);

      if (!item.isValid()) return null;

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

      if (CommonDataKeys.NAVIGATABLE.is(dataId)) {
        return getSelectedNavigatable(problem, psiElement);
      }
      else if (CommonDataKeys.PSI_ELEMENT.is(dataId)) {
        return psiElement.isValid() ? psiElement : null;
      }
    }
    else if (selectedNode instanceof ProblemDescriptionNode && CommonDataKeys.NAVIGATABLE.is(dataId)) {
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
    if (descriptor instanceof ProblemDescriptorBase) {
      Navigatable navigatable = ((ProblemDescriptorBase) descriptor).getNavigatable();
      if (navigatable != null) {
        return navigatable;
      }
    }
    if (psiElement == null || !psiElement.isValid()) return null;
    PsiFile containingFile = psiElement.getContainingFile();
    VirtualFile virtualFile = containingFile == null ? null : containingFile.getVirtualFile();

    if (virtualFile != null) {
      int startOffset = psiElement.getTextOffset();
      if (descriptor instanceof ProblemDescriptorBase) {
        final TextRange textRange = ((ProblemDescriptorBase)descriptor).getTextRangeForNavigation();
        if (textRange != null) {
          if (virtualFile instanceof VirtualFileWindow) {
            virtualFile = ((VirtualFileWindow)virtualFile).getDelegate();
          }
          startOffset = textRange.getStartOffset();
        }
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

    return PsiUtilCore.toPsiElementArray(psiElements);
  }

  private void popupInvoked(Component component, int x, int y) {
    final TreePath path = myTree.getLeadSelectionPath();

    if (path == null) return;

    final DefaultActionGroup actions = new DefaultActionGroup();
    final ActionManager actionManager = ActionManager.getInstance();
    actions.add(actionManager.getAction(IdeActions.ACTION_EDIT_SOURCE));
    actions.add(actionManager.getAction(IdeActions.ACTION_FIND_USAGES));

    actions.add(myIncludeAction);
    actions.add(myExcludeAction);

    actions.addSeparator();

    final InspectionToolWrapper toolWrapper = myTree.getSelectedToolWrapper();
    if (toolWrapper != null) {
      final QuickFixAction[] quickFixes = myProvider.getQuickFixes(toolWrapper, myTree);
      if (quickFixes != null) {
        for (QuickFixAction quickFixe : quickFixes) {
          actions.add(quickFixe);
        }
      }
      final HighlightDisplayKey key = HighlightDisplayKey.find(toolWrapper.getShortName());
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

  @NotNull
  public InspectionTree getTree(){
    return myTree;
  }

  @NotNull
  public GlobalInspectionContextImpl getGlobalInspectionContext() {
    return myGlobalInspectionContext;
  }

  @NotNull
  public InspectionRVContentProvider getProvider() {
    return myProvider;
  }

  public boolean isSingleToolInSelection() {
    return myTree != null && myTree.getSelectedToolWrapper() != null;
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
          profiles.add(profileManager.getInspectionProfile());
        }
      }
    }
    if (profiles.isEmpty()) {
      return (InspectionProfile)profileManager.getProjectProfileImpl();
    }
    return profiles.iterator().next();
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

  public void updateRightPanel() {
    syncRightPanel();
  }

  private class CloseAction extends AnAction implements DumbAware {
    private CloseAction() {
      super(CommonBundle.message("action.close"), null, AllIcons.Actions.Cancel);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      myGlobalInspectionContext.close(true);
    }
  }

  private class EditSettingsAction extends AnAction {
    private EditSettingsAction() {
      super(InspectionsBundle.message("inspection.action.edit.settings"), InspectionsBundle.message("inspection.action.edit.settings"),
            AllIcons.General.Settings);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      final InspectionProjectProfileManager profileManager = InspectionProjectProfileManager.getInstance(myProject);
      final InspectionToolWrapper toolWrapper = myTree.getSelectedToolWrapper();
      InspectionProfile inspectionProfile = myInspectionProfile;
      final boolean profileIsDefined = isProfileDefined();
      if (!profileIsDefined) {
        inspectionProfile = guessProfileToSelect(profileManager);
      }

      if (toolWrapper != null) {
        final HighlightDisplayKey key = HighlightDisplayKey.find(toolWrapper.getShortName()); //do not search for dead code entry point tool
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

  private class RerunAction extends AnAction {
    public RerunAction(JComponent comp) {
      super(InspectionsBundle.message("inspection.action.rerun"), InspectionsBundle.message("inspection.action.rerun"),
            AllIcons.Actions.Rerun);
      registerCustomShortcutSet(CommonShortcuts.getRerun(), comp);
    }

    @Override
    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(myScope.isValid());
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      rerun();
    }
    private void rerun() {
      myRerun = true;
      if (myScope.isValid()) {
        AnalysisUIOptions.getInstance(myProject).save(myGlobalInspectionContext.getUIOptions());
        myGlobalInspectionContext.doInspections(myScope);
      }
    }
  }

  private static void releaseEditor(@Nullable Editor editor) {
    if (editor != null && !editor.isDisposed()) {
      EditorFactory.getInstance().releaseEditor(editor);
    }
  }
}
