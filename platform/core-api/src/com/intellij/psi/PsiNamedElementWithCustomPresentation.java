// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/**
 * A named element whose user-visible name may differ from internal name returned from {@link #getName()}.
 * Some elements have {@link #getName()} returning something different from displayed to the user. For example,
 * some JVM languages implement PsiClass interface and have to return from {@link #getName()} the JVM class name,
 * to be reusable from other languages, rather than the class name like it's displayed in its original source code. 
 * <p>
 * If a named element implements this interface, UI-facing code may prefer the name returned from
 * {@link #getPresentationName()} to display it to user.
 * </p>
 */
@ApiStatus.Experimental
public interface PsiNamedElementWithCustomPresentation extends PsiNamedElement {
  /**
   * @return the name of the named element, like it's displayed in the source code;
   * null if a given element has no user-visible name.
   */
  default @NlsSafe @Nullable String getPresentationName() {
    return getName();
  }
}