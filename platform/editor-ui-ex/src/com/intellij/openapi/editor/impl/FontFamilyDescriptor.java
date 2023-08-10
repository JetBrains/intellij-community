// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import org.jetbrains.annotations.NotNull;

public class FontFamilyDescriptor {
  private final String myFamily;
  private final String mySubfamily;

  FontFamilyDescriptor(@NotNull String family, @NotNull String subfamily) {
    myFamily = family;
    mySubfamily = subfamily;
  }

  public @NotNull String getFamily() {
    return myFamily;
  }

  public @NotNull String getSubfamily() {
    return mySubfamily;
  }

  public static @NotNull FontFamilyDescriptor create(@NotNull String family, @NotNull String subfamily) {
    return new FontFamilyDescriptor(family, subfamily);
  }
}
