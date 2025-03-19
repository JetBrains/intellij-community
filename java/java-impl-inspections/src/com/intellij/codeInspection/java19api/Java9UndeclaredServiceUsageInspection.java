// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.java19api;

import com.intellij.codeInsight.daemon.impl.quickfix.AddUsesDirectiveFix;
import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.java.JavaBundle;
import com.intellij.java.codeserver.core.JavaPsiModuleUtil;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.impl.JavaReflectionReferenceUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

import static com.intellij.java.codeserver.core.JavaServiceProviderUtil.JAVA_UTIL_SERVICE_LOADER_METHODS;
import static com.intellij.psi.CommonClassNames.JAVA_UTIL_SERVICE_LOADER;
import static com.intellij.psi.impl.source.resolve.reference.impl.JavaReflectionReferenceUtil.ReflectiveType;

public final class Java9UndeclaredServiceUsageInspection extends AbstractBaseJavaLocalInspectionTool {

  @Override
  public @NotNull Set<@NotNull JavaFeature> requiredFeatures() {
    return Set.of(JavaFeature.MODULES);
  }

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
        checkMethodCall(expression, holder);
        super.visitMethodCallExpression(expression);
      }
    };
  }

  private static void checkMethodCall(@NotNull PsiMethodCallExpression methodCall, @NotNull ProblemsHolder holder) {
    String referenceName = methodCall.getMethodExpression().getReferenceName();
    if (JAVA_UTIL_SERVICE_LOADER_METHODS.contains(referenceName)) {
      PsiMethod method = methodCall.resolveMethod();
      if (method != null && method.hasModifierProperty(PsiModifier.STATIC)) {
        PsiClass containingClass = method.getContainingClass();
        if (containingClass != null && JAVA_UTIL_SERVICE_LOADER.equals(containingClass.getQualifiedName())) {
          checkServiceUsage(methodCall, holder);
        }
      }
    }
  }

  private static void checkServiceUsage(@NotNull PsiMethodCallExpression methodCall, @NotNull ProblemsHolder holder) {
    PsiExpression[] arguments = methodCall.getArgumentList().getExpressions();

    PsiExpression argument = null;
    ReflectiveType serviceType = null;
    for (int i = 0; i < arguments.length && serviceType == null; i++) {
      argument = arguments[i];
      serviceType = JavaReflectionReferenceUtil.getReflectiveType(argument);
    }

    if (serviceType != null && serviceType.isExact()) {
        PsiClass psiClass = serviceType.getPsiClass();
        if (psiClass != null) {
          String qualifiedName = psiClass.getQualifiedName();
          if (qualifiedName != null) {
            PsiJavaModule module = JavaPsiModuleUtil.findDescriptorByElement(argument);
            if (module != null && isUndeclaredUsage(module, psiClass)) {
              holder.problem(argument, JavaBundle.message("inspection.undeclared.service.usage.message", qualifiedName))
                .fix(new AddUsesDirectiveFix(module, qualifiedName)).register();
            }
          }
      }
    }
  }

  private static boolean isUndeclaredUsage(PsiJavaModule module, @NotNull PsiClass serviceClass) {
    for (PsiUsesStatement usesStatement : module.getUses()) {
      PsiClassType usedClass = usesStatement.getClassType();
      if (usedClass != null && serviceClass.equals(usedClass.resolve())) {
        return false;
      }
    }
    return true;
  }
}