// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.search;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author ignatov
 */
public abstract class ActionFromOptionDescriptorProvider {
  public static final ExtensionPointName<ActionFromOptionDescriptorProvider> EP =
    new ExtensionPointName<>("com.intellij.actionFromOptionDescriptorProvider");

  public abstract @Nullable AnAction provide(@NotNull OptionDescription description);
}
