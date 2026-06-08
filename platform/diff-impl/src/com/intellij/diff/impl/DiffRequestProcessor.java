// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.impl;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.diff.DiffContext;
import com.intellij.diff.DiffExtension;
import com.intellij.diff.DiffManager;
import com.intellij.diff.DiffManagerEx;
import com.intellij.diff.DiffTool;
import com.intellij.diff.EditorDiffViewer;
import com.intellij.diff.FrameDiffTool;
import com.intellij.diff.FrameDiffTool.DiffViewer;
import com.intellij.diff.actions.impl.DiffNextFileAction;
import com.intellij.diff.actions.impl.DiffPreviousFileAction;
import com.intellij.diff.editor.DiffViewerVirtualFile;
import com.intellij.diff.impl.DiffSettingsHolder.DiffSettings;
import com.intellij.diff.impl.ui.DiffHeaderToolbarPanel;
import com.intellij.diff.impl.ui.DiffToolChooser;
import com.intellij.diff.lang.DiffIgnoredRangeProvider;
import com.intellij.diff.lang.DiffLangSpecificProvider;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.requests.ErrorDiffRequest;
import com.intellij.diff.requests.LoadingDiffRequest;
import com.intellij.diff.requests.MessageDiffRequest;
import com.intellij.diff.requests.NoDiffRequest;
import com.intellij.diff.tools.ErrorDiffTool;
import com.intellij.diff.tools.combined.CombinedDiffViewer;
import com.intellij.diff.tools.external.ExternalDiffSettings;
import com.intellij.diff.tools.external.ExternalDiffSettings.ExternalTool;
import com.intellij.diff.tools.external.ExternalDiffSettings.ExternalToolGroup;
import com.intellij.diff.tools.external.ExternalDiffTool;
import com.intellij.diff.tools.util.CrossFilePrevNextDifferenceIterableSupport;
import com.intellij.diff.tools.util.DiffDataKeys;
import com.intellij.diff.tools.util.PrevNextFileIterable;
import com.intellij.diff.util.DiffUserDataKeys;
import com.intellij.diff.util.DiffUserDataKeysEx;
import com.intellij.diff.util.DiffUserDataKeysEx.ScrollToPolicy;
import com.intellij.diff.util.DiffUtil;
import com.intellij.diff.util.LineRange;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.actionSystem.UiDataProvider;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.actionSystem.toolbarLayout.ToolbarLayoutStrategy;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.diff.impl.DiffUsageTriggerCollector;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileEditor.FileEditorStateLevel;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.CheckedDisposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.ex.IdeFocusTraversalPolicy;
import com.intellij.ui.GuiUtils;
import com.intellij.ui.HintHint;
import com.intellij.ui.IslandsState;
import com.intellij.ui.JBColor;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.LightweightHint;
import com.intellij.ui.RemoteTransferUIManager;
import com.intellij.ui.components.JBPanelWithEmptyText;
import com.intellij.ui.components.panels.ListLayout;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.ui.dsl.builder.components.SegmentedButtonComponent;
import com.intellij.ui.mac.touchbar.Touchbar;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.EventDispatcher;
import com.intellij.util.ObjectUtils;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.CalledInAny;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import javax.swing.Box;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.Window;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Panel implementing a Diff-as-a-JComponent, showing one {@link DiffRequest} at a time.
 * See {@link CombinedDiffViewer} for the all-files-in-one-big-scroll-pane implementation.
 *
 * @see DiffManager#createRequestPanel(Project, Disposable, Window)
 * @see CacheDiffRequestProcessor
 * @see CacheDiffRequestProcessor.Simple
 * @see com.intellij.openapi.vcs.changes.ChangeViewDiffRequestProcessor
 * @see DiffViewerVirtualFile
 */
