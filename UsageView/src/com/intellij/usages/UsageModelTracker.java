/*
 * Copyright 2000-2005 JetBrains s.r.o.
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

import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Dec 16, 2004
 * Time: 4:46:14 PM
 * To change this template use File | Settings | File Templates.
 */
public class UsageModelTracker {
  private PsiTreeChangeListener myPsiListener;

  public interface UsageModelTrackerListener {
    void modelChanged(boolean isPropertyChange);
  }

  private Project myProject;
  private List<UsageModelTrackerListener> myListeners = new ArrayList<UsageModelTrackerListener>();

  public UsageModelTracker(Project project) {
    myProject = project;
    myPsiListener = new PsiTreeChangeAdapter() {
      public void childAdded(PsiTreeChangeEvent event) {
        if (!(event.getFile() instanceof PsiCodeFragment)) {
          fireModelChanged(false);
        }
      }

      public void childRemoved(PsiTreeChangeEvent event) {
        if (!(event.getFile() instanceof PsiCodeFragment)) {
          fireModelChanged(false);
        }
      }

      public void childReplaced(PsiTreeChangeEvent event) {
        if (!(event.getFile() instanceof PsiCodeFragment)) {
          fireModelChanged(false);
        }
      }

      public void childrenChanged(PsiTreeChangeEvent event) {
        if (!(event.getFile() instanceof PsiCodeFragment)) {
          fireModelChanged(false);
        }
      }

      public void childMoved(PsiTreeChangeEvent event) {
        if (!(event.getFile() instanceof PsiCodeFragment)) {
          fireModelChanged(false);
        }
      }

      public void propertyChanged(PsiTreeChangeEvent event) {
        if (!(event.getFile() instanceof PsiCodeFragment)) {
          fireModelChanged(true);
        }
      }
    };
    PsiManager.getInstance(project).addPsiTreeChangeListener(myPsiListener);
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

  private void fireModelChanged(final boolean isPropertyChange) {
    final UsageModelTrackerListener[] listeners = myListeners.toArray(new UsageModelTrackerListener[myListeners.size()]);
    for (UsageModelTrackerListener listener : listeners) {
      listener.modelChanged(isPropertyChange);
    }
  }
}
