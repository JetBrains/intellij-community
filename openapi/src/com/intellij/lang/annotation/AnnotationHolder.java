package com.intellij.lang.annotation;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Feb 3, 2005
 * Time: 3:08:54 PM
 * To change this template use File | Settings | File Templates.
 */
public interface AnnotationHolder {
  Annotation createErrorAnnotation(PsiElement elt, String message);
  Annotation createErrorAnnotation(ASTNode node, String message);
  Annotation createErrorAnnotation(TextRange range, String message);

  Annotation createWarningAnnotation(PsiElement elt, String message);
  Annotation createWarningAnnotation(ASTNode node, String message);
  Annotation createWarningAnnotation(TextRange range, String message);

  Annotation createInfoAnnotation(PsiElement elt, String message);
  Annotation createInfoAnnotation(ASTNode node, String message);
  Annotation createInfoAnnotation(TextRange range, String message);

  boolean hasAnnotations();
}
