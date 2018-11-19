// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInspection.ui;

import com.intellij.analysis.AnalysisScope;
import com.intellij.analysis.AnalysisUIOptions;
import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.*;
import com.intellij.codeInspection.offlineViewer.OfflineInspectionRVContentProvider;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.ui.actions.ExportHTMLAction;
import com.intellij.codeInspection.ui.actions.InvokeQuickFixAction;
import com.intellij.diff.util.DiffUtil;
import com.intellij.ide.*;
import com.intellij.ide.actions.exclusion.ExclusionHandler;
import com.intellij.ide.util.PsiNavigationSupport;
import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.pom.Navigatable;
import com.intellij.profile.ProfileChangeAdapter;
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.ui.*;
import com.intellij.util.EditSourceOnDoubleClickHandler;
import com.intellij.util.ObjectUtils;
import com.intellij.util.OpenSourceUtil;
import com.intellij.util.concurrency.SequentialTaskExecutor;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.*;
import java.util.concurrent.ExecutorService;

public class InspectionResultsView extends JPanel implements Disposable, DataProvider, OccurenceNavigator {
  private static final Logger LOG = Logger.getInstance(InspectionResultsView.class);

  public static final DataKey<InspectionResultsView> DATA_KEY = DataKey.create("inspectionView");
  private static final Key<Boolean> PREVIEW_EDITOR_IS_REUSED_KEY = Key.create("inspection.tool.window.preview.editor.is.reused");

  @NotNull
  private final InspectionTree myTree;
  @NotNull
  private final OccurenceNavigator myOccurenceNavigator;
  private volatile InspectionProfileImpl myInspectionProfile;
  private final boolean mySettingsEnabled;
  @NotNull
  private final AnalysisScope myScope;
  @NonNls
  public static final String HELP_ID = "reference.toolWindows.inspections";

  private final Splitter mySplitter;
  @NotNull
  private final GlobalInspectionContextImpl myGlobalInspectionContext;
  private boolean myRerun;
  private volatile boolean myDisposed;
  private int myUpdatingRequestors; //accessed only in edt
  private boolean myApplyingFix; //accessed only in edt

  @NotNull
  private final InspectionRVContentProvider myProvider;
  @NotNull
  private final ExclusionHandler<InspectionTreeNode> myExclusionHandler;
  private EditorEx myPreviewEditor;
  private InspectionTreeLoadingProgressAware myLoadingProgressPreview;
  private final InspectionViewSuppressActionHolder mySuppressActionHolder = new InspectionViewSuppressActionHolder();

  private final Object myTreeStructureUpdateLock = new Object();
  private final ExecutorService myTreeUpdater = SequentialTaskExecutor
    .createSequentialApplicationPoolExecutor("Inspection-View-Tree-Updater");

  public InspectionResultsView(@NotNull GlobalInspectionContextImpl globalInspectionContext,
                               @NotNull InspectionRVContentProvider provider) {
    setLayout(new BorderLayout());
    myInspectionProfile = globalInspectionContext.getCurrentProfile();
    myScope = globalInspectionContext.getCurrentScope();
    myGlobalInspectionContext = globalInspectionContext;
    myProvider = provider;
    myTree = new InspectionTree(globalInspectionContext, this);
    initTreeListeners();

    myOccurenceNavigator = initOccurenceNavigator();

    mySplitter = new OnePixelSplitter(false, AnalysisUIOptions.getInstance(globalInspectionContext.getProject()).SPLITTER_PROPORTION);
    mySplitter.setFirstComponent(ScrollPaneFactory.createScrollPane(myTree, SideBorder.LEFT));
    mySplitter.setHonorComponentsMinimumSize(false);

    mySplitter.addPropertyChangeListener(evt -> {
      if (Splitter.PROP_PROPORTION.equals(evt.getPropertyName())) {
        myGlobalInspectionContext.setSplitterProportion(((Float)evt.getNewValue()).floatValue());
      }
    });
    add(mySplitter, BorderLayout.CENTER);
    myExclusionHandler = new ExclusionHandler<InspectionTreeNode>() {
      @Override
      public boolean isNodeExclusionAvailable(@NotNull InspectionTreeNode node) {
        return true;
      }

      @Override
      public boolean isNodeExcluded(@NotNull InspectionTreeNode node) {
        return node.isExcluded();
      }

      @Override
      public void excludeNode(@NotNull InspectionTreeNode node) {
        node.excludeElement();
        node.dropProblemCountCaches();
      }

      @Override
      public void includeNode(@NotNull InspectionTreeNode node) {
        node.amnestyElement();
        node.dropProblemCountCaches();
      }

      @Override
      public boolean isActionEnabled(boolean isExcludeAction) {
        return isExcludeAction || !myGlobalInspectionContext.getUIOptions().FILTER_RESOLVED_ITEMS;
      }

      @Override
      public void onDone(boolean isExcludeAction) {
        if (isExcludeAction) {
          myTree.removeSelectedProblems();
          myTree.repaint();
        }
        else {
          resetTree();
        }
        syncRightPanel();
      }
    };
    createActionsToolbar();

    PsiManager.getInstance(getProject()).addPsiTreeChangeListener(new InspectionViewChangeAdapter(this), this);

    ProjectInspectionProfileManager profileManager = ProjectInspectionProfileManager.getInstance(getProject());
    profileManager.addProfileChangeListener(new ProfileChangeAdapter() {
      @Override
      public void profileChanged(InspectionProfile profile) {
        if (profile == profileManager.getCurrentProfile()) {
          InspectionResultsView.this.profileChanged();
        }
      }
    }, this);

    if (!isSingleInspectionRun()) {
      mySettingsEnabled = true;
    } else {
      InspectionProfileImpl profile = getCurrentProfile();
      String toolId = ObjectUtils.notNull(profile.getSingleTool());
      InspectionToolWrapper tool = ObjectUtils.notNull(profile.getInspectionTool(toolId, getProject()));
      JComponent toolPanel = tool.getTool().createOptionsPanel();
      mySettingsEnabled = toolPanel != null;
    }
  }

