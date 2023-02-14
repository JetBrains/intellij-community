// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.restriction;

import org.jetbrains.annotations.NotNull;

/**
 * Marker interface for lattice elements that provide restrictions on some named psi element (variable, class, method...)
 * 
 * For example, method can be marked with annotation @Tainted which means that this method produces unsafe strings.
 * Another example is when method does not have any annotations, but we can infer that it may return unsafe string.
 * In both cases we may have restriction info TAINTED associated with these methods. 
 */
public interface RestrictionInfo {

  @NotNull RestrictionInfoKind getKind();
  
  enum RestrictionInfoKind {
    /**
     * Restriction info is explicitly specified (e.g. annotated method)
     */
    KNOWN,
    /**
     * Restriction info that is not known right now and cannot be inferred
     */
    UNKNOWN,
    /**
     * Restriction info that may be inferred in the future
     */
    UNSPECIFIED
  }
}
