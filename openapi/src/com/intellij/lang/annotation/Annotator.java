package com.intellij.lang.annotation;

import com.intellij.psi.PsiElement;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Feb 3, 2005
 * Time: 2:13:42 PM
 * To change this template use File | Settings | File Templates.
 */
public interface Annotator {
  void annotate(PsiElement psiElement, AnnotationHolder holder);
}
