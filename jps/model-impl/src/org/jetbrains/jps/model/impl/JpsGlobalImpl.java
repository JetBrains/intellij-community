package org.jetbrains.jps.model.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.*;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.JpsLibraryCollection;
import org.jetbrains.jps.model.library.JpsLibraryType;
import org.jetbrains.jps.model.library.impl.JpsLibraryCollectionImpl;
import org.jetbrains.jps.model.library.impl.JpsLibraryKind;

/**
 * @author nik
 */
public class JpsGlobalImpl extends JpsRootElementBase<JpsGlobalImpl> implements JpsGlobal {
  private final JpsLibraryCollectionImpl myLibraryCollection;

  public JpsGlobalImpl(JpsModel model, JpsEventDispatcher eventDispatcher) {
    super(model, eventDispatcher);
    myLibraryCollection = new JpsLibraryCollectionImpl(myContainer.setChild(JpsLibraryKind.LIBRARIES_COLLECTION_KIND));
  }

  public JpsGlobalImpl(JpsGlobalImpl original, JpsModel model, JpsEventDispatcher eventDispatcher) {
    super(original, model, eventDispatcher);
    myLibraryCollection = new JpsLibraryCollectionImpl(myContainer.getChild(JpsLibraryKind.LIBRARIES_COLLECTION_KIND));
  }

  @NotNull
  @Override
  public
  <P extends JpsElementProperties, LibraryType extends JpsLibraryType<P> & JpsElementTypeWithDefaultProperties<P>>
  JpsLibrary addLibrary(@NotNull LibraryType libraryType, @NotNull final String name) {
    return myLibraryCollection.addLibrary(name, libraryType);
  }

  @NotNull
  @Override
  public JpsLibraryCollection getLibraryCollection() {
    return myLibraryCollection;
  }

  @NotNull
  @Override
  public JpsElementReference<JpsGlobal> createReference() {
    return new JpsGlobalElementReference();
  }
}
