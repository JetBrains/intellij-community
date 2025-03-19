// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeserver.highlighting.errors;

/**
 * Highlighting types for Java error messages
 */
public enum JavaErrorHighlightType {
  /**
   * Default error highlighting
   */
  ERROR,

  /**
   * Error highlighting applied for the whole file
   */
  FILE_LEVEL_ERROR,

  /**
   * Error highlighting for unresolved/unknown reference
   */
  WRONG_REF,

  /**
   * Unresolved/unknown reference in incomplete project mode 
   * (reference that potentially can be resolved when full project is loaded)
   */
  PENDING_REF,

  /**
   * Error highlighting for unhandled exception
   */
  UNHANDLED_EXCEPTION
}
