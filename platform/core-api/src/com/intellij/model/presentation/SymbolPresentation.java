// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.presentation;

import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

import static org.jetbrains.annotations.Nls.Capitalization.Sentence;

/**
 * Minimal set of data needed to present the symbol in the UI.
 * Methods of this interface are called outside of the {@link com.intellij.openapi.application.Application#runReadAction read action}.
 */
public interface SymbolPresentation {

  default @Nullable Icon getIcon() {
    return null;
  }

  /**
   * @return how the symbol is named, e.g. "Foo", "bar", etc.
   */
  @NlsSafe @NotNull String getShortNameString();

  /**
   * @return string describing what is the symbol and how it's named,
   * e.g. "Method 'hello'"
   */
  @Nls(capitalization = Sentence) @NotNull String getShortDescription();

  /**
   * @return string describing what is the symbol, how it's named and where it's located,
   * e.g. "Method 'hello(String, int)' of class 'com.foo.World'"
   */
  default @NlsContexts.DetailedDescription @NotNull String getLongDescription() {
    return getShortDescription();
  }

  @Contract("_, _ -> new")
  static @NotNull SymbolPresentation create(@NlsSafe @NotNull String shortNameString,
                                            @Nls(capitalization = Sentence) @NotNull String shortDescription) {
    return create(null, shortNameString, shortDescription, shortDescription);
  }

  @Contract("_, _, _ -> new")
  static @NotNull SymbolPresentation create(@Nullable Icon icon,
                                            @NlsSafe @NotNull String shortNameString,
                                            @Nls(capitalization = Sentence) @NotNull String shortDescription) {
    return create(icon, shortNameString, shortDescription, shortDescription);
  }

  @Contract("_, _, _, _ -> new")
  static @NotNull SymbolPresentation create(@Nullable Icon icon,
                                            @NlsSafe @NotNull String shortNameString,
                                            @Nls(capitalization = Sentence) @NotNull String shortDescription,
                                            @NlsContexts.DetailedDescription @NotNull String longDescription) {
    return new SymbolPresentationImpl(icon, shortNameString, shortDescription, longDescription);
  }
}
