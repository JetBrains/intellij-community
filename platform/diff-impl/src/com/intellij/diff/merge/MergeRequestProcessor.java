// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.merge;

import com.intellij.CommonBundle;
import com.intellij.diff.DiffManagerEx;
import com.intellij.diff.DiffNotificationIdsHolder;
import com.intellij.diff.actions.impl.NextDifferenceAction;
import com.intellij.diff.actions.impl.PrevDifferenceAction;
import com.intellij.diff.tools.util.DiffDataKeys;
import com.intellij.diff.tools.util.PrevNextDifferenceIterable;
import com.intellij.diff.util.DiffPlaces;
import com.intellij.diff.util.DiffUserDataKeys;
import com.intellij.diff.util.DiffUtil;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.BooleanGetter;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.ex.IdeFocusTraversalPolicy;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.LightColors;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBEmptyBorder;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.components.BorderLayoutPanel;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.List;

import static com.intellij.diff.util.DiffUtil.recursiveRegisterShortcutSet;

// TODO: support merge request chains
// idea - to keep in memory all viewers that were modified (so binary conflict is not the case and OOM shouldn't be too often)
// suspend() / resume() methods for viewers? To not interfere with MergeRequest lifecycle: single request -> single viewer -> single applyResult()
@ApiStatus.Internal
public abstract class MergeRequestProcessor implements Disposable {
  private static final Logger LOG = Logger.getInstance(MergeRequestProcessor.class);

  private boolean myDisposed;

  @Nullable private final Project myProject;
  @NotNull private final MergeContext myContext;

  @NotNull private final List<MergeTool> myAvailableTools;

  @NotNull private final JPanel myPanel;
  @NotNull private final MyPanel myMainPanel;
  @NotNull private final Wrapper myContentPanel;
  @NotNull private final Wrapper myToolbarPanel;
  @NotNull private final Wrapper myToolbarStatusPanel;
  @NotNull private final Wrapper myNotificationPanel;
  @NotNull private final Wrapper myButtonsPanel;

  @Nullable private MergeRequest myRequest;

  @NotNull private MergeTool.MergeViewer myViewer;
  @Nullable private BooleanGetter myCloseHandler;
  private boolean myConflictResolved = false;

  public MergeRequestProcessor(@Nullable Project project) {
    myProject = project;

    myContext = new MyDiffContext();
    myContext.putUserData(DiffUserDataKeys.PLACE, DiffPlaces.MERGE);

    myAvailableTools = DiffManagerEx.getInstance().getMergeTools();

    myMainPanel = new MyPanel();
    myContentPanel = new Wrapper();
    myToolbarPanel = new Wrapper();
    myToolbarPanel.setFocusable(true);
    myToolbarStatusPanel = new Wrapper();
    myNotificationPanel = new Wrapper();
    myButtonsPanel = new Wrapper();

    myPanel = JBUI.Panels.simplePanel(myMainPanel);

    JPanel topPanel = JBUI.Panels.simplePanel(myToolbarPanel)
      .addToRight(myToolbarStatusPanel)
      .addToBottom(myNotificationPanel);

    myMainPanel.add(topPanel, BorderLayout.NORTH);
    myMainPanel.add(myContentPanel, BorderLayout.CENTER);
    myMainPanel.add(myButtonsPanel, BorderLayout.SOUTH);

    myMainPanel.setFocusTraversalPolicyProvider(true);
    myMainPanel.setFocusTraversalPolicy(new MyFocusTraversalPolicy());

    myViewer = new MessageMergeViewer(myContext, CommonBundle.getLoadingTreeNodeText());
  }

  //
  // Update
  //

  @RequiresEdt
  public void init(@NotNull MergeRequest request) {
    setTitle(request.getTitle());

    myRequest = request;
    onAssigned(myRequest, true);
    myViewer = createViewerFor(request);
    initViewer();
    installCallbackListener(myRequest);
  }

  @RequiresEdt
  public void init(@NotNull MergeRequestProducer request) {
    setTitle(request.getName());
    initViewer();

    ModalityState modality = ModalityState.stateForComponent(myPanel);
    BackgroundTaskUtil.executeOnPooledThread(this, () -> {
      try {
        MergeRequest mergeRequest = request.process(myContext, ProgressManager.getInstance().getProgressIndicator());
        ApplicationManager.getApplication().invokeLater(
          () -> {
            if (myDisposed) return;
            myRequest = mergeRequest;
            onAssigned(myRequest, true);
            swapViewer(createViewerFor(mergeRequest));
            installCallbackListener(myRequest);
          },
          modality);
      }
      catch (Throwable e) {
        LOG.warn(e);
        ApplicationManager.getApplication().invokeLater(
          () -> {
            if (myDisposed) return;
            swapViewer(new MessageMergeViewer(myContext, DiffBundle.message("label.cant.show.merge.with.description", e.getMessage())));
          },
          modality);
      }
    });
  }

