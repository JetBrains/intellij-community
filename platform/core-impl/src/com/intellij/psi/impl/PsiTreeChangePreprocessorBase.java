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

import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class PsiTreeChangePreprocessorBase implements PsiTreeChangePreprocessor {

  protected final PsiManager myPsiManager;

  public PsiTreeChangePreprocessorBase(@NotNull PsiManager psiManager) {
    myPsiManager = psiManager;
  }

  protected abstract boolean acceptsEvent(@NotNull PsiTreeChangeEventImpl event);

  /**
   * Shall return true <em>if and only if</em> the element is considered to be "out of code block"
   * (the exact meaning is language- or technology-specific); otherwise false.
   *
   * @see PsiModificationTrackerImpl#getOutOfCodeBlockModificationCount()
   */
  protected abstract boolean isOutOfCodeBlock(@NotNull PsiElement element);

  protected boolean isOutOfCodeBlock(@NotNull PsiFileSystemItem file) {
    return true;
  }

  private boolean outOfCodeBlock(@Nullable PsiElement element) {
    if (element == null || !element.isValid()) return false;
    if (element instanceof PsiDirectory) return false; // handled by PsiModificationTrackerImpl#treeChanged()
    if (element instanceof PsiFileSystemItem) return isOutOfCodeBlock((PsiFileSystemItem)element);
    return isOutOfCodeBlock(element);
  }

  @Override
  public final void treeChanged(@NotNull PsiTreeChangeEventImpl event) {
    if (!PsiModificationTrackerImpl.canAffectPsi(event)) {
      return;
    }
    if (!acceptsEvent(event)) {
      return;
    }
    onTreeChanged(event);
  }

  protected void onTreeChanged(@NotNull PsiTreeChangeEventImpl event) {
    boolean outOfCodeBlock;

    switch (event.getCode()) {
      case BEFORE_PROPERTY_CHANGE:
      case BEFORE_CHILD_MOVEMENT:
      case BEFORE_CHILD_ADDITION:
      case BEFORE_CHILD_REMOVAL:
      case BEFORE_CHILD_REPLACEMENT:
        outOfCodeBlock = false;
        break;

      case BEFORE_CHILDREN_CHANGE:
      case CHILDREN_CHANGED:
        if (event.isGenericChange()) {
          return;
        }
        outOfCodeBlock = outOfCodeBlock(event.getParent());
        break;

      case CHILD_ADDED:
      case CHILD_REMOVED:
      case CHILD_REPLACED:
        outOfCodeBlock = outOfCodeBlock(event.getParent()) ||
                         outOfCodeBlock(event.getChild()) ||
                         outOfCodeBlock(event.getOldChild()) ||
                         outOfCodeBlock(event.getNewChild());
        break;

      case PROPERTY_CHANGED:
        outOfCodeBlock = true;
        break;

      case CHILD_MOVED:
        outOfCodeBlock = outOfCodeBlock(event.getOldParent()) ||
                         outOfCodeBlock(event.getNewParent()) ||
                         outOfCodeBlock(event.getChild());
        break;
      default:
        outOfCodeBlock = true;
        break;
    }

    if (outOfCodeBlock) {
      onOutOfCodeBlockModification(event);
      doIncOutOfCodeBlockCounter();
    }
  }

  protected void onOutOfCodeBlockModification(@NotNull PsiTreeChangeEventImpl event) {
  }

  protected void doIncOutOfCodeBlockCounter() {
    ((PsiModificationTrackerImpl)myPsiManager.getModificationTracker()).incOutOfCodeBlockModificationCounter();
  }
}
