package com.intellij.openapi.util.diff.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.diff.DiffManagerEx;
import com.intellij.openapi.util.diff.actions.impl.*;
import com.intellij.openapi.util.diff.api.DiffTool;
import com.intellij.openapi.util.diff.api.DiffTool.DiffContext;
import com.intellij.openapi.util.diff.api.DiffTool.DiffViewer;
import com.intellij.openapi.util.diff.chains.DiffRequestChain;
import com.intellij.openapi.util.diff.chains.DiffRequestPresentable;
import com.intellij.openapi.util.diff.chains.DiffRequestPresentableException;
import com.intellij.openapi.util.diff.requests.DiffRequest;
import com.intellij.openapi.util.diff.requests.ErrorDiffRequest;
import com.intellij.openapi.util.diff.requests.NoDiffRequest;
import com.intellij.openapi.util.diff.tools.ErrorDiffTool;
import com.intellij.openapi.util.diff.tools.util.DiffDataKeys;
import com.intellij.openapi.util.diff.tools.util.DiffUserDataKeys;
import com.intellij.openapi.util.diff.tools.util.PrevNextDifferenceIterable;
import com.intellij.openapi.util.diff.tools.util.SoftHardCacheMap;
import com.intellij.openapi.util.diff.util.DiffUtil;
import com.intellij.openapi.wm.ex.IdeFocusTraversalPolicy;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.LinkedList;
import java.util.List;

import static com.intellij.openapi.util.diff.tools.util.DiffUserDataKeys.ScrollToPolicy.FIRST_CHANGE;
import static com.intellij.openapi.util.diff.tools.util.DiffUserDataKeys.ScrollToPolicy.LAST_CHANGE;

public abstract class CacheDiffRequestChainProcessor implements Disposable {
  @Nullable private final Project myProject;
  @NotNull private final DiffRequestChain myRequestChain;

  @NotNull private final DiffContext myContext;

  @NotNull private final SoftHardCacheMap<DiffRequestPresentable, DiffRequest> myRequestCache =
    new SoftHardCacheMap<DiffRequestPresentable, DiffRequest>(5, 5);

  @NotNull private final List<DiffTool> myAvailableTools;
  @NotNull private final LinkedList<DiffTool> myToolOrder;

  @NotNull private final MyDiffWindow myDiffWindow;
  @NotNull private final OpenInEditorAction myOpenInEditorAction;

  @NotNull private final MyPanel myPanel;
  @NotNull private final ModifiablePanel myContentPanel;
  @NotNull private final ModifiablePanel myToolbarPanel; // TODO: allow to call 'updateToolbar' from Viewer ?
  @NotNull private final ModifiablePanel myToolbarStatusPanel;
  @Nullable private final JLabel myTitleLabel;

  @NotNull private DiffRequest myActiveRequest;

  @Nullable private DiffViewer myActiveViewer;
  @Nullable private DiffTool myActiveTool;

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

    JComponent bottomPanel = myContext.getUserData(DiffUserDataKeys.BOTTOM_PANEL);
    if (bottomPanel != null) myPanel.add(bottomPanel, BorderLayout.SOUTH);

    myPanel.setFocusTraversalPolicyProvider(true);
    myPanel.setFocusTraversalPolicy(new MyFocusTraversalPolicy());

