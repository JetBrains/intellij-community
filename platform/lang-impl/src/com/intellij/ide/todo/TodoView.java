// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.todo;

import com.intellij.ide.IdeBundle;
import com.intellij.lang.LangBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ReadConstraint;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.fileTypes.FileTypeEvent;
import com.intellij.openapi.fileTypes.FileTypeListener;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowEx;
import com.intellij.openapi.wm.ex.ToolWindowManagerListener;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.ui.IdeUICustomization;
import com.intellij.ui.content.*;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.OptionTag;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@State(name = "TodoView", storages = @Storage(StoragePathMacros.PRODUCT_WORKSPACE_FILE))
public class TodoView implements PersistentStateComponent<TodoView.State>, Disposable {

  private final @NotNull Project myProject;

  private ToolWindow myToolWindow;
  private ContentManager myContentManager;
  private TodoPanel myAllTodos;
  private @Nullable TodoPanel myChangeListTodosPanel;
  private CurrentFileTodosPanel myCurrentFileTodosPanel;
  private ScopeBasedTodosPanel myScopeBasedTodosPanel;
  private final List<TodoPanel> myPanels = new ArrayList<>();
  private final List<Content> myNotAddedContent = new ArrayList<>();

  private State state = new State();

  private final TodoViewChangesSupport myChangesSupport;
  private final TodoViewChangesSupport.Listener myChangesListener;
  private Content myChangeListTodosContent;

  public TodoView(@NotNull Project project) {
    myProject = project;

    state.all.arePackagesShown = true;

    MessageBusConnection connection = project.getMessageBus().connect(this);
    connection.subscribe(TodoConfiguration.PROPERTY_CHANGE, new MyPropertyChangeListener());
    connection.subscribe(FileTypeManager.TOPIC, new MyFileTypeListener());

    myChangesSupport = project.getService(TodoViewChangesSupport.class);
    myChangesListener = myChangesSupport.installListener(project, connection,
                                                         () -> myContentManager,
                                                         () -> myChangeListTodosContent);
  }

  static final class State {
    @Attribute("selected-index")
    public int selectedIndex;

    @OptionTag(value = "selected-file", nameAttribute = "id", tag = "todo-panel", valueAttribute = "")
    public TodoPanelSettings current = new TodoPanelSettings();

    @OptionTag(value = "all", nameAttribute = "id", tag = "todo-panel", valueAttribute = "")
    public TodoPanelSettings all = new TodoPanelSettings();

    @OptionTag(value = "default-changelist", nameAttribute = "id", tag = "todo-panel", valueAttribute = "")
    public TodoPanelSettings changeList = new TodoPanelSettings();

    public @Nls String selectedScope;
  }

  @NotNull Project getProject() {
    return myProject;
  }

  @Override
  public void loadState(@NotNull State state) {
    this.state = state;
  }

  @Override
  public @NotNull State getState() {
    if (myContentManager != null) {
      // all panel were constructed
      Content content = myContentManager.getSelectedContent();
      state.selectedIndex = content == null ? -1 : myContentManager.getIndexOfContent(content);
    }
    return state;
  }

  @Override
  public void dispose() {
  }

  @TestOnly
  public enum Scope {
    AllTodos,
    ChangeList,
    CurrentFile,
    ScopeBased
  }

  public void initToolWindow(@NotNull ToolWindow toolWindow) {
    // Create panels
    ContentFactory contentFactory = ContentFactory.getInstance();
    Content allTodosContent =
      contentFactory.createContent(null, IdeUICustomization.getInstance().projectMessage("tab.title.project"), false);
    toolWindow.setHelpId("find.todoList");
    myAllTodos = new TodoPanel(this, state.all, false, allTodosContent) {
      @Override
      protected TodoTreeBuilder createTreeBuilder(@NotNull JTree tree,
                                                  @NotNull Project project) {
        AllTodosTreeBuilder builder = createAllTodoBuilder(tree, project);
        builder.init();
        return builder;
      }
    };
    allTodosContent.setComponent(myAllTodos);
    allTodosContent.setPreferredFocusableComponent(myAllTodos.getTree());
    Disposer.register(this, myAllTodos);
    if (toolWindow instanceof ToolWindowEx) {
      DefaultActionGroup group = new DefaultActionGroup() {
        {
          getTemplatePresentation().setText(IdeBundle.messagePointer("group.view.options"));
          setPopup(true);
          add(myAllTodos.createAutoScrollToSourceAction());
          addSeparator();
          addAll(myAllTodos.createGroupByActionGroup());
        }
      };
      toolWindow.setAdditionalGearActions(group);
    }

    Content currentFileTodosContent = contentFactory.createContent(null, IdeBundle.message("title.todo.current.file"), false);
    myCurrentFileTodosPanel = new CurrentFileTodosPanel(this, state.current, currentFileTodosContent) {
      @Override
      protected TodoTreeBuilder createTreeBuilder(@NotNull JTree tree,
                                                  @NotNull Project project) {
        CurrentFileTodosTreeBuilder builder = new CurrentFileTodosTreeBuilder(tree, project);
        builder.init();
        return builder;
      }
    };
    Disposer.register(this, myCurrentFileTodosPanel);
    currentFileTodosContent.setComponent(myCurrentFileTodosPanel);
    currentFileTodosContent.setPreferredFocusableComponent(myCurrentFileTodosPanel.getTree());

    String tabName = myChangesSupport.getTabName(myProject);
    myChangeListTodosContent = contentFactory.createContent(null, tabName, false);
    myChangeListTodosPanel = myChangesSupport.createPanel(this, state.current, myChangeListTodosContent);
    if (myChangeListTodosPanel != null) {
      Disposer.register(this, myChangeListTodosPanel);
      myChangeListTodosContent.setComponent(myChangeListTodosPanel);
      myChangeListTodosContent.setPreferredFocusableComponent(myChangeListTodosPanel.getTree());
    }

    Content scopeBasedTodoContent = contentFactory.createContent(null, LangBundle.message("tab.title.scope.based"), false);
    myScopeBasedTodosPanel = new ScopeBasedTodosPanel(this, state.current, scopeBasedTodoContent);
    Disposer.register(this, myScopeBasedTodosPanel);
    scopeBasedTodoContent.setComponent(myScopeBasedTodosPanel);

    myToolWindow = toolWindow;
    myContentManager = toolWindow.getContentManager();

    myContentManager.addContent(allTodosContent);
    myContentManager.addContent(currentFileTodosContent);
    myContentManager.addContent(scopeBasedTodoContent);

    if (myChangesSupport.isContentVisible(myProject)) {
      myChangesListener.setVisible(true);
      myContentManager.addContent(myChangeListTodosContent);
    }
    for (Content content : myNotAddedContent) {
      myContentManager.addContent(content);
    }

    myChangeListTodosContent.setCloseable(false);
    allTodosContent.setCloseable(false);
    currentFileTodosContent.setCloseable(false);
    scopeBasedTodoContent.setCloseable(false);
    Content content = myContentManager.getContent(state.selectedIndex);
    myContentManager.setSelectedContent(content == null ? allTodosContent : content);

    myPanels.add(myAllTodos);
    if (myChangeListTodosPanel != null) {
      myPanels.add(myChangeListTodosPanel);
    }
    myPanels.add(myCurrentFileTodosPanel);
    myPanels.add(myScopeBasedTodosPanel);

    MyVisibilityListener visibilityListener = new MyVisibilityListener();
    myProject.getMessageBus().connect(this).subscribe(ToolWindowManagerListener.TOPIC, visibilityListener);
    toolWindow.addContentManagerListener(visibilityListener);
  }

