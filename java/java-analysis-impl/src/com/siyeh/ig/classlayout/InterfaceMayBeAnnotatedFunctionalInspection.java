// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.classlayout;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.intention.AddAnnotationModCommandAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.LambdaUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.util.MethodSignature;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * @author Bas Leijdekkers
 */
public final class InterfaceMayBeAnnotatedFunctionalInspection extends BaseInspection {

  @Override
  protected @NotNull String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("interface.may.be.annotated.functional.problem.descriptor");
  }

  @Override
  public @NotNull Set<@NotNull JavaFeature> requiredFeatures() {
    return Set.of(JavaFeature.LAMBDA_EXPRESSIONS);
  }

  @Override
  protected @NotNull LocalQuickFix buildFix(Object... infos) {
    final PsiClass aClass = (PsiClass)infos[0];
    return LocalQuickFix.from(new AddAnnotationModCommandAction(CommonClassNames.JAVA_LANG_FUNCTIONAL_INTERFACE, aClass));
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
      if (LambdaUtil.checkInterfaceFunctional(aClass) != LambdaUtil.FunctionalInterfaceStatus.VALID) {
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
