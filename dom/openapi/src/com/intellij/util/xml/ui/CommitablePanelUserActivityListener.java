/*
 * Copyright 2000-2006 JetBrains s.r.o.
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
 *
 */

package com.intellij.util.xml.ui;

import com.intellij.ui.UserActivityListener;
import com.intellij.util.Alarm;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.ProgressIndicator;

/**
 * User: Sergey.Vasiliev
 */
public class CommitablePanelUserActivityListener implements UserActivityListener, Disposable {
  private final Committable myPanel;
  private final Project myProject;
  private final Alarm myAlarm = new Alarm();
  private boolean myApplying;

  public CommitablePanelUserActivityListener(Project project) {
    this(null, project);
  }

  public CommitablePanelUserActivityListener(final Committable panel, Project project) {
    myPanel = panel;
    myProject = project;
  }

  final public void stateChanged() {
    if (myApplying) return;
    cancel();
    cancelAllRequests();
    myAlarm.addRequest(new Runnable() {
      public void run() {
        myApplying = true;
        cancel();
        try {
          applyChanges();
        }
        finally {
          myApplying = false;
        }
      }
    }, 717);
  }

  private static void cancel() {
    final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    if (indicator != null) {
      indicator.cancel();
    }
  }

  protected void applyChanges() {
    if (myPanel != null) {
      commit(myPanel);
    }
  }

  protected final void commit(final Committable panel) {
    getProject().getComponent(CommittableUtil.class).commit(panel);
  }

  protected final Project getProject() {
    return myProject;
  }

  public final boolean isWaiting() {
    return myAlarm.getActiveRequestCount() > 0;
  }

  public final void cancelAllRequests() {
    myAlarm.cancelAllRequests();
  }

  public void dispose() {
    cancelAllRequests();
  }
}
