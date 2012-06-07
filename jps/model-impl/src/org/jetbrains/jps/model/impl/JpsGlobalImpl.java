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
public class JpsGlobalImpl extends JpsCompositeElementBase<JpsGlobalImpl> implements JpsGlobal {
  public JpsGlobalImpl(JpsModel model, JpsEventDispatcher eventDispatcher) {
    super(model, eventDispatcher, null);
    myContainer.setChild(JpsLibraryKind.LIBRARIES_COLLECTION_KIND);
  }

  public JpsGlobalImpl(JpsGlobalImpl original, JpsModel model, JpsEventDispatcher eventDispatcher) {
    super(original, model, eventDispatcher, null);
  }

  @NotNull
  @Override
  public JpsGlobalImpl createCopy(@NotNull JpsModel model, @NotNull JpsEventDispatcher eventDispatcher, JpsParentElement parent) {
    return new JpsGlobalImpl(this, model, eventDispatcher);
  }

  @NotNull
  @Override
  public JpsLibrary addLibrary(@NotNull JpsLibraryType libraryType, @NotNull final String name) {
    final JpsElementCollectionImpl<JpsLibraryImpl> collection = myContainer.getChild(JpsLibraryKind.LIBRARIES_COLLECTION_KIND);
    return collection.addChild(new JpsLibraryImpl(name, libraryType, myModel, getEventDispatcher(), collection));
  }

  @NotNull
  @Override
  public JpsElementReference<JpsGlobal> createReference(JpsParentElement parent) {
    return new JpsGlobalElementReference(myModel, getEventDispatcher(), parent);
  }
}
