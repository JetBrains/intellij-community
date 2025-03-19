// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Marks actions in New File... popup with a named category.
 */
@ApiStatus.Experimental
public interface NewFileActionWithCategory {
  @NotNull String getCategory();
}
