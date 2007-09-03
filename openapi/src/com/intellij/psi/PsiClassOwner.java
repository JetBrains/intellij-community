package com.intellij.psi;

import org.jetbrains.annotations.NotNull;

/**
 * @author ven
 */
public interface PsiClassOwner {
  /**
   * @return classes owned by this element.
   */
  @NotNull
  PsiClass[] getClasses();
}
