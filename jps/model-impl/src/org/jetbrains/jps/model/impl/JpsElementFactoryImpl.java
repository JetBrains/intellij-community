package org.jetbrains.jps.model.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.*;
import org.jetbrains.jps.model.library.JpsLibraryReference;
import org.jetbrains.jps.model.library.impl.JpsLibraryReferenceImpl;
import org.jetbrains.jps.model.module.JpsModuleReference;
import org.jetbrains.jps.model.module.impl.JpsModuleReferenceImpl;

/**
 * @author nik
 */
public class JpsElementFactoryImpl extends JpsElementFactory {
  @NotNull
  @Override
  public JpsModuleReference createModuleReference(@NotNull String moduleName) {
    return new JpsModuleReferenceImpl(moduleName);
  }

  @NotNull
  @Override
  public JpsLibraryReference createLibraryReference(@NotNull String libraryName,
                                                    @NotNull JpsElementReference<? extends JpsCompositeElement> parentReference) {
    return new JpsLibraryReferenceImpl(libraryName, parentReference);
  }
}