  protected @NotNull AllTodosTreeBuilder createAllTodoBuilder(@NotNull JTree tree,
                                                              @NotNull Project project) {
    return new AllTodosTreeBuilder(tree, project);
  }

  private final class MyPropertyChangeListener implements PropertyChangeListener {
    @Override
    public void propertyChange(PropertyChangeEvent e) {
      if (TodoConfiguration.PROP_TODO_PATTERNS.equals(e.getPropertyName()) ||
          TodoConfiguration.PROP_TODO_FILTERS.equals(e.getPropertyName())) {
        updateFilters();
      }
    }

    private void updateFilters() {
      try {
        for (TodoPanel panel : myPanels) {
          panel.updateTodoFilter();
        }
      }
      catch (ProcessCanceledException ignore) {
      }
    }
  }

  private final class MyFileTypeListener implements FileTypeListener {
    @Override
    public void fileTypesChanged(@NotNull FileTypeEvent e) {
      refresh();
    }
  }

  private final class MyVisibilityListener implements ToolWindowManagerListener, ContentManagerListener {
    @Override
    public void stateChanged(@NotNull ToolWindowManager toolWindowManager) {
      visibilityChanged();
    }

    @Override
    public void selectionChanged(@NotNull ContentManagerEvent event) {
      visibilityChanged();
    }
  }

  private void visibilityChanged() {
    if (myProject.isOpen()) {
      PsiDocumentManager.getInstance(myProject).performWhenAllCommitted(
        () -> myPanels.forEach(p -> p.updateVisibility(myToolWindow)));
    }
  }

  public final void refresh(@NotNull List<VirtualFile> files) {
    if (myAllTodos == null) {
      return;
    }

    myPanels.stream()
      .map(TodoPanel::getTreeBuilder)
      .map(TodoTreeBuilder::getCoroutineHelper)
      .forEach(x -> x.scheduleMarkFilesAsDirtyAndUpdateTree(files));
  }

  @VisibleForTesting
  public final @NotNull CompletableFuture<Void> refresh() {
    if (myAllTodos == null) {
      return CompletableFuture.completedFuture(null);
    }

    ReadConstraint inSmartMode = ReadConstraint.Companion.inSmartMode(myProject);
    CompletableFuture<?>[] futures = myPanels.stream()
      .map(TodoPanel::getTreeBuilder)
      .map(TodoTreeBuilder::getCoroutineHelper)
      .map(helper -> helper.scheduleCacheAndTreeUpdate(inSmartMode))
      .toArray(CompletableFuture[]::new);
    return CompletableFuture.allOf(futures);
  }

  public @Nullable Content addCustomTodoView(@NotNull TodoTreeBuilderFactory factory,
                                             @NlsContexts.TabTitle String title,
                                             @NotNull TodoPanelSettings settings) {
    Content content = ContentFactory.getInstance().createContent(null, title, true);
    final TodoPanel panel = myChangesSupport.createPanel(this, settings, content, factory);
    if (panel == null) return null;

    content.setComponent(panel);
    Disposer.register(this, panel);

    if (myContentManager == null) {
      myNotAddedContent.add(content);
    }
    else {
      myContentManager.addContent(content);
    }
    myPanels.add(panel);
    content.setCloseable(true);
    content.setDisposer(new Disposable() {
      @Override
      public void dispose() {
        myPanels.remove(panel);
      }
    });
    return content;
  }
}
