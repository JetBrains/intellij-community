// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.customization;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * Allows adding an action group to the list of customizable actions in <b>Settings | Appearance & Behavior | Menus and Toolbars</b>.
 * Use {@link CustomActionsSchema#getCorrectedAction(String)} to get a customized version of a group.
 * <p>
 * Register implementations in {@code com.intellij.customizableActionGroupProvider} extension point.
 */
public abstract class CustomizableActionGroupProvider {
  public abstract void registerGroups(CustomizableActionGroupRegistrar registrar);

  public interface CustomizableActionGroupRegistrar {
    void addCustomizableActionGroup(@NotNull String groupId, @NotNull @Nls String groupTitle);
  }
}
