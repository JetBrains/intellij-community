// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.style;

import com.intellij.codeInsight.daemon.impl.UnusedSymbolUtil;
import com.intellij.codeInsight.options.JavaInspectionButtons;
import com.intellij.codeInsight.options.JavaInspectionControls;
import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.canBeFinal.CanBeFinalHandler;
import com.intellij.codeInspection.ex.EntryPointsManagerBase;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.codeInspection.util.SpecialAnnotationsUtilBase;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.fixes.MakeFieldFinalFix;
import com.siyeh.ig.psiutils.FinalUtils;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.codeInspection.options.OptPane.pane;

public final class FieldMayBeFinalInspection extends BaseInspection implements CleanupLocalInspectionTool {

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "field.may.be.final.problem.descriptor");
  }

  @Override
  public boolean runForWholeFile() {
    return true;
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(JavaInspectionControls.button(JavaInspectionButtons.ButtonKind.IMPLICIT_WRITE_ANNOTATIONS));
  }

  @Override
  protected LocalQuickFix @NotNull [] buildFixes(Object... infos) {
    List<LocalQuickFix> fixes = new ArrayList<>();
    PsiField field = (PsiField)infos[0];
    fixes.add(MakeFieldFinalFix.buildFixUnconditional(field));
    SpecialAnnotationsUtilBase.processUnknownAnnotations(field, annoName -> {
      fixes.add(LocalQuickFix.from(EntryPointsManagerBase.createAddImplicitWriteAnnotation(annoName)));
      return true;
    });
    return fixes.toArray(LocalQuickFix.EMPTY_ARRAY);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new FieldMayBeFinalVisitor();
  }

  private static class FieldMayBeFinalVisitor extends BaseInspectionVisitor {

    @Override
    public void visitField(@NotNull PsiField field) {
      super.visitField(field);
      if (field.hasModifierProperty(PsiModifier.FINAL)) {
        return;
      }
      if (!field.hasModifierProperty(PsiModifier.PRIVATE)) {
        PsiClass aClass = field.getContainingClass();
        if (aClass == null || !PsiUtil.isLocalOrAnonymousClass(aClass)) return;
      }
      if (!CanBeFinalHandler.allowToBeFinal(field)) return;
      if (!FinalUtils.canBeFinal(field)) {
        return;
      }
      if (UnusedSymbolUtil.isImplicitWrite(field)) return;
      registerVariableError(field, field);
    }
  }
}
