// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.impl;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.diff.*;
import com.intellij.diff.FrameDiffTool.DiffViewer;
import com.intellij.diff.actions.impl.*;
import com.intellij.diff.impl.DiffSettingsHolder.DiffSettings;
import com.intellij.diff.lang.DiffIgnoredRangeProvider;
import com.intellij.diff.requests.*;
import com.intellij.diff.tools.ErrorDiffTool;
import com.intellij.diff.tools.external.ExternalDiffTool;
import com.intellij.diff.tools.util.DiffDataKeys;
import com.intellij.diff.tools.util.PrevNextDifferenceIterable;
import com.intellij.diff.util.DiffUserDataKeys;
import com.intellij.diff.util.DiffUserDataKeysEx;
import com.intellij.diff.util.DiffUserDataKeysEx.ScrollToPolicy;
import com.intellij.diff.util.DiffUtil;
import com.intellij.diff.util.LineRange;
import com.intellij.ide.impl.DataManagerImpl;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.diff.impl.DiffUsageTriggerCollector;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
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
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.ex.IdeFocusTraversalPolicy;
import com.intellij.ui.*;
import com.intellij.ui.components.JBPanelWithEmptyText;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.ui.mac.TouchbarDataKeys;
import com.intellij.ui.mac.UpdatableDefaultActionGroup;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public abstract class DiffRequestProcessor implements Disposable {
  private static final Logger LOG = Logger.getInstance(DiffRequestProcessor.class);

  private boolean myDisposed;

  @Nullable private final Project myProject;
  @NotNull private final DiffContext myContext;

  @NotNull private final DiffSettings mySettings;
  @NotNull private final List<DiffTool> myAvailableTools = new ArrayList<>();
  @NotNull private final List<DiffTool> myToolOrder = new ArrayList<>();
  @Nullable private final DiffTool myForcedDiffTool;

  @NotNull private final DefaultActionGroup myToolbarGroup;
  @NotNull private final DefaultActionGroup myPopupActionGroup;
  @NotNull private final UpdatableDefaultActionGroup myTouchbarActionGroup;

  @NotNull private final JPanel myPanel;
  @NotNull private final MyPanel myMainPanel;
  @NotNull protected final Wrapper myContentPanel;
  @NotNull private final ActionToolbar myToolbar;
  @NotNull protected final Wrapper myToolbarWrapper;
  @NotNull private final Wrapper myToolbarStatusPanel;
  @NotNull private final MyProgressBar myProgressBar;

  @NotNull private DiffRequest myActiveRequest;

  @NotNull private ViewerState myState;

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
    myForcedDiffTool = myContext.getUserData(DiffUserDataKeysEx.FORCE_DIFF_TOOL);

    updateAvailableDiffTools();
    DiffTool.EP_NAME.addChangeListener(() -> {
      updateAvailableDiffTools();
      updateRequest(true);
    }, this);
    DiffToolSubstitutor.EP_NAME.addChangeListener(() -> updateRequest(true), this);
    DiffIgnoredRangeProvider.EP_NAME.addChangeListener(() -> updateRequest(true), this);

    myToolbarGroup = new DefaultActionGroup();
    myPopupActionGroup = new DefaultActionGroup();
    myTouchbarActionGroup = new UpdatableDefaultActionGroup();
    TouchbarDataKeys.putActionDescriptor(myTouchbarActionGroup).setReplaceEsc(false);

    // UI

    myMainPanel = new MyPanel();
    myContentPanel = new Wrapper();
    myToolbarStatusPanel = new Wrapper();
    myProgressBar = new MyProgressBar();

    myToolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.DIFF_TOOLBAR, myToolbarGroup, true);
    myToolbar.setTargetComponent(myMainPanel);
    myToolbarWrapper = new Wrapper(myToolbar.getComponent());

    myPanel = JBUI.Panels.simplePanel(myMainPanel);

    JPanel statusPanel = JBUI.Panels.simplePanel(myToolbarStatusPanel).addToLeft(myProgressBar);
    JPanel topPanel = JBUI.Panels.simplePanel(myToolbarWrapper).addToRight(statusPanel);
    GuiUtils.installVisibilityReferent(topPanel, myToolbar.getComponent());

    Splitter bottomContentSplitter = new JBSplitter(true, "DiffRequestProcessor.BottomComponentSplitter", 0.8f);
    bottomContentSplitter.setFirstComponent(myContentPanel);

    myMainPanel.add(topPanel, BorderLayout.NORTH);
    myMainPanel.add(bottomContentSplitter, BorderLayout.CENTER);

    myMainPanel.setFocusTraversalPolicyProvider(true);
    myMainPanel.setFocusTraversalPolicy(new MyFocusTraversalPolicy());

    JComponent bottomPanel = myContext.getUserData(DiffUserDataKeysEx.BOTTOM_PANEL);
    if (bottomPanel != null) bottomContentSplitter.setSecondComponent(bottomPanel);
    if (bottomPanel instanceof Disposable) Disposer.register(this, (Disposable)bottomPanel);

    myState = EmptyState.INSTANCE;
    myContentPanel.setContent(DiffUtil.createMessagePanel(((LoadingDiffRequest)myActiveRequest).getMessage()));
  }

  protected boolean shouldAddToolbarBottomBorder(@NotNull FrameDiffTool.ToolbarComponents toolbarComponents) {
    return toolbarComponents.needTopToolbarBorder;
  }

  //
  // Update
  //

  @CalledInAwt
  protected void reloadRequest() {
    updateRequest(true);
  }

  @CalledInAwt
  public void updateRequest() {
    updateRequest(false);
  }

  @CalledInAwt
  public void updateRequest(boolean force) {
    updateRequest(force, null);
  }

  @CalledInAwt
  public abstract void updateRequest(boolean force, @Nullable ScrollToPolicy scrollToChangePolicy);

  @NotNull
  private FrameDiffTool getFittedTool(boolean applySubstitutor) {
    if (myForcedDiffTool instanceof FrameDiffTool) {
      return myForcedDiffTool.canShow(myContext, myActiveRequest)
             ? (FrameDiffTool)myForcedDiffTool
             : ErrorDiffTool.INSTANCE;
    }

    List<FrameDiffTool> tools = filterFittedTools(myToolOrder);
    FrameDiffTool tool = tools.isEmpty() ? ErrorDiffTool.INSTANCE : tools.get(0);

    if (applySubstitutor) {
      FrameDiffTool substitutor = findToolSubstitutor(tool);
      if (substitutor != null) return substitutor;
    }

    return tool;
  }

  @NotNull
  private List<FrameDiffTool> getAvailableFittedTools() {
    return filterFittedTools(myAvailableTools);
  }

  @NotNull
  private List<FrameDiffTool> filterFittedTools(@NotNull List<? extends DiffTool> tools) {
    List<FrameDiffTool> result = new ArrayList<>();
    for (DiffTool tool : tools) {
      try {
        if (tool instanceof FrameDiffTool) {
          if (tool.canShow(myContext, myActiveRequest)) {
            result.add((FrameDiffTool)tool);
          }
          else {
            FrameDiffTool substitutor = findToolSubstitutor(tool);
            if (substitutor != null) {
              result.add((FrameDiffTool)tool);
            }
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

  private FrameDiffTool findToolSubstitutor(@NotNull DiffTool tool) {
    DiffTool substitutor = DiffUtil.findToolSubstitutor(tool, myContext, myActiveRequest);
    return substitutor instanceof FrameDiffTool ? (FrameDiffTool)substitutor : null;
  }

  private void updateAvailableDiffTools() {
    myAvailableTools.clear();
    myToolOrder.clear();

    myAvailableTools.addAll(DiffManagerEx.getInstance().getDiffTools());
    myToolOrder.addAll(getToolOrderFromSettings(myAvailableTools));
  }

  private void moveToolOnTop(@NotNull DiffTool tool) {
    myToolOrder.remove(tool);

    FrameDiffTool toolToReplace = getFittedTool(false);

    int index;
    for (index = 0; index < myToolOrder.size(); index++) {
      if (myToolOrder.get(index) == toolToReplace) break;
    }
    myToolOrder.add(index, tool);

    updateToolOrderSettings(myToolOrder);
  }

  @NotNull
  private ViewerState createState() {
    FrameDiffTool frameTool = getFittedTool(true);

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

  @Nullable private ApplyData myQueuedApplyRequest;

  @CalledInAwt
  protected void applyRequest(@NotNull DiffRequest request, boolean force, @Nullable ScrollToPolicy scrollToChangePolicy) {
    applyRequest(request, force, scrollToChangePolicy, false);
  }

  @CalledInAwt
  protected void applyRequest(@NotNull DiffRequest request, boolean force, @Nullable ScrollToPolicy scrollToChangePolicy, boolean sync) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myIterationState = IterationState.NONE;

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

  @CalledInAwt
  private void doApplyRequest(@NotNull DiffRequest request, boolean force, @Nullable ScrollToPolicy scrollToChangePolicy) {
    if (!force && request == myActiveRequest) return;

    request.putUserData(DiffUserDataKeysEx.SCROLL_TO_CHANGE, scrollToChangePolicy);

    DiffUtil.runPreservingFocus(myContext, () -> {
      myState.destroy();
      myToolbarStatusPanel.setContent(null);
      myContentPanel.setContent(null);
      myToolbarWrapper.setBorder(null);

      myToolbarGroup.removeAll();
      myPopupActionGroup.removeAll();
      ActionUtil.clearActions(myMainPanel);

      myActiveRequest.onAssigned(false);
      myActiveRequest = request;
      myActiveRequest.onAssigned(true);

      ProgressManager.getInstance().executeNonCancelableSection(() -> {
        try {
          myState = createState();
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
          myState = new ErrorState(new ErrorDiffRequest(DiffBundle.message("error.cant.show.diff.message")), getFittedTool(true));
          myState.init();
        }
      });
    });
  }

  protected void setWindowTitle(@NotNull String title) {
  }

  protected void onAfterNavigate() {
  }

  @CalledInAwt
  protected void onDispose() {
  }

  @Nullable
  public <T> T getContextUserData(@NotNull Key<T> key) {
    return myContext.getUserData(key);
  }

  public <T> void putContextUserData(@NotNull Key<T> key, @Nullable T value) {
    myContext.putUserData(key, value);
  }

  @NotNull
  protected List<AnAction> getNavigationActions() {
    return Arrays.asList(new MyPrevDifferenceAction(), new MyNextDifferenceAction(), new MyOpenInEditorAction(), Separator.getInstance(),
                         new MyPrevChangeAction(), new MyNextChangeAction());
  }

  @NotNull
  private List<AnAction> getTouchbarActions() {
    final DefaultActionGroup left = new DefaultActionGroup(new MyPrevDifferenceAction(), new MyNextDifferenceAction());
    final DefaultActionGroup main = new DefaultActionGroup(new MyPrevChangeAction(), new MyNextChangeAction());
    TouchbarDataKeys.putActionDescriptor(main).setShowText(true).setShowImage(false).setMainGroup(true);
    return Arrays.asList(left, main);
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
           DiffUtil.isFocusedComponentInWindow(myToolbar.getComponent());
  }

  private void requestFocusInWindow() {
    DiffUtil.requestFocusInWindow(getPreferredFocusedComponent());
  }

  @NotNull
  protected List<DiffTool> getToolOrderFromSettings(@NotNull List<? extends DiffTool> availableTools) {
    List<DiffTool> result = new ArrayList<>();
    List<String> savedOrder = getSettings().getDiffToolsOrder();

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
      myPopupActionGroup.removeAll();
      ActionUtil.clearActions(myMainPanel);

      myActiveRequest.onAssigned(false);

      myState = EmptyState.INSTANCE;
      myActiveRequest = NoDiffRequest.INSTANCE;
    });
  }

  protected void collectToolbarActions(@Nullable List<? extends AnAction> viewerActions) {
    myToolbarGroup.removeAll();

    List<AnAction> navigationActions = new ArrayList<>(getNavigationActions());
    navigationActions.add(new MyChangeDiffToolAction());
    DiffUtil.addActionBlock(myToolbarGroup,
                            navigationActions);

    DiffUtil.addActionBlock(myToolbarGroup, viewerActions);

    List<AnAction> requestContextActions = myActiveRequest.getUserData(DiffUserDataKeys.CONTEXT_ACTIONS);
    DiffUtil.addActionBlock(myToolbarGroup, requestContextActions);

    List<AnAction> contextActions = myContext.getUserData(DiffUserDataKeys.CONTEXT_ACTIONS);
    DiffUtil.addActionBlock(myToolbarGroup, contextActions);

    DiffUtil.addActionBlock(myToolbarGroup,
                            new ShowInExternalToolAction(),
                            ActionManager.getInstance().getAction(IdeActions.ACTION_CONTEXT_HELP));

    myTouchbarActionGroup.replaceAll(getTouchbarActions());
  }

  protected void collectPopupActions(@Nullable List<? extends AnAction> viewerActions) {
    myPopupActionGroup.removeAll();

    List<AnAction> selectToolActions = new ArrayList<>();
    for (DiffTool tool : getAvailableFittedTools()) {
      FrameDiffTool substitutor = findToolSubstitutor(tool);
      if (tool == myState.getActiveTool() || substitutor == myState.getActiveTool()) continue;
      selectToolActions.add(new DiffToolToggleAction(tool));
    }
    DiffUtil.addActionBlock(myPopupActionGroup, selectToolActions);

    DiffUtil.addActionBlock(myPopupActionGroup, viewerActions);
  }

  protected void buildToolbar(@Nullable List<? extends AnAction> viewerActions) {
    collectToolbarActions(viewerActions);

    ((ActionToolbarImpl)myToolbar).clearPresentationCache();
    myToolbar.updateActionsImmediately();

    ActionUtil.recursiveRegisterShortcutSet(myToolbarGroup, myMainPanel, null);
  }

  protected void buildActionPopup(@Nullable List<? extends AnAction> viewerActions) {
    collectPopupActions(viewerActions);

    DiffUtil.registerAction(new ShowActionGroupPopupAction(), myMainPanel);
  }

  private void setTitle(@Nullable String title) {
    if (getContextUserData(DiffUserDataKeys.DO_NOT_CHANGE_WINDOW_TITLE) == Boolean.TRUE) return;
    if (title == null) title = DiffBundle.message("diff.files.dialog.title");
    setWindowTitle(title);
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
    JComponent component = myState.getPreferredFocusedComponent();
    JComponent fallback = myToolbar.getComponent();
    if (component == null || !component.isFocusable()) return fallback;
    if (!component.isShowing() && fallback.isShowing()) return fallback;
    return component;
  }

  @Nullable
  public Project getProject() {
    return myProject;
  }

  @NotNull
  public DiffContext getContext() {
    return myContext;
  }

  @NotNull
  protected DiffSettings getSettings() {
    return mySettings;
  }

  public boolean isDisposed() {
    return myDisposed;
  }

  //
  // Actions
  //

  private class ShowInExternalToolAction extends DumbAwareAction {
    ShowInExternalToolAction() {
      ActionUtil.copyFrom(this, "Diff.ShowInExternalTool");
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      if (!ExternalDiffTool.isEnabled()) {
        e.getPresentation().setEnabledAndVisible(false);
        return;
      }
      e.getPresentation().setEnabled(ExternalDiffTool.canShow(myActiveRequest));
      e.getPresentation().setVisible(true);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      try {
        ExternalDiffTool.showRequest(e.getProject(), myActiveRequest);
      }
      catch (ProcessCanceledException ex) {
        throw ex;
      }
      catch (Throwable ex) {
        Messages.showErrorDialog(e.getProject(), ex.getMessage(), DiffBundle.message("can.t.show.diff.in.external.tool"));
      }
    }
  }

  private class MyChangeDiffToolAction extends ComboBoxAction implements DumbAware {
    // TODO: add icons for diff tools, show only icon in toolbar - to reduce jumping on change ?

    @Override
    public void update(@NotNull AnActionEvent e) {
      Presentation presentation = e.getPresentation();

      DiffTool activeTool = myState.getActiveTool();
      presentation.setText(activeTool.getName());

      if (myForcedDiffTool != null) {
        presentation.setEnabledAndVisible(false);
        return;
      }

      for (DiffTool tool : getAvailableFittedTools()) {
        if (tool != activeTool) {
          presentation.setEnabledAndVisible(true);
          return;
        }
      }

      presentation.setEnabledAndVisible(false);
    }

    @NotNull
    @Override
    protected DefaultActionGroup createPopupActionGroup(JComponent button) {
      DefaultActionGroup group = new DefaultActionGroup();
      for (DiffTool tool : getAvailableFittedTools()) {
        group.add(new DiffToolToggleAction(tool));
      }

      return group;
    }
  }

  private class DiffToolToggleAction extends AnAction implements DumbAware {
    @NotNull private final DiffTool myDiffTool;

    private DiffToolToggleAction(@NotNull DiffTool tool) {
      super(tool.getName());
      myDiffTool = tool;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabledAndVisible(myForcedDiffTool == null);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      if (myState.getActiveTool() == myDiffTool) return;

      DiffUsageTriggerCollector.trigger("toggle.diff.tool", myDiffTool, myContext.getUserData(DiffUserDataKeys.PLACE));
      moveToolOnTop(myDiffTool);

      updateRequest(true);
    }
  }

  private class ShowActionGroupPopupAction extends DumbAwareAction {
    ShowActionGroupPopupAction() {
      ActionUtil.copyFrom(this, "Diff.ShowSettingsPopup");
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

  private enum IterationState {NEXT, PREV, NONE}

  @NotNull private IterationState myIterationState = IterationState.NONE;

  @CalledInAwt
  protected boolean hasNextChange(boolean fromUpdate) {
    return false;
  }

  @CalledInAwt
  protected boolean hasPrevChange(boolean fromUpdate) {
    return false;
  }

  @CalledInAwt
  protected void goToNextChange(boolean fromDifferences) {
  }

  @CalledInAwt
  protected void goToPrevChange(boolean fromDifferences) {
  }

  @CalledInAwt
  protected boolean isNavigationEnabled() {
    return false;
  }

  protected class MyNextDifferenceAction extends NextDifferenceAction {
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
        myIterationState = IterationState.NONE;
        return;
      }

      if (!isNavigationEnabled() || !hasNextChange(false) || !getSettings().isGoToNextFileOnNextDifference()) return;

      if (myIterationState != IterationState.NEXT) {
        notifyMessage(e, true);
        myIterationState = IterationState.NEXT;
        return;
      }

      goToNextChange(true);
    }
  }

  protected class MyPrevDifferenceAction extends PrevDifferenceAction {
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
        myIterationState = IterationState.NONE;
        return;
      }

      if (!isNavigationEnabled() || !hasPrevChange(false) || !getSettings().isGoToNextFileOnNextDifference()) return;

      if (myIterationState != IterationState.PREV) {
        notifyMessage(e, false);
        myIterationState = IterationState.PREV;
        return;
      }

      goToPrevChange(true);
    }
  }

  private void notifyMessage(@NotNull AnActionEvent e, boolean next) {
    if (!myContentPanel.isShowing()) return;
    Editor editor = e.getData(DiffDataKeys.CURRENT_EDITOR);

    // TODO: provide "change" word in chain UserData - for tests/etc
    String message = DiffUtil.createNotificationText(next ? DiffBundle.message("press.again.to.go.to.the.next.file")
                                                          : DiffBundle.message("press.again.to.go.to.the.previous.file"),
                                                     DiffBundle.message("notification.you.can.disable.this.feature.in.0",
                                                                        DiffUtil.getSettingsConfigurablePath()));

    final LightweightHint hint = new LightweightHint(HintUtil.createInformationLabel(message));
    Point point = new Point(myContentPanel.getWidth() / 2, next ? myContentPanel.getHeight() - JBUIScale.scale(40) : JBUIScale.scale(40));

    if (editor == null) {
      final Component owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
      final HintHint hintHint = createNotifyHint(myContentPanel, point, next);
      hint.show(myContentPanel, point.x, point.y, owner instanceof JComponent ? (JComponent)owner : null, hintHint);
    }
    else {
      int x = SwingUtilities.convertPoint(myContentPanel, point, editor.getComponent()).x;

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

  @NotNull
  private static HintHint createNotifyHint(@NotNull JComponent component, @NotNull Point point, boolean above) {
    return new HintHint(component, point)
      .setPreferredPosition(above ? Balloon.Position.above : Balloon.Position.below)
      .setAwtTooltip(true)
      .setFont(UIUtil.getLabelFont().deriveFont(Font.BOLD))
      .setTextBg(HintUtil.getInformationColor())
      .setShowImmediately(true);
  }

  // Iterate requests

  protected class MyNextChangeAction extends NextChangeAction {
    public MyNextChangeAction() {
      if (DiffUtil.isUserDataFlagSet(DiffUserDataKeysEx.DIFF_IN_EDITOR, getContext())) {
        patchShortcutSet(this, IdeActions.ACTION_NEXT_TAB, IdeActions.ACTION_NEXT_EDITOR_TAB);
      }
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
    public MyPrevChangeAction() {
      if (DiffUtil.isUserDataFlagSet(DiffUserDataKeysEx.DIFF_IN_EDITOR, getContext())) {
        patchShortcutSet(this, IdeActions.ACTION_PREVIOUS_TAB, IdeActions.ACTION_PREVIOUS_EDITOR_TAB);
      }
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

  protected static void patchShortcutSet(@NotNull AnAction action,
                                         @NotNull @NonNls String originalActionId,
                                         @Nullable @NonNls String replacementActionId) {
    //noinspection ConstantConditions
    Keymap keymap = KeymapManager.getInstance().getActiveKeymap();
    Shortcut[] originalShortcuts = keymap.getShortcuts(originalActionId);

    Shortcut[] shortcuts = action.getShortcutSet().getShortcuts();
    Set<Shortcut> newShortcuts = ContainerUtil.set(shortcuts);
    boolean hadOriginalShortcut = ContainerUtil.removeAll(newShortcuts, originalShortcuts);
    if (!hadOriginalShortcut) return;

    if (replacementActionId != null) {
      Shortcut[] replacementShortcuts = keymap.getShortcuts(replacementActionId);
      ContainerUtil.addAll(newShortcuts, replacementShortcuts);
    }

    action.registerCustomShortcutSet(new CustomShortcutSet(newShortcuts.toArray(Shortcut.EMPTY_ARRAY)), null);
  }

  //
  // Helpers
  //

  protected class MyOpenInEditorAction extends OpenInEditorAction {
    @Override
    protected void onAfterEditorOpened() {
      onAfterNavigate();
    }
  }

  private class MyPanel extends JBPanelWithEmptyText implements DataProvider {
    MyPanel() {
      super(new BorderLayout());
    }

    @Override
    public Dimension getPreferredSize() {
      Dimension windowSize = DiffUtil.getDefaultDiffPanelSize();
      Dimension size = super.getPreferredSize();
      return new Dimension(Math.max(windowSize.width, size.width), Math.max(windowSize.height, size.height));
    }

    @Nullable
    @Override
    public Object getData(@NotNull @NonNls String dataId) {
      Object data;

      DataProvider contentProvider = DataManagerImpl.getDataProviderEx(myContentPanel.getTargetComponent());
      if (contentProvider != null) {
        data = contentProvider.getData(dataId);
        if (data != null) return data;
      }

      if (OpenInEditorAction.KEY.is(dataId)) {
        return new MyOpenInEditorAction();
      }
      else if (DiffDataKeys.DIFF_REQUEST.is(dataId)) {
        return myActiveRequest;
      }
      else if (CommonDataKeys.PROJECT.is(dataId)) {
        return myProject;
      }
      else if (PlatformDataKeys.HELP_ID.is(dataId)) {
        if (myActiveRequest.getUserData(DiffUserDataKeys.HELP_ID) != null) {
          return myActiveRequest.getUserData(DiffUserDataKeys.HELP_ID);
        }
        else {
          return "reference.dialogs.diff.file";
        }
      }
      else if (DiffDataKeys.DIFF_CONTEXT.is(dataId)) {
        return myContext;
      }
      else if (TouchbarDataKeys.ACTIONS_KEY.is(dataId)) {
        return myTouchbarActionGroup;
      }

      data = myState.getData(dataId);
      if (data != null) return data;

      DataProvider requestProvider = myActiveRequest.getUserData(DiffUserDataKeys.DATA_PROVIDER);
      if (requestProvider != null) {
        data = requestProvider.getData(dataId);
        if (data != null) return data;
      }

      DataProvider contextProvider = myContext.getUserData(DiffUserDataKeys.DATA_PROVIDER);
      if (contextProvider != null) {
        data = contextProvider.getData(dataId);
        if (data != null) return data;
      }
      return null;
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
      if (component == null) return null;
      return IdeFocusTraversalPolicy.getPreferredFocusedComponent(component, this);
    }

    @Nullable
    @Override
    protected Project getProject() {
      return myProject;
    }
  }

  private class MyDiffContext extends DiffContextEx {
    @NotNull private final UserDataHolder myInitialContext;
    @NotNull private final UserDataHolder myOwnContext = new UserDataHolderBase();

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

    @Nullable
    @Override
    public Project getProject() {
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

    @Nullable
    @Override
    public <T> T getUserData(@NotNull Key<T> key) {
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
    @NotNull private final DiffRequest request;
    private final boolean force;
    @Nullable private final ScrollToPolicy scrollToChangePolicy;

    ApplyData(@NotNull DiffRequest request, boolean force, @Nullable ScrollToPolicy scrollToChangePolicy) {
      this.request = request;
      this.force = force;
      this.scrollToChangePolicy = scrollToChangePolicy;
    }
  }

  //
  // States
  //

  private interface ViewerState {
    @CalledInAwt
    void init();

    @CalledInAwt
    void destroy();

    @Nullable
    JComponent getPreferredFocusedComponent();

    @Nullable
    Object getData(@NotNull @NonNls String dataId);

    @NotNull
    DiffTool getActiveTool();
  }

  private static class EmptyState implements ViewerState {
    private static final EmptyState INSTANCE = new EmptyState();

    @Override
    public void init() {
    }

    @Override
    public void destroy() {
    }

    @Nullable
    @Override
    public JComponent getPreferredFocusedComponent() {
      return null;
    }

    @Nullable
    @Override
    public Object getData(@NotNull @NonNls String dataId) {
      return null;
    }

    @NotNull
    @Override
    public DiffTool getActiveTool() {
      return ErrorDiffTool.INSTANCE;
    }
  }

  private class ErrorState implements ViewerState {
    @Nullable private final DiffTool myDiffTool;
    @NotNull private final MessageDiffRequest myRequest;

    @NotNull private final DiffViewer myViewer;

    ErrorState(@NotNull MessageDiffRequest request) {
      this(request, null);
    }

    ErrorState(@NotNull MessageDiffRequest request, @Nullable DiffTool diffTool) {
      myDiffTool = diffTool;
      myRequest = request;

      myViewer = ErrorDiffTool.INSTANCE.createComponent(myContext, myRequest);
    }

    @Override
    @CalledInAwt
    public void init() {
      myContentPanel.setContent(myViewer.getComponent());

      FrameDiffTool.ToolbarComponents init = myViewer.init();
      buildToolbar(init.toolbarActions);
    }

    @Override
    @CalledInAwt
    public void destroy() {
      Disposer.dispose(myViewer);
    }

    @Nullable
    @Override
    public JComponent getPreferredFocusedComponent() {
      return null;
    }

    @Nullable
    @Override
    public Object getData(@NotNull @NonNls String dataId) {
      return null;
    }

    @NotNull
    @Override
    public DiffTool getActiveTool() {
      return myDiffTool != null ? myDiffTool : ErrorDiffTool.INSTANCE;
    }
  }

  private class DefaultState implements ViewerState {
    @NotNull private final DiffViewer myViewer;
    @NotNull private final FrameDiffTool myTool;

    DefaultState(@NotNull DiffViewer viewer, @NotNull FrameDiffTool tool) {
      myViewer = viewer;
      myTool = tool;
    }

    @Override
    @CalledInAwt
    public void init() {
      myContentPanel.setContent(myViewer.getComponent());
      setTitle(myActiveRequest.getTitle());

      FrameDiffTool.ToolbarComponents toolbarComponents = myViewer.init();

      buildToolbar(toolbarComponents.toolbarActions);
      buildActionPopup(toolbarComponents.popupActions);

      myToolbarStatusPanel.setContent(toolbarComponents.statusPanel);
      if (shouldAddToolbarBottomBorder(toolbarComponents)) {
        myToolbarWrapper.setBorder(JBUI.Borders.customLine(JBColor.border(), 0, 0, 1, 0));
      }
    }

    @Override
    @CalledInAwt
    public void destroy() {
      Disposer.dispose(myViewer);
    }

    @Nullable
    @Override
    public JComponent getPreferredFocusedComponent() {
      return myViewer.getPreferredFocusedComponent();
    }

    @NotNull
    @Override
    public DiffTool getActiveTool() {
      return myTool;
    }

    @Nullable
    @Override
    public Object getData(@NotNull @NonNls String dataId) {
      if (DiffDataKeys.DIFF_VIEWER.is(dataId)) {
        return myViewer;
      }
      return null;
    }
  }

  private class WrapperState implements ViewerState {
    @NotNull private final DiffViewer myViewer;
    @NotNull private final FrameDiffTool myTool;

    @NotNull private final DiffViewer myWrapperViewer;

    WrapperState(@NotNull DiffViewer viewer, @NotNull FrameDiffTool tool, @NotNull DiffViewerWrapper wrapper) {
      myViewer = viewer;
      myTool = tool;
      myWrapperViewer = wrapper.createComponent(myContext, myActiveRequest, myViewer);
    }

    @Override
    @CalledInAwt
    public void init() {
      myContentPanel.setContent(myWrapperViewer.getComponent());
      setTitle(myActiveRequest.getTitle());


      FrameDiffTool.ToolbarComponents toolbarComponents1 = myViewer.init();
      FrameDiffTool.ToolbarComponents toolbarComponents2 = myWrapperViewer.init();

      buildToolbar(mergeActions(toolbarComponents1.toolbarActions, toolbarComponents2.toolbarActions));
      buildActionPopup(mergeActions(toolbarComponents1.popupActions, toolbarComponents2.popupActions));

      myToolbarStatusPanel.setContent(toolbarComponents1.statusPanel); // TODO: combine both panels ?
      if (shouldAddToolbarBottomBorder(toolbarComponents1)) {
        myToolbarWrapper.setBorder(JBUI.Borders.customLine(JBColor.border(), 0, 0, 1, 0));
      }
    }

    @Nullable
    private List<AnAction> mergeActions(@Nullable List<AnAction> actions1, @Nullable List<AnAction> actions2) {
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
    @CalledInAwt
    public void destroy() {
      Disposer.dispose(myViewer);
      Disposer.dispose(myWrapperViewer);
    }

    @Nullable
    @Override
    public JComponent getPreferredFocusedComponent() {
      return myWrapperViewer.getPreferredFocusedComponent();
    }

    @NotNull
    @Override
    public DiffTool getActiveTool() {
      return myTool;
    }

    @Nullable
    @Override
    public Object getData(@NotNull @NonNls String dataId) {
      if (DiffDataKeys.WRAPPING_DIFF_VIEWER.is(dataId)) {
        return myWrapperViewer;
      }
      if (DiffDataKeys.DIFF_VIEWER.is(dataId)) {
        return myViewer;
      }
      return null;
    }
  }
}