  @NotNull
  private MergeTool.MergeViewer createViewerFor(@NotNull MergeRequest request) {
    try {
      return getFittedTool(request).createComponent(myContext, request);
    }
    catch (Throwable e) {
      LOG.error(e);
      return ErrorMergeTool.INSTANCE.createComponent(myContext, request);
    }
  }

  @RequiresEdt
  private void initViewer() {
    myContentPanel.setContent(myViewer.getComponent());

    MergeTool.ToolbarComponents toolbarComponents = myViewer.init();

    buildToolbar(toolbarComponents.toolbarActions);
    myToolbarStatusPanel.setContent(toolbarComponents.statusPanel);
    myCloseHandler = toolbarComponents.closeHandler;

    updateBottomActions();
  }

  @RequiresEdt
  private void destroyViewer() {
    Disposer.dispose(myViewer);

    ActionUtil.clearActions(myMainPanel);

    myContentPanel.setContent(null);
    myToolbarPanel.setContent(null);
    myToolbarStatusPanel.setContent(null);
    myButtonsPanel.setContent(null);
    myCloseHandler = null;
  }

  private void updateBottomActions() {
    Action applyLeft = myViewer.getResolveAction(MergeResult.LEFT);
    Action applyRight = myViewer.getResolveAction(MergeResult.RIGHT);
    Action resolveAction = myViewer.getResolveAction(MergeResult.RESOLVED);
    Action cancelAction = myViewer.getResolveAction(MergeResult.CANCEL);

    if (resolveAction != null) {
      resolveAction.putValue(DialogWrapper.DEFAULT_ACTION, true);

      new DumbAwareAction() {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          resolveAction.actionPerformed(null);
        }
      }.registerCustomShortcutSet(CommonShortcuts.getCtrlEnter(), getRootPane(), this);
    }

    List<Action> leftActions = ContainerUtil.packNullables(applyLeft, applyRight);
    List<Action> rightActions = SystemInfo.isMac ? ContainerUtil.packNullables(cancelAction, resolveAction)
                                                 : ContainerUtil.packNullables(resolveAction, cancelAction);

    JRootPane rootPane = getRootPane();
    JPanel buttonsPanel = new NonOpaquePanel(new BorderLayout());
    buttonsPanel.setBorder(new JBEmptyBorder(UIUtil.PANEL_REGULAR_INSETS));

    if (!leftActions.isEmpty()) {
      buttonsPanel.add(createButtonsPanel(leftActions, rootPane), BorderLayout.WEST);
    }
    if (!rightActions.isEmpty()) {
      buttonsPanel.add(createButtonsPanel(rightActions, rootPane), BorderLayout.EAST);
    }

    BorderLayoutPanel toolbarPanel = createFeedbackToolbarPanel();

