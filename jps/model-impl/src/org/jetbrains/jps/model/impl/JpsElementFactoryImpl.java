package org.jetbrains.jps.model.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.*;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.JpsLibraryReference;
import org.jetbrains.jps.model.library.JpsLibraryType;
import org.jetbrains.jps.model.library.impl.JpsLibraryImpl;
import org.jetbrains.jps.model.library.impl.JpsLibraryReferenceImpl;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.module.JpsModuleReference;
import org.jetbrains.jps.model.module.JpsModuleType;
import org.jetbrains.jps.model.module.impl.JpsModuleImpl;
import org.jetbrains.jps.model.module.impl.JpsModuleReferenceImpl;

/**
 * @author nik
 */
public class JpsElementFactoryImpl extends JpsElementFactory {

  @Override
  public JpsModule createModule(String name, JpsModuleType<?> type) {
    return new JpsModuleImpl(type, name);
  }

  @Override
  public JpsLibrary createLibrary(@NotNull String name, @NotNull JpsLibraryType<?> type) {
    return new JpsLibraryImpl(name, type);
  }

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

  @NotNull
  @Override
  public JpsElementReference<JpsProject> createProjectReference() {
    return new JpsProjectElementReference();
  }

  @NotNull
  @Override
  public JpsElementReference<JpsGlobal> createGlobalReference() {
    return new JpsGlobalElementReference();
  }
}
