/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
 * Created by IntelliJ IDEA.
 * User: mike
 * Date: Jul 18, 2002
 * Time: 5:57:57 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.psi.impl;

import com.intellij.ProjectTopics;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.messages.MessageBus;

public class PsiModificationTrackerImpl implements PsiModificationTracker, PsiTreeChangePreprocessor {
  private volatile long myModificationCount = 0;
  private volatile long myOutOfCodeBlockModificationCount = 0;
  private volatile long myJavaStructureModificationCount = 0;
  private volatile long myAnnotationModificationCount = 0;
  private final Listener myPublisher;

  public PsiModificationTrackerImpl(Project project) {
    final MessageBus bus = project.getMessageBus();
    myPublisher = bus.syncPublisher(ProjectTopics.MODIFICATION_TRACKER);
    bus.connect().subscribe(DumbService.DUMB_MODE, new DumbService.DumbModeListener() {

      public void enteredDumbMode() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            incCounter();
          }
        });
      }

      public void exitDumbMode() {
        enteredDumbMode();
      }
    });
  }

  public void incCounter(){
    myModificationCount++;
    myJavaStructureModificationCount++;
    incOutOfCodeBlockModificationCounter();
  }

  public void incOutOfCodeBlockModificationCounter() {
    myOutOfCodeBlockModificationCount++;
    myPublisher.modificationCountChanged();
  }

  public void incAnnotationModificationCounter() {
    myAnnotationModificationCount++;
  }

  public void treeChanged(PsiTreeChangeEventImpl event) {
    myModificationCount++;
    if (event.getParent() instanceof PsiDirectory) {
      incOutOfCodeBlockModificationCounter();
    }

    myPublisher.modificationCountChanged();
  }

  public long getModificationCount() {
    return myModificationCount;
  }

  public long getOutOfCodeBlockModificationCount() {
    return myOutOfCodeBlockModificationCount;
  }

  public long getJavaStructureModificationCount() {
    return myJavaStructureModificationCount;
  }

  public long getAnnotationModificationCount() {
    return myAnnotationModificationCount;
  }
}
