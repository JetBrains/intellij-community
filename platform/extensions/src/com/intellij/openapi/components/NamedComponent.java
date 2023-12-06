// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.components;

import org.jetbrains.annotations.NotNull;

/**
 * @deprecated Components are deprecated, please see <a href="https://plugins.jetbrains.com/docs/intellij/plugin-components.html">SDK Docs</a> for guidelines on migrating to other APIs.
 */
@Deprecated
public interface NamedComponent {
  /**
   * No need to implement.
   *
   * Unique name of this component. If there is another component with the same name or
   * name is null internal assertion will occur.
   */
  default @NotNull String getComponentName() {
    return getClass().getName();
  }
}
