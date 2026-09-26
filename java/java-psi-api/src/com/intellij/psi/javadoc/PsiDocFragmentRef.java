// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.javadoc;

import com.intellij.psi.PsiClass;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a fragment reference, for instance {@code Foo##my-id}.
 */
public interface PsiDocFragmentRef extends PsiDocTagValue {

  /**
   * Returns the class that contains the fragment reference, e.g. {@code Foo} in {@code Foo##my-id}.
   */
  @Nullable PsiClass getScope();

  /**
   * Returns the name of the fragment reference, e.g. {@code my-id} in {@code Foo##my-id}.
   */
  @Nullable PsiDocFragmentName getFragmentName();

}