  void profileChanged() {
    UIUtil.invokeLaterIfNeeded(() -> {
      myTree.revalidate();
      myTree.repaint();
      syncRightPanel();
    });
  }

  private void initTreeListeners() {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return;
    }
    myTree.getSelectionModel().addTreeSelectionListener(e -> {
      if (myTree.isUnderQueueUpdate()) return;
      syncRightPanel();
      if (isAutoScrollMode()) {
        OpenSourceUtil.openSourcesFrom(DataManager.getInstance().getDataContext(this), false);
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

    PopupHandler.installPopupHandler(myTree, IdeActions.INSPECTION_TOOL_WINDOW_TREE_POPUP, ActionPlaces.CODE_INSPECTION);
    SmartExpander.installOn(myTree);
  }

  @NotNull
  private OccurenceNavigatorSupport initOccurenceNavigator() {
    return new OccurenceNavigatorSupport(myTree) {
      @Override
      protected boolean isOccurrenceNode(@NotNull DefaultMutableTreeNode node) {
        if (node instanceof InspectionTreeNode && ((InspectionTreeNode)node).isExcluded()) {
          return false;
        }
        if (node instanceof RefElementNode) {
          final RefElementNode refNode = (RefElementNode)node;
          if (refNode.hasDescriptorsUnder()) return false;
          final RefEntity element = refNode.getElement();
          return element != null && element.isValid();
        }
        return node instanceof ProblemDescriptionNode;
      }

      @Override
      @Nullable
      protected Navigatable createDescriptorForNode(@NotNull DefaultMutableTreeNode node) {
        if (node instanceof InspectionTreeNode && ((InspectionTreeNode)node).isExcluded()) {
          return null;
        }
        if (node instanceof RefElementNode) {
          final RefElementNode refNode = (RefElementNode)node;
          if (refNode.hasDescriptorsUnder()) return null;
          final RefEntity element = refNode.getElement();
          if (element == null || !element.isValid()) return null;
          final CommonProblemDescriptor problem = refNode.getDescriptor();
          if (problem != null) {
            return navigate(problem);
          }
          if (element instanceof RefElement) {
            return getOpenFileDescriptor((RefElement)element);
          }
        }
        else if (node instanceof ProblemDescriptionNode) {
          ProblemDescriptionNode problemNode = (ProblemDescriptionNode)node;
          boolean isValid = problemNode.isValid() && (!problemNode.isQuickFixAppliedFromView() ||
                                                      problemNode.calculateIsValid());
          return isValid
                 ? navigate(problemNode.getDescriptor())
                 : InspectionResultsViewUtil.getNavigatableForInvalidNode(problemNode);
        }
        return null;
      }

      @Nullable
      private Navigatable navigate(final CommonProblemDescriptor descriptor) {
        return getSelectedNavigatable(descriptor);
      }

      @NotNull
      @Override
      public String getNextOccurenceActionName() {
        return InspectionsBundle.message("inspection.action.go.next");
      }

      @NotNull
      @Override
      public String getPreviousOccurenceActionName() {
        return InspectionsBundle.message("inspection.action.go.prev");
      }
    };
  }

  private void createActionsToolbar() {
    JPanel westPanel = JBUI.Panels.simplePanel()
      .addToLeft(createLeftActionsToolbar())
      .addToRight(createRightActionsToolbar());
    add(westPanel, BorderLayout.WEST);
  }

  private JComponent createRightActionsToolbar() {
    DefaultActionGroup specialGroup = new DefaultActionGroup();
    specialGroup.add(myGlobalInspectionContext.getUIOptions().createGroupBySeverityAction(this));
    specialGroup.add(myGlobalInspectionContext.getUIOptions().createGroupByDirectoryAction(this));
    specialGroup.add(myGlobalInspectionContext.getUIOptions().createFilterResolvedItemsAction(this));
    specialGroup.add(myGlobalInspectionContext.createToggleAutoscrollAction());
    specialGroup.add(new ExportHTMLAction(this));
    specialGroup.add(new InvokeQuickFixAction(this));
    return createToolbar(specialGroup);
  }

  private JComponent createLeftActionsToolbar() {
    final CommonActionsManager actionsManager = CommonActionsManager.getInstance();
    DefaultActionGroup group = new DefaultActionGroup();
    group.add(new RerunAction(this));
    final TreeExpander treeExpander = new DefaultTreeExpander(myTree);
    group.add(actionsManager.createExpandAllAction(treeExpander, myTree));
    group.add(actionsManager.createCollapseAllAction(treeExpander, myTree));
    group.add(actionsManager.createPrevOccurenceAction(myOccurenceNavigator));
    group.add(actionsManager.createNextOccurenceAction(myOccurenceNavigator));
    group.add(ActionManager.getInstance().getAction("EditInspectionSettings"));

    return createToolbar(group);
  }

  @Override
  public boolean hasNextOccurence() {
    return myOccurenceNavigator.hasNextOccurence();
  }

  @Override
  public boolean hasPreviousOccurence() {
    return myOccurenceNavigator.hasPreviousOccurence();
  }

  @Override
  public OccurenceInfo goNextOccurence() {
    return myOccurenceNavigator.goNextOccurence();
  }

  @Override
  public OccurenceInfo goPreviousOccurence() {
    return myOccurenceNavigator.goPreviousOccurence();
  }

  @NotNull
  @Override
  public String getNextOccurenceActionName() {
    return myOccurenceNavigator.getNextOccurenceActionName();
  }

  @NotNull
  @Override
  public String getPreviousOccurenceActionName() {
    return myOccurenceNavigator.getPreviousOccurenceActionName();
  }

  private static JComponent createToolbar(final DefaultActionGroup specialGroup) {
    final ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.CODE_INSPECTION, specialGroup, false);
    //toolbar.setTargetComponent(this);
    return toolbar.getComponent();
  }

  @Override
  public void dispose() {
    InspectionResultsViewUtil.releaseEditor(myPreviewEditor);
    mySplitter.dispose();
    myInspectionProfile = null;
    myDisposed = true;
    if (myLoadingProgressPreview != null) {
      Disposer.dispose(myLoadingProgressPreview);
      myLoadingProgressPreview = null;
    }
  }

  private boolean isAutoScrollMode() {
    String activeToolWindowId = ToolWindowManager.getInstance(getProject()).getActiveToolWindowId();
    return myGlobalInspectionContext.getUIOptions().AUTOSCROLL_TO_SOURCE &&
           (activeToolWindowId == null || activeToolWindowId.equals(ToolWindowId.INSPECTION));
  }

  Object getTreeStructureUpdateLock() {
    return myTreeStructureUpdateLock;
  }

  @Nullable
  private static Navigatable getOpenFileDescriptor(final RefElement refElement) {
    PsiElement psiElement = refElement.getPsiElement();
    if (psiElement == null) return null;
    final PsiFile containingFile = psiElement.getContainingFile();
    if (containingFile == null) return null;
    VirtualFile file = containingFile.getVirtualFile();
    if (file == null) return null;
    return PsiNavigationSupport.getInstance().createNavigatable(refElement.getRefManager().getProject(), file,
                                                                psiElement.getTextOffset());
  }

  public void setApplyingFix(boolean applyingFix) {
    myApplyingFix = applyingFix;
    syncRightPanel();
  }

  void openRightPanelIfNeed() {
    if (mySplitter.getSecondComponent() == null) {
      syncRightPanel();
    }
  }

  public void syncRightPanel() {
    final Editor oldEditor = myPreviewEditor;
    try {
      if (myLoadingProgressPreview != null) {
        Disposer.dispose(myLoadingProgressPreview);
        myLoadingProgressPreview = null;
      }
      if (myApplyingFix) {
        final InspectionToolWrapper wrapper = myTree.getSelectedToolWrapper(true);
        LOG.assertTrue(wrapper != null);
        mySplitter.setSecondComponent(InspectionResultsViewUtil.getApplyingFixLabel(wrapper));
      }
      else {
        if (myTree.getSelectionModel().getSelectionCount() != 1) {
          if (myTree.getSelectedToolWrapper(true) == null) {
            mySplitter.setSecondComponent(InspectionResultsViewUtil.getNothingToShowTextLabel());
          }
          else {
            showInRightPanel(myTree.getCommonSelectedElement());
          }
        }
        else {
          TreePath pathSelected = myTree.getSelectionModel().getLeadSelectionPath();
          if (pathSelected != null) {
            final InspectionTreeNode node = (InspectionTreeNode)pathSelected.getLastPathComponent();
            if (node instanceof ProblemDescriptionNode) {
              final ProblemDescriptionNode problemNode = (ProblemDescriptionNode)node;
              showInRightPanel(problemNode.getElement());
            }
            else if (node instanceof InspectionPackageNode ||
                     node instanceof InspectionModuleNode ||
                     node instanceof RefElementNode) {
              showInRightPanel(node.getContainingFileLocalEntity());
            }
            else if (node instanceof InspectionNode) {
              if (myGlobalInspectionContext.getPresentation(((InspectionNode)node).getToolWrapper()).isDummy()) {
                mySplitter.setSecondComponent(InspectionResultsViewUtil.getNothingToShowTextLabel());
              }
              else {
                showInRightPanel(null);
              }
            }
            else if (node instanceof InspectionGroupNode || node instanceof InspectionSeverityGroupNode) {
              final InspectionViewNavigationPanel panel = new InspectionViewNavigationPanel(node, myTree);
              myLoadingProgressPreview = panel;
              mySplitter.setSecondComponent(panel);
            }
            else {
              LOG.error("Unexpected node: " + node.getClass());
            }
          }
        }
      }
    } finally {
      if (oldEditor != null) {
        if (Boolean.TRUE.equals(oldEditor.getUserData(PREVIEW_EDITOR_IS_REUSED_KEY))) {
          oldEditor.putUserData(PREVIEW_EDITOR_IS_REUSED_KEY, null);
        }
        else {
          InspectionResultsViewUtil.releaseEditor(oldEditor);
          if (oldEditor == myPreviewEditor) {
            myPreviewEditor = null;
          }
        }
      }
    }
  }

  private void showInRightPanel(@Nullable final RefEntity refEntity) {
    final JPanel editorPanel = new JPanel();
    editorPanel.setLayout(new BorderLayout());
    final JPanel actionsPanel = new JPanel(new BorderLayout());
    editorPanel.add(actionsPanel, BorderLayout.NORTH);
    final int problemCount = myTree.getSelectedProblemCount(true);
    JComponent previewPanel = null;
    final InspectionToolWrapper tool = myTree.getSelectedToolWrapper(true);
    if (tool != null) {
      final InspectionToolPresentation presentation = myGlobalInspectionContext.getPresentation(tool);
      final TreePath path = myTree.getSelectionPath();
      if (path != null) {
        Object last = path.getLastPathComponent();
        if (last instanceof ProblemDescriptionNode) {
          CommonProblemDescriptor descriptor = ((ProblemDescriptionNode)last).getDescriptor();
          if (descriptor != null) {
            previewPanel = presentation.getCustomPreviewPanel(descriptor, this);
            JComponent customActions = presentation.getCustomActionsPanel(descriptor, this);
            if (customActions != null) {
              actionsPanel.add(customActions, BorderLayout.EAST);
            }
          }
        }
        else {
          if (refEntity != null && refEntity.isValid()) {
            previewPanel = presentation.getCustomPreviewPanel(refEntity);
          }
        }
      }
    }
    EditorEx previewEditor = null;
    if (previewPanel == null) {
      final Pair<JComponent, EditorEx> panelAndEditor = createBaseRightComponentFor(problemCount, refEntity);
      previewPanel = panelAndEditor.getFirst();
      previewEditor = panelAndEditor.getSecond();
    }
    editorPanel.add(previewPanel, BorderLayout.CENTER);
    if (problemCount > 0) {
      final JComponent fixToolbar = QuickFixPreviewPanelFactory.create(this);
      if (fixToolbar != null) {
        if (fixToolbar instanceof InspectionTreeLoadingProgressAware) {
          myLoadingProgressPreview = (InspectionTreeLoadingProgressAware)fixToolbar;
        }
        if (previewEditor != null) {
          previewPanel.setBorder(IdeBorderFactory.createBorder(SideBorder.TOP));
        }
        actionsPanel.add(fixToolbar, BorderLayout.WEST);
      }
    }
    if (previewEditor != null) {
      ProblemPreviewEditorPresentation.setupFoldingsAndHighlightProblems(previewEditor, this);
    }
    mySplitter.setSecondComponent(editorPanel);
  }

  private Pair<JComponent, EditorEx> createBaseRightComponentFor(int problemCount, RefEntity selectedEntity) {
    if (selectedEntity instanceof RefElement &&
        selectedEntity.isValid() &&
        !(((RefElement)selectedEntity).getPsiElement() instanceof PsiDirectory)) {
      PsiElement selectedElement = ((RefElement)selectedEntity).getPsiElement();
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
      if (document == null) {
        return Pair.create(InspectionResultsViewUtil.createLabelForText("Can't open preview for \'" + file.getName() + "\'"), null);
      }

      if (reuseEditorFor(document)) {
        myPreviewEditor.putUserData(PREVIEW_EDITOR_IS_REUSED_KEY, true);
        myPreviewEditor.getFoldingModel().runBatchFoldingOperation(() -> myPreviewEditor.getFoldingModel().clearFoldRegions());
        myPreviewEditor.getMarkupModel().removeAllHighlighters();
      }
      else {
        myPreviewEditor = (EditorEx)EditorFactory.getInstance().createEditor(document, getProject(), file.getVirtualFile(), true);
        DiffUtil.setFoldingModelSupport(myPreviewEditor);
        final EditorSettings settings = myPreviewEditor.getSettings();
        settings.setLineNumbersShown(false);
        settings.setFoldingOutlineShown(true);
        settings.setLineMarkerAreaShown(true);
        settings.setGutterIconsShown(false);
        settings.setAdditionalColumnsCount(0);
        settings.setAdditionalLinesCount(0);
        settings.setLeadingWhitespaceShown(true);
        myPreviewEditor.getColorsScheme().setColor(EditorColors.GUTTER_BACKGROUND, myPreviewEditor.getColorsScheme().getDefaultBackground());
        myPreviewEditor.getScrollPane().setBorder(JBUI.Borders.empty());
      }
      if (problemCount == 0) {
        myPreviewEditor.getScrollingModel().scrollTo(myPreviewEditor.offsetToLogicalPosition(selectedElement.getTextOffset()), ScrollType.CENTER_UP);
      }
      myPreviewEditor.getComponent().setBorder(JBUI.Borders.empty());
      return Pair.create(myPreviewEditor.getComponent(), myPreviewEditor);
    }
    if (selectedEntity == null) {
      return Pair.create(new InspectionNodeInfo(myTree, getProject()), null);
    }
    if (selectedEntity.isValid()) {
      return Pair.create(InspectionResultsViewUtil.getPreviewIsNotAvailable(selectedEntity), null);
    }
    return Pair.create(InspectionResultsViewUtil.getInvalidEntityLabel(selectedEntity), null);
  }

  private boolean reuseEditorFor(Document document) {
    return myPreviewEditor != null && !myPreviewEditor.isDisposed() && myPreviewEditor.getDocument() == document;
  }

  private void addTool(@NotNull final InspectionToolWrapper toolWrapper,
                       HighlightDisplayLevel errorLevel,
                       boolean groupedBySeverity,
                       boolean isSingleInspectionRun) {
    InspectionTreeNode parentNode = myTree.getToolParentNode(toolWrapper, errorLevel, groupedBySeverity, isSingleInspectionRun);
    InspectionNode toolNode = new InspectionNode(toolWrapper, myInspectionProfile);
    boolean showStructure = myGlobalInspectionContext.getUIOptions().SHOW_STRUCTURE;
    toolNode = myProvider.appendToolNodeContent(myGlobalInspectionContext, toolNode, parentNode, showStructure, groupedBySeverity);
    InspectionToolPresentation presentation = myGlobalInspectionContext.getPresentation(toolWrapper);
    presentation.createToolNode(myGlobalInspectionContext, toolNode, myProvider, parentNode, showStructure, groupedBySeverity);
    registerActionShortcuts(presentation);
  }

  private void registerActionShortcuts(@NotNull InspectionToolPresentation presentation) {
    ApplicationManager.getApplication().invokeLater(() -> {
      for (QuickFixAction fix : presentation.getQuickFixes(RefEntity.EMPTY_ELEMENTS_ARRAY)) {
        fix.registerCustomShortcutSet(fix.getShortcutSet(), this);
      }
    });
  }

  public InspectionViewSuppressActionHolder getSuppressActionHolder() {
    return mySuppressActionHolder;
  }

  @Nullable
  public String getCurrentProfileName() {
    return myInspectionProfile == null ? null : myInspectionProfile.getDisplayName();
  }

  public InspectionProfileImpl getCurrentProfile() {
    return myInspectionProfile;
  }

  void addProblemDescriptors(InspectionToolWrapper wrapper, RefEntity refElement, CommonProblemDescriptor[] descriptors) {
    updateTree(() -> {
      synchronized (myTreeStructureUpdateLock) {
        ReadAction.run(() -> {
          if (!isDisposed()) {
            ApplicationManager.getApplication().assertReadAccessAllowed();
            final AnalysisUIOptions uiOptions = myGlobalInspectionContext.getUIOptions();
            final InspectionToolPresentation presentation = myGlobalInspectionContext.getPresentation(wrapper);
            if (presentation.getToolNode() == null) {
              presentation.updateContent();
              addTool(wrapper, HighlightDisplayLevel.find(presentation.getSeverity((RefElement)refElement)),
                      uiOptions.GROUP_BY_SEVERITY, isSingleInspectionRun());
              return;
            }
            final InspectionNode toolNode = presentation.getToolNode();
            LOG.assertTrue(toolNode != null);
            final Map<RefEntity, CommonProblemDescriptor[]> problems = new HashMap<>(1);
            problems.put(refElement, descriptors);
            final Map<String, Set<RefEntity>> contents = new HashMap<>();
            final String groupName = refElement.getRefManager().getGroupName((RefElement)refElement);
            Set<RefEntity> content = contents.computeIfAbsent(groupName, __ -> new HashSet<>());
            content.add(refElement);

            getProvider().appendToolNodeContent(myGlobalInspectionContext,
                                                toolNode,
                                                (InspectionTreeNode)toolNode.getParent(),
                                                uiOptions.SHOW_STRUCTURE,
                                                true,
                                                contents,
                                                problems::get);
          }
        });
      }
    });
  }

  public void update() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    final Application app = ApplicationManager.getApplication();
    Collection<Tools> tools = new ArrayList<>(myGlobalInspectionContext.getTools().values());
    final Runnable buildAction = () -> {
      try {
        setUpdating(true);
        synchronized (myTreeStructureUpdateLock) {
          myTree.removeAllNodes();
          addToolsSynchronously(tools);
        }
      }
      finally {
        setUpdating(false);
        UIUtil.invokeLaterIfNeeded(() -> myTree.restoreExpansionAndSelection(false));
      }
    };
    if (app.isUnitTestMode()) {
      buildAction.run();
    } else {
      updateTree(buildAction);
    }
  }

