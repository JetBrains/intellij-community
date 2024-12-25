// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.impl;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.*;
import org.jetbrains.jps.model.ex.JpsReferenceCustomFactory;
import org.jetbrains.jps.model.library.JpsLibraryReference;
import org.jetbrains.jps.model.library.JpsLibraryType;
import org.jetbrains.jps.model.library.JpsTypedLibrary;
import org.jetbrains.jps.model.library.impl.JpsLibraryImpl;
import org.jetbrains.jps.model.library.impl.JpsLibraryReferenceImpl;
import org.jetbrains.jps.model.library.impl.JpsSdkReferenceImpl;
import org.jetbrains.jps.model.library.impl.sdk.JpsSdkImpl;
import org.jetbrains.jps.model.library.sdk.JpsSdk;
import org.jetbrains.jps.model.library.sdk.JpsSdkReference;
import org.jetbrains.jps.model.library.sdk.JpsSdkType;
import org.jetbrains.jps.model.module.*;
import org.jetbrains.jps.model.module.impl.JpsModuleImpl;
import org.jetbrains.jps.model.module.impl.JpsModuleReferenceImpl;
import org.jetbrains.jps.model.module.impl.JpsModuleSourceRootImpl;
import org.jetbrains.jps.service.JpsServiceManager;

@ApiStatus.Internal
public final class JpsElementFactoryImpl extends JpsElementFactory {
  private volatile Boolean hasCustomReferenceFactory;
  
  @Override
  public JpsModel createModel() {
    return new JpsModelImpl();
  }

  @Override
  public <P extends JpsElement> JpsModule createModule(@NotNull String name, @NotNull JpsModuleType<P> type, @NotNull P properties) {
    return new JpsModuleImpl<>(type, name, properties);
  }


  @Override
  public <P extends JpsElement> JpsTypedLibrary<P> createLibrary(@NotNull String name,
                                                                   @NotNull JpsLibraryType<P> type,
                                                                   @NotNull P properties) {
    return new JpsLibraryImpl<>(name, type, properties);
  }

  @Override
  public <P extends JpsElement> JpsTypedLibrary<JpsSdk<P>> createSdk(@NotNull String name, @Nullable String homePath,
                                                                     @Nullable String versionString, @NotNull JpsSdkType<P> type,
                                                                     @NotNull P properties) {
    return createLibrary(name, type, new JpsSdkImpl<>(homePath, versionString, type, properties));
  }

  @Override
  public @NotNull <P extends JpsElement> JpsModuleSourceRoot createModuleSourceRoot(@NotNull String url,
                                                                                    @NotNull JpsModuleSourceRootType<P> type,
                                                                                    @NotNull P properties) {
    return new JpsModuleSourceRootImpl<>(url, type, properties);
  }

  @Override
  public @NotNull JpsModuleReference createModuleReference(@NotNull String moduleName) {
    if (hasCustomReferenceFactory()) {
      for (JpsReferenceCustomFactory extension : JpsServiceManager.getInstance().getExtensions(JpsReferenceCustomFactory.class)) {
        if (extension.isEnabled()) {
          return extension.createModuleReference(moduleName);
        }
      }
    }
    return new JpsModuleReferenceImpl(moduleName);
  }

  @Override
  public @NotNull JpsLibraryReference createLibraryReference(@NotNull String libraryName,
                                                             @NotNull JpsElementReference<? extends JpsCompositeElement> parentReference) {
    if (hasCustomReferenceFactory()) {
      for (JpsReferenceCustomFactory extension : JpsServiceManager.getInstance().getExtensions(JpsReferenceCustomFactory.class)) {
        if (extension.isEnabled()) {
          return extension.createLibraryReference(libraryName, parentReference);
        }
      }
    }
    return new JpsLibraryReferenceImpl(libraryName, parentReference);
  }

  @Override
  public @NotNull <P extends JpsElement> JpsSdkReference<P> createSdkReference(@NotNull String sdkName, @NotNull JpsSdkType<P> sdkType) {
    return new JpsSdkReferenceImpl<>(sdkName, sdkType, createGlobalReference());
  }

  private boolean hasCustomReferenceFactory() {
    if (hasCustomReferenceFactory == null) {
      boolean hasEnabledFactory = false;
      for (JpsReferenceCustomFactory extension : JpsServiceManager.getInstance().getExtensions(JpsReferenceCustomFactory.class)) {
        if (extension.isEnabled()) {
          hasEnabledFactory = true;
          break;
        }
      }
      hasCustomReferenceFactory = hasEnabledFactory;
    }
    return hasCustomReferenceFactory;
  }
  
  @Override
  public @NotNull JpsElementReference<JpsProject> createProjectReference() {
    return new JpsProjectElementReference();
  }

  @Override
  public @NotNull JpsElementReference<JpsGlobal> createGlobalReference() {
    return new JpsGlobalElementReference();
  }

  @Override
  public @NotNull JpsDummyElement createDummyElement() {
    return new JpsDummyElementImpl();
  }

  @Override
  public @NotNull <D> JpsSimpleElement<D> createSimpleElement(@NotNull D data) {
    return new JpsSimpleElementImpl<>(data);
  }
}
