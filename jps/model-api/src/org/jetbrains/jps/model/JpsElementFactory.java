// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.library.JpsLibraryReference;
import org.jetbrains.jps.model.library.JpsLibraryType;
import org.jetbrains.jps.model.library.JpsTypedLibrary;
import org.jetbrains.jps.model.library.sdk.JpsSdk;
import org.jetbrains.jps.model.library.sdk.JpsSdkReference;
import org.jetbrains.jps.model.library.sdk.JpsSdkType;
import org.jetbrains.jps.model.module.*;
import org.jetbrains.jps.service.JpsServiceManager;

public abstract class JpsElementFactory {
  public static JpsElementFactory getInstance() {
    return JpsServiceManager.getInstance().getService(JpsElementFactory.class);
  }

  @ApiStatus.Internal
  protected JpsElementFactory() {
  }

  /**
   * JpsModel API isn't supposed to be used for creating model from scratch, use 
   * {@link org.jetbrains.jps.model.serialization.JpsSerializationManager} to load the model from .idea directory.
   */
  @ApiStatus.Internal
  public abstract JpsModel createModel();

  /**
   * JpsModel API isn't supposed to be used for creating model from scratch, use
   * {@link org.jetbrains.jps.model.serialization.JpsSerializationManager} to load the model from .idea directory.
   */
  @ApiStatus.Internal
  public abstract <P extends JpsElement> JpsModule createModule(@NotNull String name, @NotNull JpsModuleType<P> type, @NotNull P properties);

  /**
   * JpsModel API isn't supposed to be used for creating model from scratch, use
   * {@link org.jetbrains.jps.model.serialization.JpsSerializationManager} to load the model from .idea directory.
   */
  @ApiStatus.Internal
  public abstract <P extends JpsElement> JpsTypedLibrary<P> createLibrary(@NotNull String name, @NotNull JpsLibraryType<P> type, @NotNull P properties);

  /**
   * JpsModel API isn't supposed to be used for creating model from scratch, use
   * {@link org.jetbrains.jps.model.serialization.JpsSerializationManager} to load the model from .idea directory.
   */
  @ApiStatus.Internal
  public abstract <P extends JpsElement> JpsTypedLibrary<JpsSdk<P>> createSdk(@NotNull String name, @Nullable String homePath, @Nullable String versionString,
                                                                              @NotNull JpsSdkType<P> type, @NotNull P properties);

  /**
   * JpsModel API isn't supposed to be used for creating model from scratch, use
   * {@link org.jetbrains.jps.model.serialization.JpsSerializationManager} to load the model from .idea directory.
   */
  @ApiStatus.Internal
  public abstract @NotNull <P extends JpsElement> JpsModuleSourceRoot createModuleSourceRoot(@NotNull String url, @NotNull JpsModuleSourceRootType<P> type, @NotNull P properties);

  public abstract @NotNull JpsModuleReference createModuleReference(@NotNull String moduleName);

  public abstract @NotNull JpsLibraryReference createLibraryReference(@NotNull String libraryName,
                                                                      @NotNull JpsElementReference<? extends JpsCompositeElement> parentReference);

  /**
   * JpsModel API isn't supposed to be used for creating model from scratch, use
   * {@link org.jetbrains.jps.model.serialization.JpsSerializationManager} to load the model from .idea directory.
   */
  @ApiStatus.Internal
  public abstract @NotNull <P extends JpsElement> JpsSdkReference<P> createSdkReference(@NotNull String sdkName,
                                                                                                @NotNull JpsSdkType<P> sdkType);

  /**
   * JpsModel API isn't supposed to be used for creating model from scratch, use
   * {@link org.jetbrains.jps.model.serialization.JpsSerializationManager} to load the model from .idea directory.
   */
  @ApiStatus.Internal
  public abstract @NotNull JpsElementReference<JpsProject> createProjectReference();

  /**
   * JpsModel API isn't supposed to be used for creating model from scratch, use
   * {@link org.jetbrains.jps.model.serialization.JpsSerializationManager} to load the model from .idea directory.
   */
  @ApiStatus.Internal
  public abstract @NotNull JpsElementReference<JpsGlobal> createGlobalReference();

  public abstract @NotNull JpsDummyElement createDummyElement();

  public abstract @NotNull <D> JpsSimpleElement<D> createSimpleElement(@NotNull D data);
}
