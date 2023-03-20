// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInspection.ui;

import com.intellij.analysis.AnalysisScope;
import com.intellij.analysis.AnalysisUIOptions;
import com.intellij.analysis.problemsView.toolWindow.ProblemsView;
import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.*;
import com.intellij.codeInspection.offlineViewer.OfflineInspectionRVContentProvider;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.ui.actions.InspectionResultsExportActionProvider;
import com.intellij.codeInspection.ui.actions.InvokeQuickFixAction;
import com.intellij.diff.util.DiffUtil;
import com.intellij.icons.AllIcons;
import com.intellij.ide.CommonActionsManager;
import com.intellij.ide.DefaultTreeExpander;
import com.intellij.ide.OccurenceNavigator;
import com.intellij.ide.TreeExpander;
import com.intellij.ide.actions.exclusion.ExclusionHandler;
import com.intellij.ide.util.PsiNavigationSupport;
import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
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
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.pom.Navigatable;
import com.intellij.profile.ProfileChangeAdapter;
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.ui.*;
import com.intellij.ui.content.ContentManagerEvent;
import com.intellij.ui.content.ContentManagerListener;
import com.intellij.util.Alarm;
import com.intellij.util.ThreeState;
import com.intellij.util.concurrency.SequentialTaskExecutor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.EdtInvocationManager;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;

public class InspectionResultsView extends JPanel implements Disposable, DataProvider, OccurenceNavigator {
  private static final Logger LOG = Logger.getInstance(InspectionResultsView.class);

  public static final DataKey<InspectionResultsView> DATA_KEY = DataKey.create("inspectionView");
  private static final Key<Boolean> PREVIEW_EDITOR_IS_REUSED_KEY = Key.create("inspection.tool.window.preview.editor.is.reused");

  @NotNull
  private final InspectionTree myTree;
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
  private boolean myApplyingFix; //accessed only in edt

  @NotNull
  private final InspectionRVContentProvider myProvider;
  @NotNull
  private final ExclusionHandler<InspectionTreeNode> myExclusionHandler;
  private EditorEx myPreviewEditor;
  private InspectionTreeLoadingProgressAware myLoadingProgressPreview;
  private final Alarm myLoadingProgressPreviewAlarm = new Alarm(this);
  private final InspectionViewSuppressActionHolder mySuppressActionHolder = new InspectionViewSuppressActionHolder();

  private final Executor myTreeUpdater = SequentialTaskExecutor.createSequentialApplicationPoolExecutor("Inspection-View-Tree-Updater");
  private final Executor myRightPanelUpdater = SequentialTaskExecutor.createSequentialApplicationPoolExecutor("Inspection-View-Right-Panel-Updater");
  private volatile boolean myUpdating;
  private volatile boolean myFixesAvailable;
  private ToolWindow myToolWindow;
  private ContentManagerListener myContentManagerListener;

