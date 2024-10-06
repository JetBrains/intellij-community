// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Hides some of the plugin New File... actions depending on context. For instance, web development templates in Java source sets.
 */
@ApiStatus.Experimental
public interface NewFileActionCategoryHandler {
  @NotNull ThreeState isVisible(@NotNull DataContext dataContext, @NotNull String category);
}
