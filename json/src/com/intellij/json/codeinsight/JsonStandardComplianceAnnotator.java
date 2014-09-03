package com.intellij.json.codeinsight;

import com.intellij.json.JsonBundle;
import com.intellij.json.psi.JsonStringLiteral;
import com.intellij.json.psi.impl.JsonRecursiveVisitor;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

/**
 * @author Mikhail Golubev
 */
public class JsonStandardComplianceAnnotator implements Annotator {
  @Override
  public void annotate(@NotNull final PsiElement element, @NotNull final AnnotationHolder holder) {
    new JsonRecursiveVisitor() {
      @Override
      public void visitComment(PsiComment comment) {
        holder.createErrorAnnotation(comment, JsonBundle.message("compliance.problem.comments"));
      }

      @Override
      public void visitStringLiteral(@NotNull JsonStringLiteral literal) {
        if (literal.getText().startsWith("'")) {
          holder.createErrorAnnotation(literal, JsonBundle.message("compliance.problem.single.quoted.strings"));
        }
      }
    }.visitElement(element);
  }
}
