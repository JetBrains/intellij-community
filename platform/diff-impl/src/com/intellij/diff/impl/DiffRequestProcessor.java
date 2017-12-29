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
package com.intellij.diff.impl;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.diff.*;
import com.intellij.diff.FrameDiffTool.DiffViewer;
import com.intellij.diff.actions.impl.*;
import com.intellij.diff.impl.DiffSettingsHolder.DiffSettings;
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
import com.intellij.internal.statistic.UsageTrigger;
import com.intellij.internal.statistic.beans.ConvertUsagesUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
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
import com.intellij.ui.HintHint;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.LightweightHint;
import com.intellij.ui.components.JBPanelWithEmptyText;
import com.intellij.ui.components.panels.Wrapper;
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
import java.util.List;

@SuppressWarnings("InnerClassMayBeStatic")
public abstract class DiffRequestProcessor implements Disposable {
  private static final Logger LOG = Logger.getInstance(DiffRequestProcessor.class);

  private boolean myDisposed;

  @Nullable private final Project myProject;
  @NotNull private final DiffContext myContext;

  @NotNull private final DiffSettings mySettings;
  @NotNull private final List<DiffTool> myAvailableTools;
  @NotNull private final List<DiffTool> myToolOrder;

  @NotNull private final OpenInEditorAction myOpenInEditorAction;
  @NotNull private final DefaultActionGroup myToolbarGroup;
  @NotNull private final DefaultActionGroup myPopupActionGroup;

  @NotNull private final JPanel myPanel;
  @NotNull private final MyPanel myMainPanel;
  @NotNull private final Wrapper myContentPanel;
  @NotNull private final ActionToolbar myToolbar;
  @NotNull private final Wrapper myToolbarStatusPanel;
  @NotNull private final MyProgressBar myProgressBar;

  @NotNull private DiffRequest myActiveRequest;

  @NotNull private ViewerState myState;

  public DiffRequestProcessor(@Nullable Project project) {
    this(project, new UserDataHolderBase());
  }

  public DiffRequestProcessor(@Nullable Project project, @NotNull String place) {
    this(project, DiffUtil.createUserDataHolder(DiffUserDataKeys.PLACE, place));
  }

  public DiffRequestProcessor(@Nullable Project project, @NotNull UserDataHolder context) {
    myProject = project;

    myContext = new MyDiffContext(context);
    myActiveRequest = new LoadingDiffRequest();

    mySettings = DiffSettings.getSettings(myContext.getUserData(DiffUserDataKeys.PLACE));

    myAvailableTools = DiffManagerEx.getInstance().getDiffTools();
    myToolOrder = new ArrayList<>(getToolOrderFromSettings(myAvailableTools));

    myToolbarGroup = new DefaultActionGroup();
    myPopupActionGroup = new DefaultActionGroup();

    // UI

    myMainPanel = new MyPanel();
    myContentPanel = new Wrapper();
    myToolbarStatusPanel = new Wrapper();
    myProgressBar = new MyProgressBar();

    myToolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.DIFF_TOOLBAR, myToolbarGroup, true);
    myToolbar.setTargetComponent(myMainPanel);

    myPanel = JBUI.Panels.simplePanel(myMainPanel);

    JPanel statusPanel = JBUI.Panels.simplePanel(myToolbarStatusPanel).addToLeft(myProgressBar);
    JPanel topPanel = JBUI.Panels.simplePanel(myToolbar.getComponent()).addToRight(statusPanel);

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

    myOpenInEditorAction = new OpenInEditorAction(() -> onAfterNavigate());
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
  private FrameDiffTool getFittedTool() {
    List<FrameDiffTool> tools = filterFittedTools(myToolOrder);
    return tools.isEmpty() ? ErrorDiffTool.INSTANCE : tools.get(0);
  }

  @NotNull
  private List<FrameDiffTool> getAvailableFittedTools() {
    return filterFittedTools(myAvailableTools);
  }

