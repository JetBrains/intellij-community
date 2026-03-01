// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.options;

import org.jetbrains.annotations.NotNull;

/**
 * Represents a custom control that can be rendered by a UI provider in a non-specified way.
 * The actual rendering is delegated to a {@link com.intellij.codeInspection.ui.CustomComponentExtension}
 * registered with the matching {@code componentId}.
 * 
 * @param componentId ID of component, to instantiate the corresponding renderer
 * @param data component-specific data string, to assist the rendering
 * @param splitLabel label to display alongside the custom component
 */
public record OptCustom(@NotNull String componentId, @NotNull String data, @NotNull LocMessage splitLabel) implements OptRegularComponent {
  public OptCustom(@NotNull String componentId) {
    this(componentId, "", LocMessage.empty());
  }

  public OptCustom(@NotNull String componentId, @NotNull String data) {
    this(componentId, data, LocMessage.empty());
  }
}
