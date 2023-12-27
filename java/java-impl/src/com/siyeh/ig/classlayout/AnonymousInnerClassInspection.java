/*
 * Copyright 2003-2023 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.classlayout;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiEnumConstantInitializer;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.fixes.MoveAnonymousToInnerClassFix;
import org.jetbrains.annotations.NotNull;

public final class AnonymousInnerClassInspection extends BaseInspection {

  @Override
  protected LocalQuickFix buildFix(Object... infos) {
    return new MoveAnonymousToInnerClassFix();
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("anonymous.inner.class.problem.descriptor");
  }

  @Override
  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    return true;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new AnonymousInnerClassVisitor();
  }

  private static class AnonymousInnerClassVisitor extends BaseInspectionVisitor {

    @Override
    public void visitAnonymousClass(@NotNull PsiAnonymousClass aClass) {
      super.visitAnonymousClass(aClass);
      if (aClass instanceof PsiEnumConstantInitializer) {
        return;
      }
      if (isVisibleHighlight(aClass)) {
        registerClassError(aClass);
      }
      else {
        final PsiElement lBrace = aClass.getLBrace();
        assert lBrace != null;
        int length = aClass.getStartOffsetInParent() + lBrace.getStartOffsetInParent();
        PsiElement newExpression = aClass.getParent();
        registerErrorAtOffset(newExpression, 0, length);
      }
    }
  }
}