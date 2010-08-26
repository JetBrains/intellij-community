/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.codeInspection.concurrencyAnnotations;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.BaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class NonFinalFieldInImmutableInspection extends BaseJavaLocalInspectionTool {

  @NotNull
  public String getGroupDisplayName() {
    return GroupNames.CONCURRENCY_ANNOTATION_ISSUES;
  }

  @Nls
  @NotNull
  public String getDisplayName() {
    return "Non-final field in @Immutable class";
  }

  @NotNull
  public String getShortName() {
    return "NonFinalFieldInImmutable";
  }


  @NotNull
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      public void visitField(PsiField field) {
        super.visitField(field);
        if (field.hasModifierProperty(PsiModifier.FINAL)) {
          return;
        }
        final PsiClass containingClass = field.getContainingClass();
        if (containingClass != null) {
          if (!JCiPUtil.isImmutable(containingClass)) {
            return;
          }
          holder.registerProblem(field, "Non-final field #ref in @Immutable class  #loc");
        }
      }

      public void visitReferenceExpression(PsiReferenceExpression expression) {
      }
    };
  }
}