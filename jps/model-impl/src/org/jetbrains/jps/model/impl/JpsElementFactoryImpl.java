package org.jetbrains.jps.model.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.*;
import org.jetbrains.jps.model.library.JpsLibraryReference;
import org.jetbrains.jps.model.library.JpsLibraryType;
import org.jetbrains.jps.model.library.JpsSdkType;
import org.jetbrains.jps.model.library.JpsTypedLibrary;
import org.jetbrains.jps.model.library.impl.JpsLibraryImpl;
import org.jetbrains.jps.model.library.impl.JpsLibraryReferenceImpl;
import org.jetbrains.jps.model.library.impl.JpsSdkReferenceImpl;
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
  public JpsModel createModel() {
    return new JpsModelImpl(new JpsEventDispatcherBase() {
      @Override
      public void fireElementRenamed(@NotNull JpsNamedElement element, @NotNull String oldName, @NotNull String newName) {
      }

      @Override
      public void fireElementChanged(@NotNull JpsElement element) {
      }
    });
  }

  @Override
  public <P extends JpsElementProperties> JpsModule createModule(@NotNull String name, @NotNull JpsModuleType<P> type, @NotNull P properties) {
    return new JpsModuleImpl(type, name, properties);
  }


  @Override
  public <P extends JpsElementProperties> JpsTypedLibrary<P> createLibrary(@NotNull String name,
                                                                   @NotNull JpsLibraryType<P> type,
                                                                   @NotNull P properties) {
    return new JpsLibraryImpl<P>(name, type, properties);
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
  public JpsLibraryReference createSdkReference(@NotNull String sdkName, @NotNull JpsSdkType<?> sdkType) {
    return new JpsSdkReferenceImpl(sdkName, sdkType, createGlobalReference());
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
