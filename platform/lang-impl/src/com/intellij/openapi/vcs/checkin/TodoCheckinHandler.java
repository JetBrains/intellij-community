/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.checkin;

import com.intellij.CommonBundle;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.todo.*;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionPopupMenu;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.CheckinProjectPanel;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.CommitExecutor;
import com.intellij.openapi.vcs.ui.RefreshableOnComponent;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.ui.components.labels.LinkListener;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.Consumer;
import com.intellij.util.PairConsumer;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.util.Collection;

/**
 * @author irengrig
 *         Date: 2/17/11
 *         Time: 5:54 PM
 */
public class TodoCheckinHandler extends CheckinHandler {
  private final Project myProject;
  private final CheckinProjectPanel myCheckinProjectPanel;
  private final VcsConfiguration myConfiguration;
  private TodoFilter myTodoFilter;
  private final static Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.checkin.TodoCheckinHandler");

  public TodoCheckinHandler(CheckinProjectPanel checkinProjectPanel) {
    myProject = checkinProjectPanel.getProject();
    myCheckinProjectPanel = checkinProjectPanel;
    myConfiguration = VcsConfiguration.getInstance(myProject);
  }

  @Override
  public RefreshableOnComponent getBeforeCheckinConfigurationPanel() {
    final JCheckBox checkBox = new JCheckBox(VcsBundle.message("before.checkin.new.todo.check", ""));
    return new RefreshableOnComponent() {
      @Override
      public JComponent getComponent() {
        JPanel panel = new JPanel(new BorderLayout(4, 0));
        panel.add(checkBox, BorderLayout.WEST);
        setFilterText(myConfiguration.myTodoPanelSettings.getTodoFilterName());
        if (myConfiguration.myTodoPanelSettings.getTodoFilterName() != null) {
          myTodoFilter = TodoConfiguration.getInstance().getTodoFilter(myConfiguration.myTodoPanelSettings.getTodoFilterName());
        }

        final Consumer<TodoFilter> consumer = new Consumer<TodoFilter>() {
          @Override
          public void consume(TodoFilter todoFilter) {
            myTodoFilter = todoFilter;
            final String name = todoFilter == null ? null : todoFilter.getName();
            myConfiguration.myTodoPanelSettings.setTodoFilterName(name);
            setFilterText(name);
          }
        };
        final LinkLabel linkLabel = new LinkLabel("Configure", null);
        linkLabel.setListener(new LinkListener() {
          @Override
          public void linkSelected(LinkLabel aSource, Object aLinkData) {
            DefaultActionGroup group = SetTodoFilterAction.createPopupActionGroup(myProject, myConfiguration.myTodoPanelSettings, consumer);
            ActionPopupMenu popupMenu = ActionManager.getInstance().createActionPopupMenu(ActionPlaces.TODO_VIEW_TOOLBAR, group);
            popupMenu.getComponent().show(linkLabel, 0, linkLabel.getHeight());
          }
        }, null);
        panel.add(linkLabel, BorderLayout.CENTER);

        refreshEnable(checkBox);
        return panel;
      }

      private void setFilterText(final String filterName) {
        if (filterName == null) {
          checkBox.setText(VcsBundle.message("before.checkin.new.todo.check", IdeBundle.message("action.todo.show.all")));
        } else {
          checkBox.setText(VcsBundle.message("before.checkin.new.todo.check", "Filter: " + filterName));
        }
      }

      @Override
      public void refresh() {
      }

      @Override
      public void saveState() {
        myConfiguration.CHECK_NEW_TODO = checkBox.isSelected();
      }

      @Override
      public void restoreState() {
        checkBox.setSelected(myConfiguration.CHECK_NEW_TODO);
      }
    };
  }

  private void refreshEnable(JCheckBox checkBox) {
    if (DumbService.getInstance(myProject).isDumb()) {
      checkBox.setEnabled(false);
      checkBox.setToolTipText("TODO check is impossible until indices are up-to-date");
    } else {
      checkBox.setEnabled(true);
      checkBox.setToolTipText("");
    }
  }

