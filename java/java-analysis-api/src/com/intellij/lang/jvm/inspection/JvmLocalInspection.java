// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.jvm.inspection;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.lang.jvm.JvmElement;
import com.intellij.lang.jvm.JvmElementVisitor;
import com.intellij.lang.jvm.source.JvmDeclarationSearch;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import org.jetbrains.annotations.ApiStatus.Experimental;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Experimental
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
            visitor = buildVisitor(
              holder.getProject(),
              (message, highlightType, fixes) -> holder.registerProblem(element, message, highlightType, fixes),
              isOnTheFly
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

  public interface HighlightSink {

    default void highlight(@Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String message, @NotNull LocalQuickFix... fixes) {
      highlight(message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, fixes);
    }

    void highlight(@Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String message,
                   @NotNull ProblemHighlightType highlightType,
                   @NotNull LocalQuickFix... fixes);
  }

  @Nullable
  protected abstract JvmElementVisitor<Boolean> buildVisitor(@NotNull Project project, @NotNull HighlightSink sink, boolean isOnTheFly);
}