    myOpenInEditorAction = new OpenInEditorAction(new Runnable() {
      @Override
      public void run() {
        onAfterNavigate();
      }
    });
  }

  public void updateRequest() {
    updateRequest(false);
  }

  public void updateRequest(boolean force) {
    boolean hadFocus = isFocused();

    DiffRequest request = loadRequest();
    if (!force && request == myActiveRequest) return;

    myActiveRequest.onAssigned(false);
    request.onAssigned(true);

    myActiveRequest = request;

    myActiveTool = getSuitableTool(myActiveRequest);

    if (myActiveViewer != null) Disposer.dispose(myActiveViewer);
    myActiveViewer = myActiveTool.createComponent(myContext, myActiveRequest);


    myContentPanel.setContent(myActiveViewer.getComponent());

    if (myTitleLabel != null) {
      myTitleLabel.setText(request.getWindowTitle());
    }
    else {
      setWindowTitle(request.getWindowTitle());
    }

    myPanel.validate();


    DiffTool.ToolbarComponents toolbarComponents = myActiveViewer.init();

    DefaultActionGroup group = buildToolbar(toolbarComponents.toolbarActions);
    myToolbarPanel.setContent(DiffUtil.createToolbar(group).getComponent());
    for (AnAction action : group.getChildren(null)) {
      action.registerCustomShortcutSet(action.getShortcutSet(), myPanel);
    }

    myToolbarStatusPanel.setContent(toolbarComponents.statusPanel);

    myPanel.validate();


    if (hadFocus) requestFocus();
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

  @NotNull
  private DiffTool getSuitableTool(@NotNull DiffRequest request) {
    for (DiffTool tool : myToolOrder) {
      if (tool.canShow(myContext, request)) {
        return tool;
      }
    }

    return ErrorDiffTool.INSTANCE;
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
    myActiveRequest.onAssigned(false);
    myRequestCache.clear();
    if (myActiveViewer != null) Disposer.dispose(myActiveViewer);
  }

  @NotNull
  protected DefaultActionGroup buildToolbar(@Nullable List<AnAction> viewerActions) {
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

    List<AnAction> contextActions = myContext.getUserData(DiffUserDataKeys.CONTEXT_ACTIONS);
    DiffUtil.addActionBlock(group, contextActions);

    List<AnAction> requestContextActions = myActiveRequest.getUserData(DiffUserDataKeys.CONTEXT_ACTIONS);
    DiffUtil.addActionBlock(group, requestContextActions);

    DiffUtil.addActionBlock(group, ActionManager.getInstance().getAction(IdeActions.ACTION_CONTEXT_HELP));

    return group;
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
    JComponent component = myActiveViewer != null ? myActiveViewer.getPreferredFocusedComponent() : null;
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

  @NotNull private IterationState myState = IterationState.NONE;

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

      if (iterable != null && myState != IterationState.NEXT) {
        iterable.notify("Press again to go to the next change"); // TODO: "Change" is a bad word
        myState = IterationState.NEXT;
        return;
      }

      myState = IterationState.NONE;
      myRequestChain.setIndex(myRequestChain.getIndex() + 1);
      myContext.putUserData(DiffUserDataKeys.SCROLL_TO_CHANGE, FIRST_CHANGE);
      updateRequest();
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

      if (iterable != null && myState != IterationState.PREV) {
        iterable.notify("Press again to go to the previous change");
        myState = IterationState.PREV;
        return;
      }

      myState = IterationState.NONE;
      myRequestChain.setIndex(myRequestChain.getIndex() - 1);
      myContext.putUserData(DiffUserDataKeys.SCROLL_TO_CHANGE, LAST_CHANGE);
      updateRequest();
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
      myContext.putUserData(DiffUserDataKeys.SCROLL_TO_CHANGE, null);
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
      myContext.putUserData(DiffUserDataKeys.SCROLL_TO_CHANGE, null);
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
          myContext.putUserData(DiffUserDataKeys.SCROLL_TO_CHANGE, null);
          updateRequest();
        }
      }
    });
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

      if (myActiveTool != null) {
        presentation.setText(myActiveTool.getName());
      }
      else {
        presentation.setText("<Unknown>");
      }

      if (myActiveTool == ErrorDiffTool.INSTANCE) {
        presentation.setEnabledAndVisible(false);
      }

      for (DiffTool tool : myAvailableTools) {
        if (tool != myActiveTool && tool.canShow(myContext, myActiveRequest)) {
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

    private class DiffToolToggleAction extends AnAction implements DumbAware {
      @NotNull private final DiffTool myDiffTool;

      private DiffToolToggleAction(@NotNull DiffTool tool) {
        super(tool.getName());
        setEnabledInModalContext(true);
        myDiffTool = tool;
      }

      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        if (myActiveTool == myDiffTool) return;

        myToolOrder.remove(myDiffTool);
        int index;
        for (index = 0; index < myToolOrder.size(); index++) {
          if (myToolOrder.get(index).canShow(myContext, myActiveRequest)) break;
        }
        myToolOrder.add(index, myDiffTool);

        myContext.putUserData(DiffUserDataKeys.SCROLL_TO_CHANGE, null);
        updateRequest(true);
      }
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
      else {
        return null;
      }
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

  private class MyDiffContext implements DiffTool.DiffContext {
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
}
