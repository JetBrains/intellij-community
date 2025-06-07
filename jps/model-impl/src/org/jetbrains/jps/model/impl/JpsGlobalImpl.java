// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.JpsElementFactory;
import org.jetbrains.jps.model.JpsElementTypeWithDefaultProperties;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.JpsLibraryCollection;
import org.jetbrains.jps.model.library.JpsLibraryType;
import org.jetbrains.jps.model.library.JpsTypedLibrary;
import org.jetbrains.jps.model.library.impl.JpsLibraryCollectionImpl;
import org.jetbrains.jps.model.library.impl.JpsLibraryRole;
import org.jetbrains.jps.model.library.sdk.JpsSdk;
import org.jetbrains.jps.model.library.sdk.JpsSdkType;

final class JpsGlobalImpl extends JpsGlobalBase {
  private final JpsLibraryCollectionImpl myLibraryCollection;

  JpsGlobalImpl(@NotNull JpsModel model) {
    super(model);
    myLibraryCollection = new JpsLibraryCollectionImpl(myContainer.setChild(JpsLibraryRole.LIBRARIES_COLLECTION_ROLE));
  }

  @Override
  public @NotNull
  <P extends JpsElement, LibraryType extends JpsLibraryType<P> & JpsElementTypeWithDefaultProperties<P>>
  JpsLibrary addLibrary(@NotNull LibraryType libraryType, final @NotNull String name) {
    return myLibraryCollection.addLibrary(name, libraryType);
  }

  @Override
  public <P extends JpsElement> JpsTypedLibrary<JpsSdk<P>> addSdk(@NotNull String name, @Nullable String homePath,
                                                                  @Nullable String versionString, @NotNull JpsSdkType<P> type,
                                                                  @NotNull P properties) {
    JpsTypedLibrary<JpsSdk<P>> sdk = JpsElementFactory.getInstance().createSdk(name, homePath, versionString, type, properties);
    myLibraryCollection.addLibrary(sdk);
    return sdk;
  }

  @Override
  public <P extends JpsElement, SdkType extends JpsSdkType<P> & JpsElementTypeWithDefaultProperties<P>> JpsTypedLibrary<JpsSdk<P>>
  addSdk(@NotNull String name, @Nullable String homePath, @Nullable String versionString, @NotNull SdkType type) {
    return addSdk(name, homePath, versionString, type, type.createDefaultProperties());
  }

  @Override
  public @NotNull JpsLibraryCollection getLibraryCollection() {
    return myLibraryCollection;
  }
}
