// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.library.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.JpsElementTypeWithDefaultProperties;
import org.jetbrains.jps.model.JpsNamedElementCollection;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.JpsLibraryCollection;
import org.jetbrains.jps.model.library.JpsLibraryType;
import org.jetbrains.jps.model.library.JpsTypedLibrary;

import java.util.List;

public final class JpsLibraryCollectionImpl implements JpsLibraryCollection {
  private final JpsNamedElementCollection<JpsLibrary> myCollection;

  public JpsLibraryCollectionImpl(JpsNamedElementCollection<JpsLibrary> collection) {
    myCollection = collection;
  }

  @Override
  public @NotNull <P extends JpsElement, LibraryType extends JpsLibraryType<P> & JpsElementTypeWithDefaultProperties<P>>
  JpsLibrary addLibrary(@NotNull String name, @NotNull LibraryType type) {
    return addLibrary(name, type, type.createDefaultProperties());
  }

  @Override
  public @NotNull <P extends JpsElement> JpsTypedLibrary<P> addLibrary(@NotNull String name, @NotNull JpsLibraryType<P> type,
                                                                       @NotNull P properties) {
    return myCollection.addChild(new JpsLibraryImpl<>(name, type, properties));
  }

  @Override
  public @NotNull List<JpsLibrary> getLibraries() {
    return myCollection.getElements();
  }

  @Override
  public @NotNull <P extends JpsElement> Iterable<JpsTypedLibrary<P>> getLibraries(@NotNull JpsLibraryType<P> type) {
    return myCollection.getElementsOfType(type);
  }

  @Override
  public void addLibrary(@NotNull JpsLibrary library) {
    myCollection.addChild(library);
  }

  @Override
  public void removeLibrary(@NotNull JpsLibrary library) {
    myCollection.removeChild(library);
  }

  @Override
  public JpsLibrary findLibrary(@NotNull String name) {
    return myCollection.findChild(name);
  }

  @Override
  public @Nullable <E extends JpsElement> JpsTypedLibrary<E> findLibrary(@NotNull String name, @NotNull JpsLibraryType<E> type) {
    JpsLibrary byName = myCollection.findChild(name);
    if (byName != null && type.equals(byName.getType())) {
      return byName.asTyped(type);
    }
    return null;
  }


}
