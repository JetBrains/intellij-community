// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.components;

import org.jetbrains.annotations.NotNull;

public interface NamedComponent {
  /**
   * No need to implement.
   *
   * Unique name of this component. If there is another component with the same name or
   * name is null internal assertion will occur.
   */
  @NotNull
  default String getComponentName() {
    return getClass().getName();
  }
}