  public void setUpdating(boolean isUpdating) {
    final Runnable update = () -> {
      if (isUpdating) {
        myUpdatingRequestors++;
      } else {
        myUpdatingRequestors--;
      }
      boolean hasUpdatingRequestors = myUpdatingRequestors > 0;
      myTree.setPaintBusy(hasUpdatingRequestors);
      if (!hasUpdatingRequestors && myLoadingProgressPreview != null) {
        myLoadingProgressPreview.treeLoaded();
      }
      //TODO Dmitrii Batkovich it's a hack (preview panel should be created during selection update)
      if (!hasUpdatingRequestors && mySplitter.getSecondComponent() == null) {
        final int count = myTree.getRoot().getChildCount();
        if (count != 0) {
          if (myTree.getSelectionCount() == 0) {
            TreeUtil.selectFirstNode(myTree);
          }
          syncRightPanel();
        }
      }
    };
    final Application app = ApplicationManager.getApplication();
    if (app.isDispatchThread()) {
      update.run();
    }
    else {
      app.invokeLater(update, ModalityState.any());
    }
  }

  public void addTools(Collection<? extends Tools> tools) {
    updateTree(() -> addToolsSynchronously(tools));
  }

  private void addToolsSynchronously(Collection<? extends Tools> tools) {
    if (isDisposed()) return;
    synchronized (myTreeStructureUpdateLock) {
      InspectionProfileImpl profile = myInspectionProfile;
      boolean isGroupedBySeverity = myGlobalInspectionContext.getUIOptions().GROUP_BY_SEVERITY;
      boolean singleInspectionRun = isSingleInspectionRun();
      for (Tools currentTools : tools) {
        InspectionToolWrapper defaultToolWrapper = currentTools.getDefaultState().getTool();
        final HighlightDisplayKey key = HighlightDisplayKey.find(defaultToolWrapper.getShortName());
        for (ScopeToolState state : myProvider.getTools(currentTools)) {
          InspectionToolWrapper toolWrapper = state.getTool();
          if (ReadAction.compute(() -> myProvider.checkReportedProblems(myGlobalInspectionContext, toolWrapper))) {
            addTool(toolWrapper,
                    profile.getErrorLevel(key, state.getScope(getProject()), getProject()),
                    isGroupedBySeverity,
                    singleInspectionRun);
          }
        }
      }
    }
    ApplicationManager.getApplication().invokeLater(() -> {
      if (myTree.getSelectionCount() == 0) {
        TreeUtil.selectFirstNode(myTree);
      }
      syncRightPanel();
    });
  }

