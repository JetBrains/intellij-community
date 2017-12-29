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
package com.intellij.ide.todo;

import com.intellij.ConfigurableFactory;
import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.util.Consumer;

import javax.swing.*;

/**
* @author irengrig
 *         moved from inner class
*/
public class SetTodoFilterAction extends AnAction implements CustomComponentAction {
  private final Project myProject;
  private final TodoPanelSettings myToDoSettings;
  private final Consumer<TodoFilter> myTodoFilterConsumer;

  public SetTodoFilterAction(final Project project, final TodoPanelSettings toDoSettings, final Consumer<TodoFilter> todoFilterConsumer) {
    super(IdeBundle.message("action.filter.todo.items"), null, AllIcons.General.Filter);
    myProject = project;
    myToDoSettings = toDoSettings;
    myTodoFilterConsumer = todoFilterConsumer;
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    JComponent button = (JComponent)presentation.getClientProperty("button");
    DefaultActionGroup group = createPopupActionGroup(myProject, myToDoSettings, myTodoFilterConsumer);
    ActionPopupMenu popupMenu = ActionManager.getInstance().createActionPopupMenu(ActionPlaces.TODO_VIEW_TOOLBAR,
                                                                                  group);
    popupMenu.getComponent().show(button, button.getWidth(), 0);
  }

  @Override
  public JComponent createCustomComponent(Presentation presentation) {
    ActionButton button = new ActionButton(
      this,
      presentation,
      ActionPlaces.TODO_VIEW_TOOLBAR,
      ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE
    );
    presentation.putClientProperty("button", button);
    return button;
  }

  public static DefaultActionGroup createPopupActionGroup(final Project project,
                                                          final TodoPanelSettings settings,
                                                          Consumer<TodoFilter> todoFilterConsumer) {
    TodoFilter[] filters = TodoConfiguration.getInstance().getTodoFilters();
    DefaultActionGroup group = new DefaultActionGroup();
    group.add(new TodoFilterApplier(IdeBundle.message("action.todo.show.all"),
                                    IdeBundle.message("action.description.todo.show.all"), null, settings, todoFilterConsumer));
    for (TodoFilter filter : filters) {
      group.add(new TodoFilterApplier(filter.getName(), null, filter, settings, todoFilterConsumer));
    }
    group.addSeparator();
    group.add(
      new AnAction(IdeBundle.message("action.todo.edit.filters"),
                   IdeBundle.message("action.todo.edit.filters"), AllIcons.General.Settings) {
        @Override
        public void actionPerformed(AnActionEvent e) {
          final ShowSettingsUtil util = ShowSettingsUtil.getInstance();
          util.editConfigurable(project, ConfigurableFactory.Companion.getInstance().getTodoConfigurable(project));
        }
      }
    );
    return group;
  }

  private static class TodoFilterApplier extends ToggleAction {
    private final TodoFilter myFilter;
    private final TodoPanelSettings mySettings;
    private final Consumer<TodoFilter> myTodoFilterConsumer;

    /**
     * @param text        action's text.
     * @param description action's description.
     * @param filter      filter to be applied. {@code null} value means "empty" filter.
     * @param settings
     * @param todoFilterConsumer
     */
    TodoFilterApplier(String text,
                      String description,
                      TodoFilter filter,
                      TodoPanelSettings settings,
                      Consumer<TodoFilter> todoFilterConsumer) {
      super(null, description, null);
      mySettings = settings;
      myTodoFilterConsumer = todoFilterConsumer;
      getTemplatePresentation().setText(text, false);
      myFilter = filter;
    }

    @Override
    public void update(AnActionEvent e) {
      super.update(e);
      if (myFilter != null) {
        e.getPresentation().setEnabled(!myFilter.isEmpty());
      }
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return Comparing.equal(myFilter != null ? myFilter.getName() : null, mySettings.todoFilterName);
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      if (state) {
        myTodoFilterConsumer.consume(myFilter);
        //setTodoFilter(myFilter);
      }
    }
  }
}
