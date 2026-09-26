// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.console;

import com.intellij.build.CompositeView;
import com.intellij.build.ExecutionNode;
import com.intellij.build.events.BuildEventPresentationData;
import com.intellij.codeWithMe.ClientId;
import com.intellij.execution.actions.ClearConsoleAction;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.editor.ClientEditorManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actions.ScrollToTheEndToolbarAction;
import com.intellij.openapi.editor.actions.ToggleUseSoftWrapsToolbarAction;
import com.intellij.openapi.editor.impl.softwrap.SoftWrapAppliancePlaces;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.ExperimentalUI;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.progress.ProgressUIUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.annotations.VisibleForTesting;

import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static com.intellij.execution.ui.ConsoleViewWithDelegateKt.unwrapDelegate;

@ApiStatus.Internal
public final class BuildConsoleViewHandler implements Disposable.Default {
  private final Project myProject;
  private final JPanel myPanel;
  private final CompositeView<ExecutionConsole> myView;
  private final @NotNull ExecutionNode myRootExecutionNode;
  private @Nullable ExecutionNode myExecutionNode;
  private final @NotNull List<? extends Filter> myExecutionConsoleFilters;
  private final BuildProgressStripe myPanelWithProgress;
  private final DefaultActionGroup myConsoleToolbarActionGroup;
  private final ActionToolbar myToolbar;

  private final @NotNull BuildConsoleViewStrategy myStrategy;

  public BuildConsoleViewHandler(@NotNull Project project,
                                 @NotNull ExecutionNode buildProgressRootNode,
                                 @NotNull Disposable parentDisposable,
                                 @Nullable ExecutionConsole executionConsole,
                                 @NotNull List<? extends Filter> executionConsoleFilters,
                                 @NotNull BuildConsoleViewStrategy strategy) {
    myProject = project;
    myStrategy = strategy;
    myPanel = new NonOpaquePanel(new BorderLayout());
    myPanelWithProgress = new BuildProgressStripe(myPanel, parentDisposable, (int)ProgressUIUtil.DEFAULT_PROGRESS_DELAY_MILLIS);
    myExecutionConsoleFilters = executionConsoleFilters;

    myView = new CompositeView<>(null) {
      @Override
      public void addView(@NotNull ExecutionConsole view, @NotNull String viewName) {
        super.addView(view, viewName);
        UIUtil.removeScrollBorder(view.getComponent());
      }

      @Override
      public void showView(@NotNull String viewName, boolean requestFocus) {
        super.showView(viewName, requestFocus);
        withView(viewName, it -> showTextConsoleToolbarActions(it));
        myPanel.setVisible(true);
      }
    };
    Disposer.register(this, myView);
    myPanel.add(myView.getComponent(), BorderLayout.CENTER);

    myConsoleToolbarActionGroup = new DefaultActionGroup();
    myConsoleToolbarActionGroup.copyFromGroup(createDefaultTextConsoleToolbar());
    myToolbar = ActionManager.getInstance().createActionToolbar("BuildConsole", myConsoleToolbarActionGroup, false);
    myToolbar.setTargetComponent(myView);
    myPanel.add(myToolbar.getComponent(), BorderLayout.EAST);

    myRootExecutionNode = buildProgressRootNode;
    if (executionConsole != null) {
      var rootConsoleViewName = getNodeConsoleViewName(buildProgressRootNode);
      var rootConsoleView = myStrategy.createRootConsoleView(project, myRootExecutionNode, executionConsole);
      myView.addViewAndShowIfNeeded(rootConsoleView, rootConsoleViewName, true, false);
    }

    if (ExperimentalUI.isNewUI()) {
      UIUtil.setBackgroundRecursively(myPanel, JBUI.CurrentTheme.ToolWindow.background());
    }

    Disposer.register(parentDisposable, this);
  }

  private void showTextConsoleToolbarActions(@NotNull ExecutionConsole console) {
    if (console instanceof CustomExecutionConsole customConsole) {
      var actionGroup = customConsole.myActions;
      if (actionGroup instanceof DefaultActionGroup defaultActionGroup) {
        myConsoleToolbarActionGroup.copyFromGroup(defaultActionGroup);
      }
      else if (actionGroup != null) {
        myConsoleToolbarActionGroup.copyFrom(actionGroup);
      }
      else {
        myConsoleToolbarActionGroup.removeAll();
      }
    }
    else {
      myConsoleToolbarActionGroup.copyFromGroup(createDefaultTextConsoleToolbar());
    }
    updateToolbarActionsImmediately();
  }

  private void updateToolbarActionsImmediately() {
    UIUtil.invokeLaterIfNeeded(() -> myToolbar.updateActionsImmediately());
  }