  @NotNull
  public Project getProject() {
    return myGlobalInspectionContext.getProject();
  }

  @Override
  public Object getData(@NotNull String dataId) {
    if (PlatformDataKeys.HELP_ID.is(dataId)) return HELP_ID;
    if (DATA_KEY.is(dataId)) return this;
    if (ExclusionHandler.EXCLUSION_HANDLER.is(dataId)) return myExclusionHandler;
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

    if (!CommonDataKeys.NAVIGATABLE.is(dataId) && !CommonDataKeys.PSI_ELEMENT.is(dataId)) {
      return null;
    }

    if (selectedNode instanceof RefElementNode) {
      final RefElementNode refElementNode = (RefElementNode)selectedNode;
      RefEntity refElement = refElementNode.getElement();
      if (refElement == null || !refElement.isValid()) return null;
      final RefEntity item = refElement.getRefManager().getRefinedElement(refElement);

      if (!item.isValid()) return null;

      PsiElement psiElement = item instanceof RefElement ? ((RefElement)item).getPsiElement() : null;
      if (psiElement == null) return null;

      final CommonProblemDescriptor problem = refElementNode.getDescriptor();
      if (problem instanceof ProblemDescriptor) {
        PsiElement elementFromDescriptor = ((ProblemDescriptor)problem).getPsiElement();
        if (elementFromDescriptor == null && CommonDataKeys.NAVIGATABLE.is(dataId) && refElementNode.getChildCount() != 0) {
          final InspectionTreeNode node = (InspectionTreeNode)refElementNode.getChildAt(0);
          if (node.isValid()) {
            return InspectionResultsViewUtil.getNavigatableForInvalidNode((ProblemDescriptionNode)node);
          }
        }
        else {
          psiElement = elementFromDescriptor;
        }
      }

      if (CommonDataKeys.NAVIGATABLE.is(dataId)) {
        return getSelectedNavigatable(problem, psiElement);
      }
      else if (CommonDataKeys.PSI_ELEMENT.is(dataId)) {
        return psiElement != null && psiElement.isValid() ? psiElement : null;
      }
    }
    else if (selectedNode instanceof ProblemDescriptionNode && CommonDataKeys.NAVIGATABLE.is(dataId)) {
      Navigatable navigatable = getSelectedNavigatable(((ProblemDescriptionNode)selectedNode).getDescriptor());
      return navigatable == null ? InspectionResultsViewUtil.getNavigatableForInvalidNode((ProblemDescriptionNode)selectedNode) : navigatable;
    }

    return null;
  }

