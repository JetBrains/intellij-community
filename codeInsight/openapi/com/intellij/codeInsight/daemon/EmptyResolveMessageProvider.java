package com.intellij.codeInsight.daemon;

/**
 * Implement this in your {@link com.intellij.psi.PsiReference} to provide custom error message.
 */
public interface EmptyResolveMessageProvider {
  String getUnresolvedMessagePattern();
}
