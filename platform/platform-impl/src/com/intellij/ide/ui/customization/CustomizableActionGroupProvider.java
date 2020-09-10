// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.customization;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * This extension allows to add an action group to the list of customizable actions in Settings | Menus and Toolbars.
 * Use {@code CustomActionsSchema.getInstance().getCorrectedAction(id)} to get customized version of a group.
 */
public abstract class CustomizableActionGroupProvider {
  public static final ExtensionPointName<CustomizableActionGroupProvider> EP_NAME = ExtensionPointName.create("com.intellij.customizableActionGroupProvider");

  public abstract void registerGroups(CustomizableActionGroupRegistrar registrar);

  public interface CustomizableActionGroupRegistrar {
    void addCustomizableActionGroup(@NotNull String groupId, @NotNull @Nls String groupTitle);
  }
}
