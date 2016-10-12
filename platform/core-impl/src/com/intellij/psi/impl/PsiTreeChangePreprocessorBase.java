/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

package com.intellij.psi.impl;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;

public abstract class PsiTreeChangePreprocessorBase implements PsiTreeChangePreprocessor {
  @NotNull private final Project myProject;

  public PsiTreeChangePreprocessorBase(@NotNull Project project) {
    myProject = project;
  }

  @NotNull
  protected Project getProject() {
    return myProject;
  }

  @Override
  public void treeChanged(@NotNull PsiTreeChangeEventImpl event) {
    boolean changedInsideCodeBlock = false;

    switch (event.getCode()) {
      case BEFORE_CHILDREN_CHANGE:
        if (event.getParent() instanceof PsiFile) {
          changedInsideCodeBlock = true;
          break; // May be caused by fake PSI event from PomTransaction. A real event will anyway follow.
        }

      case CHILDREN_CHANGED:
        if (event.isGenericChange()) {
          return;
        }
        changedInsideCodeBlock = isInsideCodeBlock(event.getParent());
        break;

      case BEFORE_CHILD_ADDITION:
      case BEFORE_CHILD_REMOVAL:
      case CHILD_ADDED:
      case CHILD_REMOVED:
      case BEFORE_CHILD_REPLACEMENT:
      case CHILD_REPLACED :
        changedInsideCodeBlock = isInsideCodeBlock(event.getParent()) &&
                                 isInsideCodeBlock(event.getChild()) &&
                                 isInsideCodeBlock(event.getOldChild()) &&
                                 isInsideCodeBlock(event.getNewChild());
        break;

      case BEFORE_PROPERTY_CHANGE:
      case PROPERTY_CHANGED:
        changedInsideCodeBlock = false;
        break;

      case BEFORE_CHILD_MOVEMENT:
      case CHILD_MOVED:
        changedInsideCodeBlock = isInsideCodeBlock(event.getOldParent()) && 
                                 isInsideCodeBlock(event.getNewParent()) && 
                                 isInsideCodeBlock(event.getChild());
        break;
    }

    if (!changedInsideCodeBlock) {
      processOutOfCodeBlockModification(event);
    }
  }

  @NotNull
  private PsiModificationTrackerImpl getModificationTracker() {
    return (PsiModificationTrackerImpl)PsiManager.getInstance(myProject).getModificationTracker();
  }

  protected void processOutOfCodeBlockModification(final PsiTreeChangeEventImpl event) {
    getModificationTracker().incOutOfCodeBlockModificationCounter();
  }

  protected abstract boolean isInsideCodeBlock(PsiElement element);
}
