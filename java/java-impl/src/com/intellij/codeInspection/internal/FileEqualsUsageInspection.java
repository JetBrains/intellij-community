/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.codeInspection.internal;

import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.psi.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class FileEqualsUsageInspection extends InternalInspection {
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return "File.equals() Usage";
  }

  @NotNull
  @Override
  public String getShortName() {
    return "FileEqualsUsage";
  }

  @NotNull
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    if (!ApplicationManagerEx.getApplicationEx().isInternal()) {
      return new JavaElementVisitor() {
      };
    }
    return new JavaElementVisitor() {
      @Override
      public void visitMethodCallExpression(PsiMethodCallExpression expression) {
        PsiReferenceExpression methodExpression = expression.getMethodExpression();
        PsiElement resolved = methodExpression.resolve();
        if (!(resolved instanceof PsiMethod)) return;

        PsiMethod method = (PsiMethod)resolved;

        PsiClass clazz = method.getContainingClass();
        if (clazz == null) return;

        String methodName = method.getName();
        if ("java.io.File".equals(clazz.getQualifiedName())
            && ("equals".equals(methodName) || "compareTo".equals(methodName) || "hashCode".equals(methodName))) {
          holder.registerProblem(methodExpression,
                                 "Do not use File.equals/hashCode/compareTo as they don't honor case-sensitivity on MacOS. Use FileUtil.filesEquals/fileHashCode/compareFiles instead",
                                 ProblemHighlightType.LIKE_DEPRECATED);
        }
      }
    };
  }
}
