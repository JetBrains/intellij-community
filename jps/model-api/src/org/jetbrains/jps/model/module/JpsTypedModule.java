package org.jetbrains.jps.model.module;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElement;

/**
 * @author nik
 */
public interface JpsTypedModule<P extends JpsElement> extends JpsModule {
  @NotNull
  JpsModuleType<P> getModuleType();

  @NotNull
  P getProperties();
}
