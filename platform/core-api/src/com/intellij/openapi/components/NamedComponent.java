package com.intellij.openapi.components;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public interface NamedComponent {
  /**
   * Unique name of this component. If there is another component with the same name or
   * name is null internal assertion will occur.
   *
   * @return the name of this component
   */
  @NonNls
  @NotNull
  String getComponentName();
}
