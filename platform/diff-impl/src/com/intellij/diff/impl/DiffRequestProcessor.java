// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.impl;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.diff.*;
import com.intellij.diff.FrameDiffTool.DiffViewer;
import com.intellij.diff.actions.impl.*;
import com.intellij.diff.editor.DiffViewerVirtualFile;
import com.intellij.diff.impl.DiffSettingsHolder.DiffSettings;
import com.intellij.diff.impl.ui.DiffToolChooser;
import com.intellij.diff.lang.DiffIgnoredRangeProvider;
import com.intellij.diff.requests.*;
import com.intellij.diff.tools.ErrorDiffTool;
import com.intellij.diff.tools.combined.CombinedDiffViewer;
import com.intellij.diff.tools.external.ExternalDiffSettings;
import com.intellij.diff.tools.external.ExternalDiffSettings.ExternalTool;
import com.intellij.diff.tools.external.ExternalDiffSettings.ExternalToolGroup;
import com.intellij.diff.tools.external.ExternalDiffTool;
import com.intellij.diff.tools.util.DiffDataKeys;
import com.intellij.diff.tools.util.PrevNextDifferenceIterable;
import com.intellij.diff.util.DiffUserDataKeys;
import com.intellij.diff.util.DiffUserDataKeysEx;
import com.intellij.diff.util.DiffUserDataKeysEx.ScrollToPolicy;
import com.intellij.diff.util.DiffUtil;
import com.intellij.diff.util.LineRange;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.actionSystem.toolbarLayout.ToolbarLayoutStrategy;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.diff.impl.DiffUsageTriggerCollector;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
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
import com.intellij.openapi.util.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.ex.IdeFocusTraversalPolicy;
import com.intellij.ui.*;
import com.intellij.ui.components.JBPanelWithEmptyText;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.ui.mac.touchbar.Touchbar;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.EventDispatcher;
import com.intellij.util.ObjectUtils;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.components.BorderLayoutPanel;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.intellij.diff.util.DiffUtil.recursiveRegisterShortcutSet;
import static com.intellij.util.ObjectUtils.chooseNotNull;

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

  private final @NotNull JPanel myPanel;
  private final @NotNull MyPanel myMainPanel;
  private final @NotNull Wrapper myContentPanel;
  private final @NotNull JPanel myTopPanel;
  private final @NotNull ActionToolbar myToolbar;
  private final @NotNull ActionToolbar myRightToolbar;
  private final @NotNull Wrapper myToolbarWrapper;
  private final @NotNull Wrapper myDiffInfoWrapper;
  private final @NotNull Wrapper myRightToolbarWrapper;
  private final @NotNull Wrapper myToolbarStatusPanel;
  private final @NotNull MyProgressBar myProgressBar;

  private final @NotNull EventDispatcher<DiffRequestProcessorListener> myEventDispatcher =
    EventDispatcher.create(DiffRequestProcessorListener.class);

  private @NotNull DiffRequest myActiveRequest;

  private @NotNull ViewerState myState;

  private @Nullable ScrollToPolicy myCurrentScrollToPolicy;

  private final boolean myIsNewToolbar;

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

    myIsNewToolbar = DiffUtil.isUserDataFlagSet(DiffUserDataKeysEx.DIFF_NEW_TOOLBAR, myContext);

    readToolOrderFromSettings();
    DiffTool.EP_NAME.addChangeListener(() -> {
      readToolOrderFromSettings();
      updateRequest(true);
    }, this);
    DiffToolSubstitutor.EP_NAME.addChangeListener(() -> updateRequest(true), this);
    DiffIgnoredRangeProvider.EP_NAME.addChangeListener(() -> updateRequest(true), this);

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
    if (myIsNewToolbar) {
      myToolbar.setLayoutStrategy(ToolbarLayoutStrategy.NOWRAP_STRATEGY);
    }
    myToolbar.setTargetComponent(myContentPanel);
    myToolbarWrapper = new Wrapper(myToolbar.getComponent());

    myRightToolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.DIFF_RIGHT_TOOLBAR, myRightToolbarGroup, true);
    myRightToolbar.setLayoutStrategy(ToolbarLayoutStrategy.NOWRAP_STRATEGY);
    myRightToolbar.setTargetComponent(myContentPanel.getTargetComponent());

    myRightToolbarWrapper = new Wrapper(JBUI.Panels.simplePanel(myRightToolbar.getComponent()));

    myPanel = JBUI.Panels.simplePanel(myMainPanel);
    myDiffInfoWrapper = new Wrapper();
    myTopPanel = buildTopPanel();

    Splitter bottomContentSplitter = new JBSplitter(true, "DiffRequestProcessor.BottomComponentSplitter", 0.8f);
    bottomContentSplitter.setFirstComponent(myContentPanel);

    myMainPanel.add(myTopPanel, BorderLayout.NORTH);
    myMainPanel.add(bottomContentSplitter, BorderLayout.CENTER);

    myMainPanel.setFocusTraversalPolicyProvider(true);
    myMainPanel.setFocusTraversalPolicy(new MyFocusTraversalPolicy());

    JComponent bottomPanel = myContext.getUserData(DiffUserDataKeysEx.BOTTOM_PANEL);
    if (bottomPanel != null) bottomContentSplitter.setSecondComponent(bottomPanel);
    if (bottomPanel instanceof Disposable) Disposer.register(this, (Disposable)bottomPanel);

    myState = EmptyState.INSTANCE;
    myContentPanel.setContent(DiffUtil.createMessagePanel(((LoadingDiffRequest)myActiveRequest).getMessage()));
  }

  private @NotNull BorderLayoutPanel buildTopPanel() {
    BorderLayoutPanel topPanel;
    if (myIsNewToolbar) {
      BorderLayoutPanel rightPanel = JBUI.Panels.simplePanel(myRightToolbarWrapper).addToLeft(myProgressBar);
      topPanel = JBUI.Panels.simplePanel(myDiffInfoWrapper).addToLeft(myToolbarWrapper).addToRight(rightPanel);
      GuiUtils.installVisibilityReferent(topPanel, myToolbar.getComponent());
      GuiUtils.installVisibilityReferent(topPanel, myRightToolbar.getComponent());
      RemoteTransferUIManager.forceDirectTransfer(topPanel);
    }
    else {
      JPanel statusPanel = JBUI.Panels.simplePanel(myToolbarStatusPanel).addToLeft(myProgressBar);
      topPanel = JBUI.Panels.simplePanel(myToolbarWrapper).addToRight(statusPanel);
      GuiUtils.installVisibilityReferent(topPanel, myToolbar.getComponent());
    }

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
    myIterationState = DiffIterationState.NONE;

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

    DiffUtil.runPreservingFocus(myContext, () -> {
      myState.destroy();
      myToolbarStatusPanel.setContent(null);
      myContentPanel.setContent(null);
      myTopPanel.setBorder(null);
      myDiffInfoWrapper.setContent(null);

      myToolbarGroup.removeAll();
      myRightToolbarGroup.removeAll();
      myPopupActionGroup.removeAll();
      ActionUtil.clearActions(myMainPanel);

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
    return () -> DiffUtil.minimizeDiffIfOpenedInWindow(myPanel);
  }

  protected @NotNull List<AnAction> getNavigationActions() {
    List<AnAction> actions = List.of(
      new MyPrevDifferenceAction(), new MyNextDifferenceAction(), new OpenInEditorAction(),
      Separator.getInstance(),
      new MyPrevChangeAction(), new MyNextChangeAction());

    AnAction goToChangeAction = createGoToChangeAction();
    if (goToChangeAction != null) {
      actions = ContainerUtil.append(actions, goToChangeAction);
    }

    return actions;
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
    Window window = SwingUtilities.getWindowAncestor(myPanel);
    return window != null && window.isFocused();
  }

  private boolean isFocusedInWindow() {
    return DiffUtil.isFocusedComponentInWindow(myContentPanel) ||
           DiffUtil.isFocusedComponentInWindow(myToolbar.getComponent()) ||
           (myIsNewToolbar && DiffUtil.isFocusedComponentInWindow(myRightToolbar.getComponent()));
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
      myDiffInfoWrapper.setContent(null);

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

  protected void collectToolbarActions(@Nullable List<? extends AnAction> viewerActions) {
    myToolbarGroup.removeAll();

    boolean oldToolbar = !myIsNewToolbar;
    List<AnAction> navigationActions = new ArrayList<>(getNavigationActions());
    if (oldToolbar) {
      navigationActions.add(new MyChangeDiffToolComboBoxAction());
    }
    else {
      myRightToolbarGroup.add(new MyDiffToolChooser());
    }
    DiffUtil.addActionBlock(myToolbarGroup,
                            navigationActions);

    if (oldToolbar) {
      DiffUtil.addActionBlock(myToolbarGroup, viewerActions, true);
    }
    else {
      DiffUtil.addActionBlock(myRightToolbarGroup, viewerActions, false);
    }

    List<AnAction> requestContextActions = myActiveRequest.getUserData(DiffUserDataKeys.CONTEXT_ACTIONS);
    DiffUtil.addActionBlock(myToolbarGroup, requestContextActions);

    List<AnAction> contextActions = myContext.getUserData(DiffUserDataKeys.CONTEXT_ACTIONS);
    DiffUtil.addActionBlock(myToolbarGroup, contextActions);

    if (oldToolbar) {
      DiffUtil.addActionBlock(myToolbarGroup,
                              new ShowInExternalToolActionGroup(),
                              ActionManager.getInstance().getAction(IdeActions.ACTION_CONTEXT_HELP));
    }

    if (SystemInfo.isMac) { // collect touchbar actions
      myTouchbarActionGroup.removeAll();
      myTouchbarActionGroup.addAll(
        new MyPrevDifferenceAction(), new MyNextDifferenceAction(), new OpenInEditorAction(), Separator.getInstance(),
        new MyPrevChangeAction(), new MyNextChangeAction()
      );
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

  protected void buildToolbar(@Nullable List<? extends AnAction> viewerActions) {
    collectToolbarActions(viewerActions);

    ((ActionToolbarImpl)myToolbar).reset(); // do not leak previous DiffViewer via caches
    myToolbar.setTargetComponent(myContentPanel.getTargetComponent());
    myToolbar.updateActionsImmediately();
    recursiveRegisterShortcutSet(myToolbarGroup, myMainPanel, null);

    if (myIsNewToolbar) {
      myRightToolbar.setTargetComponent(myContentPanel.getTargetComponent());
      ((ActionToolbarImpl)myRightToolbar).reset();
      myRightToolbar.updateActionsImmediately();
      recursiveRegisterShortcutSet(myRightToolbarGroup, myMainPanel, null);
    }
  }

  public @NotNull ActionToolbar getToolbar() {
    return myToolbar;
  }

  @Override
  public void setToolbarVerticalSizeReferent(@NotNull JComponent component) {
    myToolbarWrapper.setVerticalSizeReferent(component);
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
    return myPanel;
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

  @NotNull
  @Override
  public CheckedDisposable getDisposable() {
    return this;
  }

  @NotNull
  @Override
  public List<Editor> getEmbeddedEditors() {
    DiffViewer viewer = getActiveViewer();
    if (viewer instanceof EditorDiffViewer editorDiffViewer) {
      return new ArrayList<>(editorDiffViewer.getHighlightEditors());
    }
    return Collections.emptyList();
  }

  @NotNull
  @Override
  public List<VirtualFile> getFilesToRefresh() {
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
      presentation.putClientProperty(ActionButton.HIDE_DROPDOWN_ICON, presentation.isPerformGroup());
      presentation.setPopupGroup(true);
      presentation.setVisible(true);
    }

    @Override
    public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
      List<ShowInExternalToolAction> actions = getShowActions();
      if (actions.size() <= 1) return AnAction.EMPTY_ARRAY;

      return actions.toArray(AnAction.EMPTY_ARRAY);
    }

    private @NotNull List<ShowInExternalToolAction> getShowActions() {
      Map<ExternalToolGroup, List<ExternalTool>> externalTools = ExternalDiffSettings.getInstance().getExternalTools();
      List<ExternalTool> diffTools = externalTools.getOrDefault(ExternalToolGroup.DIFF_TOOL, Collections.emptyList());

      return ContainerUtil.map(diffTools, ShowInExternalToolAction::new);
    }
  }

  private class MyDiffToolChooser extends DiffToolChooser {
    private MyDiffToolChooser() {
      super(chooseNotNull(myProject, myContext.getProject()));
    }

    @Override
    public void onSelected(@NotNull Project project, @NotNull DiffTool diffTool) {
      switchToDiffTool(diffTool);
    }

    @Override
    public @NotNull List<DiffTool> getTools() {
      return new ArrayList<>(filterFittedTools(getAllKnownTools(), myContext, myActiveRequest));
    }

    @Override
    public @NotNull DiffTool getActiveTool() {
      return myState.getActiveTool();
    }

    @Override
    public @Nullable DiffTool getForcedDiffTool() {
      return myForcedDiffTool;
    }
  }

  private class MyChangeDiffToolComboBoxAction extends ComboBoxAction implements DumbAware {
    // TODO: add icons for diff tools, show only icon in toolbar - to reduce jumping on change ?
    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      Presentation presentation = e.getPresentation();

      if (myForcedDiffTool != null) {
        presentation.setEnabledAndVisible(false);
        return;
      }

      DiffTool activeTool = e.getData(ACTIVE_DIFF_TOOL);
      DiffContext diffContext = e.getData(DiffDataKeys.DIFF_CONTEXT);
      DiffRequest diffRequest = e.getData(DiffDataKeys.DIFF_REQUEST);
      if (activeTool == null || diffContext == null || diffRequest == null) {
        presentation.setEnabledAndVisible(false);
        return;
      }

      //noinspection DialogTitleCapitalization
      presentation.setText(activeTool.getName());

      for (DiffTool tool : filterFittedTools(getAllKnownTools(), diffContext, diffRequest)) {
        if (!isSameToolOrSubstitutor(tool, activeTool, diffContext, diffRequest)) {
          presentation.setEnabledAndVisible(true);
          return;
        }
      }

      presentation.setEnabledAndVisible(false);
    }

    @Override
    protected @NotNull DefaultActionGroup createPopupActionGroup(@NotNull JComponent button, @NotNull DataContext context) {
      DiffContext diffContext = context.getData(DiffDataKeys.DIFF_CONTEXT);
      DiffRequest diffRequest = context.getData(DiffDataKeys.DIFF_REQUEST);
      if (diffContext == null || diffRequest == null) return new DefaultActionGroup();

      DefaultActionGroup group = new DefaultActionGroup();
      for (DiffTool tool : filterFittedTools(getAllKnownTools(), diffContext, diffRequest)) {
        group.add(new DiffToolToggleAction(tool));
      }

      return group;
    }
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
      popup.showInCenterOf(myPanel);
    }
  }

  //
  // Navigation
  //

  private enum DiffIterationState {NEXT, PREV, NONE}

  private @NotNull DiffRequestProcessor.DiffIterationState myIterationState = DiffIterationState.NONE;

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

  protected class MyNextDifferenceAction extends NextDifferenceAction {

    public MyNextDifferenceAction() {
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      if (DiffUtil.isFromShortcut(e)) {
        e.getPresentation().setEnabledAndVisible(true);
        return;
      }

      PrevNextDifferenceIterable iterable = e.getData(DiffDataKeys.PREV_NEXT_DIFFERENCE_ITERABLE);
      if (iterable != null && iterable.canGoNext()) {
        e.getPresentation().setEnabled(true);
        return;
      }

      if (getSettings().isGoToNextFileOnNextDifference() && isNavigationEnabled() && hasNextChange(true)) {
        e.getPresentation().setEnabled(true);
        return;
      }

      e.getPresentation().setEnabled(false);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      PrevNextDifferenceIterable iterable = e.getData(DiffDataKeys.PREV_NEXT_DIFFERENCE_ITERABLE);
      if (iterable != null && iterable.canGoNext()) {
        iterable.goNext();
        myIterationState = DiffIterationState.NONE;
        return;
      }

      if (!isNavigationEnabled() || !hasNextChange(false) || !getSettings().isGoToNextFileOnNextDifference()) return;

      if (myIterationState != DiffIterationState.NEXT) {
        notifyMessage(e, true);
        myIterationState = DiffIterationState.NEXT;
        return;
      }

      goToNextChange(true);
      myIterationState = DiffIterationState.NONE;
    }
  }

  protected class MyPrevDifferenceAction extends PrevDifferenceAction {

    public MyPrevDifferenceAction() {
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      if (DiffUtil.isFromShortcut(e)) {
        e.getPresentation().setEnabledAndVisible(true);
        return;
      }

      PrevNextDifferenceIterable iterable = e.getData(DiffDataKeys.PREV_NEXT_DIFFERENCE_ITERABLE);
      if (iterable != null && iterable.canGoPrev()) {
        e.getPresentation().setEnabled(true);
        return;
      }

      if (getSettings().isGoToNextFileOnNextDifference() && isNavigationEnabled() && hasPrevChange(true)) {
        e.getPresentation().setEnabled(true);
        return;
      }

      e.getPresentation().setEnabled(false);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      PrevNextDifferenceIterable iterable = e.getData(DiffDataKeys.PREV_NEXT_DIFFERENCE_ITERABLE);
      if (iterable != null && iterable.canGoPrev()) {
        iterable.goPrev();
        myIterationState = DiffIterationState.NONE;
        return;
      }

      if (!isNavigationEnabled() || !hasPrevChange(false) || !getSettings().isGoToNextFileOnNextDifference()) return;

      if (myIterationState != DiffIterationState.PREV) {
        notifyMessage(e, false);
        myIterationState = DiffIterationState.PREV;
        return;
      }

      goToPrevChange(true);
      myIterationState = DiffIterationState.NONE;
    }
  }

  private void notifyMessage(@NotNull AnActionEvent e, boolean next) {
    notifyMessage(e, myContentPanel, next);
  }

  public static void notifyMessage(@NotNull AnActionEvent e, @NotNull JComponent contentPanel, boolean next) {
    if (!contentPanel.isShowing()) return;
    Editor editor = e.getData(DiffDataKeys.CURRENT_EDITOR);

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
      LineRange changeRange = e.getData(DiffDataKeys.CURRENT_CHANGE_RANGE);
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

  // Iterate requests

  protected class MyNextChangeAction extends NextChangeAction {
    public MyNextChangeAction() { }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      if (DiffUtil.isFromShortcut(e)) {
        e.getPresentation().setEnabledAndVisible(true);
        return;
      }

      if (!isNavigationEnabled()) {
        e.getPresentation().setEnabledAndVisible(false);
        return;
      }

      e.getPresentation().setVisible(true);
      e.getPresentation().setEnabled(hasNextChange(true));
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      if (!isNavigationEnabled() || !hasNextChange(false)) return;

      goToNextChange(false);
    }
  }

  protected class MyPrevChangeAction extends PrevChangeAction {
    public MyPrevChangeAction() { }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      if (DiffUtil.isFromShortcut(e)) {
        e.getPresentation().setEnabledAndVisible(true);
        return;
      }

      if (!isNavigationEnabled()) {
        e.getPresentation().setEnabledAndVisible(false);
        return;
      }

      e.getPresentation().setVisible(true);
      e.getPresentation().setEnabled(hasPrevChange(true));
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      if (!isNavigationEnabled() || !hasPrevChange(false)) return;

      goToPrevChange(false);
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
      sink.set(OpenInEditorAction.AFTER_NAVIGATE_CALLBACK, createAfterNavigateCallback());

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

  private class MyDiffContext extends DiffContextEx {
    private final @NotNull UserDataHolder myInitialContext;
    private final @NotNull UserDataHolder myOwnContext = new UserDataHolderBase();

    MyDiffContext(@NotNull UserDataHolder initialContext) {
      myInitialContext = initialContext;
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

    @Override
    public @Nullable <T> T getUserData(@NotNull Key<T> key) {
      T data = myOwnContext.getUserData(key);
      if (data != null) return data;
      return myInitialContext.getUserData(key);
    }

    @Override
    public <T> void putUserData(@NotNull Key<T> key, @Nullable T value) {
      myOwnContext.putUserData(key, value);
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
      LOG.error(e);
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

    @Nullable
    default JComponent getPreferredFocusedComponent() { return null; }

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
      buildToolbar(init.toolbarActions);
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
      FrameDiffTool.DiffInfo diffInfo = toolbarComponents.diffInfo;
      if (diffInfo != null) {
        myDiffInfoWrapper.setContent(diffInfo.getComponent());
      }
      else {
        myDiffInfoWrapper.setContent(null);
      }
      buildToolbar(toolbarComponents.toolbarActions);
      buildActionPopup(toolbarComponents.popupActions);

      myToolbarStatusPanel.setContent(toolbarComponents.statusPanel);
      if (shouldAddToolbarBottomBorder(toolbarComponents)) {
        myTopPanel.setBorder(JBUI.Borders.customLine(JBColor.border(), 0, 0, 1, 0));
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

      buildToolbar(mergeActions(toolbarComponents1.toolbarActions, toolbarComponents2.toolbarActions));
      buildActionPopup(mergeActions(toolbarComponents1.popupActions, toolbarComponents2.popupActions));

      myToolbarStatusPanel.setContent(toolbarComponents1.statusPanel); // TODO: combine both panels ?
      if (shouldAddToolbarBottomBorder(toolbarComponents1)) {
        myTopPanel.setBorder(JBUI.Borders.customLine(JBColor.border(), 0, 0, 1, 0));
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

  /**
   * @deprecated use {@link OpenInEditorAction}
   */
  @SuppressWarnings("InnerClassMayBeStatic") // left non-static for plugin compatibility
  @Deprecated
  protected class MyOpenInEditorAction extends OpenInEditorAction {
    public MyOpenInEditorAction() {
    }
  }
}
