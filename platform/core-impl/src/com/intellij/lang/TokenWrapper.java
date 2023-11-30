// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * @author max
 */
package com.intellij.lang;

import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

public class TokenWrapper extends IElementType {
  private final IElementType myDelegate;
  private final @NotNull String myValue;

  public TokenWrapper(@NotNull IElementType delegate, @NotNull CharSequence value) {
    super("Wrapper", delegate.getLanguage(), false);
    myDelegate = delegate;
    myValue = value.toString();
  }

  public @NotNull IElementType getDelegate() {
    return myDelegate;
  }

  public @NotNull String getValue() {
    return myValue;
  }

  @Override
  public String toString() {
    return "Wrapper (" + myDelegate + ")";
  }
}
