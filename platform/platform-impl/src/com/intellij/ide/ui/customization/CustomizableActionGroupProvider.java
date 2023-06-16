// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.customization;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * This extension allows adding an action group to the list of customizable actions in Settings | Menus and Toolbars.
 * Use {@code CustomActionsSchema.getInstance().getCorrectedAction(id)} to get a customized version of a group.
 */
public abstract class CustomizableActionGroupProvider {
  public abstract void registerGroups(CustomizableActionGroupRegistrar registrar);

  public interface CustomizableActionGroupRegistrar {
    void addCustomizableActionGroup(@NotNull String groupId, @NotNull @Nls String groupTitle);
  }
}
