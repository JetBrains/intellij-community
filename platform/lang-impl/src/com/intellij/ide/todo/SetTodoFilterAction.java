/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.ide.IdeBundle;
import com.intellij.ide.todo.configurable.TodoConfigurable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.IconLoader;
import com.intellij.util.Consumer;

import javax.swing.*;

/**
* @author irengrig
*         Date: 2/24/11
*         Time: 3:38 PM
 *         moved from inner class
*/
public class SetTodoFilterAction extends AnAction implements CustomComponentAction {
  private final Project myProject;
  private final TodoPanelSettings myToDoSettings;
  private final Consumer<TodoFilter> myTodoFilterConsumer;

  public SetTodoFilterAction(final Project project, final TodoPanelSettings toDoSettings, final Consumer<TodoFilter> todoFilterConsumer) {
    super(IdeBundle.message("action.filter.todo.items"), null, IconLoader.getIcon("/ant/filter.png"));
    myProject = project;
    myToDoSettings = toDoSettings;
    myTodoFilterConsumer = todoFilterConsumer;
  }

  public void actionPerformed(AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    JComponent button = (JComponent)presentation.getClientProperty("button");
    DefaultActionGroup group = createPopupActionGroup();
    ActionPopupMenu popupMenu = ActionManager.getInstance().createActionPopupMenu(ActionPlaces.TODO_VIEW_TOOLBAR,
                                                                                  group);
    popupMenu.getComponent().show(button, button.getWidth(), 0);
  }

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

  private DefaultActionGroup createPopupActionGroup() {
    TodoFilter[] filters = TodoConfiguration.getInstance().getTodoFilters();
    DefaultActionGroup group = new DefaultActionGroup();
    group.add(new TodoFilterApplier(IdeBundle.message("action.todo.show.all"),
                                    IdeBundle.message("action.description.todo.show.all"), null));
    for (TodoFilter filter : filters) {
      group.add(new TodoFilterApplier(filter.getName(), null, filter));
    }
    group.addSeparator();
    group.add(
      new AnAction(IdeBundle.message("action.todo.edit.filters"),
                   IdeBundle.message("action.todo.edit.filters"), IconLoader.getIcon("/general/ideOptions.png")) {
        public void actionPerformed(AnActionEvent e) {
          final ShowSettingsUtil util = ShowSettingsUtil.getInstance();
          util.editConfigurable(myProject, new TodoConfigurable());
        }
      }
    );
    return group;
  }

  private class TodoFilterApplier extends ToggleAction {
    private final TodoFilter myFilter;

    /**
     * @param text        action's text.
     * @param description action's description.
     * @param filter      filter to be applied. <code>null</code> value means "empty" filter.
     */
    TodoFilterApplier(String text, String description, TodoFilter filter) {
      super(null, description, null);
      getTemplatePresentation().setText(text, false);
      myFilter = filter;
    }

    public void update(AnActionEvent e) {
      super.update(e);
      if (myFilter != null) {
        e.getPresentation().setEnabled(!myFilter.isEmpty());
      }
    }

    public boolean isSelected(AnActionEvent e) {
      return Comparing.equal(myFilter != null ? myFilter.getName() : null, myToDoSettings.getTodoFilterName());
    }

    public void setSelected(AnActionEvent e, boolean state) {
      if (state) {
        myTodoFilterConsumer.consume(myFilter);
        //setTodoFilter(myFilter);
      }
    }
  }
}
