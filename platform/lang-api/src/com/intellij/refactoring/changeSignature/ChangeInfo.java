package com.intellij.refactoring.changeSignature;

import org.jetbrains.annotations.NotNull;

/**
 * Represents the set of changes performed by a "Change Signature" refactoring.
 *
 * @author yole
 * @since 8.1
 */
public interface ChangeInfo {
  /**
   * Returns the list of parameters after the refactoring.
   *
   * @return parameter list.
   */
  @NotNull
  ParameterInfo[] getNewParameters();

  boolean isParameterSetOrOrderChanged();
}
