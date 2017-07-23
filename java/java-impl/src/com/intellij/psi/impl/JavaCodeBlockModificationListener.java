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
import com.intellij.psi.util.PsiTreeUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Set;

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
  protected boolean containsStructuralElements(@NotNull PsiElement element) {
    return hasClassesInside(element);
  }

  @Override
  protected void onTreeChanged(@NotNull PsiTreeChangeEventImpl event) {
    Set<PsiElement> changedChildren = getChangedChildren(event);

    PsiModificationTrackerImpl tracker = (PsiModificationTrackerImpl)myPsiManager.getModificationTracker();
    if (!changedChildren.isEmpty() && changedChildren.stream().anyMatch(JavaCodeBlockModificationListener::hasClassesInside)) {
      tracker.incCounter();
    }

    if (isOutOfCodeBlockChangeEvent(event)) {
      if (changedChildren.isEmpty() || changedChildren.stream().anyMatch(e -> !isWhiteSpaceOrComment(e))) {
        tracker.incCounter(); // java structure change
      }
      else {
        tracker.incOutOfCodeBlockModificationCounter();
      }
    }
  }

  private static boolean isWhiteSpaceOrComment(@NotNull PsiElement e) {
    return e instanceof PsiWhiteSpace || PsiTreeUtil.getParentOfType(e, PsiComment.class, false) != null;
  }

  private static Set<PsiElement> getChangedChildren(@NotNull PsiTreeChangeEventImpl event) {
    PsiEventType code = event.getCode();
    if (code == PsiEventType.CHILD_ADDED || code == PsiEventType.CHILD_REMOVED || code == PsiEventType.CHILD_REPLACED) {
      return StreamEx.of(event.getOldChild(), event.getChild(), event.getNewChild()).nonNull().toSet();
    }
    return Collections.emptySet();
  }

  private static boolean hasClassesInside(@NotNull PsiElement element) {
    return !SyntaxTraverser.psiTraverser(element).traverse()
      .filter(Conditions.instanceOf(PsiClass.class, PsiLambdaExpression.class)).isEmpty();
  }

}