  public InspectionResultsView(@NotNull GlobalInspectionContextImpl globalInspectionContext,
                               @NotNull InspectionRVContentProvider provider) {
    setLayout(new BorderLayout());
    myInspectionProfile = globalInspectionContext.getCurrentProfile();
    myScope = globalInspectionContext.getCurrentScope();
    myGlobalInspectionContext = globalInspectionContext;
    myProvider = provider;
    myTree = new InspectionTree(this);

    mySplitter = new OnePixelSplitter(false, AnalysisUIOptions.getInstance(globalInspectionContext.getProject()).SPLITTER_PROPORTION);
    mySplitter.setFirstComponent(ScrollPaneFactory.createScrollPane(myTree, SideBorder.LEFT));
    mySplitter.setHonorComponentsMinimumSize(false);

    mySplitter.addPropertyChangeListener(evt -> {
      if (Splitter.PROP_PROPORTION.equals(evt.getPropertyName())) {
        myGlobalInspectionContext.setSplitterProportion(((Float)evt.getNewValue()).floatValue());
      }
    });
    add(mySplitter, BorderLayout.CENTER);
    myExclusionHandler = new ExclusionHandler<>() {
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
        }
        else {
          myTree.repaint();
        }
        syncRightPanel();
      }
    };
    createActionsToolbar();

    PsiManager.getInstance(getProject()).addPsiTreeChangeListener(new InspectionViewChangeAdapter(this), this);

    getProject().getMessageBus().connect(this).subscribe(ProfileChangeAdapter.TOPIC, new ProfileChangeAdapter() {
      @Override
      public void profileChanged(@NotNull InspectionProfile profile) {
        if (profile == ProjectInspectionProfileManager.getInstance(getProject()).getCurrentProfile()) {
          InspectionResultsView.this.profileChanged();
        }
      }
    });

    if (!isSingleInspectionRun()) {
      mySettingsEnabled = true;
    } else {
      InspectionProfileImpl profile = getCurrentProfile();
      String toolId = Objects.requireNonNull(profile.getSingleTool());
      InspectionToolWrapper<?,?> tool = Objects.requireNonNull(profile.getInspectionTool(toolId, getProject()));
      mySettingsEnabled = InspectionOptionPaneRenderer.hasSettings(tool.getTool());
    }
  }

  void profileChanged() {
    UIUtil.invokeLaterIfNeeded(() -> {
      myTree.revalidate();
      myTree.repaint();
      syncRightPanel();
    });
  }

  public void initAdditionalGearActions(@NotNull ToolWindow toolWindow) {
    if (ExperimentalUI.isNewUI()) {
      myToolWindow = toolWindow;
      myContentManagerListener = new ContentManagerListener() {
        @Override
        public void selectionChanged(@NotNull ContentManagerEvent event) {
          boolean selected = ContentManagerEvent.ContentOperation.add == event.getOperation();
          if (selected && event.getContent().getComponent() == InspectionResultsView.this) {
            setAdditionalGearActions();
          }
        }
      };
      myToolWindow.getContentManager().addContentManagerListener(myContentManagerListener);
      setAdditionalGearActions();
    }
  }

  private void setAdditionalGearActions() {
    if (myToolWindow != null) {
      DefaultActionGroup group = new DefaultActionGroup(myGlobalInspectionContext.createToggleAutoscrollAction());
      myToolWindow.setAdditionalGearActions(group);
    }
  }

  private void createActionsToolbar() {
    JComponent westComponent;
    if (ExperimentalUI.isNewUI()) {
      westComponent = createNewActionsToolbar();
    }
    else {
      westComponent = JBUI.Panels.simplePanel()
        .addToLeft(createLeftActionsToolbar())
        .addToRight(createRightActionsToolbar());
    }
    add(westComponent, BorderLayout.WEST);
  }

  private static DefaultActionGroup createExportActions() {
    var result = new DefaultActionGroup(InspectionsBundle.message("inspection.action.export.html"), null, AllIcons.ToolbarDecorator.Export);
    result.addAll(InspectionResultsExportActionProvider.Companion.getEP_NAME().getExtensionList());
    result.setPopup(true);
    return result;
  }

  private JComponent createNewActionsToolbar() {
    final CommonActionsManager actionsManager = CommonActionsManager.getInstance();
    DefaultActionGroup group = new DefaultActionGroup();
    group.add(new RerunAction(this));
    group.add(actionsManager.createPrevOccurenceAction(myTree.getOccurenceNavigator()));
    group.add(actionsManager.createNextOccurenceAction(myTree.getOccurenceNavigator()));
    group.add(new InvokeQuickFixAction(this));
    group.addSeparator();
    group.add(ActionManager.getInstance().getAction("EditInspectionSettings"));
    var viewOptionsActions = new DefaultActionGroup(InspectionsBundle.message("inspection.action.view.options"), null, AllIcons.Actions.Show);
    viewOptionsActions.addSeparator(InspectionsBundle.message("inspection.action.view.options.group.by"));
    viewOptionsActions.add(myGlobalInspectionContext.getUIOptions().createGroupByDirectoryAction(this));
    viewOptionsActions.add(myGlobalInspectionContext.getUIOptions().createGroupBySeverityAction(this));
    viewOptionsActions.addSeparator();
    viewOptionsActions.add(myGlobalInspectionContext.getUIOptions().createFilterResolvedItemsAction(this));
    viewOptionsActions.setPopup(true);
    group.add(viewOptionsActions);
    group.addSeparator();
    TreeExpander treeExpander = new DefaultTreeExpander(myTree);
    group.add(actionsManager.createExpandAllAction(treeExpander, myTree));
    group.add(actionsManager.createCollapseAllAction(treeExpander, myTree));
    group.addSeparator();
    group.add(createExportActions());
    return createToolbar(group);
  }

  private JComponent createRightActionsToolbar() {
    DefaultActionGroup specialGroup = new DefaultActionGroup();
    specialGroup.add(myGlobalInspectionContext.getUIOptions().createGroupBySeverityAction(this));
    specialGroup.add(myGlobalInspectionContext.getUIOptions().createGroupByDirectoryAction(this));
    specialGroup.add(myGlobalInspectionContext.getUIOptions().createFilterResolvedItemsAction(this));
    specialGroup.add(myGlobalInspectionContext.createToggleAutoscrollAction());
    specialGroup.add(createExportActions());
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
    group.add(actionsManager.createPrevOccurenceAction(myTree.getOccurenceNavigator()));
    group.add(actionsManager.createNextOccurenceAction(myTree.getOccurenceNavigator()));
    group.add(ActionManager.getInstance().getAction("EditInspectionSettings"));

    return createToolbar(group);
  }

  @Override
  public boolean hasNextOccurence() {
    return myTree.getOccurenceNavigator().hasNextOccurence();
  }

  @Override
  public boolean hasPreviousOccurence() {
    return myTree.getOccurenceNavigator().hasPreviousOccurence();
  }

  @Override
  public OccurenceInfo goNextOccurence() {
    return myTree.getOccurenceNavigator().goNextOccurence();
  }

  @Override
  public OccurenceInfo goPreviousOccurence() {
    return myTree.getOccurenceNavigator().goPreviousOccurence();
  }

  @NotNull
  @Override
  public String getNextOccurenceActionName() {
    return myTree.getOccurenceNavigator().getNextOccurenceActionName();
  }

  @NotNull
  @Override
  public String getPreviousOccurenceActionName() {
    return myTree.getOccurenceNavigator().getPreviousOccurenceActionName();
  }

  private JComponent createToolbar(final DefaultActionGroup specialGroup) {
    final ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.CODE_INSPECTION, specialGroup, false);
    toolbar.setTargetComponent(this);
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
    if (myToolWindow != null && myContentManagerListener != null) {
      myToolWindow.getContentManager().removeContentManagerListener(myContentManagerListener);
    }
  }

  boolean isAutoScrollMode() {
    String activeToolWindowId = ToolWindowManager.getInstance(getProject()).getActiveToolWindowId();
    //noinspection removal
    return myGlobalInspectionContext.getUIOptions().AUTOSCROLL_TO_SOURCE &&
           (activeToolWindowId == null
            || activeToolWindowId.equals(ProblemsView.ID)
            // TODO: compatibility mode for Rider where there's no problems view; remove in 2021.2
            // see RIDER-59000
            || activeToolWindowId.equals(ToolWindowId.INSPECTION));
  }

  public void setApplyingFix(boolean applyingFix) {
    myApplyingFix = applyingFix;
    syncRightPanel();
  }

  public void syncRightPanel() {
    myFixesAvailable = false;
    final Editor oldEditor = myPreviewEditor;
    try {
      if (myLoadingProgressPreview != null) {
        Disposer.dispose(myLoadingProgressPreview);
        myLoadingProgressPreview = null;
      }
      if (myApplyingFix) {
        final InspectionToolWrapper<?,?> wrapper = myTree.getSelectedToolWrapper(true);
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
            if (node instanceof ProblemDescriptionNode problemNode) {
              showInRightPanel(problemNode.getElement());
            }
            else if (node instanceof InspectionPackageNode ||
                     node instanceof InspectionModuleNode ||
                     node instanceof RefElementNode ||
                     (isSingleInspectionRun() && node instanceof InspectionSeverityGroupNode)) {
              myRightPanelUpdater.execute(() -> {
                final var entity = node.getContainingFileLocalEntity();
                SwingUtilities.invokeLater(() -> {
                  TreePath newPath = myTree.getSelectionModel().getLeadSelectionPath();
                  if (newPath == pathSelected) showInRightPanel(entity);
                });
              });
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
            else if (node instanceof InspectionRootNode) {
              mySplitter.setSecondComponent(InspectionResultsViewUtil.getNothingToShowTextLabel());
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
    final int problemCount = myTree.getSelectedProblemCount();
    JComponent previewPanel = null;
    final InspectionToolWrapper<?,?> tool = myTree.getSelectedToolWrapper(true);
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
      var paths = myTree.getSelectionPaths();
      if (paths != null) for (TreePath path: paths) {
        var node = (InspectionTreeNode)path.getLastPathComponent();
        if (node instanceof SuppressableInspectionTreeNode) ((SuppressableInspectionTreeNode)node).updateAvailableSuppressActions();
      }
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
      final PsiFile file = InjectedLanguageManager.getInstance(getProject()).getTopLevelFile(selectedElement);
      final Document document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
      if (document == null) {
        return Pair.create(InspectionResultsViewUtil.createLabelForText(
          InspectionsBundle.message("inspections.view.no.preview.label", file.getName())), null);
      }

      if (reuseEditorFor(document)) {
        myPreviewEditor.putUserData(PREVIEW_EDITOR_IS_REUSED_KEY, true);
        myPreviewEditor.getFoldingModel().runBatchFoldingOperation(() -> myPreviewEditor.getFoldingModel().clearFoldRegions());
        myPreviewEditor.getMarkupModel().removeAllHighlighters();
      }
      else {
        myPreviewEditor = (EditorEx)EditorFactory.getInstance().createEditor(document, getProject(), file.getVirtualFile(), false, EditorKind.PREVIEW);
        DiffUtil.setFoldingModelSupport(myPreviewEditor);
        final EditorSettings settings = myPreviewEditor.getSettings();
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

  private void addTool(@NotNull final InspectionToolWrapper<?,?> toolWrapper,
                       HighlightDisplayLevel errorLevel,
                       boolean groupedBySeverity,
                       boolean isSingleInspectionRun) {
    InspectionTreeNode toolNode = myTree.getToolProblemsRootNode(toolWrapper, errorLevel, groupedBySeverity, isSingleInspectionRun);
    myProvider.appendToolNodeContent(myGlobalInspectionContext,
                                                               toolWrapper,
                                                               toolNode,
                                                               myGlobalInspectionContext.getUIOptions().SHOW_STRUCTURE,
                                                               groupedBySeverity);
    InspectionToolPresentation presentation = myGlobalInspectionContext.getPresentation(toolWrapper);
    presentation.patchToolNode(toolNode, myProvider,
                               myGlobalInspectionContext.getUIOptions().SHOW_STRUCTURE, groupedBySeverity);
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
  private String getCurrentProfileName() {
    return myInspectionProfile == null ? null : myInspectionProfile.getDisplayName();
  }

  public InspectionProfileImpl getCurrentProfile() {
    return myInspectionProfile;
  }

  void addProblemDescriptors(InspectionToolWrapper<?,?> wrapper, RefEntity refElement, CommonProblemDescriptor[] descriptors) {
    updateTree(() -> ReadAction.run(() -> {
      if (!isDisposed()) {
        final AnalysisUIOptions uiOptions = myGlobalInspectionContext.getUIOptions();
        final InspectionToolPresentation presentation = myGlobalInspectionContext.getPresentation(wrapper);

        HighlightSeverity severity = presentation.getSeverity((RefElement)refElement);
        HighlightDisplayLevel level = HighlightDisplayLevel.find(severity == null ? HighlightSeverity.INFORMATION : severity);
        final InspectionTreeNode toolNode =
          myTree.getToolProblemsRootNode(wrapper, level,
                                         uiOptions.GROUP_BY_SEVERITY, isSingleInspectionRun());
        final Map<RefEntity, CommonProblemDescriptor[]> problems = new HashMap<>(1);
        problems.put(refElement, descriptors);
        final Map<String, Set<RefEntity>> contents = new HashMap<>();
        final String groupName = refElement.getRefManager().getGroupName((RefElement)refElement);
        Set<RefEntity> content = contents.computeIfAbsent(groupName, __ -> new HashSet<>());
        content.add(refElement);

        getProvider().appendToolNodeContent(myGlobalInspectionContext,
                                            wrapper,
                                            toolNode,
                                            uiOptions.SHOW_STRUCTURE,
                                            true,
                                            contents,
                                            problems::get);

        if (!myLoadingProgressPreviewAlarm.isDisposed()) {
          myLoadingProgressPreviewAlarm.cancelAllRequests();
          myLoadingProgressPreviewAlarm.addRequest(() -> {
            if (myLoadingProgressPreview != null) {
              myLoadingProgressPreview.updateLoadingProgress();
            }
          }, 200);
        }
      }
    }));
  }

  public void update() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    Collection<Tools> tools = new ArrayList<>(myGlobalInspectionContext.getTools().values());
    updateTree(() -> updateResults(tools));
  }

  public void updateResults(@NotNull Collection<? extends Tools> tools) {
    try {
      setUpdating(true);
      myTree.removeAllNodes();
      addToolsSynchronously(tools);
    }
    finally {
      setUpdating(false);
    }
  }

  public void setUpdating(boolean isUpdating) {
    myUpdating = isUpdating;
    if (!isUpdating) {
      myLoadingProgressPreviewAlarm.cancelAllRequests();
      myLoadingProgressPreviewAlarm.addRequest(() -> {
        if (myLoadingProgressPreview != null) {
          myLoadingProgressPreview.treeLoaded();
        }
      }, 200);
    }
    EdtInvocationManager.getInstance().invokeLater(() -> myTree.setPaintBusy(myUpdating));
  }

  public void addTools(Collection<? extends Tools> tools) {
    updateTree(() -> addToolsSynchronously(tools));
  }

  private void addToolsSynchronously(@NotNull Collection<? extends Tools> tools) {
    if (isDisposed()) return;
    InspectionProfileImpl profile = myInspectionProfile;
    boolean isGroupedBySeverity = myGlobalInspectionContext.getUIOptions().GROUP_BY_SEVERITY;
    boolean singleInspectionRun = isSingleInspectionRun();
    for (Tools currentTools : tools) {
      InspectionToolWrapper<?,?> defaultToolWrapper = currentTools.getDefaultState().getTool();
      final HighlightDisplayKey key = HighlightDisplayKey.find(defaultToolWrapper.getShortName());
      for (ScopeToolState state : myProvider.getTools(currentTools)) {
        InspectionToolWrapper<?,?> toolWrapper = state.getTool();
        if (ReadAction.compute(() -> myProvider.checkReportedProblems(myGlobalInspectionContext, toolWrapper))) {
          addTool(toolWrapper,
                  profile.getErrorLevel(key, state.getScope(getProject()), getProject()),
                  isGroupedBySeverity,
                  singleInspectionRun);
        }
      }
    }

  }

  @NotNull
  public Project getProject() {
    return myGlobalInspectionContext.getProject();
  }

  @Override
  public Object getData(@NotNull String dataId) {
    if (PlatformCoreDataKeys.HELP_ID.is(dataId)) {
      return HELP_ID;
    }
    if (DATA_KEY.is(dataId)) {
      return this;
    }
    if (ExclusionHandler.EXCLUSION_HANDLER.is(dataId)) {
      return myExclusionHandler;
    }
    if (PlatformCoreDataKeys.SELECTED_ITEM.is(dataId)) {
      TreePath[] paths = myTree.getSelectionPaths();
      if (paths == null || paths.length == 0) return null;
      return paths[0].getLastPathComponent();
    }
    if (PlatformCoreDataKeys.SELECTED_ITEMS.is(dataId)) {
      TreePath[] paths = myTree.getSelectionPaths();
      if (paths == null || paths.length == 0) return null;
      return ContainerUtil.map2Array(paths, p -> p.getLastPathComponent());
    }
    if (PlatformCoreDataKeys.BGT_DATA_PROVIDER.is(dataId)) {
      TreePath[] paths = myTree.getSelectionPaths();
      if (paths == null || paths.length == 0) return null;
      return (DataProvider)slowId -> getSlowData(slowId, paths);
    }
    return null;
  }

  private @Nullable Object getSlowData(@NotNull String dataId, TreePath @NotNull [] paths) {
    if (paths.length > 1) {
      if (PlatformCoreDataKeys.PSI_ELEMENT_ARRAY.is(dataId)) {
        RefEntity[] refElements = myTree.getElementsFromSelection(paths);
        List<PsiElement> psiElements = new ArrayList<>();
        for (RefEntity refElement : refElements) {
          PsiElement psiElement = refElement instanceof RefElement ? ((RefElement)refElement).getPsiElement() : null;
          if (psiElement != null && psiElement.isValid()) {
            psiElements.add(psiElement);
          }
        }

        return PsiUtilCore.toPsiElementArray(psiElements);
      }
      return null;
    }

    TreePath path = paths[0];
    InspectionTreeNode selectedNode = (InspectionTreeNode)path.getLastPathComponent();

    if (!CommonDataKeys.NAVIGATABLE.is(dataId) && !CommonDataKeys.PSI_ELEMENT.is(dataId)) {
      return null;
    }

    if (selectedNode instanceof RefElementNode refElementNode) {
      RefEntity refElement = refElementNode.getElement();
      if (refElement == null || !refElement.isValid()) return null;
      final RefEntity item = refElement.getRefManager().getRefinedElement(refElement);

      if (!item.isValid()) return null;

      PsiElement psiElement = item instanceof RefElement ? ((RefElement)item).getPsiElement() : null;
      if (psiElement == null) return null;

      if (CommonDataKeys.NAVIGATABLE.is(dataId)) {
        return getSelectedNavigatable(null, psiElement);
      }
      else if (CommonDataKeys.PSI_ELEMENT.is(dataId)) {
        return psiElement.isValid() ? psiElement : null;
      }
    }
    else if (selectedNode instanceof ProblemDescriptionNode) {
      if (CommonDataKeys.NAVIGATABLE.is(dataId)) {
        Navigatable navigatable = getSelectedNavigatable(((ProblemDescriptionNode)selectedNode).getDescriptor());
        return navigatable == null
               ? InspectionResultsViewUtil.getNavigatableForInvalidNode((ProblemDescriptionNode)selectedNode)
               : navigatable;
      }
      if (CommonDataKeys.PSI_ELEMENT.is(dataId)) {
        RefEntity item = ((ProblemDescriptionNode)selectedNode).getElement();
        return item instanceof RefElement ? ((RefElement)item).getPsiElement() : null;
      }
    }
    return null;
  }

  public @NlsContexts.TabTitle String getViewTitle() {
    if (ExperimentalUI.isNewUI()) {
      return InspectionsBundle.message("inspection.results.toolwindow.title", myScope.getShortenName());
    }

    return InspectionsBundle.message(isSingleInspectionRun() ?
                              "inspection.results.for.inspection.toolwindow.title" :
                              "inspection.results.for.profile.toolwindow.title",
                              getCurrentProfileName(), myScope.getShortenName());
  }

  void setFixesAvailable(boolean available) {
    myFixesAvailable = available;
  }

  public boolean areFixesAvailable() {
    return myFixesAvailable;
  }

  @Nullable
  static Navigatable getSelectedNavigatable(final CommonProblemDescriptor descriptor) {
    return getSelectedNavigatable(descriptor,
                                  descriptor instanceof ProblemDescriptor ? ((ProblemDescriptor)descriptor).getPsiElement() : null);
  }

  @Nullable
  private static Navigatable getSelectedNavigatable(CommonProblemDescriptor descriptor, PsiElement psiElement) {
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
      return PsiNavigationSupport.getInstance().createNavigatable(psiElement.getProject(), virtualFile, startOffset);
    }
    return null;
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
    return myUpdating;
  }

  public boolean hasProblems() {
    return hasProblems(myGlobalInspectionContext.getTools().values(), myGlobalInspectionContext, myProvider);
  }

  public static boolean hasProblems(@NotNull Collection<? extends Tools> tools,
                                    @NotNull GlobalInspectionContextImpl context,
                                    @NotNull InspectionRVContentProvider contentProvider) {
    for (Tools currentTools : tools) {
      for (ScopeToolState state : contentProvider.getTools(currentTools)) {
        InspectionToolWrapper<?,?> toolWrapper = state.getTool();
        ThreeState hasReportedProblems = context.getPresentation(toolWrapper).hasReportedProblems();
        if (hasReportedProblems == ThreeState.NO) continue;
        if (hasReportedProblems == ThreeState.YES || ReadAction.compute(() -> contentProvider.checkReportedProblems(context, toolWrapper))) {
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
      myGlobalInspectionContext.doInspections(myScope);
    } else {
      GlobalInspectionContextImpl.NOTIFICATION_GROUP.createNotification(InspectionsBundle.message("inspection.view.invalid.scope.message"), NotificationType.INFORMATION).notify(getProject());
    }
  }

  private void updateTree(@NotNull Runnable action) {
    myTreeUpdater.execute(() -> ProgressManager.getInstance().runProcess(action, new EmptyProgressIndicator()));
  }


  @TestOnly
  public void dispatchTreeUpdate() throws InterruptedException {
    CountDownLatch latch = new CountDownLatch(1);
    myTreeUpdater.execute(()-> latch.countDown());
    latch.await();
  }
}