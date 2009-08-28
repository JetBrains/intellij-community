package com.intellij.refactoring.changeSignature;

import org.jetbrains.annotations.Nullable;

/**
 * Represents a parameter of a method affected by the "Change Signature" refactoring.
 *
 * @author yole
 * @since 8.1
 */
public interface ParameterInfo {
  /**
   * Returns the name of the parameter after the refactoring.
   *
   * @return parameter name.
   */
  String getName();

  /**
   * Returns the index of the parameter in the old parameter list, or -1 if the parameter
   * was added by the refactoring.
   *
   * @return old parameter index, or -1.
   */
  int getOldIndex();

  /**
   * For added parameters, returns the string representation of the default parameter value.
   *
   * @return default value, or null if the parameter wasn't added.
   */
  @Nullable
  String getDefaultValue();
}
