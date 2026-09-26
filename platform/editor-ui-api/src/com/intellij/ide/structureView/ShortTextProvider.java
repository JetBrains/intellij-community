// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.structureView;

import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/**
 * Allows specifying short text separately from its {@link ItemPresentation} for {@link StructureViewTreeElement}.
 */
@ApiStatus.Internal
public interface ShortTextProvider extends StructureViewTreeElement {
  /**
   * Returns short name of an element.
   */
  default @NlsSafe @Nullable String getShortText() {
    return null;
  }
}
