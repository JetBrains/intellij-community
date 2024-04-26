// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.threading;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.InheritanceUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.fixes.MakeFieldStaticFinalFix;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public final class AtomicFieldUpdaterNotStaticFinalInspection extends BaseInspection {

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    final PsiField field = (PsiField)infos[0];
    final PsiType type = field.getType();
    final String typeText = type.getPresentableText();
    return InspectionGadgetsBundle.message("atomic.field.updater.not.static.final.problem.descriptor", typeText);
  }

  @Override
  protected LocalQuickFix buildFix(Object... infos) {
    return MakeFieldStaticFinalFix.buildFix((PsiField)infos[0]);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new AtomicFieldUpdaterNotStaticFinalVisitor();
  }

  private static class AtomicFieldUpdaterNotStaticFinalVisitor extends BaseInspectionVisitor {

    @Override
    public void visitField(@NotNull PsiField field) {
      super.visitField(field);
      if (field.hasModifierProperty(PsiModifier.STATIC) && field.hasModifierProperty(PsiModifier.FINAL)) {
        return;
      }
      final PsiType type = field.getType();
      if (!InheritanceUtil.isInheritor(type, "java.util.concurrent.atomic.AtomicIntegerFieldUpdater") &&
        !InheritanceUtil.isInheritor(type, "java.util.concurrent.atomic.AtomicLongFieldUpdater") &&
        !InheritanceUtil.isInheritor(type, "java.util.concurrent.atomic.AtomicReferenceFieldUpdater")) {
        return;
      }
      registerFieldError(field, field);
    }
  }
}
