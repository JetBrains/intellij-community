package org.jetbrains.jps.model.module;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.JpsTypedElement;

/**
 * @author nik
 */
public interface JpsTypedModuleSourceRoot<P extends JpsElement> extends JpsModuleSourceRoot, JpsTypedElement<P> {
  @NotNull
  @Override
  P getProperties();

  @NotNull
  @Override
  JpsModuleSourceRootType<P> getRootType();
}
