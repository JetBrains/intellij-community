// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.colors.impl;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

class TemporaryParent extends EditorColorsSchemeImpl {
  private final String myParentName;

  TemporaryParent(@NotNull String parentName) {
    super(EmptyColorScheme.getEmptyScheme());
    myParentName = parentName;
  }

  public @NonNls String getParentName() {
    return myParentName;
  }
}
