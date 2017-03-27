/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.openapi.util.Conditions;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiTreeChangeEventImpl.PsiEventType;
import com.intellij.psi.impl.source.jsp.jspXml.JspDirective;
import com.intellij.psi.util.PsiModificationTracker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JavaCodeBlockModificationListener extends PsiTreeChangePreprocessorBase {

  public JavaCodeBlockModificationListener(@NotNull PsiManager psiManager) {
    super(psiManager);
  }

  @Override
  protected boolean acceptsEvent(@NotNull PsiTreeChangeEventImpl event) {
    return event.getFile() instanceof PsiClassOwner;
  }

  @Override
  protected boolean isOutOfCodeBlock(@NotNull PsiElement element) {
    for (PsiElement e : SyntaxTraverser.psiApi().parents(element)) {
      if (e instanceof PsiModifiableCodeBlock) {
        // trigger OOCBM for final variables initialized in constructors & class initializers
        if (!((PsiModifiableCodeBlock)e).shouldChangeModificationCount(element)) return false;
      }
      if (e instanceof PsiClass) break;
      if (e instanceof PsiClassOwner || e instanceof JspDirective) break;
    }
    return true;
  }

  @Override
  protected boolean isOutOfCodeBlock(@NotNull PsiFileSystemItem file) {
    if (file instanceof PsiModifiableCodeBlock) {
      return ((PsiModifiableCodeBlock)file).shouldChangeModificationCount(file);
    }
    return super.isOutOfCodeBlock(file);
  }

  @Override
  protected void onTreeChanged(@NotNull PsiTreeChangeEventImpl event) {
    PsiModificationTracker tracker = myPsiManager.getModificationTracker();
    long cur = tracker.getOutOfCodeBlockModificationCount();
    super.onTreeChanged(event);
    if (cur == tracker.getOutOfCodeBlockModificationCount()) {
      PsiEventType code = event.getCode();
      if (code == PsiEventType.CHILD_ADDED || code == PsiEventType.CHILD_REMOVED || code == PsiEventType.CHILD_REPLACED) {
        if (hasClassesInside(event.getOldChild()) ||
            event.getOldChild() != event.getChild() && hasClassesInside(event.getChild())) {
          onOutOfCodeBlockModification(event);
          doIncOutOfCodeBlockCounter();
        }
      }
    }
  }

  @Override
  protected void doIncOutOfCodeBlockCounter() {
    ((PsiModificationTrackerImpl)myPsiManager.getModificationTracker()).incCounter();
  }

  private static boolean hasClassesInside(@Nullable PsiElement element) {
    return !SyntaxTraverser.psiTraverser(element).traverse()
      .filter(Conditions.instanceOf(PsiClass.class, PsiLambdaExpression.class)).isEmpty();
  }

}
