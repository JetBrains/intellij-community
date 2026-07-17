// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.console;

import com.intellij.build.BuildTextConsoleView;
import com.intellij.build.BuildView;
import com.intellij.build.CompositeView;
import com.intellij.build.ExecutionNode;
import com.intellij.build.events.BuildEventPresentationData;
import com.intellij.codeWithMe.ClientId;
import com.intellij.execution.actions.ClearConsoleAction;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.ui.ConsoleView;
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
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.ExperimentalUI;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.progress.ProgressUIUtil;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.BorderLayout;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

@ApiStatus.Internal
public final class BuildConsoleViewHandler implements Disposable.Default {
  private final Project myProject;
  private final JPanel myPanel;
  private final CompositeView<ExecutionConsole> myView;
  private final AtomicReference<String> myNodeConsoleViewName = new AtomicReference<>();
  private @Nullable ExecutionNode myExecutionNode;
  private final @NotNull List<? extends Filter> myExecutionConsoleFilters;
  private final BuildProgressStripe myPanelWithProgress;
  private final DefaultActionGroup myConsoleToolbarActionGroup;
  private final ActionToolbar myToolbar;

  public BuildConsoleViewHandler(@NotNull Project project,
                                 @Nullable Tree tree,
                                 @NotNull ExecutionNode buildProgressRootNode,
                                 @NotNull Disposable parentDisposable,
                                 @Nullable ExecutionConsole executionConsole,
                                 @NotNull List<? extends Filter> executionConsoleFilters) {
    myProject = project;
    myPanel = new NonOpaquePanel(new BorderLayout());
    myPanelWithProgress = new BuildProgressStripe(myPanel, parentDisposable, (int)ProgressUIUtil.DEFAULT_PROGRESS_DELAY_MILLIS);
    myExecutionConsoleFilters = executionConsoleFilters;
    Disposer.register(parentDisposable, this);
    myView = new CompositeView<>(null) {
      @Override
      public void addView(@NotNull ExecutionConsole view, @NotNull String viewName) {
        super.addView(view, viewName);
        UIUtil.removeScrollBorder(view.getComponent());
      }
    };
    Disposer.register(this, myView);
    if (executionConsole != null) {
      var nodeConsoleViewName = getNodeConsoleViewName(buildProgressRootNode);
      var console = executionConsole instanceof ConsoleView consoleView ?
                    new BuildConsoleViewImpl(project, consoleView) :
                    executionConsole;
      myView.addViewAndShowIfNeeded(console, nodeConsoleViewName, true, false);
      myNodeConsoleViewName.set(nodeConsoleViewName);
    }
    myPanel.add(myView.getComponent(), BorderLayout.CENTER);
    myConsoleToolbarActionGroup = new DefaultActionGroup();
    myConsoleToolbarActionGroup.copyFromGroup(createDefaultTextConsoleToolbar());
    myToolbar = ActionManager.getInstance().createActionToolbar("BuildConsole", myConsoleToolbarActionGroup, false);
    myToolbar.setTargetComponent(myView);
    myPanel.add(myToolbar.getComponent(), BorderLayout.EAST);

    if (ExperimentalUI.isNewUI()) {
      UIUtil.setBackgroundRecursively(myPanel, JBUI.CurrentTheme.ToolWindow.background());
    }

    if (tree != null) {
      tree.addTreeSelectionListener(e -> {
        if (Disposer.isDisposed(myView)) return;
        TreePath path = e.getPath();
        if (path == null) {
          return;
        }
        TreePath selectionPath = tree.getSelectionPath();
        setNode(selectionPath != null ? (DefaultMutableTreeNode)selectionPath.getLastPathComponent() : null);
      });
    }
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
        var editor = BuildConsoleViewHandler.this.getEditor();
        if (editor == null) return null;
        return ClientEditorManager.getClientEditor(editor, ClientId.getCurrentOrNull());
      }
    });
    textConsoleToolbarActionGroup.add(new ScrollToTheEndToolbarAction(getEditor()));
    textConsoleToolbarActionGroup.add(new ClearConsoleAction());
    return textConsoleToolbarActionGroup;
  }

  public @Nullable ExecutionConsole getCurrentConsole() {
    String nodeConsoleViewName = myNodeConsoleViewName.get();
    if (nodeConsoleViewName == null) return null;
    return myView.getView(nodeConsoleViewName);
  }

  @TestOnly
  public @NotNull ExecutionConsole getCurrentConsoleOrEmpty() {
    var console = ObjectUtils.notNull(getCurrentConsole(),
                                      () -> new ConsoleViewImpl(myProject, GlobalSearchScope.EMPTY_SCOPE, true, false));
    if (console instanceof BuildConsoleView buildConsole) {
      console = buildConsole.getConsoleView();
    }
    if (console instanceof ConsoleViewImpl consoleImpl) {
      consoleImpl.flushDeferredText();
    }
    return console;
  }

  private @Nullable Editor getEditor() {
    ExecutionConsole console = getCurrentConsole();
    if (console instanceof ConsoleViewImpl) {
      return ((ConsoleViewImpl)console).getEditor();
    }
    return null;
  }

  public void setNodeIfChanged(@NotNull ExecutionNode node) {
    if (myProject.isDisposed() || node == myExecutionNode) return;
    setExecutionNode(node);
  }

  public @Nullable ExecutionNode getExecutionNode() {
    return myExecutionNode;
  }

  public void setExecutionNode(@NotNull ExecutionNode node) {
    myExecutionNode = node;
    var nodeConsoleViewName = getNodeConsoleViewName(node);
    myNodeConsoleViewName.set(nodeConsoleViewName);

    var console = myView.getView(nodeConsoleViewName);
    if (console == null) {
      var consoleImpl = new BuildTextConsoleView(myProject, true, myExecutionConsoleFilters);
      console = new BuildConsoleViewImpl(myProject, consoleImpl);
      myView.addView(console, nodeConsoleViewName);
    }
    myView.showView(nodeConsoleViewName, false);

    showTextConsoleToolbarActions(console);

    myPanel.setVisible(true);
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

  private void setNode(@Nullable DefaultMutableTreeNode node) {
    if (myProject.isDisposed()) return;
    if (node == null || node.getUserObject() == myExecutionNode) return;
    if (node.getUserObject() instanceof ExecutionNode executionNode) {
      setExecutionNode(executionNode);
      return;
    }

    myExecutionNode = null;
    if (myView.getView(BuildView.CONSOLE_VIEW_NAME) != null/* && myViewSettingsProvider.isSideBySideView()*/) {
      myView.showView(BuildView.CONSOLE_VIEW_NAME, false);
      myPanel.setVisible(true);
    }
    else {
      myPanel.setVisible(false);
    }
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
