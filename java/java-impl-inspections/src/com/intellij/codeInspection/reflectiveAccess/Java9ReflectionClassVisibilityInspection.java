// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.reflectiveAccess;

import com.intellij.codeInsight.daemon.impl.quickfix.AddExportsDirectiveFix;
import com.intellij.codeInsight.daemon.impl.quickfix.AddOpensDirectiveFix;
import com.intellij.codeInsight.daemon.impl.quickfix.AddRequiresDirectiveFix;
import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.java.JavaBundle;
import com.intellij.java.codeserver.core.JavaPsiModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

import static com.intellij.psi.CommonClassNames.JAVA_LANG_CLASS;
import static com.intellij.psi.impl.source.resolve.reference.impl.JavaReflectionReferenceUtil.*;

public final class Java9ReflectionClassVisibilityInspection extends AbstractBaseJavaLocalInspectionTool {

  @Override
  public @NotNull Set<@NotNull JavaFeature> requiredFeatures() {
    return Set.of(JavaFeature.MODULES);
  }

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    final PsiJavaModule javaModule = JavaPsiModuleUtil.findDescriptorByElement(holder.getFile());
    if (javaModule != null) {
      return new JavaElementVisitor() {
        @Override
        public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
          super.visitMethodCallExpression(expression);

          if (isCallToMethod(expression, JAVA_LANG_CLASS, FOR_NAME) || isCallToMethod(expression, JAVA_LANG_CLASS_LOADER, LOAD_CLASS)) {
            checkClassVisibility(expression, holder, javaModule);
          }
        }
      };
    }

    return PsiElementVisitor.EMPTY_VISITOR;
  }

  private static void checkClassVisibility(@NotNull PsiMethodCallExpression callExpression,
                                           @NotNull ProblemsHolder holder,
                                           @NotNull PsiJavaModule javaModule) {

    final PsiExpression[] arguments = callExpression.getArgumentList().getExpressions();
    if (arguments.length != 0) {
      final PsiExpression classNameArgument = arguments[0];
      final String className = computeConstantExpression(classNameArgument, String.class);
      if (className != null) {
        final Project project = holder.getProject();
        final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
        final PsiClass psiClass = facade.findClass(className, callExpression.getResolveScope());
        if (psiClass != null) {
          final PsiJavaModule otherModule = JavaPsiModuleUtil.findDescriptorByElement(psiClass);
          if (otherModule != null && otherModule != javaModule) {
            if (!JavaPsiModuleUtil.reads(javaModule, otherModule)) {
              String message = JavaBundle.message("module.not.in.requirements", javaModule.getName(), otherModule.getName());
              holder.problem(classNameArgument, message).fix(new AddRequiresDirectiveFix(javaModule, otherModule.getName())).register();
              return;
            }

            if (otherModule.hasModifierProperty(PsiModifier.OPEN)) {
              return;
            }
            final PsiJavaFile file = PsiTreeUtil.getParentOfType(psiClass, PsiJavaFile.class);
            if (file != null) {
              final String packageName = file.getPackageName();
              if (isPackageAccessible(otherModule.getOpens(), packageName, javaModule)) {
                return;
              }
              final boolean publicApi = isPublicApi(psiClass);
              if (publicApi && isPackageAccessible(otherModule.getExports(), packageName, javaModule)) {
                return;
              }
              if (publicApi) {
                final String message = JavaBundle.message("module.package.not.exported", otherModule.getName(), packageName, javaModule.getName());
                holder.problem(classNameArgument, message).fix(new AddExportsDirectiveFix(otherModule, packageName, javaModule.getName())).register();
              } else {
                final String message = JavaBundle.message("module.package.not.open", otherModule.getName(), packageName, javaModule.getName());
                holder.problem(classNameArgument, message).fix(new AddOpensDirectiveFix(otherModule, packageName, javaModule.getName())).register();
              }
            }
          }
        }
      }
    }
  }

  private static boolean isPackageAccessible(@NotNull Iterable<? extends PsiPackageAccessibilityStatement> statements,
                                             @NotNull String packageName,
                                             @NotNull PsiJavaModule javaModule) {
    for (PsiPackageAccessibilityStatement statement : statements) {
      if (packageName.equals(statement.getPackageName())) {
        final List<String> moduleNames = statement.getModuleNames();
        if (moduleNames.isEmpty() || moduleNames.contains(javaModule.getName())) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean isPublicApi(@NotNull PsiClass psiClass) {
    if (psiClass.hasModifierProperty(PsiModifier.PUBLIC) || psiClass.hasModifierProperty(PsiModifier.PROTECTED)) {
      final PsiElement parent = psiClass.getParent();
      return parent instanceof PsiJavaFile || parent instanceof PsiClass && isPublicApi((PsiClass)parent);
    }
    return false;
  }
}
