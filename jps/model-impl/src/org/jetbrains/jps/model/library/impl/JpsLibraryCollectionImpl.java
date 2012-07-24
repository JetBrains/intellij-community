package org.jetbrains.jps.model.library.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElementCollection;
import org.jetbrains.jps.model.JpsElementProperties;
import org.jetbrains.jps.model.JpsElementTypeWithDefaultProperties;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.JpsLibraryCollection;
import org.jetbrains.jps.model.library.JpsLibraryType;
import org.jetbrains.jps.model.library.JpsTypedLibrary;

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
  public <P extends JpsElementProperties, LibraryType extends JpsLibraryType<P> & JpsElementTypeWithDefaultProperties<P>>
  JpsLibrary addLibrary(@NotNull String name, @NotNull LibraryType type) {
    return addLibrary(name, type, type.createDefaultProperties());
  }

  @NotNull
  @Override
  public <P extends JpsElementProperties> JpsTypedLibrary<P> addLibrary(@NotNull String name, @NotNull JpsLibraryType<P> type,
                                                                        @NotNull P properties) {
    return myCollection.addChild(new JpsLibraryImpl<P>(name, type, properties));
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

  @Override
  public JpsLibrary findLibrary(@NotNull String name) {
    for (JpsLibrary library : getLibraries()) {
      if (name.equals(library.getName())) {
        return library;
      }
    }
    return null;
  }
}
