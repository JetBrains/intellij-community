// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.colors.impl;

import org.jetbrains.annotations.NotNull;

/**
 * Stands for a parent scheme that a read cannot find.
 * Its name is the name of that parent scheme, so a write of the child scheme keeps the parent name.
 * {@link AbstractColorsScheme#resolveParent} replaces it with the real parent scheme.
 */
final class TemporaryParent extends EditorColorsSchemeImpl {
  TemporaryParent(@NotNull String parentName) {
    super(EmptyColorScheme.getEmptyScheme());
    setName(parentName);
  }
}
