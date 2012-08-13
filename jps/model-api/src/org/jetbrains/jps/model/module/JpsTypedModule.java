package org.jetbrains.jps.model.module;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.JpsTypedElement;

/**
 * @author nik
 */
public interface JpsTypedModule<P extends JpsElement> extends JpsModule, JpsTypedElement<P> {
  @NotNull
  JpsModuleType<P> getModuleType();

  @NotNull
  @Override
  P getProperties();
}
