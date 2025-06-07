// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * @author max
 */
package com.intellij.lang;

import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

/**
 * Allows replacing text in a given node.
 * Useful for string-preprocessor-like macro support
 */
public class TokenWrapper extends IElementType {
  private final IElementType myDelegate;
  private final @NotNull String myText;

  public TokenWrapper(@NotNull IElementType delegate, @NotNull CharSequence text) {
    super("Wrapper", delegate.getLanguage(), false);
    myDelegate = delegate;
    myText = text.toString();
  }

  public @NotNull IElementType getDelegate() {
    return myDelegate;
  }

  /**
   * @deprecated Use {@link #getText()} instead as it's name is more descriptive.
   */
  @Deprecated
  public @NotNull String getValue() {
    return getText();
  }

  public @NotNull String getText() {
    return myText;
  }

  @Override
  public String toString() {
    return "Wrapper (" + myDelegate + ")";
  }
}
