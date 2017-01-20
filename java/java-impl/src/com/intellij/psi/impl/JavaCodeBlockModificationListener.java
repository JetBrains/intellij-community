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

import com.intellij.psi.*;
import com.intellij.psi.impl.source.jsp.jspXml.JspDirective;
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
        if (hasClassesInside(e)) break;
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
  protected boolean isOutOfCodeBlockInvalid(@NotNull PsiElement element) {
    return hasClassesInside(element);
  }

  @Override
  protected void doIncOutOfCodeBlockCounter() {
    ((PsiModificationTrackerImpl)myPsiManager.getModificationTracker()).incCounter();
  }

  private static boolean hasClassesInside(@Nullable PsiElement element) {
    return !SyntaxTraverser.psiTraverser(element).filter(PsiClass.class).isEmpty();
  }

}
