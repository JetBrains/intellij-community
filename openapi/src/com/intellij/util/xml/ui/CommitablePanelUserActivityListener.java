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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;

/**
 * User: Sergey.Vasiliev
 */
public class CommitablePanelUserActivityListener implements UserActivityListener {
  private boolean myResetting;

  CommittablePanel myPanel;

  private MergingUpdateQueue myUpdateQueue;

  public CommitablePanelUserActivityListener() {
  }

  public CommitablePanelUserActivityListener(final CommittablePanel panel) {
    myPanel = panel;
    myUpdateQueue = new MergingUpdateQueue("DomCommitableUpdateQueue", 239, true, myPanel.getComponent());
  }

  final public void stateChanged() {
    if (!myResetting) {
      myUpdateQueue.queue(new Update("DoActivityUpdate") {
        public void run() {
          doActivity();
        }
      });
    }
  }

  protected void doActivity() {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {

        myResetting = true;
        try {
          applyChanges();
        } finally {
          myResetting = false;
        }
        doAfterApply();
      }
    });
  }

  protected void applyChanges() {
    if (myPanel != null) {
      myPanel.commit();
    }
  }

  protected void doAfterApply() {
  }
}
