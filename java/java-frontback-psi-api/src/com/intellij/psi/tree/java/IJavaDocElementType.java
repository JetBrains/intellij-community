// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.tree.java;

import com.intellij.lang.java.JavaLanguage;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NonNls;

public class IJavaDocElementType extends IElementType {
  public IJavaDocElementType(final @NonNls String debugName) {
    super(debugName, JavaLanguage.INSTANCE);
  }
}