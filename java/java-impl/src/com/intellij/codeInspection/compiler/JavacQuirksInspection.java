/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.codeInspection.compiler;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.ex.BaseLocalInspectionTool;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class JavacQuirksInspection extends BaseLocalInspectionTool {
  @Nls @NotNull
  @Override
  public String getGroupDisplayName() {
    return GroupNames.COMPILER_ISSUES;
  }

  @Nls @NotNull
  @Override
  public String getDisplayName() {
    return InspectionsBundle.message("inspection.compiler.javac.quirks.name");
  }

  @NotNull
  @Override
  public String getShortName() {
    return "JavacQuirks";
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, final boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitAnnotationArrayInitializer(final PsiArrayInitializerMemberValue initializer) {
        final PsiElement lastElement = PsiTreeUtil.skipSiblingsBackward(initializer.getLastChild(), PsiWhiteSpace.class, PsiComment.class);
        if (lastElement != null && PsiUtil.isJavaToken(lastElement, JavaTokenType.COMMA)) {
          holder.registerProblem(lastElement, InspectionsBundle.message("inspection.compiler.javac.quirks.anno.array.comma.problem"), new RemoveCommaQuickFix());
        }
      }
    };
  }

  private static class RemoveCommaQuickFix implements LocalQuickFix {
    @NotNull
    @Override
    public String getName() {
      return InspectionsBundle.message("inspection.compiler.javac.quirks.anno.array.comma.fix");
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return getName();
    }

    @Override
    public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
      final PsiElement psiElement = descriptor.getPsiElement();
      if (PsiUtil.isJavaToken(psiElement, JavaTokenType.COMMA)) {
        psiElement.delete();
      }
    }
  }
}
