package com.intellij.json.codeinsight;

import com.intellij.json.JsonBundle;
import com.intellij.json.psi.*;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author Mikhail Golubev
 */
public class JsonStandardComplianceAnnotator implements Annotator {
  @Override
  public void annotate(@NotNull final PsiElement element, @NotNull final AnnotationHolder holder) {
    element.accept(new JsonElementVisitor() {
      @Override
      public void visitComment(PsiComment comment) {
        holder.createErrorAnnotation(comment, JsonBundle.message("compliance.problem.comments"));
      }

      @Override
      public void visitStringLiteral(@NotNull JsonStringLiteral stringLiteral) {
        if (stringLiteral.getText().startsWith("'")) {
          holder.createErrorAnnotation(stringLiteral, JsonBundle.message("compliance.problem.single.quoted.strings"));
        }
        super.visitStringLiteral(stringLiteral);
      }

      @Override
      public void visitLiteral(@NotNull JsonLiteral literal) {
        final PsiFile psiFile = literal.getContainingFile();
        if (psiFile instanceof JsonFile && literal == ((JsonFile)psiFile).getTopLevelValue()) {
          holder.createErrorAnnotation(literal, JsonBundle.message("compliance.problem.illegal.top.level.value"));
        }
      }

      @Override
      public void visitReferenceExpression(@NotNull JsonReferenceExpression reference) {
        holder.createErrorAnnotation(reference, JsonBundle.message("compliance.problem.identifier"));
      }

    });
  }
}
