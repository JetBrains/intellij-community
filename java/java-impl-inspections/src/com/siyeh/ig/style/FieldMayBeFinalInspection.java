// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.style;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.impl.UnusedSymbolUtil;
import com.intellij.codeInsight.options.JavaInspectionButtons;
import com.intellij.codeInsight.options.JavaInspectionControls;
import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.IncorrectLazyConstantUsageInspection;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.canBeFinal.CanBeFinalHandler;
import com.intellij.codeInspection.ex.EntryPointsManagerBase;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.codeInspection.util.SpecialAnnotationsUtilBase;
import com.intellij.pom.java.JavaFeature;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
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
  private static final String JAVA_LANG_LAZY_CONSTANT = "java.lang.LazyConstant";
  private static final String INCORRECT_LAZY_CONSTANT_USAGE_SHORT_NAME = "IncorrectLazyConstantUsage";

  @Override
  protected @NotNull String buildErrorString(Object... infos) {
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
  public @NotNull BaseInspectionVisitor buildVisitor() {
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

      // IDEA-386070: IncorrectLazyConstantUsageInspection already reports non-final LazyConstant fields on the fly.
      if (isOnTheFly() && isEnabledIncorrectLazyConstantUsage(field) && isLazyConstantField(field)) {
        return;
      }

      registerVariableError(field, field);
    }

    private static boolean isEnabledIncorrectLazyConstantUsage(@NotNull PsiField field) {
      if (!PsiUtil.isAvailable(JavaFeature.LAZY_CONSTANTS, field)) {
        return false;
      }

      HighlightDisplayKey key = HighlightDisplayKey.find(INCORRECT_LAZY_CONSTANT_USAGE_SHORT_NAME);
      if (key == null) {
        return false;
      }

      InspectionProfile profile = InspectionProjectProfileManager.getInstance(field.getProject()).getCurrentProfile();
      return profile.isToolEnabled(key, field) && !HighlightDisplayLevel.DO_NOT_SHOW.equals(profile.getErrorLevel(key, field));
    }

    private static boolean isLazyConstantField(@NotNull PsiField field) {
      PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(field.getType());
      if (aClass != null && JAVA_LANG_LAZY_CONSTANT.equals(aClass.getQualifiedName())) {
        return true;
      }
      return IncorrectLazyConstantUsageInspection.isLazyCollectionInitializer(field);
    }
  }
}
