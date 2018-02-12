// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.java19api;

import com.intellij.codeInsight.daemon.impl.analysis.JavaModuleGraphUtil;
import com.intellij.codeInsight.daemon.impl.quickfix.AddUsesDirectiveFix;
import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.impl.JavaReflectionReferenceUtil;
import org.jetbrains.annotations.NotNull;

import static com.intellij.psi.impl.source.resolve.reference.impl.JavaReflectionReferenceUtil.ReflectiveType;

/**
 * @author Pavel.Dolgov
 */
public class Java9UndeclaredServiceUsageInspection extends AbstractBaseJavaLocalInspectionTool {
  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    PsiFile file = holder.getFile();
    if (file instanceof PsiJavaFile && ((PsiJavaFile)file).getLanguageLevel().isAtLeast(LanguageLevel.JDK_1_9)) {
      return new JavaElementVisitor() {
        @Override
        public void visitMethodCallExpression(PsiMethodCallExpression expression) {
          checkMethodCall(expression, holder);
          super.visitMethodCallExpression(expression);
        }
      };
    }
    return PsiElementVisitor.EMPTY_VISITOR;
  }

  private static void checkMethodCall(@NotNull PsiMethodCallExpression methodCall, @NotNull ProblemsHolder holder) {
    String referenceName = methodCall.getMethodExpression().getReferenceName();
    if ("load".equals(referenceName) || "loadInstalled".equals(referenceName)) {
      PsiElement resolved = methodCall.getMethodExpression().resolve();
      if (resolved instanceof PsiMethod) {
        PsiMethod method = (PsiMethod)resolved;
        if (method.hasModifierProperty(PsiModifier.STATIC)) {
          PsiClass containingClass = method.getContainingClass();
          if (containingClass != null && "java.util.ServiceLoader".equals(containingClass.getQualifiedName())) {
            checkServiceUsage(methodCall, holder);
          }
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
            PsiJavaModule module = JavaModuleGraphUtil.findDescriptorByElement(argument);
            if (module != null && isUndeclaredUsage(module, psiClass)) {
              holder.registerProblem(
                argument, InspectionsBundle.message("inspection.undeclared.service.usage.message", qualifiedName),
                new AddUsesDirectiveFix(module, qualifiedName));
            }
          }
      }
    }
  }

  private static boolean isUndeclaredUsage(PsiJavaModule module, @NotNull PsiClass serviceClass) {
    for (PsiUsesStatement usesStatement : module.getUses()) {
      PsiJavaCodeReferenceElement reference = usesStatement.getClassReference();
      if (reference != null && reference.isReferenceTo(serviceClass)) {
        return false;
      }
    }
    return true;
  }
}
