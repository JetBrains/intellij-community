/*
 * User: anna
 * Date: 27-Jul-2007
 */
package com.intellij.ide.todo;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.ChangeListAdapter;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.ui.content.Content;
import com.intellij.util.Alarm;

import java.util.Collection;

public abstract class ChangeListTodosPanel extends TodoPanel{
  private final MyChangeListManagerListener myChangeListManagerListener = new MyChangeListManagerListener();
  private final Alarm myAlarm;

  public ChangeListTodosPanel(Project project,TodoPanelSettings settings, Content content){
    super(project,settings,false,content);
    ChangeListManager changeListManager = ChangeListManager.getInstance(project);
    changeListManager.addChangeListListener(myChangeListManagerListener);
    myAlarm = new Alarm(Alarm.ThreadToUse.SHARED_THREAD, project);
  }

  void dispose(){
    ChangeListManager.getInstance(myProject).removeChangeListListener(myChangeListManagerListener);
    super.dispose();
  }

  private final class MyChangeListManagerListener extends ChangeListAdapter {
    public void defaultListChanged(final ChangeList oldDefaultList, final ChangeList newDefaultList) {
      rebuild();
      setDisplayName(IdeBundle.message("changelist.todo.title", newDefaultList.getName()));
    }

    public void changeListRenamed(final ChangeList list, final String oldName) {
      setDisplayName(IdeBundle.message("changelist.todo.title", list.getName()));
    }

    public void changesMoved(final Collection<Change> changes, final ChangeList fromList, final ChangeList toList) {
      rebuild();
    }

    private void rebuild() {
      myAlarm.cancelAllRequests();
      myAlarm.addRequest(new Runnable() {
        public void run() {
          ApplicationManager.getApplication().runReadAction(new Runnable() {
            public void run() {
              myTodoTreeBuilder.rebuildCache();
            }
          });
          updateTree();
        }
      }, 300);
    }
  }
}