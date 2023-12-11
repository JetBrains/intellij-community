// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.classlayout;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.daemon.impl.analysis.LambdaHighlightingUtil;
import com.intellij.codeInsight.intention.AddAnnotationPsiFix;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.psi.*;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public final class InterfaceMayBeAnnotatedFunctionalInspection extends BaseInspection {

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("interface.may.be.annotated.functional.problem.descriptor");
  }

  @Override
  public boolean shouldInspect(@NotNull PsiFile file) {
    return PsiUtil.isLanguageLevel8OrHigher(file);
  }

  @Nullable
  @Override
  protected LocalQuickFix buildFix(Object... infos) {
    final PsiClass aClass = (PsiClass)infos[0];
    return new AddAnnotationPsiFix(CommonClassNames.JAVA_LANG_FUNCTIONAL_INTERFACE, aClass);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new InterfaceMayBeAnnotatedFunctionalVisitor();
  }

  private static class InterfaceMayBeAnnotatedFunctionalVisitor extends BaseInspectionVisitor {

    @Override
    public void visitClass(@NotNull PsiClass aClass) {
      super.visitClass(aClass);
      if (!aClass.isInterface() ||
          aClass.isAnnotationType() ||
          aClass.hasModifierProperty(PsiModifier.SEALED) ||
          AnnotationUtil.isAnnotated(aClass, CommonClassNames.JAVA_LANG_FUNCTIONAL_INTERFACE, 0)) {
        return;
      }
      if (LambdaHighlightingUtil.checkInterfaceFunctional(aClass) != null) {
        return;
      }
      MethodSignature signature = LambdaUtil.getFunction(aClass);
      if (signature == null || signature.getTypeParameters().length > 0) {
        return;
      }
      registerClassError(aClass, aClass);
    }
  }
}
