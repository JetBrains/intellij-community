// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.library.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.*;
import org.jetbrains.jps.model.ex.JpsElementCollectionRole;
import org.jetbrains.jps.model.ex.JpsNamedCompositeElementBase;
import org.jetbrains.jps.model.impl.JpsElementCollectionImpl;
import org.jetbrains.jps.model.library.*;
import org.jetbrains.jps.util.JpsPathUtil;

import java.io.File;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class JpsLibraryImpl<P extends JpsElement> extends JpsNamedCompositeElementBase<JpsLibraryImpl<P>> implements JpsTypedLibrary<P> {
  private static final ConcurrentMap<JpsOrderRootType, JpsElementCollectionRole<JpsLibraryRoot>> ourRootRoles = new ConcurrentHashMap<>();
  private final JpsLibraryType<P> myLibraryType;

  public JpsLibraryImpl(@NotNull String name, @NotNull JpsLibraryType<P> type, @NotNull P properties) {
    super(name);
    myLibraryType = type;
    myContainer.setChild(myLibraryType.getPropertiesRole(), properties);
  }

  private JpsLibraryImpl(@NotNull JpsLibraryImpl<P> original) {
    super(original);
    myLibraryType = original.myLibraryType;
  }

  @Override
  public @NotNull JpsLibraryType<P> getType() {
    return myLibraryType;
  }

  @Override
  public @Nullable <P extends JpsElement> JpsTypedLibrary<P> asTyped(@NotNull JpsLibraryType<P> type) {
    //noinspection unchecked
    return myLibraryType.equals(type) ? (JpsTypedLibrary<P>)this : null;
  }

  @Override
  public @NotNull P getProperties() {
    return myContainer.getChild(myLibraryType.getPropertiesRole());
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
  public void addRoot(@NotNull File file, @NotNull JpsOrderRootType rootType) {
    addRoot(JpsPathUtil.getLibraryRootUrl(file), rootType);
  }

  @Override
  public void addRoot(final @NotNull String url, final @NotNull JpsOrderRootType rootType,
                      @NotNull JpsLibraryRoot.InclusionOptions options) {
    myContainer.getOrSetChild(getRole(rootType)).addChild(new JpsLibraryRootImpl(url, rootType, options));
  }

  @Override
  public void removeUrl(final @NotNull String url, final @NotNull JpsOrderRootType rootType) {
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

  private static JpsElementCollectionRole<JpsLibraryRoot> getRole(JpsOrderRootType type) {
    JpsElementCollectionRole<JpsLibraryRoot> role = ourRootRoles.get(type);
    if (role != null) return role;
    ourRootRoles.putIfAbsent(type, JpsElementCollectionRole.create(new JpsLibraryRootRole(type)));
    return ourRootRoles.get(type);
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
  public List<File> getFiles(final JpsOrderRootType rootType) {
    return JpsLibraryRootProcessing.convertToFiles(getRoots(rootType));
  }

  @Override
  public @NotNull List<Path> getPaths(@NotNull JpsOrderRootType rootType) {
    return JpsLibraryRootProcessing.convertToPaths(getRoots(rootType));
  }

  @Override
  public List<String> getRootUrls(JpsOrderRootType rootType) {
    return JpsLibraryRootProcessing.convertToUrls(getRoots(rootType));
  }

  @Override
  public String toString() {
    return "JpsLibraryImpl: "+getName();
  }
}