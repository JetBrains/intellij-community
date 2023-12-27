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

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.fixes.MoveAnonymousToInnerClassFix;
import org.jetbrains.annotations.NotNull;

public final class LimitedScopeInnerClassInspection extends BaseInspection {

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("limited.scope.inner.class.problem.descriptor");
  }

  @Override
  protected MoveAnonymousToInnerClassFix buildFix(Object... infos) {
    return new MoveAnonymousToInnerClassFix(InspectionGadgetsBundle.message("move.local.to.inner.quickfix"));
  }

  @Override
  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    return true;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new LimitedScopeInnerClassVisitor();
  }

  private static class LimitedScopeInnerClassVisitor extends BaseInspectionVisitor {

    @Override
    public void visitClass(@NotNull PsiClass aClass) {
      if (!PsiUtil.isLocalClass(aClass)) {
        return;
      }
      if (isVisibleHighlight(aClass)) {
        registerClassError(aClass);
      }
      else {
        PsiElement lBrace = aClass.getLBrace();
        assert lBrace != null;
        registerErrorAtOffset(aClass, 0, lBrace.getStartOffsetInParent());
      }
    }
  }
}