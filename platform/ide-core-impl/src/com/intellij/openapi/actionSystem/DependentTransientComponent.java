// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * Transient components like popup contents can retarget
 * {@link com.intellij.ide.DataManager#getDataContext(Component)} requests
 * to their more stable counterpart components.
 */
@ApiStatus.Experimental
public interface DependentTransientComponent {
  @NotNull Component getPermanentComponent();
}