  @NotNull
  private List<FrameDiffTool> filterFittedTools(@NotNull List<DiffTool> tools) {
    List<FrameDiffTool> result = new ArrayList<>();
    for (DiffTool tool : tools) {
      try {
        if (tool instanceof FrameDiffTool && tool.canShow(myContext, myActiveRequest)) {
          result.add((FrameDiffTool)tool);
        }
      }
      catch (Throwable e) {
        LOG.error(e);
      }
    }

    return DiffUtil.filterSuppressedTools(result);
  }

  private void moveToolOnTop(@NotNull DiffTool tool) {
    myToolOrder.remove(tool);

    FrameDiffTool toolToReplace = getFittedTool();

    int index;
    for (index = 0; index < myToolOrder.size(); index++) {
      if (myToolOrder.get(index) == toolToReplace) break;
    }
    myToolOrder.add(index, tool);

    updateToolOrderSettings(myToolOrder);
  }

  @NotNull
  private ViewerState createState() {
    FrameDiffTool frameTool = getFittedTool();

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
    ApplicationManager.getApplication().assertIsDispatchThread();
    myIterationState = IterationState.NONE;

    force = force || (myQueuedApplyRequest != null && myQueuedApplyRequest.force);
    myQueuedApplyRequest = new ApplyData(request, force, scrollToChangePolicy);

    IdeFocusManager.getInstance(myProject).doWhenFocusSettlesDown(() -> {
      if (myQueuedApplyRequest == null || myDisposed) return;
      doApplyRequest(myQueuedApplyRequest.request, myQueuedApplyRequest.force, myQueuedApplyRequest.scrollToChangePolicy);
      myQueuedApplyRequest = null;
    }, ModalityState.current());
  }

