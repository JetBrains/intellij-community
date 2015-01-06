package com.intellij.openapi.util.diff.impl;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.diff.DiffManagerEx;
import com.intellij.openapi.util.diff.actions.impl.*;
import com.intellij.openapi.util.diff.api.DiffTool;
import com.intellij.openapi.util.diff.api.FrameDiffTool;
import com.intellij.openapi.util.diff.api.FrameDiffTool.DiffContext;
import com.intellij.openapi.util.diff.api.FrameDiffTool.DiffViewer;
import com.intellij.openapi.util.diff.chains.DiffRequestChain;
import com.intellij.openapi.util.diff.chains.DiffRequestPresentable;
import com.intellij.openapi.util.diff.chains.DiffRequestPresentableException;
import com.intellij.openapi.util.diff.requests.DiffRequest;
import com.intellij.openapi.util.diff.requests.ErrorDiffRequest;
import com.intellij.openapi.util.diff.requests.NoDiffRequest;
import com.intellij.openapi.util.diff.tools.ErrorDiffTool;
import com.intellij.openapi.util.diff.tools.external.ExternalDiffTool;
import com.intellij.openapi.util.diff.tools.util.DiffDataKeys;
import com.intellij.openapi.util.diff.tools.util.PrevNextDifferenceIterable;
import com.intellij.openapi.util.diff.tools.util.SoftHardCacheMap;
import com.intellij.openapi.util.diff.util.DiffUserDataKeys;
import com.intellij.openapi.util.diff.util.DiffUserDataKeys.ScrollToPolicy;
import com.intellij.openapi.util.diff.util.DiffUserDataKeysEx;
import com.intellij.openapi.util.diff.util.DiffUtil;
import com.intellij.openapi.wm.ex.IdeFocusTraversalPolicy;
import com.intellij.util.Consumer;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public abstract class CacheDiffRequestChainProcessor implements Disposable {
  private static final Logger LOG = Logger.getInstance(CacheDiffRequestChainProcessor.class);

  private boolean myDisposed;

  @Nullable private final Project myProject;
  @NotNull private final DiffRequestChain myRequestChain;

  @NotNull private final FrameDiffTool.DiffContext myContext;

  @NotNull private final SoftHardCacheMap<DiffRequestPresentable, DiffRequest> myRequestCache =
    new SoftHardCacheMap<DiffRequestPresentable, DiffRequest>(5, 5);

  @NotNull private final List<DiffTool> myAvailableTools;
  @NotNull private final LinkedList<DiffTool> myToolOrder;

  @NotNull private final MyDiffWindow myDiffWindow;
  @NotNull private final OpenInEditorAction myOpenInEditorAction;
  @NotNull private final ShowActionGroupPopupAction myShowActionGroupPopupAction;

  @NotNull private final MyPanel myPanel;
  @NotNull private final ModifiablePanel myContentPanel;
  @NotNull private final ModifiablePanel myToolbarPanel; // TODO: allow to call 'updateToolbar' from Viewer ?
  @NotNull private final ModifiablePanel myToolbarStatusPanel;
  @Nullable private final JLabel myTitleLabel;

  @NotNull private DiffRequest myActiveRequest;

  @NotNull private ViewerState myState = new EmptyState();

  public CacheDiffRequestChainProcessor(@Nullable Project project, @NotNull DiffRequestChain requestChain, boolean useShortHeader) {
    myProject = project;
    myRequestChain = requestChain;

    myAvailableTools = DiffManagerEx.getInstance().getDiffTools();
    myToolOrder = new LinkedList<DiffTool>(myAvailableTools);

    myContext = new MyDiffContext();
    myActiveRequest = NoDiffRequest.INSTANCE;

    // UI

    myDiffWindow = new MyDiffWindow();

    myPanel = new MyPanel();
    myContentPanel = new ModifiablePanel();
    myToolbarPanel = new ModifiablePanel();
    myToolbarStatusPanel = new ModifiablePanel();

    JPanel topPanel = new JPanel(new BorderLayout());
    if (!useShortHeader) {
      myTitleLabel = null;
      topPanel.add(myToolbarPanel, BorderLayout.WEST);
      topPanel.add(myToolbarStatusPanel, BorderLayout.EAST);
    }
    else {
      JPanel toolbarPanel = new JPanel(new BorderLayout());
      toolbarPanel.add(myToolbarPanel, BorderLayout.WEST);
      toolbarPanel.add(myToolbarStatusPanel, BorderLayout.EAST);

      myTitleLabel = new JLabel();
      myTitleLabel.setBorder(BorderFactory.createEmptyBorder(1, 2, 0, 0));
      topPanel.add(myTitleLabel, BorderLayout.WEST);
      topPanel.add(toolbarPanel, BorderLayout.EAST);
    }

    myPanel.add(topPanel, BorderLayout.NORTH);
    myPanel.add(myContentPanel, BorderLayout.CENTER);

    JComponent bottomPanel = myContext.getUserData(DiffUserDataKeysEx.BOTTOM_PANEL);
    if (bottomPanel != null) myPanel.add(bottomPanel, BorderLayout.SOUTH);
    if (bottomPanel instanceof Disposable) Disposer.register(this, (Disposable)bottomPanel);

    myPanel.setFocusTraversalPolicyProvider(true);
    myPanel.setFocusTraversalPolicy(new MyFocusTraversalPolicy());

    myOpenInEditorAction = new OpenInEditorAction(new Runnable() {
      @Override
      public void run() {
        onAfterNavigate();
      }
    });

    myShowActionGroupPopupAction = new ShowActionGroupPopupAction();
  }

  //
  // Update
  //

  public void updateRequest() {
    updateRequest(false, null);
  }

  public void updateRequest(boolean force, @Nullable ScrollToPolicy scrollToChangePolicy) {
    boolean hadFocus = isFocused();

    DiffRequest request = loadRequest();
    if (!force && request == myActiveRequest) return;

    request.putUserData(DiffUserDataKeys.SCROLL_TO_CHANGE, scrollToChangePolicy);

    myState.destroy();
    myToolbarStatusPanel.setContent(null);
    myToolbarPanel.setContent(null);
    myContentPanel.setContent(null);
    myShowActionGroupPopupAction.setActionGroup(null);

    myActiveRequest.onAssigned(false);
    myActiveRequest = request;
    myActiveRequest.onAssigned(true);

    try {
      myState = createState();
      myState.init();
    }
    catch (Exception e) {
      LOG.error(e);
      myState = new ErrorState(getFrameTool());
      myState.init();
    }

    if (hadFocus) requestFocus();
  }

  @NotNull
  private FrameDiffTool getFrameTool() {
    for (DiffTool tool : myToolOrder) {
      if (tool instanceof FrameDiffTool && tool.canShow(myContext, myActiveRequest)) {
        return (FrameDiffTool)tool;
      }
    }

    return ErrorDiffTool.INSTANCE;
  }

  @NotNull
  private ViewerState createState() {
    FrameDiffTool frameTool = getFrameTool();

    DiffViewer viewer = frameTool.createComponent(myContext, myActiveRequest);

    DiffViewerWrapper wrapper = myActiveRequest.getUserData(DiffViewerWrapper.KEY);
    if (wrapper == null) {
      return new DefaultState(viewer, frameTool);
    }
    else {
      return new WrapperState(viewer, frameTool, wrapper);
    }
  }

  @NotNull
  private DiffRequest loadRequest() {
    List<? extends DiffRequestPresentable> requests = myRequestChain.getRequests();
    int index = myRequestChain.getIndex();

    if (index < 0 || index >= requests.size()) return NoDiffRequest.INSTANCE;

    final DiffRequestPresentable presentable = requests.get(index);

    DiffRequest request = myRequestCache.get(presentable);
    if (request != null) return request;

    final Error[] errorRef = new Error[1];
    final DiffRequest[] requestRef = new DiffRequest[1];
    ProgressManager.getInstance().run(new Task.Modal(myProject, "Collecting data", true) {
      public void run(@NotNull ProgressIndicator indicator) {
        try {
          requestRef[0] = presentable.process(myContext, indicator);
        }
        catch (ProcessCanceledException e) {
          requestRef[0] = new ErrorDiffRequest(presentable, "Operation Canceled"); // TODO: add reload action
        }
        catch (DiffRequestPresentableException e) {
          requestRef[0] = new ErrorDiffRequest(presentable, e);
        }
        catch (Exception e) {
          requestRef[0] = new ErrorDiffRequest(presentable, e);
        }
        catch (Error e) {
          errorRef[0] = e;
        }
      }
    });
    if (errorRef[0] != null) throw errorRef[0];

    request = requestRef[0];
    assert request != null;

    myRequestCache.put(presentable, request);

    return request;
  }

  //
  // Abstract
  //

  protected void setWindowTitle(@NotNull String title) {
  }

  protected void onAfterNavigate() {
  }

  //
  // Misc
  //

  public boolean isFocused() {
    return DiffUtil.isFocusedComponent(myProject, myPanel);
  }

  public void requestFocus() {
    DiffUtil.requestFocus(myProject, getPreferredFocusedComponent());
  }

  @Override
  public void dispose() {
    if (myDisposed) return;
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        if (myDisposed) return;
        myDisposed = true;

        myRequestCache.clear();

        myState.destroy();
        myToolbarStatusPanel.setContent(null);
        myToolbarPanel.setContent(null);
        myContentPanel.setContent(null);
        myShowActionGroupPopupAction.setActionGroup(null);

        myActiveRequest.onAssigned(false);
      }
    });
  }

  protected void buildToolbar(@Nullable List<AnAction> viewerActions) {
    DefaultActionGroup group = new DefaultActionGroup();

    DiffUtil.addActionBlock(group,
                            new MyPrevDifferenceAction(),
                            new MyNextDifferenceAction(),
                            new MyPrevChangeAction(),
                            new MyNextChangeAction(),
                            createGoToChangeAction(),
                            myOpenInEditorAction,
                            new MyChangeDiffToolAction());

    DiffUtil.addActionBlock(group, viewerActions);

    List<AnAction> requestContextActions = myActiveRequest.getUserData(DiffUserDataKeys.CONTEXT_ACTIONS);
    DiffUtil.addActionBlock(group, requestContextActions);

    List<AnAction> contextActions = myContext.getUserData(DiffUserDataKeys.CONTEXT_ACTIONS);
    DiffUtil.addActionBlock(group, contextActions);

    DiffUtil.addActionBlock(group,
                            new ShowInExternalToolAction(),
                            new ShowOldDiffAction(),
                            ActionManager.getInstance().getAction(IdeActions.ACTION_CONTEXT_HELP));

    myToolbarPanel.setContent(DiffUtil.createToolbar(group).getComponent());
    for (AnAction action : group.getChildren(null)) {
      action.registerCustomShortcutSet(action.getShortcutSet(), myPanel);
    }
  }

  protected void buildActionPopup(@Nullable List<AnAction> viewerActions) {
    DefaultActionGroup group = new DefaultActionGroup();

    List<AnAction> selectToolActions = new ArrayList<AnAction>();
    for (DiffTool tool : myAvailableTools) {
      if (tool == myState.getActiveTool()) continue;
      if (tool.canShow(myContext, myActiveRequest)) {
        selectToolActions.add(new DiffToolToggleAction(tool));
      }
    }
    DiffUtil.addActionBlock(group, selectToolActions);

    DiffUtil.addActionBlock(group, viewerActions);

    myShowActionGroupPopupAction.setActionGroup(group);
  }

  private void setTitle(@Nullable String title) {
    if (myTitleLabel != null) {
      if (title == null) title = "";
      myTitleLabel.setText(title);
    }
    else {
      if (title == null) title = "Diff";
      setWindowTitle(title);
    }
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
    return component != null ? component : myToolbarPanel;
  }

  @Nullable
  public Project getProject() {
    return myProject;
  }

  @NotNull
  public DiffRequestChain getRequestChain() {
    return myRequestChain;
  }

  @NotNull
  public DiffContext getContext() {
    return myContext;
  }

  //
  // Actions
  //

  // Iterate differences

  private enum IterationState {NEXT, PREV, NONE}

  @NotNull private IterationState myIterationState = IterationState.NONE;

  private class MyNextDifferenceAction extends NextDifferenceAction {
    @Override
    public void update(@NotNull AnActionEvent e) {
      PrevNextDifferenceIterable iterable = DiffDataKeys.PREV_NEXT_DIFFERENCE_ITERABLE.getData(e.getDataContext());
      if (iterable != null && iterable.canGoNext()) {
        e.getPresentation().setEnabled(true);
        return;
      }

      if (myRequestChain.getRequests().size() <= 1) {
        e.getPresentation().setEnabled(false);
        return;
      }

      if (myRequestChain.getIndex() >= myRequestChain.getRequests().size() - 1) {
        e.getPresentation().setEnabled(false);
        return;
      }

      e.getPresentation().setEnabled(true);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      PrevNextDifferenceIterable iterable = DiffDataKeys.PREV_NEXT_DIFFERENCE_ITERABLE.getData(e.getDataContext());
      if (iterable != null && iterable.canGoNext()) {
        iterable.goNext();
        return;
      }

      if (myIterationState != IterationState.NEXT) {
        // TODO: provide "change" word in chain UserData - for tests/etc
        if (iterable != null) iterable.notify("Press again to go to the next file");
        myIterationState = IterationState.NEXT;
        return;
      }

      myIterationState = IterationState.NONE;
      myRequestChain.setIndex(myRequestChain.getIndex() + 1);
      updateRequest(false, ScrollToPolicy.FIRST_CHANGE);
    }
  }

  private class MyPrevDifferenceAction extends PrevDifferenceAction {
    @Override
    public void update(@NotNull AnActionEvent e) {
      PrevNextDifferenceIterable iterable = DiffDataKeys.PREV_NEXT_DIFFERENCE_ITERABLE.getData(e.getDataContext());
      if (iterable != null && iterable.canGoPrev()) {
        e.getPresentation().setEnabled(true);
        return;
      }

      if (myRequestChain.getRequests().size() <= 1) {
        e.getPresentation().setEnabled(false);
        return;
      }

      if (myRequestChain.getIndex() <= 0) {
        e.getPresentation().setEnabled(false);
        return;
      }

      e.getPresentation().setEnabled(true);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      PrevNextDifferenceIterable iterable = DiffDataKeys.PREV_NEXT_DIFFERENCE_ITERABLE.getData(e.getDataContext());
      if (iterable != null && iterable.canGoPrev()) {
        iterable.goPrev();
        return;
      }

      if (myIterationState != IterationState.PREV) {
        if (iterable != null) iterable.notify("Press again to go to the previous file");
        myIterationState = IterationState.PREV;
        return;
      }

      myIterationState = IterationState.NONE;
      myRequestChain.setIndex(myRequestChain.getIndex() - 1);
      updateRequest(false, ScrollToPolicy.LAST_CHANGE);
    }
  }

  // Iterate presentable

  private class MyNextChangeAction extends NextChangeAction {
    @Override
    public void update(@NotNull AnActionEvent e) {
      if (myRequestChain.getRequests().size() <= 1) {
        e.getPresentation().setEnabledAndVisible(false);
        return;
      }

      if (myRequestChain.getIndex() >= myRequestChain.getRequests().size() - 1) {
        e.getPresentation().setEnabled(false);
        return;
      }

      e.getPresentation().setEnabled(true);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      myRequestChain.setIndex(myRequestChain.getIndex() + 1);
      updateRequest();
    }
  }

  private class MyPrevChangeAction extends PrevChangeAction {
    @Override
    public void update(@NotNull AnActionEvent e) {
      if (myRequestChain.getRequests().size() <= 1) {
        e.getPresentation().setEnabledAndVisible(false);
        return;
      }

      if (myRequestChain.getIndex() <= 0) {
        e.getPresentation().setEnabled(false);
        return;
      }

      e.getPresentation().setEnabled(true);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      myRequestChain.setIndex(myRequestChain.getIndex() - 1);
      updateRequest();
    }
  }

  @NotNull
  private AnAction createGoToChangeAction() {
    return GoToChangePopupBuilder.create(myRequestChain, new Consumer<Integer>() {
      @Override
      public void consume(Integer index) {
        if (index >= 0 && index != myRequestChain.getIndex()) {
          myRequestChain.setIndex(index);
          updateRequest();
        }
      }
    });
  }

  private class ShowInExternalToolAction extends DumbAwareAction {
    public ShowInExternalToolAction() {
      super("Show in external tool", null, AllIcons.General.ExternalToolsSmall);
    }

    @Override
    public void update(AnActionEvent e) {
      if (!ExternalDiffTool.isEnabled()) {
        e.getPresentation().setVisible(false);
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

  // Other

  private class MyChangeDiffToolAction extends ComboBoxAction implements DumbAware {
    public MyChangeDiffToolAction() {
      // TODO: add icons for diff tools, show only icon in toolbar - to reduce jumping on change ?
      setEnabledInModalContext(true);
    }

    @Override
    public void update(AnActionEvent e) {
      Presentation presentation = e.getPresentation();

      DiffTool activeTool = myState.getActiveTool();
      presentation.setText(activeTool.getName());

      if (activeTool == ErrorDiffTool.INSTANCE) {
        presentation.setEnabledAndVisible(false);
      }

      for (DiffTool tool : myAvailableTools) {
        if (tool != activeTool && tool.canShow(myContext, myActiveRequest)) {
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
      for (DiffTool tool : myAvailableTools) {
        if (tool.canShow(myContext, myActiveRequest)) {
          group.add(new DiffToolToggleAction(tool));
        }
      }

      return group;
    }
  }

  private class DiffToolToggleAction extends AnAction implements DumbAware {
    @NotNull private final DiffTool myDiffTool;

    private DiffToolToggleAction(@NotNull DiffTool tool) {
      super(tool.getName());
      setEnabledInModalContext(true);
      myDiffTool = tool;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      if (myState.getActiveTool() == myDiffTool) return;

      myToolOrder.remove(myDiffTool);
      int index;
      for (index = 0; index < myToolOrder.size(); index++) {
        if (myToolOrder.get(index).canShow(myContext, myActiveRequest)) break;
      }
      myToolOrder.add(index, myDiffTool);

      updateRequest(true, null);
    }
  }

  private class ShowActionGroupPopupAction extends DumbAwareAction {
    @Nullable private ActionGroup myActionGroup;

    public ShowActionGroupPopupAction() {
      registerCustomShortcutSet(CommonShortcuts.getDiff(), myPanel); // TODO: configurable shortcut
    }

    @Override
    public void update(AnActionEvent e) {
      if (myActionGroup == null) {
        e.getPresentation().setEnabled(false);
        return;
      }

      e.getPresentation().setEnabled(true);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      ListPopup popup = JBPopupFactory.getInstance().createActionGroupPopup("Diff Actions", myActionGroup, e.getDataContext(),
                                                                            JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, false);
      popup.showInCenterOf(myPanel);
    }

    public void setActionGroup(@Nullable ActionGroup actionGroup) {
      myActionGroup = actionGroup;
    }
  }

  //
  // Helpers
  //

  private class MyPanel extends JPanel implements DataProvider {
    public MyPanel() {
      super(new BorderLayout());
    }

    @Nullable
    @Override
    public Object getData(@NonNls String dataId) {
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

      Object data = myState.getData(dataId);
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

  private class MyFocusTraversalPolicy extends IdeFocusTraversalPolicy {
    @Override
    public final Component getDefaultComponentImpl(final Container focusCycleRoot) {
      JComponent component = CacheDiffRequestChainProcessor.this.getPreferredFocusedComponent();
      if (component == null) return null;
      return IdeFocusTraversalPolicy.getPreferredFocusedComponent(component, this);
    }
  }

  private class MyDiffWindow implements DiffContext.DiffWindow {
    @Override
    public boolean isFocused() {
      return CacheDiffRequestChainProcessor.this.isFocused();
    }

    @Override
    public void requestFocus() {
      CacheDiffRequestChainProcessor.this.requestFocus();
    }
  }

  private class MyDiffContext implements FrameDiffTool.DiffContext {
    @Nullable
    @Override
    public Project getProject() {
      return CacheDiffRequestChainProcessor.this.getProject();
    }

    @NotNull
    @Override
    public DiffWindow getDiffWindow() {
      return myDiffWindow;
    }

    @Nullable
    @Override
    public <T> T getUserData(@NotNull Key<T> key) {
      return myRequestChain.getUserData(key);
    }

    @Override
    public <T> void putUserData(@NotNull Key<T> key, @Nullable T value) {
      myRequestChain.putUserData(key, value);
    }
  }

  //
  // States
  //

  private interface ViewerState {
    void init();

    void destroy();

    @Nullable
    JComponent getPreferredFocusedComponent();

    @Nullable
    Object getData(@NonNls String dataId);

    @NotNull
    DiffTool getActiveTool();
  }

  private static class EmptyState implements ViewerState {
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

    public ErrorState(@Nullable DiffTool diffTool) {
      myDiffTool = diffTool;
    }

    @Override
    public void init() {
      myContentPanel.setContent(DiffUtil.createMessagePanel("Error: can't show diff"));

      buildToolbar(null);

      myPanel.validate();
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
    public void init() {
      myContentPanel.setContent(myViewer.getComponent());

      setTitle(myActiveRequest.getTitle());

      myPanel.validate();

      FrameDiffTool.ToolbarComponents toolbarComponents = myViewer.init();

      buildToolbar(toolbarComponents.toolbarActions);
      buildActionPopup(toolbarComponents.popupActions);

      myToolbarStatusPanel.setContent(toolbarComponents.statusPanel);

      myPanel.validate();
    }

    @Override
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
    public void init() {
      myContentPanel.setContent(myWrapperViewer.getComponent());

      setTitle(myActiveRequest.getTitle());

      myPanel.validate();


      FrameDiffTool.ToolbarComponents toolbarComponents1 = myViewer.init();
      FrameDiffTool.ToolbarComponents toolbarComponents2 = myWrapperViewer.init();

      List<AnAction> toolbarActions = new ArrayList<AnAction>();
      if (toolbarComponents1.toolbarActions != null) toolbarActions.addAll(toolbarComponents1.toolbarActions);
      if (toolbarComponents2.toolbarActions != null) {
        if (!toolbarActions.isEmpty() && !toolbarComponents2.toolbarActions.isEmpty()) toolbarActions.add(Separator.getInstance());
        toolbarActions.addAll(toolbarComponents2.toolbarActions);
      }
      buildToolbar(toolbarActions);

      List<AnAction> popupActions = new ArrayList<AnAction>();
      if (toolbarComponents1.popupActions != null) popupActions.addAll(toolbarComponents1.popupActions);
      if (toolbarComponents2.popupActions != null) {
        if (!popupActions.isEmpty() && !toolbarComponents2.popupActions.isEmpty()) popupActions.add(Separator.getInstance());
        popupActions.addAll(toolbarComponents2.popupActions);
      }
      buildActionPopup(popupActions);


      myToolbarStatusPanel.setContent(toolbarComponents2.statusPanel); // TODO: combine both panels ?

      myPanel.validate();
    }

    @Override
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
      if (DiffDataKeys.DIFF_VIEWER.is(dataId)) {
        return myWrapperViewer;
      }
      return null;
    }
  }
}
