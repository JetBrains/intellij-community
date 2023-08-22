// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.options.colors;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

/**
 * @author gregsh
 */
public abstract class AbstractKeyDescriptor<T> {
  protected static class StaticSupplier implements Supplier<@Nls(capitalization = Nls.Capitalization.Sentence) String> {
    private final String myKey;

    protected StaticSupplier(@Nls(capitalization = Nls.Capitalization.Sentence) String key) {
      myKey = key;
    }

    @Override
    public String get() {
      return myKey;
    }
  }

  private final Supplier<@Nls(capitalization = Nls.Capitalization.Sentence) String> myDisplayName;
  private final T myKey;

  protected AbstractKeyDescriptor(@NotNull Supplier<@Nls(capitalization = Nls.Capitalization.Sentence) String> displayName, @NotNull T key) {
    myKey = key;
    myDisplayName = displayName;
  }

  /**
   * Returns the name of the attribute shown in the colors settings page.
   */
  public @NotNull String getDisplayName() {
    return myDisplayName.get();
  }

  /**
   * Returns the text attributes or color key for which the colors are specified.
   */
  public @NotNull T getKey() {
    return myKey;
  }
}
