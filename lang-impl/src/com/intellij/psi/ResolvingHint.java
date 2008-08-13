package com.intellij.psi;

/**
 * @author Dmitry Avdeev
 */
public interface ResolvingHint {

  boolean canResolveTo(PsiElement element);
}