public abstract class DiffRequestProcessor
  implements DiffEditorViewer, CheckedDisposable {
  private static final Logger LOG = Logger.getInstance(DiffRequestProcessor.class);

  private static final DataKey<DiffTool> ACTIVE_DIFF_TOOL = DataKey.create("active_diff_tool");

  private boolean myDisposed;

  private final @Nullable Project myProject;
  private final @NotNull DiffContext myContext;

  private final @NotNull DiffSettings mySettings;
  private @NotNull List<DiffTool> myToolOrder = Collections.emptyList(); // stores non-FrameDiffTool to keep the ordering
  private final @Nullable FrameDiffTool myForcedDiffTool;

  private final @NotNull DefaultActionGroup myToolbarGroup;
  private final @NotNull DefaultActionGroup myRightToolbarGroup;
  private final @NotNull DefaultActionGroup myPopupActionGroup;
  private final @NotNull DefaultActionGroup myTouchbarActionGroup;

  private final @NotNull MyPanel myMainPanel;
  private final @NotNull Wrapper myContentPanel;
  private final @NotNull DiffHeaderToolbarPanel myTopPanel;
  private final @NotNull SegmentedButtonComponent<DiffTool> myDiffToolChooser;
  private final @NotNull ActionToolbar myToolbar;
  private final @NotNull ActionToolbar myRightToolbar;
  private final @NotNull Wrapper myToolbarStatusPanel;
  private final @NotNull MyProgressBar myProgressBar;

  private final @NotNull EventDispatcher<DiffRequestProcessorListener> myEventDispatcher =
    EventDispatcher.create(DiffRequestProcessorListener.class);

  private @NotNull DiffRequest myActiveRequest;

  private @NotNull ViewerState myState;

  private @Nullable ScrollToPolicy myCurrentScrollToPolicy;

  private final @NotNull DiffRequestProcessor.DiffNavigator navigator;

  public DiffRequestProcessor(@Nullable Project project) {
    this(project, new UserDataHolderBase());
  }

  public DiffRequestProcessor(@Nullable Project project, @NonNls @NotNull String place) {
    this(project, DiffUtil.createUserDataHolder(DiffUserDataKeys.PLACE, place));
  }

  public DiffRequestProcessor(@Nullable Project project, @NotNull UserDataHolder context) {
    myProject = project;

    myContext = new MyDiffContext(context);
    myActiveRequest = new LoadingDiffRequest();

    mySettings = DiffSettings.getSettings(myContext.getUserData(DiffUserDataKeys.PLACE));
    myForcedDiffTool = ObjectUtils.tryCast(myContext.getUserData(DiffUserDataKeysEx.FORCE_DIFF_TOOL), FrameDiffTool.class);

    readToolOrderFromSettings();
    DiffTool.EP_NAME.addChangeListener(() -> {
      readToolOrderFromSettings();
      updateRequest(true);
    }, this);
    DiffToolSubstitutor.EP_NAME.addChangeListener(() -> updateRequest(true), this);
    DiffIgnoredRangeProvider.EP_NAME.addChangeListener(() -> updateRequest(true), this);
    DiffLangSpecificProvider.EP_NAME.addChangeListener(() -> updateRequest(true), this);

    myToolbarGroup = new DefaultActionGroup();
    myRightToolbarGroup = new DefaultActionGroup();
    myPopupActionGroup = new DefaultActionGroup();
    myTouchbarActionGroup = new DefaultActionGroup();

    // UI

    myMainPanel = new MyPanel();
    Touchbar.setActions(myMainPanel, myTouchbarActionGroup);

    myContentPanel = new Wrapper();
    myToolbarStatusPanel = new Wrapper();
    myProgressBar = new MyProgressBar();

    myToolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.DIFF_TOOLBAR, myToolbarGroup, true);
    putContextUserData(DiffUserDataKeysEx.LEFT_TOOLBAR, myToolbar);

    myToolbar.setLayoutStrategy(ToolbarLayoutStrategy.NOWRAP_STRATEGY);
    myToolbar.setTargetComponent(myContentPanel);
    myToolbar.getComponent().setOpaque(false);

    myRightToolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.DIFF_RIGHT_TOOLBAR, myRightToolbarGroup, true);
    myRightToolbar.setLayoutStrategy(ToolbarLayoutStrategy.NOWRAP_STRATEGY);
    myRightToolbar.setTargetComponent(myContentPanel.getTargetComponent());
    myRightToolbar.getComponent().setOpaque(false);

    myDiffToolChooser = createDiffToolChooser();
    myTopPanel = buildTopPanel();

    Splitter bottomContentSplitter = new JBSplitter(true, "DiffRequestProcessor.BottomComponentSplitter", 0.8f);
    bottomContentSplitter.setFirstComponent(myContentPanel);

    // only needed for lux to transfer the BG color correctly
    var topPanelWrapper = new Wrapper(myTopPanel);
    topPanelWrapper.setOpaque(true);
    topPanelWrapper.setBackground(JBColor.lazy(() -> {
      if (IslandsState.Companion.isEnabled()) {
        EditorColorsManager manager = EditorColorsManager.getInstance();
        return manager.getGlobalScheme().getDefaultBackground();
      }
      else {
        return UIUtil.getPanelBackground();
      }
    }));
    RemoteTransferUIManager.forceDirectTransfer(topPanelWrapper);

    myMainPanel.add(topPanelWrapper, BorderLayout.NORTH);
    myMainPanel.add(bottomContentSplitter, BorderLayout.CENTER);

    myMainPanel.setFocusTraversalPolicyProvider(true);
    myMainPanel.setFocusTraversalPolicy(new MyFocusTraversalPolicy());

    JComponent bottomPanel = myContext.getUserData(DiffUserDataKeysEx.BOTTOM_PANEL);
    if (bottomPanel != null) bottomContentSplitter.setSecondComponent(bottomPanel);
    if (bottomPanel instanceof Disposable) Disposer.register(this, (Disposable)bottomPanel);

    myState = EmptyState.INSTANCE;
    myContentPanel.setContent(DiffUtil.createMessagePanel(((LoadingDiffRequest)myActiveRequest).getMessage()));
    navigator = new DiffNavigator();
  }

  private @NotNull DiffHeaderToolbarPanel buildTopPanel() {
    JPanel rightPanel = new NonOpaquePanel(ListLayout.horizontal(0, ListLayout.Alignment.CENTER, ListLayout.GrowPolicy.NO_GROW));
    rightPanel.add(myToolbarStatusPanel);
    rightPanel.add(myProgressBar);
    rightPanel.add(myDiffToolChooser);
    rightPanel.add(myRightToolbar.getComponent());

    var topPanel = new DiffHeaderToolbarPanel(new GridBagLayout());
    var gbc = new GridBagConstraints();

    // Add toolbar on the left
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.weightx = 0.0;
    gbc.weighty = 1.0;
    gbc.anchor = GridBagConstraints.WEST;
    gbc.fill = GridBagConstraints.NONE;
    topPanel.add(myToolbar.getComponent(), gbc);

    // Add spacer in the middle to push components to edges
    gbc.gridx = 1;
    gbc.weightx = 1.0;
    gbc.fill = GridBagConstraints.BOTH;
    topPanel.add(Box.createHorizontalGlue(), gbc);

    // Add rightPanel on the right
    gbc.gridx = 2;
    gbc.weightx = 0.0;
    gbc.anchor = GridBagConstraints.EAST;
    gbc.fill = GridBagConstraints.NONE;
    topPanel.add(rightPanel, gbc);

    GuiUtils.installVisibilityReferents(topPanel, myToolbar.getComponent(), myRightToolbar.getComponent(), myDiffToolChooser);

    return topPanel;
  }

  protected boolean shouldAddToolbarBottomBorder(@NotNull FrameDiffTool.ToolbarComponents toolbarComponents) {
    return toolbarComponents.needTopToolbarBorder;
  }

  //
  // Update
  //

  public void addListener(@NotNull DiffRequestProcessorListener listener, @Nullable Disposable disposable) {
    if (disposable != null) {
      myEventDispatcher.addListener(listener, disposable);
    }
    else {
      myEventDispatcher.addListener(listener);
    }
  }

  @RequiresEdt
  protected void reloadRequest() {
    updateRequest(true);
  }

  @RequiresEdt
  public void updateRequest() {
    updateRequest(false);
  }

  @RequiresEdt
  public void updateRequest(boolean force) {
    updateRequest(force, myCurrentScrollToPolicy);
  }

  /**
   * Perform a request to update the current DiffRequest
   * Typically invoked after navigation or on error recovery attempt
   *
   * @param force                if the request should be re-applied
   * @param scrollToChangePolicy optional scrolling request passed when navigating between changes in adjacent requests
   */
  @RequiresEdt
  public abstract void updateRequest(boolean force, @Nullable ScrollToPolicy scrollToChangePolicy);

  private static @NotNull FrameDiffTool findFittedTool(@NotNull List<? extends DiffTool> tools,
                                                       @NotNull DiffContext diffContext,
                                                       @NotNull DiffRequest diffRequest,
                                                       @Nullable FrameDiffTool forcedDiffTool) {
    if (forcedDiffTool != null) {
      return forcedDiffTool.canShow(diffContext, diffRequest)
             ? forcedDiffTool
             : ErrorDiffTool.INSTANCE;
    }

    List<FrameDiffTool> fittedTools = filterFittedTools(tools, diffContext, diffRequest);
    FrameDiffTool tool = ContainerUtil.getFirstItem(fittedTools, ErrorDiffTool.INSTANCE);

    FrameDiffTool substitutor = findToolSubstitutor(tool, diffContext, diffRequest);
    if (substitutor != null) return substitutor;

    return tool;
  }

  private static @NotNull List<FrameDiffTool> filterFittedTools(@NotNull List<? extends DiffTool> tools,
                                                                @NotNull DiffContext diffContext,
                                                                @NotNull DiffRequest diffRequest) {
    List<FrameDiffTool> result = new ArrayList<>();
    for (DiffTool tool : tools) {
      try {
        if (tool instanceof FrameDiffTool) {
          if (tool.canShow(diffContext, diffRequest)) {
            result.add((FrameDiffTool)tool);
          }
          else if (findToolSubstitutor(tool, diffContext, diffRequest) != null) {
            result.add((FrameDiffTool)tool);
          }
        }
      }
      catch (ProcessCanceledException e) {
        throw e;
      }
      catch (Throwable e) {
        LOG.error(e);
      }
    }

    return DiffUtil.filterSuppressedTools(result);
  }

  private static @Nullable FrameDiffTool findToolSubstitutor(@NotNull DiffTool tool,
                                                             @NotNull DiffContext diffContext,
                                                             @NotNull DiffRequest diffRequest) {
    DiffTool substitutor = DiffUtil.findToolSubstitutor(tool, diffContext, diffRequest);
    return substitutor instanceof FrameDiffTool ? (FrameDiffTool)substitutor : null;
  }

  private static @NotNull List<DiffTool> getAllKnownTools() {
    return DiffManagerEx.getInstance().getDiffTools();
  }

  @RequiresEdt
  private void readToolOrderFromSettings() {
    myToolOrder = getToolOrderFromSettings(getAllKnownTools());
  }

  private void switchToDiffTool(@NotNull DiffTool diffTool) {
    if (myForcedDiffTool != null) return;
    if (isSameToolOrSubstitutor(diffTool, myState.getActiveTool(), myContext, myActiveRequest)) return;

    DiffUsageTriggerCollector.logToggleDiffTool(myProject, diffTool, myContext.getUserData(DiffUserDataKeys.PLACE));

    myToolOrder = moveToolToTop(diffTool, myToolOrder, myContext, myActiveRequest);
    updateToolOrderSettings(myToolOrder);

    updateRequest(true);
  }

  private static @NotNull List<DiffTool> moveToolToTop(@NotNull DiffTool tool,
                                                       @NotNull List<DiffTool> oldOrder,
                                                       @NotNull DiffContext diffContext,
                                                       @NotNull DiffRequest diffRequest) {
    List<FrameDiffTool> fittedTools = filterFittedTools(oldOrder, diffContext, diffRequest);
    FrameDiffTool toolToReplace = ContainerUtil.getFirstItem(fittedTools);
    if (toolToReplace == tool) return oldOrder;

    List<DiffTool> newOrder = new ArrayList<>(oldOrder);
    newOrder.remove(tool);

    int index = ContainerUtil.indexOf(newOrder, it -> it == toolToReplace);
    if (index == -1) index = newOrder.size();
    newOrder.add(index, tool);

    return newOrder;
  }

  private @NotNull ViewerState createState(@NotNull FrameDiffTool frameTool) {
    DiffViewer viewer = frameTool.createComponent(myContext, myActiveRequest);

    for (DiffExtension extension : DiffExtension.EP_NAME.getExtensions()) {
      try {
        extension.onViewerCreated(viewer, myContext, myActiveRequest);
      }
      catch (Throwable e) {
        LOG.error(e);
      }
    }

    DiffViewerWrapper wrapper = myActiveRequest.getUserData(DiffViewerWrapper.KEY);
    if (wrapper == null) {
      return new DefaultState(viewer, frameTool);
    }
    else {
      return new WrapperState(viewer, frameTool, wrapper);
    }
  }

  //
  // Abstract
  //

  private @Nullable ApplyData myQueuedApplyRequest;

  @RequiresEdt
  protected void applyRequest(@NotNull DiffRequest request, boolean force, @Nullable ScrollToPolicy scrollToChangePolicy) {
    applyRequest(request, force, scrollToChangePolicy, false);
  }

  @RequiresEdt
  protected void applyRequest(@NotNull DiffRequest request, boolean force, @Nullable ScrollToPolicy scrollToChangePolicy, boolean sync) {
    ThreadingAssertions.assertEventDispatchThread();
    navigator.reset();

    force = force || (myQueuedApplyRequest != null && myQueuedApplyRequest.force);
    myQueuedApplyRequest = new ApplyData(request, force, scrollToChangePolicy);

    Runnable task = () -> {
      if (myQueuedApplyRequest == null || myDisposed) return;
      doApplyRequest(myQueuedApplyRequest.request, myQueuedApplyRequest.force, myQueuedApplyRequest.scrollToChangePolicy);
      myQueuedApplyRequest = null;
    };

    if (sync) {
      task.run();
    }
    else {
      IdeFocusManager.getInstance(myProject).doWhenFocusSettlesDown(task, ModalityState.current());
    }
  }

  @RequiresEdt
  private void doApplyRequest(@NotNull DiffRequest request, boolean force, @Nullable ScrollToPolicy scrollToChangePolicy) {
    if (!force && request == myActiveRequest) return;
    request.putUserData(DiffUserDataKeysEx.SCROLL_TO_CHANGE, scrollToChangePolicy);
    doApplyRequest(request);
  }

  @RequiresEdt
  @ApiStatus.Internal
  protected void doApplyRequest(@NotNull DiffRequest request) {
    DiffUtil.runPreservingFocus(myContext, () -> {
      myState.destroy();
      myToolbarStatusPanel.setContent(null);
      myContentPanel.setContent(null);
      myTopPanel.setNeedBottomSeparatorBorder(false);

      myToolbarGroup.removeAll();
      myRightToolbarGroup.removeAll();
      myPopupActionGroup.removeAll();
      ActionUtil.clearActions(myMainPanel);

      // NB: we should clean up the tool chooser here, but this causes the chooser to flicker
      // Instead update the chooser after state init

      ProgressManager.getInstance().executeNonCancelableSection(() -> {
        onAssigned(myActiveRequest, false);
        myActiveRequest = request;
        onAssigned(myActiveRequest, true);

        FrameDiffTool frameTool = null;
        try {
          frameTool = findFittedTool(myToolOrder, myContext, myActiveRequest, myForcedDiffTool);
          myState = createState(frameTool);
          try {
            myState.init();
            updateDiffToolChooser();

            boolean isLoading = request instanceof LoadingDiffRequest || request instanceof NoDiffRequest;
            if (!isLoading) {
              DiffUsageTriggerCollector.logShowDiffTool(myProject, frameTool, myContext.getUserData(DiffUserDataKeys.PLACE));
            }
          }
          catch (Throwable e) {
            myState.destroy();
            throw e;
          }
        }
        catch (Throwable e) {
          LOG.error(e);
          myState = new ErrorState(new ErrorDiffRequest(DiffBundle.message("error.cant.show.diff.message"), e), frameTool);
          myState.init();
          updateDiffToolChooser();
        }
      });
    });

    myEventDispatcher.getMulticaster().onViewerChanged();
  }

  protected void setWindowTitle(@NotNull @NlsContexts.DialogTitle String title) {
  }

  @RequiresEdt
  protected void onDispose() {
  }

  public @Nullable <T> T getContextUserData(@NotNull Key<T> key) {
    return myContext.getUserData(key);
  }

  public <T> void putContextUserData(@NotNull Key<T> key, @Nullable T value) {
    myContext.putUserData(key, value);
  }

  protected @Nullable Runnable createAfterNavigateCallback() {
    return () -> DiffUtil.minimizeDiffIfOpenedInWindow(myMainPanel);
  }

  protected @NotNull List<AnAction> getNavigationActions() {
    ActionManager am = ActionManager.getInstance();
    List<AnAction> result = new ArrayList<>();
    result.add(am.getAction("PreviousDiff"));
    result.add(am.getAction("NextDiff"));
    result.add(Separator.getInstance());
    result.add(am.getAction("Diff.OpenInEditor"));
    result.add(Separator.getInstance());
    result.add(am.getAction("Diff.PrevChange"));
    AnAction goToChangeAction = createGoToChangeAction();
    if (goToChangeAction != null) {
      result.add(goToChangeAction);
    }
    result.add(am.getAction("Diff.NextChange"));
    result.add(Separator.getInstance());
    return result;
  }

  /**
   * @see com.intellij.openapi.vcs.changes.actions.diff.PresentableGoToChangePopupAction
   */
  protected @Nullable AnAction createGoToChangeAction() {
    return null;
  }

  //
  // Misc
  //

  protected boolean isWindowFocused() {
    Window window = SwingUtilities.getWindowAncestor(myMainPanel);
    return window != null && window.isFocused();
  }

  private boolean isFocusedInWindow() {
    return DiffUtil.isFocusedComponentInWindow(myContentPanel) ||
           DiffUtil.isFocusedComponentInWindow(myToolbar.getComponent()) ||
           DiffUtil.isFocusedComponentInWindow(myRightToolbar.getComponent());
  }

  private void requestFocusInWindow() {
    DiffUtil.requestFocusInWindow(getPreferredFocusedComponent());
  }

  protected @NotNull List<DiffTool> getToolOrderFromSettings(@NotNull List<? extends DiffTool> availableTools) {
    return getToolOrderFromSettings(getSettings(), availableTools);
  }

  public static @NotNull List<DiffTool> getToolOrderFromSettings(@NotNull DiffSettings diffSettings,
                                                                 @NotNull List<? extends DiffTool> availableTools) {
    List<DiffTool> result = new ArrayList<>();
    List<String> savedOrder = diffSettings.getDiffToolsOrder();

    for (final String clazz : savedOrder) {
      DiffTool tool = ContainerUtil.find(availableTools, t -> t.getClass().getCanonicalName().equals(clazz));
      if (tool != null) result.add(tool);
    }

    for (DiffTool tool : availableTools) {
      if (!result.contains(tool)) result.add(tool);
    }

    return result;
  }

  protected void updateToolOrderSettings(@NotNull List<? extends DiffTool> toolOrder) {
    List<String> savedOrder = new ArrayList<>();
    for (DiffTool tool : toolOrder) {
      savedOrder.add(tool.getClass().getCanonicalName());
    }
    getSettings().setDiffToolsOrder(savedOrder);
  }

  @Override
  public void dispose() {
    if (myDisposed) return;
    UIUtil.invokeLaterIfNeeded(() -> {
      if (myDisposed) return;
      myDisposed = true;

      onDispose();

      myState.destroy();
      myToolbarStatusPanel.setContent(null);
      myContentPanel.setContent(null);

      myToolbarGroup.removeAll();
      myRightToolbarGroup.removeAll();
      myPopupActionGroup.removeAll();
      ActionUtil.clearActions(myMainPanel);

      onAssigned(myActiveRequest, false);

      myState = EmptyState.INSTANCE;
      myActiveRequest = NoDiffRequest.INSTANCE;
    });
  }

  private static final boolean SHOW_VIEWER_ACTIONS_IN_TOUCHBAR = Boolean.getBoolean("touchbar.diff.show.viewer.actions");

  protected void collectToolbarActions(@Nullable List<? extends AnAction> viewerActions,
                                       @Nullable List<? extends AnAction> rightViewerActions) {
    myToolbarGroup.removeAll();
    myRightToolbarGroup.removeAll();

    List<AnAction> navigationActions = new ArrayList<>(getNavigationActions());
    DiffUtil.addActionBlock(myToolbarGroup, navigationActions);
    DiffUtil.addActionBlock(myToolbarGroup, viewerActions);

    List<AnAction> requestContextActions = myActiveRequest.getUserData(DiffUserDataKeys.CONTEXT_ACTIONS);
    List<AnAction> contextActions = myContext.getUserData(DiffUserDataKeys.CONTEXT_ACTIONS);
    DiffUtil.addActionBlock(myToolbarGroup, requestContextActions);
    DiffUtil.addActionBlock(myToolbarGroup, contextActions, false);
    DiffUtil.addActionBlock(myToolbarGroup, new ShowInExternalToolActionGroup());

    DiffUtil.addActionBlock(myRightToolbarGroup, rightViewerActions, false);

    if (SystemInfo.isMac) { // collect touchbar actions
      myTouchbarActionGroup.removeAll();
      myTouchbarActionGroup.addAll(getNavigationActions());
      if (SHOW_VIEWER_ACTIONS_IN_TOUCHBAR && viewerActions != null) {
        myTouchbarActionGroup.addAll(viewerActions);
      }
    }
  }

  protected void collectPopupActions(@Nullable List<? extends AnAction> viewerActions) {
    myPopupActionGroup.removeAll();

    DiffUtil.addActionBlock(myPopupActionGroup, new MyChangeDiffToolActionGroup());

    DiffUtil.addActionBlock(myPopupActionGroup, viewerActions);
  }

  protected void buildToolbar(@Nullable List<? extends AnAction> viewerActions,
                              @Nullable List<? extends AnAction> rightViewerActions) {
    collectToolbarActions(viewerActions, rightViewerActions);

    ((ActionToolbarImpl)myToolbar).reset(); // do not leak previous DiffViewer via caches
    myToolbar.setTargetComponent(myContentPanel.getTargetComponent());

    myRightToolbar.setTargetComponent(myContentPanel.getTargetComponent());
    ((ActionToolbarImpl)myRightToolbar).reset();
  }

  public @NotNull ActionToolbar getToolbar() {
    return myToolbar;
  }

  @Override
  public void setToolbarVerticalSizeReferent(@NotNull JComponent component) {
    myTopPanel.setHeightReferent(component);
  }

  protected void buildActionPopup(@Nullable List<? extends AnAction> viewerActions) {
    collectPopupActions(viewerActions);

    DiffUtil.registerAction(new ShowActionGroupPopupAction(), myMainPanel);
  }

  private void setTitle(@Nullable @NlsContexts.DialogTitle String title) {
    if (getContextUserData(DiffUserDataKeys.DO_NOT_CHANGE_WINDOW_TITLE) == Boolean.TRUE) return;
    if (title == null) title = DiffBundle.message("diff.files.dialog.title");
    setWindowTitle(title);
  }

  //
  // Getters
  //

  @Override
  public @NotNull JComponent getComponent() {
    return myMainPanel;
  }

  @Override
  public @NotNull JComponent getPreferredFocusedComponent() {
    JComponent component = myState.getPreferredFocusedComponent();
    JComponent fallback = myToolbar.getComponent();
    if (component == null || !component.isFocusable()) return fallback;
    if (!component.isShowing() && fallback.isShowing()) return fallback;
    return component;
  }

  public @Nullable Project getProject() {
    return myProject;
  }

  @CalledInAny
  public @Nullable DiffRequest getActiveRequest() {
    return myActiveRequest;
  }

  @Override
  public @NotNull DiffContext getContext() {
    return myContext;
  }

  @Override
  public void setState(@NotNull FileEditorState state) {
    if (!(state instanceof DiffRequestProcessorEditorState processorState)) return;

    DiffViewer viewer = getActiveViewer();
    if (!(viewer instanceof EditorDiffViewer)) return;

    var editors = ((EditorDiffViewer)viewer).getEditors();
    var editorStates = processorState.embeddedEditorStates;

    TextEditorProvider textEditorProvider = TextEditorProvider.getInstance();
    for (int i = 0; i < Math.min(editorStates.size(), editors.size()); i++) {
      textEditorProvider.setStateImpl(myProject, editors.get(i), editorStates.get(i), true);
    }
  }

  @Override
  public @NotNull FileEditorState getState(@NotNull FileEditorStateLevel level) {
    DiffViewer viewer = getActiveViewer();
    if (!(viewer instanceof EditorDiffViewer)) return FileEditorState.INSTANCE;

    List<? extends Editor> editors = ((EditorDiffViewer)viewer).getEditors();

    TextEditorProvider textEditorProvider = TextEditorProvider.getInstance();
    return new DiffRequestProcessorEditorState(ContainerUtil.map(editors, (editor) ->
      textEditorProvider.getStateImpl(null, editor, level)));
  }

  public @Nullable DiffViewer getActiveViewer() {
    if (myState instanceof DefaultState) {
      return ((DefaultState)myState).myViewer;
    }
    if (myState instanceof WrapperState) {
      return ((WrapperState)myState).myViewer;
    }
    return null;
  }

  protected @NotNull DiffSettings getSettings() {
    return mySettings;
  }

  @Override
  public boolean isDisposed() {
    return myDisposed;
  }

  @Override
  public @NotNull CheckedDisposable getDisposable() {
    return this;
  }

  @Override
  public @NotNull List<Editor> getEmbeddedEditors() {
    DiffViewer viewer = getActiveViewer();
    if (viewer instanceof EditorDiffViewer editorDiffViewer) {
      return new ArrayList<>(editorDiffViewer.getHighlightEditors());
    }
    return Collections.emptyList();
  }

  @Override
  public @NotNull @Unmodifiable List<VirtualFile> getFilesToRefresh() {
    DiffRequest request = getActiveRequest();
    if (request != null) {
      return request.getFilesToRefresh();
    }
    return Collections.emptyList();
  }

  @Override
  public void fireProcessorActivated() {
    updateRequest();
  }

  @Override
  public void addListener(@NotNull DiffEditorViewerListener listener, @Nullable Disposable disposable) {
    addListener(new DiffRequestProcessorListener() {
      @Override
      public void onViewerChanged() {
        listener.onActiveFileChanged();
      }
    }, disposable);
  }

  //
  // Actions
  //

  private class ShowInExternalToolAction extends DumbAwareAction {
    private final @NotNull ExternalDiffSettings.ExternalTool myExternalTool;

    private ShowInExternalToolAction(ExternalDiffSettings.@NotNull ExternalTool externalTool) {
      super(DiffBundle.message("action.use.external.tool.text", externalTool.getName()));
      myExternalTool = externalTool;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      try {
        ExternalDiffTool.showRequest(e.getProject(), myActiveRequest, myExternalTool);
      }
      catch (ProcessCanceledException ex) {
        throw ex;
      }
      catch (Throwable ex) {
        Messages.showErrorDialog(e.getProject(), ex.getMessage(), DiffBundle.message("can.t.show.diff.in.external.tool"));
      }
    }
  }

  private class ShowInExternalToolActionGroup extends ActionGroup implements DumbAware {
    private ShowInExternalToolActionGroup() {
      ActionUtil.copyFrom(this, "Diff.ShowInExternalTool");
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      getShowActions().get(0).actionPerformed(e);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      Presentation presentation = e.getPresentation();
      if (!ExternalDiffTool.isEnabled()) {
        presentation.setEnabledAndVisible(false);
        return;
      }

      List<ShowInExternalToolAction> actions = getShowActions();

      presentation.setEnabled(ExternalDiffTool.canShow(myActiveRequest));
      presentation.setPerformGroup(actions.size() == 1);
      presentation.putClientProperty(ActionUtil.HIDE_DROPDOWN_ICON, presentation.isPerformGroup());
      presentation.setPopupGroup(true);
      presentation.setVisible(true);
    }

    @Override
    public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
      List<ShowInExternalToolAction> actions = getShowActions();
      if (actions.size() <= 1) return AnAction.EMPTY_ARRAY;

      return actions.toArray(AnAction.EMPTY_ARRAY);
    }

    private @Unmodifiable @NotNull List<ShowInExternalToolAction> getShowActions() {
      Map<ExternalToolGroup, List<ExternalTool>> externalTools = ExternalDiffSettings.getInstance().getExternalTools();
      List<ExternalTool> diffTools = externalTools.getOrDefault(ExternalToolGroup.DIFF_TOOL, Collections.emptyList());

      return ContainerUtil.map(diffTools, ShowInExternalToolAction::new);
    }
  }

  private @NotNull SegmentedButtonComponent<DiffTool> createDiffToolChooser() {
    var chooser = DiffToolChooser.createComponent();
    chooser.setOpaque(false);
    chooser.setFocusable(false);
    chooser.addModelListener(new SegmentedButtonComponent.ModelListener() {
      @Override
      public void onItemSelected() {
        var tool = chooser.getSelectedItem();
        if (tool != null) {
          switchToDiffTool(tool);
        }
      }
    });
    return chooser;
  }

  private void updateDiffToolChooser() {
    if (myForcedDiffTool != null) {
      myDiffToolChooser.setItems(Collections.emptyList());
      myDiffToolChooser.setSelectedItem(null);
      myDiffToolChooser.setVisible(false);
      return;
    }

    var tools = filterFittedTools(getAllKnownTools(), myContext, myActiveRequest);
    myDiffToolChooser.setItems(tools);

    var activeTool = myState.getActiveTool();
    for (DiffTool tool : tools) {
      if (isSameToolOrSubstitutor(tool, activeTool, myContext, myActiveRequest)) {
        activeTool = tool;
        break;
      }
    }
    myDiffToolChooser.setSelectedItem(activeTool);

    final var fActiveTool = activeTool;
    var hasChoice = ContainerUtil.find(tools, tool -> fActiveTool != tool) != null;
    myDiffToolChooser.setVisible(hasChoice);
  }

  private class MyChangeDiffToolActionGroup extends ActionGroup implements DumbAware {
    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }

    @Override
    public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
      if (e == null) return AnAction.EMPTY_ARRAY;

      if (myForcedDiffTool != null) {
        return AnAction.EMPTY_ARRAY;
      }

      DiffTool activeTool = e.getData(ACTIVE_DIFF_TOOL);
      DiffContext diffContext = e.getData(DiffDataKeys.DIFF_CONTEXT);
      DiffRequest diffRequest = e.getData(DiffDataKeys.DIFF_REQUEST);
      if (activeTool == null || diffContext == null || diffRequest == null) {
        return AnAction.EMPTY_ARRAY;
      }

      List<AnAction> actions = new ArrayList<>();
      for (DiffTool tool : filterFittedTools(getAllKnownTools(), diffContext, diffRequest)) {
        if (isSameToolOrSubstitutor(tool, activeTool, diffContext, diffRequest)) continue;
        actions.add(new DiffToolToggleAction(tool));
      }
      return actions.toArray(AnAction.EMPTY_ARRAY);
    }
  }

  private static boolean isSameToolOrSubstitutor(@NotNull DiffTool tool,
                                                 @NotNull DiffTool activeTool,
                                                 @NotNull DiffContext diffContext,
                                                 @NotNull DiffRequest diffRequest) {
    if (tool == activeTool) return true;
    if (findToolSubstitutor(tool, diffContext, diffRequest) == activeTool) return true;
    return false;
  }

  private final class DiffToolToggleAction extends AnAction implements DumbAware {
    private final @NotNull DiffTool myDiffTool;

    private DiffToolToggleAction(@NotNull DiffTool tool) {
      //noinspection DialogTitleCapitalization
      super(tool.getName());
      myDiffTool = tool;
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabledAndVisible(myForcedDiffTool == null);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      switchToDiffTool(myDiffTool);
    }
  }

  private class ShowActionGroupPopupAction extends DumbAwareAction {
    ShowActionGroupPopupAction() {
      ActionUtil.copyFrom(this, "Diff.ShowSettingsPopup");
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabled(myPopupActionGroup.getChildrenCount() > 0);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      ListPopup popup = JBPopupFactory.getInstance().createActionGroupPopup(
        DiffBundle.message("diff.actions"), myPopupActionGroup, e.getDataContext(),
        JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, false);
      popup.showInCenterOf(myMainPanel);
    }
  }

  //
  // Navigation
  //

  @RequiresEdt
  protected boolean hasNextChange(boolean fromUpdate) {
    return false;
  }

  @RequiresEdt
  protected boolean hasPrevChange(boolean fromUpdate) {
    return false;
  }

  /**
   * @see #goToNextChangeImpl(boolean, Runnable)
   */
  @RequiresEdt
  protected void goToNextChange(boolean fromDifferences) {
  }

  /**
   * @see #goToPrevChangeImpl(boolean, Runnable)
   */
  @RequiresEdt
  protected void goToPrevChange(boolean fromDifferences) {
  }

  @RequiresEdt
  protected boolean isNavigationEnabled() {
    return false;
  }

  protected void goToNextChangeImpl(boolean fromDifferences, @NotNull Runnable navigationTask) {
    runWithScrollPolicy(fromDifferences, ScrollToPolicy.FIRST_CHANGE, navigationTask);
  }

  protected void goToPrevChangeImpl(boolean fromDifferences, @NotNull Runnable navigationTask) {
    runWithScrollPolicy(fromDifferences, ScrollToPolicy.LAST_CHANGE, navigationTask);
  }

  /**
   * This is a workaround for use cases, when {@code navigationTask} updates some external state (ex: selection in JTree),
   * and this update triggers an uncontrollable listener that calls {@link #updateRequest()}
   * without knowledge about requested {@link ScrollToPolicy}.
   * <p>
   * Thus, we make sure any synchronous {@link #updateRequest} calls from {@code navigationTask} will use specified scroll policy.
   */
  private void runWithScrollPolicy(boolean fromDifferences, @NotNull ScrollToPolicy lastChange, @NotNull Runnable navigationTask) {
    if (fromDifferences) {
      assert myCurrentScrollToPolicy == null;
      myCurrentScrollToPolicy = lastChange;
      try {
        navigationTask.run();
        updateRequest();
      }
      finally {
        myCurrentScrollToPolicy = null;
      }
    }
    else {
      navigationTask.run();
      updateRequest();
    }
  }

  /**
   * @deprecated {@code IdeActions.ACTION_NEXT_DIFF} action or {@code getNavigationActions()} group should be used instead
   */
  @SuppressWarnings("InnerClassMayBeStatic")
  @Deprecated
  protected class MyNextDifferenceAction extends DelegatingNavigationAction {
    public MyNextDifferenceAction() {
      super(IdeActions.ACTION_NEXT_DIFF);
    }
  }

  /**
   * @deprecated {@code IdeActions.ACTION_PREVIOUS_DIFF} action or {@code getNavigationActions()} group should be used instead
   */
  @SuppressWarnings("InnerClassMayBeStatic")
  @Deprecated
  protected class MyPrevDifferenceAction extends DelegatingNavigationAction {
    public MyPrevDifferenceAction() {
      super(IdeActions.ACTION_PREVIOUS_DIFF);
    }
  }

  // Iterate requests

  /**
   * @deprecated {@code Diff.NextChange} action or {@code getNavigationActions()} group should be used instead
   */
  @SuppressWarnings("InnerClassMayBeStatic")
  @Deprecated
  protected class MyNextChangeAction extends DelegatingNavigationAction {
    public MyNextChangeAction() {
      super(DiffNextFileAction.ID);
    }
  }

  /**
   * @deprecated {@code Diff.PrevChange} action or {@code getNavigationActions()} group should be used instead
   */
  @SuppressWarnings("InnerClassMayBeStatic")
  @Deprecated
  protected class MyPrevChangeAction extends DelegatingNavigationAction {
    public MyPrevChangeAction() {
      super(DiffPreviousFileAction.ID);
    }
  }

  /**
   * @deprecated only for compatibility
   **/
  @Deprecated(forRemoval = true)
  protected static abstract class DelegatingNavigationAction extends AnAction implements DumbAware {
    private final @NotNull AnAction delegate;

    DelegatingNavigationAction(@NotNull String actionId) {
      delegate = ActionManager.getInstance().getAction(actionId);
      ActionUtil.copyFrom(this, actionId);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return delegate.getActionUpdateThread();
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      delegate.update(e);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      delegate.actionPerformed(e);
    }
  }

  //
  // Helpers
  //

  @ApiStatus.Internal
  public class MyPanel extends JBPanelWithEmptyText implements UiDataProvider {
    MyPanel() {
      super(new BorderLayout());
    }

    public @NotNull DiffRequestProcessor getProcessor() {
      return DiffRequestProcessor.this;
    }

    @Override
    public Dimension getPreferredSize() {
      Dimension windowSize = DiffUtil.getDefaultDiffPanelSize();
      Dimension size = super.getPreferredSize();
      return new Dimension(Math.max(windowSize.width, size.width), Math.max(windowSize.height, size.height));
    }

    @Override
    public void uiDataSnapshot(@NotNull DataSink sink) {
      sink.set(DiffDataKeys.NAVIGATION_CALLBACK, createAfterNavigateCallback());

      DataSink.uiDataSnapshot(sink, myContext.getUserData(DiffUserDataKeys.DATA_PROVIDER));
      DataSink.uiDataSnapshot(sink, myActiveRequest.getUserData(DiffUserDataKeys.DATA_PROVIDER));
      DataSink.uiDataSnapshot(sink, myState);

      sink.set(CommonDataKeys.PROJECT, myProject);
      sink.set(DiffDataKeys.DIFF_CONTEXT, myContext);
      sink.set(DiffDataKeys.DIFF_REQUEST, myActiveRequest);
      sink.set(ACTIVE_DIFF_TOOL, myState.getActiveTool());
      sink.set(PlatformCoreDataKeys.HELP_ID,
               myActiveRequest.getUserData(DiffUserDataKeys.HELP_ID) != null
               ? myActiveRequest.getUserData(DiffUserDataKeys.HELP_ID)
               : "reference.dialogs.diff.file");
      if (isNavigationEnabled()) {
        sink.set(DiffDataKeys.PREV_NEXT_FILE_ITERABLE, navigator);
      }
      if (getSettings().isGoToNextFileOnNextDifference()) {
        sink.set(DiffDataKeys.CROSS_FILE_PREV_NEXT_DIFFERENCE_ITERABLE, navigator);
      }
    }
  }

  private static class MyProgressBar extends JProgressBar {
    private int myProgressCount = 0;

    MyProgressBar() {
      setIndeterminate(true);
      setVisible(false);
    }

    public void startProgress() {
      myProgressCount++;
      setVisible(true);
    }

    public void stopProgress() {
      myProgressCount--;
      LOG.assertTrue(myProgressCount >= 0);
      if (myProgressCount == 0) setVisible(false);
    }
  }

  private class MyFocusTraversalPolicy extends IdeFocusTraversalPolicy {
    @Override
    public final Component getDefaultComponent(final Container focusCycleRoot) {
      JComponent component = DiffRequestProcessor.this.getPreferredFocusedComponent();
      return IdeFocusTraversalPolicy.getPreferredFocusedComponent(component, this);
    }

    @Override
    protected @Nullable Project getProject() {
      return myProject;
    }
  }

  private class MyDiffContext extends DiffContextOnDataHolders {
    MyDiffContext(@NotNull UserDataHolder initialContext) {
      super(initialContext);
    }

    @Override
    public void reopenDiffRequest() {
      updateRequest(true);
    }

    @Override
    public void reloadDiffRequest() {
      reloadRequest();
    }

    @Override
    public void showProgressBar(boolean enabled) {
      if (enabled) {
        myProgressBar.startProgress();
      }
      else {
        myProgressBar.stopProgress();
      }
    }

    @Override
    public void setWindowTitle(@NotNull String title) {
      setTitle(title);
    }

    @Override
    public @Nullable Project getProject() {
      return DiffRequestProcessor.this.getProject();
    }

    @Override
    public boolean isFocusedInWindow() {
      return DiffRequestProcessor.this.isFocusedInWindow();
    }

    @Override
    public boolean isWindowFocused() {
      return DiffRequestProcessor.this.isWindowFocused();
    }

    @Override
    public void requestFocusInWindow() {
      DiffRequestProcessor.this.requestFocusInWindow();
    }
  }

  private static class ApplyData {
    private final @NotNull DiffRequest request;
    private final boolean force;
    private final @Nullable ScrollToPolicy scrollToChangePolicy;

    ApplyData(@NotNull DiffRequest request, boolean force, @Nullable ScrollToPolicy scrollToChangePolicy) {
      this.request = request;
      this.force = force;
      this.scrollToChangePolicy = scrollToChangePolicy;
    }
  }

  private static void onAssigned(@NotNull DiffRequest request, boolean isAssigned) {
    try {
      request.onAssigned(isAssigned);
    }
    catch (Exception e) {
      LOG.error(Logger.shouldRethrow(e) ? new RuntimeException(e) : e);
    }
  }

  //
  // States
  //

  private interface ViewerState extends UiDataProvider {
    @RequiresEdt
    default void init() { }

    @RequiresEdt
    default void destroy() { }

    default @Nullable JComponent getPreferredFocusedComponent() { return null; }

    @Override
    default void uiDataSnapshot(@NotNull DataSink sink) { }

    @NotNull
    DiffTool getActiveTool();
  }

  private static final class EmptyState implements ViewerState {
    private static final EmptyState INSTANCE = new EmptyState();

    @Override
    public @NotNull DiffTool getActiveTool() {
      return ErrorDiffTool.INSTANCE;
    }
  }

  private class ErrorState implements ViewerState {
    private final @Nullable DiffTool myDiffTool;

    private final @NotNull DiffViewer myViewer;

    ErrorState(@NotNull MessageDiffRequest request, @Nullable DiffTool diffTool) {
      myDiffTool = diffTool;
      myViewer = ErrorDiffTool.INSTANCE.createComponent(myContext, request);
    }

    @Override
    @RequiresEdt
    public void init() {
      myContentPanel.setContent(myViewer.getComponent());

      FrameDiffTool.ToolbarComponents init = myViewer.init();
      buildToolbar(init.toolbarActions, init.rightToolbarActions);
    }

    @Override
    @RequiresEdt
    public void destroy() {
      try {
        Disposer.dispose(myViewer);
      }
      catch (Throwable e) {
        LOG.error(e);
      }
    }

    @Override
    public @NotNull DiffTool getActiveTool() {
      return myDiffTool != null ? myDiffTool : ErrorDiffTool.INSTANCE;
    }
  }

  private class DefaultState implements ViewerState {
    private final @NotNull DiffViewer myViewer;
    private final @NotNull FrameDiffTool myTool;

    DefaultState(@NotNull DiffViewer viewer, @NotNull FrameDiffTool tool) {
      myViewer = viewer;
      myTool = tool;
    }

    @Override
    @RequiresEdt
    public void init() {
      myContentPanel.setContent(myViewer.getComponent());
      setTitle(myActiveRequest.getTitle());

      FrameDiffTool.ToolbarComponents toolbarComponents = myViewer.init();
      buildToolbar(toolbarComponents.toolbarActions, toolbarComponents.rightToolbarActions);
      buildActionPopup(toolbarComponents.popupActions);

      myToolbarStatusPanel.setContent(toolbarComponents.statusPanel);
      if (shouldAddToolbarBottomBorder(toolbarComponents)) {
        myTopPanel.setNeedBottomSeparatorBorder(true);
      }
    }

    @Override
    @RequiresEdt
    public void destroy() {
      try {
        Disposer.dispose(myViewer);
      }
      catch (Throwable e) {
        LOG.error(e);
      }
    }

    @Override
    public @Nullable JComponent getPreferredFocusedComponent() {
      return myViewer.getPreferredFocusedComponent();
    }

    @Override
    public @NotNull DiffTool getActiveTool() {
      return myTool;
    }

    @Override
    public void uiDataSnapshot(@NotNull DataSink sink) {
      sink.set(DiffDataKeys.DIFF_VIEWER, myViewer);
    }
  }

  private class WrapperState implements ViewerState {
    private final @NotNull DiffViewer myViewer;
    private final @NotNull FrameDiffTool myTool;

    private final @NotNull DiffViewer myWrapperViewer;

    WrapperState(@NotNull DiffViewer viewer, @NotNull FrameDiffTool tool, @NotNull DiffViewerWrapper wrapper) {
      myViewer = viewer;
      myTool = tool;
      myWrapperViewer = wrapper.createComponent(myContext, myActiveRequest, myViewer);
    }

    @Override
    @RequiresEdt
    public void init() {
      myContentPanel.setContent(myWrapperViewer.getComponent());
      setTitle(myActiveRequest.getTitle());


      FrameDiffTool.ToolbarComponents toolbarComponents1 = myViewer.init();
      FrameDiffTool.ToolbarComponents toolbarComponents2 = myWrapperViewer.init();

      buildToolbar(mergeActions(toolbarComponents1.toolbarActions, toolbarComponents2.toolbarActions),
                   mergeActions(toolbarComponents1.rightToolbarActions, toolbarComponents2.rightToolbarActions));
      buildActionPopup(mergeActions(toolbarComponents1.popupActions, toolbarComponents2.popupActions));

      myToolbarStatusPanel.setContent(toolbarComponents1.statusPanel); // TODO: combine both panels ?
      if (shouldAddToolbarBottomBorder(toolbarComponents1)) {
        myTopPanel.setNeedBottomSeparatorBorder(true);
      }
    }

    private static @Nullable List<AnAction> mergeActions(@Nullable List<AnAction> actions1, @Nullable List<AnAction> actions2) {
      if (actions1 == null && actions2 == null) return null;
      if (ContainerUtil.isEmpty(actions1)) return actions2;
      if (ContainerUtil.isEmpty(actions2)) return actions1;

      List<AnAction> result = new ArrayList<>(actions1);
      result.add(Separator.getInstance());

      for (AnAction action : actions2) {
        if (action instanceof Separator ||
            !actions1.contains(action)) {
          result.add(action);
        }
      }

      return result;
    }

    @Override
    @RequiresEdt
    public void destroy() {
      try {
        Disposer.dispose(myViewer);
        Disposer.dispose(myWrapperViewer);
      }
      catch (Throwable e) {
        LOG.error(e);
      }
    }

    @Override
    public @Nullable JComponent getPreferredFocusedComponent() {
      return myWrapperViewer.getPreferredFocusedComponent();
    }

    @Override
    public @NotNull DiffTool getActiveTool() {
      return myTool;
    }

    @Override
    public void uiDataSnapshot(@NotNull DataSink sink) {
      sink.set(DiffDataKeys.WRAPPING_DIFF_VIEWER, myWrapperViewer);
      sink.set(DiffDataKeys.DIFF_VIEWER, myViewer);
    }
  }

  private class DiffNavigator implements PrevNextFileIterable, CrossFilePrevNextDifferenceIterableSupport {
    private enum DiffIterationState {NEXT, PREV, NONE}

    private @NotNull volatile DiffIterationState myIterationState = DiffIterationState.NONE;

    @Override
    public boolean canGoPrev(boolean fastCheckOnly) {
      return hasPrevChange(fastCheckOnly);
    }

    @Override
    public boolean canGoNext(boolean fastCheckOnly) {
      return hasNextChange(fastCheckOnly);
    }

    @Override
    public void goPrev(boolean showLastChange) {
      goToPrevChange(showLastChange);
      myIterationState = DiffIterationState.NONE;
    }

    @Override
    public void goNext(boolean showFirstChange) {
      goToNextChange(showFirstChange);
      myIterationState = DiffIterationState.NONE;
    }

    @Override
    public boolean canGoNextNow() {
      return myIterationState == DiffIterationState.NEXT;
    }

    @Override
    public boolean canGoPrevNow() {
      return myIterationState == DiffIterationState.PREV;
    }

    @Override
    public void prepareGoNext(@NotNull DataContext dataContext) {
      notifyMessage(dataContext, myContentPanel, true);
      myIterationState = DiffIterationState.NEXT;
    }

    @Override
    public void prepareGoPrev(@NotNull DataContext dataContext) {
      notifyMessage(dataContext, myContentPanel, false);
      myIterationState = DiffIterationState.PREV;
    }

    @Override
    public void reset() {
      myIterationState = DiffIterationState.NONE;
    }

    private static void notifyMessage(@NotNull DataContext dataContext, @NotNull JComponent contentPanel, boolean next) {
      if (!UIUtil.isShowing(contentPanel)) return;
      Editor editor = dataContext.getData(DiffDataKeys.CURRENT_EDITOR);

      // TODO: provide "change" word in chain UserData - for tests/etc
      String message = DiffUtil.createNotificationText(next ? DiffBundle.message("press.again.to.go.to.the.next.file")
                                                            : DiffBundle.message("press.again.to.go.to.the.previous.file"),
                                                       DiffBundle.message("notification.you.can.disable.this.feature.in.0",
                                                                          DiffUtil.getSettingsConfigurablePath()));

      final LightweightHint hint = new LightweightHint(HintUtil.createInformationLabel(message));
      Point point = new Point(contentPanel.getWidth() / 2, next ? contentPanel.getHeight() - JBUIScale.scale(40) : JBUIScale.scale(40));

      if (editor == null || editor.isDisposed()) {
        final Component owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        final HintHint hintHint = createNotifyHint(contentPanel, point, next);
        hint.show(contentPanel, point.x, point.y, owner instanceof JComponent ? (JComponent)owner : null, hintHint);
      }
      else {
        int x = SwingUtilities.convertPoint(contentPanel, point, editor.getComponent()).x;

        JComponent header = editor.getHeaderComponent();
        int shift = editor.getScrollingModel().getVerticalScrollOffset() - (header != null ? header.getHeight() : 0);

        LogicalPosition position;
        LineRange changeRange = dataContext.getData(DiffDataKeys.CURRENT_CHANGE_RANGE);
        if (changeRange == null) {
          position = new LogicalPosition(editor.getCaretModel().getLogicalPosition().line + (next ? 1 : 0), 0);
        }
        else {
          position = new LogicalPosition(next ? changeRange.end : changeRange.start, 0);
        }
        int y = editor.logicalPositionToXY(position).y - shift;

        Point editorPoint = new Point(x, y);
        final HintHint hintHint = createNotifyHint(editor.getComponent(), editorPoint, !next);
        HintManagerImpl.getInstanceImpl().showEditorHint(hint, editor, editorPoint, HintManager.HIDE_BY_ANY_KEY |
                                                                                    HintManager.HIDE_BY_TEXT_CHANGE |
                                                                                    HintManager.HIDE_BY_SCROLLING, 0, false, hintHint);
      }
    }

    private static @NotNull HintHint createNotifyHint(@NotNull JComponent component, @NotNull Point point, boolean above) {
      return new HintHint(component, point)
        .setPreferredPosition(above ? Balloon.Position.above : Balloon.Position.below)
        .setAwtTooltip(true)
        .setFont(StartupUiUtil.getLabelFont().deriveFont(Font.BOLD))
        .setBorderColor(HintUtil.getHintBorderColor())
        .setTextBg(HintUtil.getInformationColor())
        .setShowImmediately(true);
    }
  }
}
