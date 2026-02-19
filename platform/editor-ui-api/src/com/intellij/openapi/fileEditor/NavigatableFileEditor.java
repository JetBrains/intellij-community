// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.fileEditor;

import com.intellij.pom.Navigatable;
import org.jetbrains.annotations.NotNull;

/**
 * File editor that supports navigating to {@link Navigatable} elements.
 *
 * @see FileEditorNavigatable
 */
public interface NavigatableFileEditor extends FileEditor {

  /**
   * Check whether the editor can navigate to the given element.
   *
   * @return true if editor can navigate, false otherwise
   */
  boolean canNavigateTo(final @NotNull Navigatable navigatable);

  /**
   * Navigate the editor to the given navigatable if {@link #canNavigateTo(Navigatable)} is true
   *
   * @param navigatable navigation target
   */
  void navigateTo(final @NotNull Navigatable navigatable);
}
