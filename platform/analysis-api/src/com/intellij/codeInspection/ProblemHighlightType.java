// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

/**
 * Defines type used to determine highlighting of {@link ProblemDescriptor}.
 * Please use {@link #GENERIC_ERROR_OR_WARNING}, otherwise user's settings would be ignored.
 * <p/>
 * If you need specific text attributes in the editor, please use {@link InspectionProfileEntry#getEditorAttributesKey()} instead.
 * @see com.intellij.codeInspection.ProblemDescriptorUtil#getHighlightInfoType
 */
public enum ProblemHighlightType {

  /**
   * Underlying highlighting with color depending on the inspection {@link com.intellij.codeHighlighting.HighlightDisplayLevel}.
   */
  GENERIC_ERROR_OR_WARNING,

  /**
   * Changes font color depending on the inspection {@link com.intellij.codeHighlighting.HighlightDisplayLevel}.
   */
  LIKE_UNKNOWN_SYMBOL,

  LIKE_DEPRECATED,

  LIKE_UNUSED_SYMBOL,

  /**
   * The same as {@link #LIKE_UNKNOWN_SYMBOL} with enforced {@link com.intellij.codeHighlighting.HighlightDisplayLevel#ERROR} severity level.
   */
  ERROR,

  /**
   * Enforces {@link com.intellij.codeHighlighting.HighlightDisplayLevel#WARNING} severity level.
   */
  WARNING,

  /**
   * The same as {@link #GENERIC_ERROR_OR_WARNING} with enforced {@link com.intellij.codeHighlighting.HighlightDisplayLevel#ERROR} severity level.
   */
  GENERIC_ERROR,

  /**
   * Enforces {@link com.intellij.codeHighlighting.HighlightDisplayLevel#INFO} severity level.
   *
   * @deprecated use {@link #WEAK_WARNING} instead
   */
  @Deprecated(forRemoval = true)
  INFO,

  /**
   * Enforces {@link com.intellij.codeHighlighting.HighlightDisplayLevel#WEAK_WARNING} severity level.
   */
  WEAK_WARNING,

  /**
   * Enforces {@link com.intellij.codeHighlighting.HighlightDisplayLevel#DO_NOT_SHOW} severity level.
   * Please ensure that if used from inspection explicitly, corresponding problem is added in {@code onTheFly} mode only.
   */
  INFORMATION,

  /**
   * JEP 277 enhanced deprecation.
   */
  LIKE_MARKED_FOR_REMOVAL,

  /**
   * Marks places which were not checked during local analysis e.g., because of performance considerations. 
   * These places won't be anyhow highlighted in the editor though Redundant suppression inspection would skip reporting. 
   */
  POSSIBLE_PROBLEM
}
