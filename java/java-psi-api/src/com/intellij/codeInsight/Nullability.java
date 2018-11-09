// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight;

/**
 * Represents a nullability (ability to hold, accept or return null) for variables, fields, method parameters, method return values,
 * type parameters and type arguments.
 */
public enum Nullability {
  /**
   * Value which cannot hold null; method which cannot return null; parameter which cannot accept null
   */
  NOT_NULL,
  /**
   * Value which holds null; method which can return null; parameter which accepts null
   */
  NULLABLE,
  /**
   * Nullability is not yet defined or "weak nullability" (e.g. method may return null, but often it can be determined
   * in some other way whether null is possible here, so null-check is not strictly necessary).
   */
  UNKNOWN
}
