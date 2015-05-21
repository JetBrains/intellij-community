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

import com.intellij.diff.util.DiffUserDataKeys;
import com.intellij.diff.util.DiffUtil;
import com.intellij.ide.DataManager;
import com.intellij.ide.impl.DataManagerImpl;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.BooleanGetter;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.wm.ex.IdeFocusTraversalPolicy;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.util.containers.ContainerUtil;
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
  @NotNull private final MergeTool.MergeViewer myViewer;

  @Nullable private BooleanGetter myCloseHandler;

  public MergeRequestProcessor(@Nullable Project project, @NotNull MergeRequest request) {
    myProject = project;
    myRequest = request;

    myContext = new MyDiffContext();

    myAvailableTools = ContainerUtil.list(TextMergeTool.INSTANCE, BinaryMergeTool.INSTANCE);

    myPanel = new JPanel(new BorderLayout());
    myMainPanel = new MyPanel();
    myContentPanel = new Wrapper();
    myToolbarPanel = new Wrapper();
    myToolbarPanel.setFocusable(true);
    myToolbarStatusPanel = new Wrapper();

    myPanel.add(myMainPanel, BorderLayout.CENTER);

    JPanel topPanel = new JPanel(new BorderLayout());
    topPanel.add(myToolbarPanel, BorderLayout.CENTER);
    topPanel.add(myToolbarStatusPanel, BorderLayout.EAST);


    myMainPanel.add(topPanel, BorderLayout.NORTH);
    myMainPanel.add(myContentPanel, BorderLayout.CENTER);

    myMainPanel.setFocusTraversalPolicyProvider(true);
    myMainPanel.setFocusTraversalPolicy(new MyFocusTraversalPolicy());

    MergeTool.MergeViewer viewer;
    try {
      MergeTool tool = getFittedTool();
      viewer = tool.createComponent(myContext, myRequest);
    }
    catch (Throwable e) {
      LOG.error(e);
      viewer = ErrorMergeTool.INSTANCE.createComponent(myContext, myRequest);
    }
    myViewer = viewer;
  }

  //
  // Update
  //

  public void init() {
    myContentPanel.setContent(myViewer.getComponent());
    setTitle(myRequest.getTitle());

    MergeTool.ToolbarComponents toolbarComponents = myViewer.init();

    buildToolbar(toolbarComponents.toolbarActions);
    myToolbarStatusPanel.setContent(toolbarComponents.statusPanel);
    myCloseHandler = toolbarComponents.closeHandler;
  }

  protected void buildToolbar(@Nullable List<AnAction> viewerActions) {
    ActionGroup group = new DefaultActionGroup(ContainerUtil.notNullize(viewerActions));
    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.DIFF_TOOLBAR, group, true);

    DataManager.registerDataProvider(toolbar.getComponent(), myMainPanel);
    toolbar.setTargetComponent(toolbar.getComponent());

    myToolbarPanel.setContent(toolbar.getComponent());
    for (AnAction action : group.getChildren(null)) {
      action.registerCustomShortcutSet(action.getShortcutSet(), myMainPanel);
    }
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
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        if (myDisposed) return;
        myDisposed = true;

        onDispose();

        Disposer.dispose(myViewer);

        myToolbarStatusPanel.setContent(null);
        myToolbarPanel.setContent(null);
        myContentPanel.setContent(null);
      }
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

  public boolean checkCloseAction() {
    if (myCloseHandler != null && !myCloseHandler.get()) {
      return Messages.showYesNoDialog(myPanel.getRootPane(),
                                      DiffBundle.message("merge.dialog.exit.without.applying.changes.confirmation.message"),
                                      DiffBundle.message("cancel.visual.merge.dialog.title"),
                                      Messages.getQuestionIcon()) == Messages.YES;
    }
    return true;
  }

  @Nullable
  public String getHelpId() {
    return PlatformDataKeys.HELP_ID.getData(myMainPanel);
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

  protected void requestFocusInternal() {
    JComponent component = getPreferredFocusedComponent();
    if (component != null) component.requestFocus();
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

  private class MyDiffContext extends MergeContext {
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
    public void closeDialog() {
      MergeRequestProcessor.this.closeDialog();
    }
  }
}
