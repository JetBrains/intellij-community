// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.todo;

import com.intellij.ConfigurableFactory;
import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsActions;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
* @author irengrig
*/
public class SetTodoFilterAction extends ActionGroup implements DumbAware {
  private final Project myProject;
  private final TodoPanelSettings myToDoSettings;
  private final Consumer<? super TodoFilter> myTodoFilterConsumer;

  public SetTodoFilterAction(@NotNull Project project,
                             @NotNull TodoPanelSettings toDoSettings,
                             @NotNull Consumer<? super TodoFilter> todoFilterConsumer) {
    super(IdeBundle.message("action.filter.todo.items"), null, AllIcons.General.Filter);
    setPopup(true);
    myProject = project;
    myToDoSettings = toDoSettings;
    myTodoFilterConsumer = todoFilterConsumer;
  }

  @Override
  public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
    return createPopupActionGroup(myProject, myToDoSettings, myTodoFilterConsumer).getChildren(e);
  }

  public static DefaultActionGroup createPopupActionGroup(@NotNull Project project,
                                                          @NotNull TodoPanelSettings settings,
                                                          @NotNull Consumer<? super TodoFilter> todoFilterConsumer) {
    TodoFilter[] filters = TodoConfiguration.getInstance().getTodoFilters();
    DefaultActionGroup group = new DefaultActionGroup();
    group.add(new TodoFilterApplier(IdeBundle.message("action.todo.show.all"),
                                    IdeBundle.message("action.description.todo.show.all"), null, settings, todoFilterConsumer));
    for (TodoFilter filter : filters) {
      group.add(new TodoFilterApplier(filter.getName(), null, filter, settings, todoFilterConsumer));
    }
    group.addSeparator();
    group.add(new DumbAwareAction(IdeBundle.messagePointer("action.todo.edit.filters"),
                                  IdeBundle.messagePointer("action.todo.edit.filters.description"), AllIcons.General.Settings) {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          final ShowSettingsUtil util = ShowSettingsUtil.getInstance();
          util.editConfigurable(project, ConfigurableFactory.Companion.getInstance().getTodoConfigurable(project));
        }
      }
    );
    return group;
  }

  private static class TodoFilterApplier extends ToggleAction implements DumbAware {
    private final TodoFilter myFilter;
    private final TodoPanelSettings mySettings;
    private final Consumer<? super TodoFilter> myTodoFilterConsumer;

    /**
     * @param text        action's text.
     * @param description action's description.
     * @param filter      filter to be applied. {@code null} value means "empty" filter.
     * @param settings
     * @param todoFilterConsumer
     */
    TodoFilterApplier(@NlsActions.ActionText String text,
                      @NlsActions.ActionDescription String description,
                      TodoFilter filter,
                      TodoPanelSettings settings,
                      Consumer<? super TodoFilter> todoFilterConsumer) {
      super(null, description, null);
      mySettings = settings;
      myTodoFilterConsumer = todoFilterConsumer;
      getTemplatePresentation().setText(text, false);
      myFilter = filter;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      super.update(e);
      if (myFilter != null) {
        e.getPresentation().setEnabled(!myFilter.isEmpty());
      }
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      String arg1 = myFilter != null ? myFilter.getName() : null;
      return Objects.equals(arg1, mySettings.todoFilterName);
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
      if (state) {
        myTodoFilterConsumer.consume(myFilter);
      }
    }
  }
}
