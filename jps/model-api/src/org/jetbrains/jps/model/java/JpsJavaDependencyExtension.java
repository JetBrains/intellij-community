package org.jetbrains.jps.model.java;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElement;

/**
 * @author nik
 */
public interface JpsJavaDependencyExtension extends JpsElement {
  boolean isExported();

  void setExported(boolean exported);

  @NotNull
  JpsJavaDependencyScope getScope();

  void setScope(@NotNull JpsJavaDependencyScope scope);
}
