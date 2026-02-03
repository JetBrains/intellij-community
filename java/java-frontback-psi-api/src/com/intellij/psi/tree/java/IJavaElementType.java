// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.tree.java;

import com.intellij.lang.java.JavaLanguage;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NonNls;

public class IJavaElementType extends IElementType {
  private final boolean myLeftBound;

  public IJavaElementType(final @NonNls String debugName) {
    this(debugName, false);
  }

  public IJavaElementType(final @NonNls String debugName, final boolean leftBound) {
    super(debugName, JavaLanguage.INSTANCE);
    myLeftBound = leftBound;
  }

  @Override
  public boolean isLeftBound() {
    return myLeftBound;
  }
}