    buttonsPanel.add(toolbarPanel, BorderLayout.CENTER);
    myButtonsPanel.setContent(buttonsPanel);
  }

  private @NotNull BorderLayoutPanel createFeedbackToolbarPanel() {
    AnAction action = ActionManager.getInstance().getAction("Diff.Conflicts.Feedback");
    ActionToolbar actionToolbar = ActionManager.getInstance().createActionToolbar("FeedbackToolbar", (ActionGroup)action, true);
    actionToolbar.setTargetComponent(myContentPanel.getTargetComponent());
    return new BorderLayoutPanel().addToRight(actionToolbar.getComponent());
  }

  @NotNull
  private static JPanel createButtonsPanel(@NotNull List<? extends Action> actions, @Nullable JRootPane rootPane) {
    List<JButton> buttons = ContainerUtil.map(actions, action -> DialogWrapper.createJButtonForAction(action, rootPane));
    return DialogWrapper.layoutButtonsPanel(buttons);
  }

  @NotNull
  protected DefaultActionGroup collectToolbarActions(@Nullable List<? extends AnAction> viewerActions) {
    DefaultActionGroup group = new DefaultActionGroup();

    List<AnAction> navigationActions = Arrays.asList(new MyPrevDifferenceAction(), new MyNextDifferenceAction());
    DiffUtil.addActionBlock(group, navigationActions);

    DiffUtil.addActionBlock(group, viewerActions);

    List<AnAction> requestContextActions = myRequest != null ? myRequest.getUserData(DiffUserDataKeys.CONTEXT_ACTIONS) : null;
    DiffUtil.addActionBlock(group, requestContextActions);

    List<AnAction> contextActions = myContext.getUserData(DiffUserDataKeys.CONTEXT_ACTIONS);
    DiffUtil.addActionBlock(group, contextActions);

    return group;
  }

  protected void buildToolbar(@Nullable List<? extends AnAction> viewerActions) {
    ActionGroup group = collectToolbarActions(viewerActions);
    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.DIFF_TOOLBAR, group, true);
    toolbar.setShowSeparatorTitles(true);

    toolbar.setTargetComponent(myContentPanel.getTargetComponent());

    myToolbarPanel.setContent(toolbar.getComponent());
    recursiveRegisterShortcutSet(group, myMainPanel, null);
  }

  @NotNull
  private MergeTool getFittedTool(@NotNull MergeRequest request) {
    for (MergeTool tool : myAvailableTools) {
      try {
        if (tool.canShow(myContext, request)) return tool;
      }
      catch (Throwable e) {
        LOG.error(e);
      }
    }

    return ErrorMergeTool.INSTANCE;
  }

  private void setTitle(@Nullable String title) {
    if (title == null) title = DiffBundle.message("merge.files.dialog.title");
    setWindowTitle(title);
  }

  private void installCallbackListener(@NotNull MergeRequest request) {
    MergeCallback callback = MergeCallback.getCallback(request);
    callback.addListener(new MergeCallback.Listener() {
      @Override
      public void fireConflictInvalid() {
        showInvalidRequestNotification();
      }
    }, this);

    if (!callback.checkIsValid()) {
      showInvalidRequestNotification();
    }
  }

  private void showInvalidRequestNotification() {
    ApplicationManager.getApplication().invokeLater(() -> {
      if (myDisposed) return;
      if (!myNotificationPanel.isNull()) return;

      EditorNotificationPanel notification = new EditorNotificationPanel(LightColors.RED, EditorNotificationPanel.Status.Error);
      notification.setText(DiffBundle.message("error.conflict.is.not.valid.and.no.longer.can.be.resolved"));
      notification.createActionLabel(DiffBundle.message("button.abort.resolve"), () -> {
        applyRequestResult(MergeResult.CANCEL);
        closeDialog();
      });
      myNotificationPanel.setContent(notification);
      myMainPanel.validate();
      myMainPanel.repaint();
    }, ModalityState.stateForComponent(myPanel));
  }

  @Override
  public void dispose() {
    if (myDisposed) return;
    UIUtil.invokeLaterIfNeeded(() -> {
      if (myDisposed) return;
      myDisposed = true;

      onDispose();

      destroyViewer();
      applyRequestResult(MergeResult.CANCEL);

      if (myRequest != null) {
        onAssigned(myRequest, false);
      }
    });
  }

  @RequiresEdt
  private void applyRequestResult(@NotNull MergeResult result) {
    if (myConflictResolved || myRequest == null) return;
    myConflictResolved = true;
    try {
      myRequest.applyResult(result);
    }
    catch (Exception e) {
      LOG.warn(e);
      new Notification("Merge Internal Error",
                       DiffBundle.message("can.t.finish.merge.resolve"),
                       e.getMessage(),
                       NotificationType.ERROR)
        .setDisplayId(DiffNotificationIdsHolder.MERGE_INTERNAL_ERROR)
        .notify(myProject);
    }
  }

  @RequiresEdt
  private void reopenWithTool(@NotNull MergeTool tool) {
    if (myRequest == null) return;
    if (myConflictResolved) {
      LOG.warn("Can't reopen with " + tool + " - conflict already resolved");
      return;
    }

    if (!tool.canShow(myContext, myRequest)) {
      LOG.warn("Can't reopen with " + tool + " - " + myRequest);
      return;
    }

    MergeTool.MergeViewer newViewer;
    try {
      newViewer = tool.createComponent(myContext, myRequest);
    }
    catch (Throwable e) {
      LOG.error(e);
      return;
    }

    swapViewer(newViewer);
  }

  private void swapViewer(@NotNull MergeTool.MergeViewer newViewer) {
    DiffUtil.runPreservingFocus(myContext, () -> {
      destroyViewer();
      myViewer = newViewer;
      initViewer();
    });
  }

  private static void onAssigned(@NotNull MergeRequest request, boolean isAssigned) {
    try {
      request.onAssigned(isAssigned);
    }
    catch (Exception e) {
      LOG.error(e);
    }
  }

  //
  // Abstract
  //

  @RequiresEdt
  protected void onDispose() {
  }

  protected void setWindowTitle(@NotNull String title) {
  }

  public abstract void closeDialog();

  @Nullable
  protected abstract JRootPane getRootPane();

  @Nullable
  public <T> T getContextUserData(@NotNull Key<T> key) {
    return myContext.getUserData(key);
  }

  public <T> void putContextUserData(@NotNull Key<T> key, @Nullable T value) {
    myContext.putUserData(key, value);
  }

  //
  // Getters
  //

  @NotNull
  public JComponent getComponent() {
    return myPanel;
  }

  @Nullable
  public JComponent getPreferredFocusedComponent() {
    JComponent component = myViewer.getPreferredFocusedComponent();
    return component != null ? component : myToolbarPanel.getTargetComponent();
  }

  @Nullable
  public Project getProject() {
    return myProject;
  }

  @NotNull
  public MergeContext getContext() {
    return myContext;
  }

  @RequiresEdt
  public boolean checkCloseAction() {
    return myConflictResolved || myCloseHandler == null || myCloseHandler.get();
  }

  //
  // Misc
  //

  private boolean isFocusedInWindow() {
    return DiffUtil.isFocusedComponentInWindow(myPanel);
  }

  private void requestFocusInWindow() {
    DiffUtil.requestFocusInWindow(getPreferredFocusedComponent());
  }

  //
  // Navigation
  //

  private static class MyNextDifferenceAction extends NextDifferenceAction {
    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      if (!ActionPlaces.DIFF_TOOLBAR.equals(e.getPlace())) {
        e.getPresentation().setEnabled(true);
        return;
      }

      PrevNextDifferenceIterable iterable = e.getData(DiffDataKeys.PREV_NEXT_DIFFERENCE_ITERABLE);
      if (iterable != null && iterable.canGoNext()) {
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
      }
    }
  }

  private static class MyPrevDifferenceAction extends PrevDifferenceAction {
    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      if (!ActionPlaces.DIFF_TOOLBAR.equals(e.getPlace())) {
        e.getPresentation().setEnabled(true);
        return;
      }

      PrevNextDifferenceIterable iterable = e.getData(DiffDataKeys.PREV_NEXT_DIFFERENCE_ITERABLE);
      if (iterable != null && iterable.canGoPrev()) {
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
      }
    }
  }

  //
  // Helpers
  //

  private class MyPanel extends JPanel implements UiDataProvider {
    MyPanel() {
      super(new BorderLayout());
    }

    @Override
    public void uiDataSnapshot(@NotNull DataSink sink) {
      sink.set(CommonDataKeys.PROJECT, myProject);
      String requestHelpId = myRequest == null ? null : myRequest.getUserData(DiffUserDataKeys.HELP_ID);
      sink.set(PlatformCoreDataKeys.HELP_ID,
               requestHelpId != null ? requestHelpId :
               "procedures.vcWithIDEA.commonVcsOps.integrateDiffs.resolveConflict");
      sink.set(DiffDataKeys.MERGE_VIEWER, myViewer);

      DataProvider contextProvider = myContext.getUserData(DiffUserDataKeys.DATA_PROVIDER);
      DataSink.uiDataSnapshot(sink, contextProvider);

      DataProvider requestProvider = myRequest != null ? myRequest.getUserData(DiffUserDataKeys.DATA_PROVIDER) : null;
      DataSink.uiDataSnapshot(sink, requestProvider);
    }
  }

  private class MyFocusTraversalPolicy extends IdeFocusTraversalPolicy {
    @Override
    public final Component getDefaultComponent(final Container focusCycleRoot) {
      JComponent component = MergeRequestProcessor.this.getPreferredFocusedComponent();
      if (component == null) return null;
      return IdeFocusTraversalPolicy.getPreferredFocusedComponent(component, this);
    }

    @Nullable
    @Override
    protected Project getProject() {
      return myProject;
    }
  }

  private class MyDiffContext extends MergeContextEx {
    @Nullable
    @Override
    public Project getProject() {
      return MergeRequestProcessor.this.getProject();
    }

    @Override
    public boolean isFocusedInWindow() {
      return MergeRequestProcessor.this.isFocusedInWindow();
    }

    @Override
    public void requestFocusInWindow() {
      MergeRequestProcessor.this.requestFocusInWindow();
    }

    @Override
    public void finishMerge(@NotNull MergeResult result) {
      applyRequestResult(result);
      MergeRequestProcessor.this.closeDialog();
    }

    @Override
    @RequiresEdt
    public void reopenWithTool(@NotNull MergeTool tool) {
      MergeRequestProcessor.this.reopenWithTool(tool);
    }
  }
}
