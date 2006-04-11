package com.intellij.psi.filters;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NonNls;

public interface ElementFilter{
  /**
   * Checks if element in given context is mathed by given filter.
   * @param element most often PsiElement
   * @param context context of the element (if any)
   * @return true if matched
   */
  boolean isAcceptable(Object element, PsiElement context);

  /**
   * Quick check if the filter acceptable for elements of given class at all.
   * @param hintClass class for which we are looking for metadata
   * @return true if class matched
   */
  boolean isClassAcceptable(Class hintClass);

   // To be used only for debug purposes!
  @NonNls String toString();
}