  private @NotNull DefaultActionGroup createDefaultTextConsoleToolbar() {
    DefaultActionGroup textConsoleToolbarActionGroup = new DefaultActionGroup();
    textConsoleToolbarActionGroup.add(new ToggleUseSoftWrapsToolbarAction(SoftWrapAppliancePlaces.CONSOLE) {
      @Override
      protected @Nullable Editor getEditor(@NotNull AnActionEvent e) {
        return BuildConsoleViewHandler.this.getCurrentConsoleEditor();
      }
    });
    textConsoleToolbarActionGroup.add(new ScrollToTheEndToolbarAction(getCurrentConsoleEditor()));
    textConsoleToolbarActionGroup.add(new ClearConsoleAction());
    return textConsoleToolbarActionGroup;
  }

  public @Nullable ExecutionConsole getCurrentConsole() {
    return myView.getVisibleView();
  }

  @TestOnly
  @ApiStatus.Internal
  public @NotNull DefaultActionGroup getConsoleToolbarActionGroup() {
    return myConsoleToolbarActionGroup;
  }

  @VisibleForTesting
  @ApiStatus.Internal
  public @Nullable Editor getCurrentConsoleEditor() {
    var currentConsole = ObjectUtils.doIfNotNull(getCurrentConsole(), it -> unwrapDelegate(it));
    if (currentConsole instanceof ConsoleViewImpl) {
      Editor editor = ((ConsoleViewImpl)currentConsole).getEditor();
      if (editor == null) return null;
      return ClientEditorManager.getClientEditor(editor, ClientId.getCurrentOrNull());
    }
    return null;
  }

  public @Nullable ExecutionNode getExecutionNode() {
    return myExecutionNode;
  }

  public void setExecutionNode(@NotNull ExecutionNode node) {
    if (myProject.isDisposed()) return;
    myExecutionNode = node;
    myStrategy.showExecutionNode(myProject, this, node);
  }

  public @NotNull ExecutionNode getRootExecutionNode() {
    return myRootExecutionNode;
  }

  public @NotNull List<? extends Filter> getExecutionConsoleFilters() {
    return myExecutionConsoleFilters;
  }

  public boolean hasNodeView(@NotNull ExecutionNode node) {
    return myView.hasView(getNodeConsoleViewName(node));
  }

  public boolean hasDeferredNodeView(@NotNull ExecutionNode node) {
    return myView.hasDeferredView(getNodeConsoleViewName(node));
  }

  public @Nullable ExecutionConsole getNodeView(@NotNull ExecutionNode node) {
    return myView.getView(getNodeConsoleViewName(node));
  }

  public void addNodeView(@NotNull ExecutionNode node, @NotNull ExecutionConsole view) {
    myView.addView(view, getNodeConsoleViewName(node));
  }

  public @NotNull ExecutionConsole getOrAddNodeView(@NotNull ExecutionNode node, @NotNull Supplier<? extends ExecutionConsole> create) {
    return myView.getOrAddView(getNodeConsoleViewName(node), create);
  }

  public void showNodeView(@NotNull ExecutionNode node) {
    myView.showView(getNodeConsoleViewName(node), false);
  }

  public void maybeAddExecutionConsole(@NotNull ExecutionNode node, @NotNull BuildEventPresentationData presentationData) {
    UIUtil.invokeLaterIfNeeded(() -> {
      var customConsole = presentationData.getExecutionConsole();
      if (customConsole == null) return;
      var customView = new CustomExecutionConsole(customConsole, presentationData.consoleToolbarActions());
      myView.addView(customView, getNodeConsoleViewName(node));
    });
  }

  public void withConsoleView(@NotNull ExecutionNode node, Consumer<? super BuildConsoleView> consumer) {
    myView.withView(getNodeConsoleViewName(node), console -> {
      if (console instanceof BuildConsoleView consoleView) {
        consumer.accept(consoleView);
      }
    });
  }

  public JComponent getComponent() {
    return myPanelWithProgress;
  }

  public void updateProgressBar(long total, long progress) {
    myPanelWithProgress.updateProgress(total, progress);
  }


  public void stopProgressBar() {
    myPanelWithProgress.stopLoading();
  }

  private static @NotNull String getNodeConsoleViewName(@NotNull ExecutionNode node) {
    return String.valueOf(System.identityHashCode(node));
  }

  public void clear() {
    myPanel.setVisible(false);
  }

  private static final class CustomExecutionConsole implements ExecutionConsole {
    private final ExecutionConsole myExecutionConsole;
    private final @Nullable ActionGroup myActions;

    private CustomExecutionConsole(
      @NotNull ExecutionConsole executionConsole,
      @Nullable ActionGroup toolbarActions
    ) {
      myExecutionConsole = executionConsole;
      myActions = toolbarActions;
    }

    @Override
    public @NotNull JComponent getComponent() {
      return myExecutionConsole.getComponent();
    }

    @Override
    public JComponent getPreferredFocusableComponent() {
      return myExecutionConsole.getPreferredFocusableComponent();
    }

    @Override
    public void dispose() {
      Disposer.dispose(myExecutionConsole);
    }
  }
}
