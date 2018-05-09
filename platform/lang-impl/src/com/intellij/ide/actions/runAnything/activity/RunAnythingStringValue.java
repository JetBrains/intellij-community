// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.runAnything.activity;

import org.jetbrains.annotations.NotNull;

public class RunAnythingStringValue {
  @NotNull private final String myDelegate;

  private RunAnythingStringValue(@NotNull String delegate) {
    myDelegate = delegate;
  }

  @NotNull
  public static RunAnythingStringValue create(@NotNull String string) {
    return new RunAnythingStringValue(string);
  }

  @NotNull
  public String getDelegate() {
    return myDelegate;
  }
}