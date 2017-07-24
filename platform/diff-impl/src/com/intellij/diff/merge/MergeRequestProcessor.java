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
package com.intellij.diff.merge;

import com.intellij.diff.DiffManagerEx;
import com.intellij.diff.actions.impl.NextDifferenceAction;
import com.intellij.diff.actions.impl.PrevDifferenceAction;
import com.intellij.diff.tools.util.DiffDataKeys;
import com.intellij.diff.tools.util.PrevNextDifferenceIterable;
import com.intellij.diff.util.DiffPlaces;
import com.intellij.diff.util.DiffUserDataKeys;
import com.intellij.diff.util.DiffUtil;
import com.intellij.ide.DataManager;
import com.intellij.ide.impl.DataManagerImpl;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.BooleanGetter;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.wm.ex.IdeFocusTraversalPolicy;
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
import java.util.List;

// TODO: support merge request chains
// idea - to keep in memory all viewers that were modified (so binary conflict is not the case and OOM shouldn't be too often)
// suspend() / resume() methods for viewers? To not interfere with MergeRequest lifecycle: single request -> single viewer -> single applyResult()
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

  @NotNull private final MergeRequest myRequest;

  @NotNull private MergeTool.MergeViewer myViewer;
  @Nullable private BooleanGetter myCloseHandler;
  @Nullable private BottomActions myBottomActions;
  private boolean myConflictResolved = false;

  public MergeRequestProcessor(@Nullable Project project, @NotNull MergeRequest request) {
    myProject = project;
    myRequest = request;

    myContext = new MyDiffContext();
    myContext.putUserData(DiffUserDataKeys.PLACE, DiffPlaces.MERGE);

    myAvailableTools = DiffManagerEx.getInstance().getMergeTools();

    myMainPanel = new MyPanel();
    myContentPanel = new Wrapper();
    myToolbarPanel = new Wrapper();
    myToolbarPanel.setFocusable(true);
    myToolbarStatusPanel = new Wrapper();

    myPanel = JBUI.Panels.simplePanel(myMainPanel);

    JPanel topPanel = JBUI.Panels.simplePanel(myToolbarPanel).addToRight(myToolbarStatusPanel);

    myMainPanel.add(topPanel, BorderLayout.NORTH);
    myMainPanel.add(myContentPanel, BorderLayout.CENTER);

    myMainPanel.setFocusTraversalPolicyProvider(true);
    myMainPanel.setFocusTraversalPolicy(new MyFocusTraversalPolicy());

    MergeTool.MergeViewer viewer;
    try {
      viewer = getFittedTool().createComponent(myContext, myRequest);
    }
    catch (Throwable e) {
      LOG.error(e);
      viewer = ErrorMergeTool.INSTANCE.createComponent(myContext, myRequest);
    }

    myViewer = viewer;
    updateBottomActions();
  }

  //
  // Update
  //

  @CalledInAwt
  public void init() {
    setTitle(myRequest.getTitle());
    initViewer();
  }

  @CalledInAwt
  private void initViewer() {
    myContentPanel.setContent(myViewer.getComponent());

    MergeTool.ToolbarComponents toolbarComponents = myViewer.init();

    buildToolbar(toolbarComponents.toolbarActions);
    myToolbarStatusPanel.setContent(toolbarComponents.statusPanel);
    myCloseHandler = toolbarComponents.closeHandler;
  }

  @CalledInAwt
  private void destroyViewer() {
    Disposer.dispose(myViewer);

    ActionUtil.clearActions(myMainPanel);

    myContentPanel.setContent(null);
    myToolbarPanel.setContent(null);
    myToolbarStatusPanel.setContent(null);
    myCloseHandler = null;
    myBottomActions = null;
  }

  private void updateBottomActions() {
    myBottomActions = new BottomActions();
    myBottomActions.applyLeft = myViewer.getResolveAction(MergeResult.LEFT);
    myBottomActions.applyRight = myViewer.getResolveAction(MergeResult.RIGHT);
    myBottomActions.resolveAction = myViewer.getResolveAction(MergeResult.RESOLVED);
    myBottomActions.cancelAction = myViewer.getResolveAction(MergeResult.CANCEL);
  }

  @NotNull
  protected DefaultActionGroup collectToolbarActions(@Nullable List<AnAction> viewerActions) {
    DefaultActionGroup group = new DefaultActionGroup();

    List<AnAction> navigationActions = ContainerUtil.list(ActionManager.getInstance().getAction(IdeActions.ACTION_PREVIOUS_DIFF),
                                                          ActionManager.getInstance().getAction(IdeActions.ACTION_NEXT_DIFF));
    DiffUtil.addActionBlock(group, navigationActions);

    DiffUtil.addActionBlock(group, viewerActions);

    List<AnAction> requestContextActions = myRequest.getUserData(DiffUserDataKeys.CONTEXT_ACTIONS);
    DiffUtil.addActionBlock(group, requestContextActions);

    List<AnAction> contextActions = myContext.getUserData(DiffUserDataKeys.CONTEXT_ACTIONS);
    DiffUtil.addActionBlock(group, contextActions);

    return group;
  }

  protected void buildToolbar(@Nullable List<AnAction> viewerActions) {
    ActionGroup group = collectToolbarActions(viewerActions);
    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.DIFF_TOOLBAR, group, true);

    DataManager.registerDataProvider(toolbar.getComponent(), myMainPanel);
    toolbar.setTargetComponent(toolbar.getComponent());

    myToolbarPanel.setContent(toolbar.getComponent());
    ActionUtil.recursiveRegisterShortcutSet(group, myMainPanel, null);
  }

  @NotNull
  private MergeTool getFittedTool() {
    for (MergeTool tool : myAvailableTools) {
      try {
        if (tool.canShow(myContext, myRequest)) return tool;
      }
      catch (Throwable e) {
        LOG.error(e);
      }
    }

    return ErrorMergeTool.INSTANCE;
  }

  private void setTitle(@Nullable String title) {
    if (title == null) title = "Merge";
    setWindowTitle(title);
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
    });
  }

  @CalledInAwt
  private void applyRequestResult(@NotNull MergeResult result) {
    if (myConflictResolved) return;
    myConflictResolved = true;
    try {
      myRequest.applyResult(result);
    }
    catch (Exception e) {
      LOG.error(e);
    }
  }

  @CalledInAwt
  private void reopenWithTool(@NotNull MergeTool tool) {
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

    DiffUtil.runPreservingFocus(myContext, () -> {
      destroyViewer();
      myViewer = newViewer;
      updateBottomActions();
      rebuildSouthPanel();
      initViewer();
    });
  }

  //
  // Abstract
  //

  @CalledInAwt
  protected void onDispose() {
  }

  protected void setWindowTitle(@NotNull String title) {
  }

  protected abstract void rebuildSouthPanel();

  public abstract void closeDialog();

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

  @CalledInAwt
  public boolean checkCloseAction() {
    return myConflictResolved || myCloseHandler == null || myCloseHandler.get();
  }

  @NotNull
  public BottomActions getBottomActions() {
    return myBottomActions != null ? myBottomActions : new BottomActions();
  }

  @Nullable
  public String getHelpId() {
    return PlatformDataKeys.HELP_ID.getData(myMainPanel);
  }

  //
  // Misc
  //

  private boolean isFocused() {
    return DiffUtil.isFocusedComponent(myProject, myPanel);
  }

  private void requestFocusInternal() {
    JComponent component = getPreferredFocusedComponent();
    if (component != null) component.requestFocusInWindow();
  }

  //
  // Navigation
  //

  private static class MyNextDifferenceAction implements AnActionExtensionProvider {
    @Override
    public boolean isActive(@NotNull AnActionEvent e) {
      return true;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      if (!ActionPlaces.DIFF_TOOLBAR.equals(e.getPlace())) {
        e.getPresentation().setEnabled(true);
        return;
      }

      PrevNextDifferenceIterable iterable = DiffDataKeys.PREV_NEXT_DIFFERENCE_ITERABLE.getData(e.getDataContext());
      if (iterable != null && iterable.canGoNext()) {
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
      }
    }
  }

  private static class MyPrevDifferenceAction implements AnActionExtensionProvider {
    @Override
    public boolean isActive(@NotNull AnActionEvent e) {
      return true;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      if (!ActionPlaces.DIFF_TOOLBAR.equals(e.getPlace())) {
        e.getPresentation().setEnabled(true);
        return;
      }

      PrevNextDifferenceIterable iterable = DiffDataKeys.PREV_NEXT_DIFFERENCE_ITERABLE.getData(e.getDataContext());
      if (iterable != null && iterable.canGoPrev()) {
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
      Object data;

      DataProvider contentProvider = DataManagerImpl.getDataProviderEx(myContentPanel.getTargetComponent());
      if (contentProvider != null) {
        data = contentProvider.getData(dataId);
        if (data != null) return data;
      }

      if (CommonDataKeys.PROJECT.is(dataId)) {
        return myProject;
      }
      else if (PlatformDataKeys.HELP_ID.is(dataId)) {
        if (myRequest.getUserData(DiffUserDataKeys.HELP_ID) != null) {
          return myRequest.getUserData(DiffUserDataKeys.HELP_ID);
        }
        else {
          return "procedures.vcWithIDEA.commonVcsOps.integrateDiffs.resolveConflict";
        }
      }
      else if (DiffDataKeys.MERGE_VIEWER.is(dataId)) {
        return myViewer;
      }

      if (NextDifferenceAction.DATA_KEY.is(dataId)) {
        return new MyNextDifferenceAction();
      }
      else if (PrevDifferenceAction.DATA_KEY.is(dataId)) {
        return new MyPrevDifferenceAction();
      }

      DataProvider requestProvider = myRequest.getUserData(DiffUserDataKeys.DATA_PROVIDER);
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
      JComponent component = MergeRequestProcessor.this.getPreferredFocusedComponent();
      if (component == null) return null;
      return IdeFocusTraversalPolicy.getPreferredFocusedComponent(component, this);
    }
  }

  private class MyDiffContext extends MergeContextEx {
    @Nullable
    @Override
    public Project getProject() {
      return MergeRequestProcessor.this.getProject();
    }

    @Override
    public boolean isFocused() {
      return MergeRequestProcessor.this.isFocused();
    }

    @Override
    public void requestFocus() {
      MergeRequestProcessor.this.requestFocusInternal();
    }

    @Override
    public void finishMerge(@NotNull MergeResult result) {
      applyRequestResult(result);
      MergeRequestProcessor.this.closeDialog();
    }

    @Override
    @CalledInAwt
    public void reopenWithTool(@NotNull MergeTool tool) {
      MergeRequestProcessor.this.reopenWithTool(tool);
    }
  }

  public static class BottomActions {
    @Nullable public Action applyLeft;
    @Nullable public Action applyRight;
    @Nullable public Action resolveAction;
    @Nullable public Action cancelAction;
  }
}
