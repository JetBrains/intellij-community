// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.library.impl;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.jetbrains.jps.model.*;
import org.jetbrains.jps.model.ex.JpsElementCollectionRole;
import org.jetbrains.jps.model.ex.JpsNamedCompositeElementBase;
import org.jetbrains.jps.model.impl.JpsElementCollectionImpl;
import org.jetbrains.jps.model.library.*;
import org.jetbrains.jps.util.JpsPathUtil;

import java.io.File;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@ApiStatus.Internal
public final class JpsLibraryImpl<P extends JpsElement> extends JpsNamedCompositeElementBase<JpsLibraryImpl<P>> implements JpsTypedLibrary<P> {
  private static final ConcurrentMap<JpsOrderRootType, JpsElementCollectionRole<JpsLibraryRoot>> rootRoles = new ConcurrentHashMap<>();
  private final JpsLibraryType<P> libraryType;

  public JpsLibraryImpl(@NotNull String name, @NotNull JpsLibraryType<P> type, @NotNull P properties) {
    super(name);
    libraryType = type;
    myContainer.setChild(libraryType.getPropertiesRole(), properties);
  }

  private JpsLibraryImpl(@NotNull JpsLibraryImpl<P> original) {
    super(original);
    libraryType = original.libraryType;
  }

  @Override
  public @NotNull JpsLibraryType<P> getType() {
    return libraryType;
  }

  @Override
  public @Nullable <T extends JpsElement> JpsTypedLibrary<T> asTyped(@NotNull JpsLibraryType<T> type) {
    //noinspection unchecked
    return libraryType.equals(type) ? (JpsTypedLibrary<T>)this : null;
  }

  @Override
  public @NotNull P getProperties() {
    return myContainer.getChild(libraryType.getPropertiesRole());
  }

  @Override
  public @NotNull List<JpsLibraryRoot> getRoots(@NotNull JpsOrderRootType rootType) {
    final JpsElementCollection<JpsLibraryRoot> rootsCollection = myContainer.getChild(getRole(rootType));
    return rootsCollection != null ? rootsCollection.getElements() : Collections.emptyList();
  }

  @Override
  public void addRoot(@NotNull String url, @NotNull JpsOrderRootType rootType) {
    addRoot(url, rootType, JpsLibraryRoot.InclusionOptions.ROOT_ITSELF);
  }

  @Override
  public void addRoot(@NotNull Path path, @NotNull JpsOrderRootType rootType) {
    addRoot(JpsPathUtil.getLibraryRootUrl(path), rootType);
  }

  @Override
  public void addRoot(@NotNull String url,
                      @NotNull JpsOrderRootType rootType,
                      @NotNull JpsLibraryRoot.InclusionOptions options) {
    myContainer.getOrSetChild(getRole(rootType)).addChild(new JpsLibraryRootImpl(url, rootType, options));
  }

  @Override
  public void removeUrl(@NotNull String url, @NotNull JpsOrderRootType rootType) {
    final JpsElementCollection<JpsLibraryRoot> rootsCollection = myContainer.getChild(getRole(rootType));
    if (rootsCollection != null) {
      for (JpsLibraryRoot root : rootsCollection.getElements()) {
        if (root.getUrl().equals(url) && root.getRootType().equals(rootType)) {
          rootsCollection.removeChild(root);
          break;
        }
      }
    }
  }

  private static @NotNull JpsElementCollectionRole<JpsLibraryRoot> getRole(@NotNull JpsOrderRootType type) {
    return rootRoles.computeIfAbsent(type, it -> {
      return JpsElementCollectionRole.create(new JpsLibraryRootRole(it));
    });
  }

  @Override
  public void delete() {
    getParent().removeChild(this);
  }

  @Override
  public JpsElementCollectionImpl<JpsLibrary> getParent() {
    //noinspection unchecked
    return (JpsElementCollectionImpl<JpsLibrary>)myParent;
  }

  @SuppressWarnings("removal")
  @Override
  public @NotNull JpsLibraryImpl<P> createCopy() {
    return new JpsLibraryImpl<>(this);
  }

  @Override
  public @NotNull JpsLibraryReference createReference() {
    return new JpsLibraryReferenceImpl(getName(), createParentReference());
  }

  private JpsElementReference<JpsCompositeElement> createParentReference() {
    //noinspection unchecked
    return ((JpsReferenceableElement<JpsCompositeElement>)getParent().getParent()).createReference();
  }

  @Override
  public @NotNull @Unmodifiable List<File> getFiles(@NotNull JpsOrderRootType rootType) {
    return JpsLibraryRootProcessing.convertToFiles(getRoots(rootType));
  }

  @Override
  public @NotNull List<Path> getPaths(@NotNull JpsOrderRootType rootType) {
    return JpsLibraryRootProcessing.convertToPaths(getRoots(rootType));
  }

  @Override
  public List<String> getRootUrls(@NotNull JpsOrderRootType rootType) {
    return JpsLibraryRootProcessing.convertToUrls(getRoots(rootType));
  }

  @Override
  public String toString() {
    return "JpsLibraryImpl: " + getName();
  }
}