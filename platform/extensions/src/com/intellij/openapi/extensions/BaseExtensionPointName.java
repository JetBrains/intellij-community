// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.extensions;

import org.jetbrains.annotations.NotNull;

public abstract class BaseExtensionPointName {
  private final String myName;

  public BaseExtensionPointName(@NotNull String name) {
    myName = name;
  }

  @NotNull
  public final String getName() {
    return myName;
  }

  @Override
  public final String toString() {
    return myName;
  }
}
