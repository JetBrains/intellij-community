package org.jetbrains.jps.model.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.*;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.JpsLibraryType;
import org.jetbrains.jps.model.library.impl.JpsLibraryImpl;
import org.jetbrains.jps.model.library.impl.JpsLibraryKind;

/**
 * @author nik
 */
public class JpsGlobalImpl extends JpsRootElementBase<JpsGlobalImpl> implements JpsGlobal {
  public JpsGlobalImpl(JpsModel model, JpsEventDispatcher eventDispatcher) {
    super(model, eventDispatcher);
    myContainer.setChild(JpsLibraryKind.LIBRARIES_COLLECTION_KIND);
  }

  public JpsGlobalImpl(JpsGlobalImpl original, JpsModel model, JpsEventDispatcher eventDispatcher) {
    super(original, model, eventDispatcher);
  }

  @NotNull
  @Override
  public JpsLibrary addLibrary(@NotNull JpsLibraryType libraryType, @NotNull final String name) {
    final JpsElementCollectionImpl<JpsLibraryImpl> collection = myContainer.getChild(JpsLibraryKind.LIBRARIES_COLLECTION_KIND);
    return collection.addChild(new JpsLibraryImpl(name, libraryType));
  }

  @NotNull
  @Override
  public JpsElementReference<JpsGlobal> createReference() {
    return new JpsGlobalElementReference();
  }
}