  void resetTree() {
    try {
      myTree.setQueueUpdate(true);
      final TreePath[] selectionPath = myTree.getSelectionPaths();
      final List<TreePath> expandedPaths = TreeUtil.collectExpandedPaths(myTree);
      ((DefaultTreeModel)myTree.getModel()).reload();
      TreeUtil.restoreExpandedPaths(myTree, expandedPaths);
      myTree.setSelectionPaths(selectionPath);
    }
    finally {
      myTree.setQueueUpdate(false);
    }
  }

  @Nullable
  private Navigatable getSelectedNavigatable(final CommonProblemDescriptor descriptor) {
    return getSelectedNavigatable(descriptor,
                                  descriptor instanceof ProblemDescriptor ? ((ProblemDescriptor)descriptor).getPsiElement() : null);
  }

  @Nullable
  private Navigatable getSelectedNavigatable(final CommonProblemDescriptor descriptor, final PsiElement psiElement) {
    if (descriptor instanceof ProblemDescriptorBase) {
      Navigatable navigatable = ((ProblemDescriptorBase)descriptor).getNavigatable();
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
      return PsiNavigationSupport.getInstance().createNavigatable(getProject(), virtualFile, startOffset);
    }
    return null;
  }

  @NotNull
  private PsiElement[] collectPsiElements() {
    RefEntity[] refElements = myTree.getSelectedElements();
    List<PsiElement> psiElements = new ArrayList<>();
    for (RefEntity refElement : refElements) {
      PsiElement psiElement = refElement instanceof RefElement ? ((RefElement)refElement).getPsiElement() : null;
      if (psiElement != null && psiElement.isValid()) {
        psiElements.add(psiElement);
      }
    }

    return PsiUtilCore.toPsiElementArray(psiElements);
  }

