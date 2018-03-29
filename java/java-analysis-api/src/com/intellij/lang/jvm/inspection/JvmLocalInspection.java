// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.jvm.inspection;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.lang.jvm.JvmElement;
import com.intellij.lang.jvm.JvmElementVisitor;
import com.intellij.lang.jvm.source.JvmDeclarationSearch;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class JvmLocalInspection extends LocalInspectionTool {

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new PsiElementVisitor() {
      @Override
      public void visitElement(PsiElement element) {
        JvmElementVisitor<Boolean> visitor = null;
        for (JvmElement jvmElement : JvmDeclarationSearch.getElementsByIdentifier(element)) {
          if (visitor == null) {
            // don't build visitor until there is at least one JvmElement
            visitor = buildJvmVisitor(
              holder.getProject(),
              (message, highlightType) -> holder.registerProblem(element, message, highlightType)
            );
            if (visitor == null) return;
          }
          Boolean result = jvmElement.accept(visitor);
          if (result == Boolean.TRUE) {
            return;
          }
        }
      }
    };
  }

  protected interface HighlightSink {

    default void highlight(@Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String message) {
      highlight(message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
    }

    void highlight(@Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String message,
                   @NotNull ProblemHighlightType highlightType);
  }

  @Nullable
  protected abstract JvmElementVisitor<Boolean> buildJvmVisitor(@NotNull Project project, @NotNull HighlightSink message);
}
