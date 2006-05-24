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

/**
 * User: Sergey.Vasiliev
 */
public class CommitablePanelUserActivityListener implements UserActivityListener, Disposable {
  private final Committable myPanel;
  private final Alarm myAlarm = new Alarm();
  private boolean myApplying;

  public CommitablePanelUserActivityListener() {
    this(null);
  }

  public CommitablePanelUserActivityListener(final Committable panel) {
    myPanel = panel;
  }

  final public void stateChanged() {
    if (myApplying) return;
    cancelAllRequests();
    myAlarm.addRequest(new Runnable() {
      public void run() {
        myApplying = true;
        try {
          applyChanges();
        }
        finally {
          myApplying = false;
        }
      }
    }, 239);
  }

  protected void applyChanges() {
    if (myPanel != null) {
      myPanel.commit();
    }
  }

  public final void cancelAllRequests() {
    myAlarm.cancelAllRequests();
  }

  public void dispose() {
    cancelAllRequests();
  }
}