  @CalledInAwt
  private void doApplyRequest(@NotNull DiffRequest request, boolean force, @Nullable ScrollToPolicy scrollToChangePolicy) {
    if (!force && request == myActiveRequest) return;

    request.putUserData(DiffUserDataKeysEx.SCROLL_TO_CHANGE, scrollToChangePolicy);

    DiffUtil.runPreservingFocus(myContext, () -> {
      myState.destroy();
      myToolbarStatusPanel.setContent(null);
      myContentPanel.setContent(null);

      myToolbarGroup.removeAll();
      myPopupActionGroup.removeAll();
      ActionUtil.clearActions(myMainPanel);

      myActiveRequest.onAssigned(false);
      myActiveRequest = request;
      myActiveRequest.onAssigned(true);

      try {
        myState = createState();
        myState.init();
      }
      catch (Throwable e) {
        LOG.error(e);
        myState = new ErrorState(new ErrorDiffRequest(DiffBundle.message("error.cant.show.diff.message")), getFittedTool());
        myState.init();
      }
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
    return ContainerUtil.list(
      new MyPrevDifferenceAction(),
      new MyNextDifferenceAction(),
      new MyPrevChangeAction(),
      new MyNextChangeAction()
    );
  }

  //
  // Misc
  //

  protected boolean isWindowFocused() {
    Window window = SwingUtilities.getWindowAncestor(myPanel);
    return window != null && window.isFocused();
  }

  protected boolean isFocused() {
    return DiffUtil.isFocusedComponent(myProject, myContentPanel) ||
           DiffUtil.isFocusedComponent(myProject, myToolbar.getComponent());
  }

  private void requestFocusInternal() {
    JComponent component = getPreferredFocusedComponent();
    if (component != null) component.requestFocusInWindow();
  }

  @NotNull
  protected List<DiffTool> getToolOrderFromSettings(@NotNull List<DiffTool> availableTools) {
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

  protected void updateToolOrderSettings(@NotNull List<DiffTool> toolOrder) {
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

  protected void collectToolbarActions(@Nullable List<AnAction> viewerActions) {
    myToolbarGroup.removeAll();

    List<AnAction> navigationActions = new ArrayList<>(getNavigationActions());
    navigationActions.add(myOpenInEditorAction);
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
  }

  protected void collectPopupActions(@Nullable List<AnAction> viewerActions) {
    myPopupActionGroup.removeAll();

    List<AnAction> selectToolActions = new ArrayList<>();
    for (DiffTool tool : getAvailableFittedTools()) {
      if (tool == myState.getActiveTool()) continue;
      selectToolActions.add(new DiffToolToggleAction(tool));
    }
    DiffUtil.addActionBlock(myPopupActionGroup, selectToolActions);

    DiffUtil.addActionBlock(myPopupActionGroup, viewerActions);
  }

  protected void buildToolbar(@Nullable List<AnAction> viewerActions) {
    collectToolbarActions(viewerActions);

    ((ActionToolbarImpl)myToolbar).clearPresentationCache();
    myToolbar.updateActionsImmediately();

    ActionUtil.recursiveRegisterShortcutSet(myToolbarGroup, myMainPanel, null);
  }

  protected void buildActionPopup(@Nullable List<AnAction> viewerActions) {
    collectPopupActions(viewerActions);

    DiffUtil.registerAction(new ShowActionGroupPopupAction(), myMainPanel);
  }

  private void setTitle(@Nullable String title) {
    if (getContextUserData(DiffUserDataKeys.DO_NOT_CHANGE_WINDOW_TITLE) == Boolean.TRUE) return;
    if (title == null) title = "Diff";
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
    return component != null ? component : myToolbar.getComponent();
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
    public ShowInExternalToolAction() {
      ActionUtil.copyFrom(this, "Diff.ShowInExternalTool");
    }

    @Override
    public void update(AnActionEvent e) {
      if (!ExternalDiffTool.isEnabled()) {
        e.getPresentation().setEnabledAndVisible(false);
        return;
      }
      e.getPresentation().setEnabled(ExternalDiffTool.canShow(myActiveRequest));
      e.getPresentation().setVisible(true);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      try {
        ExternalDiffTool.showRequest(e.getProject(), myActiveRequest);
      }
      catch (Throwable ex) {
        Messages.showErrorDialog(e.getProject(), ex.getMessage(), "Can't Show Diff In External Tool");
      }
    }
  }

  private class MyChangeDiffToolAction extends ComboBoxAction implements DumbAware {
    // TODO: add icons for diff tools, show only icon in toolbar - to reduce jumping on change ?

    @Override
    public void update(AnActionEvent e) {
      Presentation presentation = e.getPresentation();

      DiffTool activeTool = myState.getActiveTool();
      presentation.setText(activeTool.getName());

      if (activeTool == ErrorDiffTool.INSTANCE) {
        presentation.setEnabledAndVisible(false);
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
    public void actionPerformed(@NotNull AnActionEvent e) {
      if (myState.getActiveTool() == myDiffTool) return;

      UsageTrigger.trigger("diff.DiffSettings.Tool." + ConvertUsagesUtil.ensureProperKey(myDiffTool.getName()));
      moveToolOnTop(myDiffTool);

      updateRequest(true);
    }
  }

  private class ShowActionGroupPopupAction extends DumbAwareAction {
    public ShowActionGroupPopupAction() {
      ActionUtil.copyFrom(this, "Diff.ShowSettingsPopup");
    }

    @Override
    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(myPopupActionGroup.getChildrenCount() > 0);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      ListPopup popup = JBPopupFactory.getInstance().createActionGroupPopup("Diff Actions", myPopupActionGroup, e.getDataContext(),
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
  protected boolean hasNextChange() {
    return false;
  }

  @CalledInAwt
  protected boolean hasPrevChange() {
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
      if (!ActionPlaces.DIFF_TOOLBAR.equals(e.getPlace())) {
        e.getPresentation().setEnabledAndVisible(true);
        return;
      }

      PrevNextDifferenceIterable iterable = DiffDataKeys.PREV_NEXT_DIFFERENCE_ITERABLE.getData(e.getDataContext());
      if (iterable != null && iterable.canGoNext()) {
        e.getPresentation().setEnabled(true);
        return;
      }

      if (getSettings().isGoToNextFileOnNextDifference() && isNavigationEnabled() && hasNextChange()) {
        e.getPresentation().setEnabled(true);
        return;
      }

      e.getPresentation().setEnabled(false);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      PrevNextDifferenceIterable iterable = DiffDataKeys.PREV_NEXT_DIFFERENCE_ITERABLE.getData(e.getDataContext());
      if (iterable != null && iterable.canGoNext()) {
        iterable.goNext();
        myIterationState = IterationState.NONE;
        return;
      }

      if (!isNavigationEnabled() || !hasNextChange() || !getSettings().isGoToNextFileOnNextDifference()) return;

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
      if (!ActionPlaces.DIFF_TOOLBAR.equals(e.getPlace())) {
        e.getPresentation().setEnabledAndVisible(true);
        return;
      }

      PrevNextDifferenceIterable iterable = DiffDataKeys.PREV_NEXT_DIFFERENCE_ITERABLE.getData(e.getDataContext());
      if (iterable != null && iterable.canGoPrev()) {
        e.getPresentation().setEnabled(true);
        return;
      }

      if (getSettings().isGoToNextFileOnNextDifference() && isNavigationEnabled() && hasPrevChange()) {
        e.getPresentation().setEnabled(true);
        return;
      }

      e.getPresentation().setEnabled(false);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      PrevNextDifferenceIterable iterable = DiffDataKeys.PREV_NEXT_DIFFERENCE_ITERABLE.getData(e.getDataContext());
      if (iterable != null && iterable.canGoPrev()) {
        iterable.goPrev();
        myIterationState = IterationState.NONE;
        return;
      }

      if (!isNavigationEnabled() || !hasPrevChange() || !getSettings().isGoToNextFileOnNextDifference()) return;

      if (myIterationState != IterationState.PREV) {
        notifyMessage(e, false);
        myIterationState = IterationState.PREV;
        return;
      }

      goToPrevChange(true);
    }
  }

  private void notifyMessage(@NotNull AnActionEvent e, boolean next) {
    Editor editor = e.getData(DiffDataKeys.CURRENT_EDITOR);

    // TODO: provide "change" word in chain UserData - for tests/etc
    String message = DiffUtil.createNotificationText(next ? "Press again to go to the next file" : "Press again to go to the previous file",
                                                     "You can disable this feature in " + DiffUtil.getSettingsConfigurablePath());

    final LightweightHint hint = new LightweightHint(HintUtil.createInformationLabel(message));
    Point point = new Point(myContentPanel.getWidth() / 2, next ? myContentPanel.getHeight() - JBUI.scale(40) : JBUI.scale(40));

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
    @Override
    public void update(@NotNull AnActionEvent e) {
      if (!ActionPlaces.DIFF_TOOLBAR.equals(e.getPlace())) {
        e.getPresentation().setEnabledAndVisible(true);
        return;
      }

      if (!isNavigationEnabled()) {
        e.getPresentation().setEnabledAndVisible(false);
        return;
      }

      e.getPresentation().setVisible(true);
      e.getPresentation().setEnabled(hasNextChange());
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      if (!isNavigationEnabled() || !hasNextChange()) return;

      goToNextChange(false);
    }
  }

  protected class MyPrevChangeAction extends PrevChangeAction {
    @Override
    public void update(@NotNull AnActionEvent e) {
      if (!ActionPlaces.DIFF_TOOLBAR.equals(e.getPlace())) {
        e.getPresentation().setEnabledAndVisible(true);
        return;
      }

      if (!isNavigationEnabled()) {
        e.getPresentation().setEnabledAndVisible(false);
        return;
      }

      e.getPresentation().setVisible(true);
      e.getPresentation().setEnabled(hasPrevChange());
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      if (!isNavigationEnabled() || !hasPrevChange()) return;

      goToPrevChange(false);
    }
  }

  //
  // Helpers
  //

  private class MyPanel extends JBPanelWithEmptyText implements DataProvider {
    public MyPanel() {
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
    public Object getData(@NonNls String dataId) {
      Object data;

      DataProvider contentProvider = DataManagerImpl.getDataProviderEx(myContentPanel.getTargetComponent());
      if (contentProvider != null) {
        data = contentProvider.getData(dataId);
        if (data != null) return data;
      }

      if (OpenInEditorAction.KEY.is(dataId)) {
        return myOpenInEditorAction;
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

    public MyProgressBar() {
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
    public final Component getDefaultComponentImpl(final Container focusCycleRoot) {
      JComponent component = DiffRequestProcessor.this.getPreferredFocusedComponent();
      if (component == null) return null;
      return IdeFocusTraversalPolicy.getPreferredFocusedComponent(component, this);
    }
  }

  private class MyDiffContext extends DiffContextEx {
    @NotNull private final UserDataHolder myContext;

    public MyDiffContext(@NotNull UserDataHolder context) {
      myContext = context;
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
    public boolean isFocused() {
      return DiffRequestProcessor.this.isFocused();
    }

    @Override
    public boolean isWindowFocused() {
      return DiffRequestProcessor.this.isWindowFocused();
    }

    @Override
    public void requestFocus() {
      DiffRequestProcessor.this.requestFocusInternal();
    }

    @Nullable
    @Override
    public <T> T getUserData(@NotNull Key<T> key) {
      return myContext.getUserData(key);
    }

    @Override
    public <T> void putUserData(@NotNull Key<T> key, @Nullable T value) {
      myContext.putUserData(key, value);
    }
  }

  private static class ApplyData {
    @NotNull private final DiffRequest request;
    private final boolean force;
    @Nullable private final ScrollToPolicy scrollToChangePolicy;

    public ApplyData(@NotNull DiffRequest request, boolean force, @Nullable ScrollToPolicy scrollToChangePolicy) {
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
    Object getData(@NonNls String dataId);

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
    public Object getData(@NonNls String dataId) {
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

    public ErrorState(@NotNull MessageDiffRequest request) {
      this(request, null);
    }

    public ErrorState(@NotNull MessageDiffRequest request, @Nullable DiffTool diffTool) {
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
    public Object getData(@NonNls String dataId) {
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

    public DefaultState(@NotNull DiffViewer viewer, @NotNull FrameDiffTool tool) {
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
    public Object getData(@NonNls String dataId) {
      if (DiffDataKeys.DIFF_VIEWER.is(dataId)) {
        return myViewer;
      }
      return null;
    }
  }

  private class WrapperState implements ViewerState {
    @NotNull private final DiffViewer myViewer;
    @NotNull private final FrameDiffTool myTool;

    @NotNull private DiffViewer myWrapperViewer;

    public WrapperState(@NotNull DiffViewer viewer, @NotNull FrameDiffTool tool, @NotNull DiffViewerWrapper wrapper) {
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

      List<AnAction> toolbarActions = new ArrayList<>();
      if (toolbarComponents1.toolbarActions != null) toolbarActions.addAll(toolbarComponents1.toolbarActions);
      if (toolbarComponents2.toolbarActions != null) {
        if (!toolbarActions.isEmpty() && !toolbarComponents2.toolbarActions.isEmpty()) toolbarActions.add(Separator.getInstance());
        toolbarActions.addAll(toolbarComponents2.toolbarActions);
      }
      buildToolbar(toolbarActions);

      List<AnAction> popupActions = new ArrayList<>();
      if (toolbarComponents1.popupActions != null) popupActions.addAll(toolbarComponents1.popupActions);
      if (toolbarComponents2.popupActions != null) {
        if (!popupActions.isEmpty() && !toolbarComponents2.popupActions.isEmpty()) popupActions.add(Separator.getInstance());
        popupActions.addAll(toolbarComponents2.popupActions);
      }
      buildActionPopup(popupActions);


      myToolbarStatusPanel.setContent(toolbarComponents1.statusPanel); // TODO: combine both panels ?
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
    public Object getData(@NonNls String dataId) {
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
