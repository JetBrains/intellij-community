package com.intellij.codeInsight.daemon.impl;

import com.intellij.lang.ASTNode;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.util.SmartList;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author max
 */
public class AnnotationHolderImpl extends SmartList<Annotation> implements AnnotationHolder {
  public Annotation createErrorAnnotation(@NotNull PsiElement elt, String message) {
    return createAnnotation(elt.getTextRange(), HighlightSeverity.ERROR, message);
  }

  public Annotation createErrorAnnotation(ASTNode node, String message) {
    return createAnnotation(node.getTextRange(), HighlightSeverity.ERROR, message);
  }

  public Annotation createErrorAnnotation(TextRange range, String message) {
    return createAnnotation(range, HighlightSeverity.ERROR, message);
  }

  public Annotation createWarningAnnotation(PsiElement elt, String message) {
    return createAnnotation(elt.getTextRange(), HighlightSeverity.WARNING, message);
  }

  public Annotation createWarningAnnotation(ASTNode node, String message) {
    return createAnnotation(node.getTextRange(), HighlightSeverity.WARNING, message);
  }

  public Annotation createWarningAnnotation(TextRange range, String message) {
    return createAnnotation(range, HighlightSeverity.WARNING, message);
  }

  public Annotation createInformationAnnotation(PsiElement elt, String message) {
    return createAnnotation(elt.getTextRange(), HighlightSeverity.INFO, message);
  }

  public Annotation createInformationAnnotation(ASTNode node, String message) {
    return createAnnotation(node.getTextRange(), HighlightSeverity.INFO, message);
  }

  public Annotation createInformationAnnotation(TextRange range, String message) {
    return createAnnotation(range, HighlightSeverity.INFO, message);
  }

  public Annotation createInfoAnnotation(PsiElement elt, String message) {
    return createAnnotation(elt.getTextRange(), HighlightSeverity.INFORMATION, message);
  }

  public Annotation createInfoAnnotation(ASTNode node, String message) {
    return createAnnotation(node.getTextRange(), HighlightSeverity.INFORMATION, message);
  }

  public Annotation createInfoAnnotation(TextRange range, String message) {
    return createAnnotation(range, HighlightSeverity.INFORMATION, message);
  }

  protected Annotation createAnnotation(TextRange range, HighlightSeverity severity, String message) {
    assert range.getStartOffset() <= range.getEndOffset() : range;
    //noinspection HardCodedStringLiteral
    String tooltip = message == null ? null : "<html><body>" + XmlStringUtil.escapeString(message) + "</body></html>";
    Annotation annotation = new Annotation(range.getStartOffset(), range.getEndOffset(), severity, message, tooltip);
    add(annotation);
    return annotation;
  }

  public boolean hasAnnotations() {
    return !isEmpty();
  }
}