  @Override
  public ReturnResult beforeCheckin(@Nullable CommitExecutor executor, PairConsumer<Object, Object> additionalDataConsumer) {
    if (! myConfiguration.CHECK_NEW_TODO) return ReturnResult.COMMIT;
    if (DumbService.getInstance(myProject).isDumb()) {
      final String todoName = VcsBundle.message("before.checkin.new.todo.check.title");
      if (Messages.showOkCancelDialog(myProject,
                              todoName +
                              " can't be performed while " + ApplicationNamesInfo.getInstance().getFullProductName() + " updates the indices in background.\n" +
                              "You can commit the changes without running checks, or you can wait until indices are built.",
                              todoName + " is not possible right now",
                              "&Wait", "&Commit", null) == DialogWrapper.OK_EXIT_CODE) {
        return ReturnResult.CANCEL;
      }
      return ReturnResult.COMMIT;
    }
    final Collection<Change> changes = myCheckinProjectPanel.getSelectedChanges();
    final TodoCheckinHandlerWorker worker = new TodoCheckinHandlerWorker(myProject, changes, myTodoFilter, true);

    final Runnable runnable = new Runnable() {
      @Override
      public void run() {
        worker.execute();
        }
    };
    final boolean completed = ProgressManager.getInstance().runProcessWithProgressSynchronously(runnable, "Looking for new and edited TODO items...", true, myProject);
    if (completed && (worker.getAddedOrEditedTodos().isEmpty() && worker.getInChangedTodos().isEmpty() &&
      worker.getSkipped().isEmpty())) return ReturnResult.COMMIT;
    if (! completed) return ReturnResult.CANCEL;
    return showResults(worker, executor);
  }

  private ReturnResult showResults(final TodoCheckinHandlerWorker worker, CommitExecutor executor) {
    String commitButtonText = executor != null ? executor.getActionText() : myCheckinProjectPanel.getCommitActionName();
    if (commitButtonText.endsWith("...")) {
      commitButtonText = commitButtonText.substring(0, commitButtonText.length()-3);
    }

    final String text = createMessage(worker);
    final String[] buttons;
    final boolean thereAreTodoFound = worker.getAddedOrEditedTodos().size() + worker.getInChangedTodos().size() > 0;
    int commitOption;
    if (thereAreTodoFound) {
      buttons = new String[]{VcsBundle.message("todo.in.new.review.button"), commitButtonText, CommonBundle.getCancelButtonText()};
      commitOption = 1;
    }
    else {
      buttons = new String[]{commitButtonText, CommonBundle.getCancelButtonText()};
      commitOption = 0;
    }
    final int answer = Messages.showDialog(myProject, text, "TODO", null, buttons, 0, 1, UIUtil.getWarningIcon());
    if (thereAreTodoFound && answer == Messages.OK) {
      showTodo(worker);
      return ReturnResult.CLOSE_WINDOW;
    }
    if (answer == commitOption) {
      return ReturnResult.COMMIT;
    }
    return ReturnResult.CANCEL;
  }

  private void showTodo(final TodoCheckinHandlerWorker worker) {
    TodoView todoView = ServiceManager.getService(myProject, TodoView.class);
    final String title = "For commit (" + DateFormatUtil.formatDateTime(System.currentTimeMillis()) + ")";
    todoView.addCustomTodoView(new TodoTreeBuilderFactory() {
      @Override
      public TodoTreeBuilder createTreeBuilder(JTree tree, DefaultTreeModel treeModel, Project project) {
        return new CustomChangelistTodosTreeBuilder(tree, treeModel, myProject, title, worker.inOneList());
      }
    }, title, new TodoPanelSettings(myConfiguration.myTodoPanelSettings));

    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        final ToolWindowManager manager = ToolWindowManager.getInstance(myProject);
        if (manager != null) {
          final ToolWindow window = manager.getToolWindow("TODO");
          if (window != null) {
            window.show(new Runnable() {
              @Override
              public void run() {
                final ContentManager cm = window.getContentManager();
                final Content[] contents = cm.getContents();
                if (contents.length > 0) {
                  cm.setSelectedContent(contents[contents.length - 1], true);
                }
              }
            });
          }
        }
      }
    }, ModalityState.NON_MODAL, myProject.getDisposed());
  }

  private static String createMessage(TodoCheckinHandlerWorker worker) {
    final int added = worker.getAddedOrEditedTodos().size();
    final int changed = worker.getInChangedTodos().size();
    final int skipped = worker.getSkipped().size();
    if (added == 0 && changed == 0) {
      return VcsBundle.message("todo.handler.only.skipped", skipped);
    } else if (changed == 0) {
      return VcsBundle.message("todo.handler.only.added", added, skipped);
    } else if (added == 0) {
      return VcsBundle.message("todo.handler.only.in.changed", changed, skipped);
    } else {
      return VcsBundle.message("todo.handler.only.both", added, changed, skipped);
    }
  }
}
