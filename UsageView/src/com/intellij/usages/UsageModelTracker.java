/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
package com.intellij.usages;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author max
 */
public class UsageModelTracker {
  private final PsiTreeChangeListener myPsiListener;

  public interface UsageModelTrackerListener {
    void modelChanged(boolean isPropertyChange);
  }

  private final Project myProject;
  private final List<UsageModelTrackerListener> myListeners = new CopyOnWriteArrayList<UsageModelTrackerListener>();

  public UsageModelTracker(Project project) {
    myProject = project;
    myPsiListener = new PsiTreeChangeAdapter() {
      public void childAdded(PsiTreeChangeEvent event) {
        doFire(event, false);
      }

      public void childRemoved(PsiTreeChangeEvent event) {
        doFire(event, false);
      }

      public void childReplaced(PsiTreeChangeEvent event) {
        doFire(event, false);
      }

      public void childrenChanged(PsiTreeChangeEvent event) {
        doFire(event, false);
      }

      public void childMoved(PsiTreeChangeEvent event) {
        doFire(event, false);
      }

      public void propertyChanged(PsiTreeChangeEvent event) {
        doFire(event, true);
      }
    };
    PsiManager.getInstance(project).addPsiTreeChangeListener(myPsiListener);
  }

  private void doFire(final PsiTreeChangeEvent event, boolean propertyChange) {
    if (!(event.getFile() instanceof PsiCodeFragment)) {
      for (UsageModelTrackerListener listener : myListeners) {
        listener.modelChanged(propertyChange);
      }
    }
  }

  public void dispose() {
    PsiManager.getInstance(myProject).removePsiTreeChangeListener(myPsiListener);
  }

  public void addListener(UsageModelTrackerListener listener) {
    myListeners.add(listener);
  }

  public void removeListener(UsageModelTrackerListener listener) {
    myListeners.remove(listener);
  }
}
