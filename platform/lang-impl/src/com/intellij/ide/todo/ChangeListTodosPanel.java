/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

/*
 * User: anna
 * Date: 27-Jul-2007
 */
package com.intellij.ide.todo;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.ChangeListAdapter;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.Disposable;
import com.intellij.ui.AppUIUtil;
import com.intellij.ui.content.Content;
import com.intellij.util.Alarm;

import java.util.Collection;

public abstract class ChangeListTodosPanel extends TodoPanel{
  private final Alarm myAlarm;

  public ChangeListTodosPanel(Project project,TodoPanelSettings settings, Content content){
    super(project,settings,false,content);
    ChangeListManager changeListManager = ChangeListManager.getInstance(project);
    final MyChangeListManagerListener myChangeListManagerListener = new MyChangeListManagerListener();
    changeListManager.addChangeListListener(myChangeListManagerListener);
    Disposer.register(this, new Disposable() {
      @Override
      public void dispose() {
        ChangeListManager.getInstance(myProject).removeChangeListListener(myChangeListManagerListener);
      }
    });
    myAlarm = new Alarm(Alarm.ThreadToUse.SHARED_THREAD, project);
  }

  private final class MyChangeListManagerListener extends ChangeListAdapter {
    @Override
    public void defaultListChanged(final ChangeList oldDefaultList, final ChangeList newDefaultList) {
      rebuildWithAlarm(myAlarm);
      AppUIUtil.invokeOnEdt(new Runnable() {
        @Override
        public void run() {
          setDisplayName(IdeBundle.message("changelist.todo.title", newDefaultList.getName()));
        }
      });
    }

    @Override
    public void changeListRenamed(final ChangeList list, final String oldName) {
      setDisplayName(IdeBundle.message("changelist.todo.title", list.getName()));
    }

    @Override
    public void changesMoved(final Collection<Change> changes, final ChangeList fromList, final ChangeList toList) {
      rebuildWithAlarm(myAlarm);
    }
  }
}