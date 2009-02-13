package com.intellij.openapi.paths;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;

/**
 * @author Dmitry Avdeev
 */
public interface DynamicContextProvider {

  ExtensionPointName<DynamicContextProvider> EP_NAME = ExtensionPointName.create("com.intellij.dynamicContextProvider");

  /**
   * Returns starting position for file references
   *
   * @param element
   * @param offset initial offset
   * @param elementText
   * @return -1 to suppress file references generation
   */
  int getOffset(final PsiElement element, final int offset, final String elementText);
}