  @NotNull
  public InspectionTree getTree() {
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
    return myTree.getSelectedToolWrapper(true) != null;
  }

  public boolean isRerun() {
    boolean rerun = myRerun;
    myRerun = false;
    return rerun;
  }

  public boolean areSettingsEnabled() {
    return mySettingsEnabled;
  }

  public boolean isSingleInspectionRun() {
    return myInspectionProfile.getSingleTool() != null;
  }

  public static void showPopup(AnActionEvent e, JBPopup popup) {
    final InputEvent event = e.getInputEvent();
    if (event instanceof MouseEvent) {
      popup.showUnderneathOf(event.getComponent());
    }
    else {
      popup.showInBestPositionFor(e.getDataContext());
    }
  }

  @NotNull
  public AnalysisScope getScope() {
    return myScope;
  }

  public boolean isUpdating() {
    return myUpdatingRequestors > 0;
  }

  void updateRightPanelLoading() {
    if (!myDisposed && isUpdating() && myLoadingProgressPreview != null) {
      myLoadingProgressPreview.updateLoadingProgress();
    }
  }

  public boolean hasProblems() {
    return hasProblems(myGlobalInspectionContext.getTools().values(), myGlobalInspectionContext, myProvider);
  }

  public static boolean hasProblems(@NotNull Collection<? extends Tools> tools,
                                    @NotNull GlobalInspectionContextImpl context,
                                    @NotNull InspectionRVContentProvider contentProvider) {
    for (Tools currentTools : tools) {
      for (ScopeToolState state : contentProvider.getTools(currentTools)) {
        InspectionToolWrapper toolWrapper = state.getTool();
        if (context.getPresentation(toolWrapper).hasReportedProblems() || contentProvider.checkReportedProblems(context, toolWrapper)) {
          return true;
        }
      }
    }
    return false;
  }

  public boolean isDisposed() {
    return myDisposed;
  }

  public boolean isRerunAvailable() {
    return !(myProvider instanceof OfflineInspectionRVContentProvider) && myScope.isValid();
  }

  public void rerun() {
    myRerun = true;
    if (myScope.isValid()) {
      AnalysisUIOptions.getInstance(getProject()).save(myGlobalInspectionContext.getUIOptions());
      myGlobalInspectionContext.setTreeState(getTree().getTreeState());
      myGlobalInspectionContext.doInspections(myScope);
    } else {
      GlobalInspectionContextImpl.NOTIFICATION_GROUP.createNotification(InspectionsBundle.message("inspection.view.invalid.scope.message"), NotificationType.INFORMATION).notify(getProject());
    }
  }

  private void updateTree(@NotNull Runnable action) {
    myTreeUpdater.execute(() -> ProgressManager.getInstance().runProcess(action, new EmptyProgressIndicator()));
  }


  @TestOnly
  public void dispatchTreeUpdate() throws Exception {
    myTreeUpdater.submit(EmptyRunnable.getInstance()).get();
  }
}
