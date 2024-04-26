// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.threading;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.InheritanceUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.fixes.MakeFieldStaticFinalFix;
import org.jetbrains.annotations.NotNull;

public final class ThreadLocalNotStaticFinalInspection extends BaseInspection {

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("thread.local.not.static.final.problem.descriptor");
  }

  @Override
  protected LocalQuickFix buildFix(Object... infos) {
    return MakeFieldStaticFinalFix.buildFix((PsiField)infos[0]);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ThreadLocalNotStaticFinalVisitor();
  }

  private static class ThreadLocalNotStaticFinalVisitor extends BaseInspectionVisitor {

    @Override
    public void visitField(@NotNull PsiField field) {
      super.visitField(field);
      final PsiType type = field.getType();
      if (!InheritanceUtil.isInheritor(type, "java.lang.ThreadLocal")) {
        return;
      }
      final PsiModifierList modifierList = field.getModifierList();
      if (modifierList == null) {
        return;
      }
      if (modifierList.hasModifierProperty(PsiModifier.STATIC) &&
          modifierList.hasModifierProperty(PsiModifier.FINAL)) {
        return;
      }
      registerFieldError(field, field);
    }
  }
}