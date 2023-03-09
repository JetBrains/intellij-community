// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.options;

import org.jetbrains.annotations.NotNull;

/**
 * Represents a custom control that can be rendered by some UI provides in non-specified way.
 * 
 * @param componentId ID of component, to instantiate the corresponding renderer
 * @param data component-specific data string, to assist the rendering
 */
public record OptCustom(@NotNull String componentId, @NotNull String data) implements OptRegularComponent {
  public OptCustom(@NotNull String componentId) {
    this(componentId, "");
  }
}
