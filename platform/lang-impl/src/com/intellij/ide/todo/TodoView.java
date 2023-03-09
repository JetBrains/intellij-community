// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ex.ToolWindowEx;
import com.intellij.ui.IdeUICustomization;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.OptionTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.annotations.VisibleForTesting;

import javax.swing.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@State(name = "TodoView", storages = @Storage(StoragePathMacros.PRODUCT_WORKSPACE_FILE))
public class TodoView implements PersistentStateComponent<TodoView.State>, Disposable {

  private final @NotNull Project myProject;

  private ContentManager myContentManager;
  private TodoPanel myAllTodos;
  @Nullable
  private TodoPanel myChangeListTodosPanel;
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
    state.all.isAutoScrollToSource = true;

    state.current.isAutoScrollToSource = true;

    MessageBusConnection connection = project.getMessageBus().connect(this);
    connection.subscribe(TodoConfiguration.PROPERTY_CHANGE, new MyPropertyChangeListener());
    connection.subscribe(FileTypeManager.TOPIC, new MyFileTypeListener());

    myChangesSupport = project.getService(TodoViewChangesSupport.class);
    myChangesListener = myChangesSupport.installListener(project, connection,
                                                         () -> myContentManager,
                                                         () -> myChangeListTodosContent);
  }

  static class State {
    @Attribute("selected-index")
    public int selectedIndex;

    @OptionTag(value = "selected-file", nameAttribute = "id", tag = "todo-panel", valueAttribute = "")
    public TodoPanelSettings current = new TodoPanelSettings();

    @OptionTag(value = "all", nameAttribute = "id", tag = "todo-panel", valueAttribute = "")
    public TodoPanelSettings all = new TodoPanelSettings();

    @OptionTag(value = "default-changelist", nameAttribute = "id", tag = "todo-panel", valueAttribute = "")
    public TodoPanelSettings changeList = new TodoPanelSettings();
  }

  @Override
  public void loadState(@NotNull State state) {
    this.state = state;
  }

  @Override
  public State getState() {
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
    myAllTodos = new TodoPanel(myProject, state.all, false, allTodosContent) {
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
    myCurrentFileTodosPanel = new CurrentFileTodosPanel(myProject, state.current, currentFileTodosContent) {
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
    myChangeListTodosPanel = myChangesSupport.createPanel(myProject, state.current, myChangeListTodosContent);
    if (myChangeListTodosPanel != null) {
      Disposer.register(this, myChangeListTodosPanel);
      myChangeListTodosContent.setComponent(myChangeListTodosPanel);
      myChangeListTodosContent.setPreferredFocusableComponent(myChangeListTodosPanel.getTree());
    }

    Content scopeBasedTodoContent = contentFactory.createContent(null, LangBundle.message("tab.title.scope.based"), false);
    myScopeBasedTodosPanel = new ScopeBasedTodosPanel(myProject, state.current, scopeBasedTodoContent);
    Disposer.register(this, myScopeBasedTodosPanel);
    scopeBasedTodoContent.setComponent(myScopeBasedTodosPanel);

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

  @Nullable
  public Content addCustomTodoView(@NotNull TodoTreeBuilderFactory factory,
                                   @NlsContexts.TabTitle String title,
                                   @NotNull TodoPanelSettings settings) {
    Content content = ContentFactory.getInstance().createContent(null, title, true);
    final TodoPanel panel = myChangesSupport.createPanel(myProject, settings, content, factory);
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
