package org.jetbrains.jps.model.library.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElementCollection;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.JpsLibraryCollection;
import org.jetbrains.jps.model.library.JpsLibraryType;

import java.util.List;

/**
 * @author nik
 */
public class JpsLibraryCollectionImpl implements JpsLibraryCollection {
  private final JpsElementCollection<JpsLibrary> myCollection;

  public JpsLibraryCollectionImpl(JpsElementCollection<JpsLibrary> collection) {
    myCollection = collection;
  }

  @NotNull
  @Override
  public JpsLibrary addLibrary(@NotNull JpsLibraryType<?> libraryType, @NotNull String name) {
    return myCollection.addChild(new JpsLibraryImpl(name, libraryType));
  }

  @NotNull
  @Override
  public List<JpsLibrary> getLibraries() {
    return myCollection.getElements();
  }

  @Override
  public void addLibrary(@NotNull JpsLibrary library) {
    myCollection.addChild(library);
  }
}
