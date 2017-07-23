/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.codeInspection.reflectiveAccess;

import com.intellij.codeInsight.daemon.impl.analysis.JavaModuleGraphUtil;
import com.intellij.codeInsight.daemon.impl.quickfix.AddRequiredModuleFix;
import com.intellij.codeInspection.BaseJavaBatchLocalInspectionTool;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.intellij.psi.CommonClassNames.JAVA_LANG_CLASS;
import static com.intellij.psi.impl.source.resolve.reference.impl.JavaReflectionReferenceUtil.*;

/**
 * @author Pavel.Dolgov
 */
public class Java9ReflectionClassVisibilityInspection extends BaseJavaBatchLocalInspectionTool {

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    final PsiFile file = holder.getFile();
    if (PsiUtil.isLanguageLevel9OrHigher(file)) {
      final PsiJavaModule javaModule = JavaModuleGraphUtil.findDescriptorByElement(file);
      if (javaModule != null) {
        return new JavaElementVisitor() {
          @Override
          public void visitMethodCallExpression(PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);

            if (isCallToMethod(expression, JAVA_LANG_CLASS, FOR_NAME) || isCallToMethod(expression, JAVA_LANG_CLASS_LOADER, LOAD_CLASS)) {
              checkClassVisibility(expression, holder, javaModule);
            }
          }
        };
      }
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
          final PsiJavaModule otherModule = JavaModuleGraphUtil.findDescriptorByElement(psiClass);
          if (otherModule != null && otherModule != javaModule) {
            if (!JavaModuleGraphUtil.reads(javaModule, otherModule)) {
              String message = InspectionsBundle.message(
                "module.not.in.requirements", javaModule.getName(), otherModule.getName());
              holder.registerProblem(classNameArgument, message, new AddRequiredModuleFix(javaModule, otherModule.getName()));
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
              final String message = InspectionsBundle.message(
                publicApi ? "module.package.not.exported" : "module.package.not.open",
                otherModule.getName(), packageName, javaModule.getName());
              holder.registerProblem(classNameArgument, message);
            }
          }
        }
      }
    }
  }

  private static boolean isPackageAccessible(@NotNull Iterable<PsiPackageAccessibilityStatement> statements,
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
