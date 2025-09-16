// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.javadoc;

import com.intellij.psi.PsiClass;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a fragment reference, for instance {@code Foo##my-id}.
 */
public interface PsiDocFragmentRef extends PsiDocTagValue {

  /**
   * Returns the class that contains the fragment reference.
   */
  @Nullable PsiClass getScope();

}
