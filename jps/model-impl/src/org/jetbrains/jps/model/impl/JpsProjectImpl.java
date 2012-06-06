package org.jetbrains.jps.model.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.*;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.JpsLibraryType;
import org.jetbrains.jps.model.library.impl.JpsLibraryImpl;
import org.jetbrains.jps.model.library.impl.JpsLibraryKind;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.module.JpsModuleType;
import org.jetbrains.jps.model.module.impl.JpsModuleImpl;
import org.jetbrains.jps.model.module.impl.JpsModuleKind;

import java.util.List;

/**
 * @author nik
 */
public class JpsProjectImpl extends JpsCompositeElementBase<JpsProjectImpl> implements JpsProject {
  public JpsProjectImpl(JpsModel model, JpsEventDispatcher eventDispatcher) {
    super(model, eventDispatcher, null);
    myContainer.setChild(JpsModuleKind.MODULE_COLLECTION_KIND);
    myContainer.setChild(JpsLibraryKind.LIBRARIES_COLLECTION_KIND);
  }

  public JpsProjectImpl(JpsProjectImpl original, JpsModel model, JpsEventDispatcher eventDispatcher) {
    super(original, model, eventDispatcher, null);
  }

  @NotNull
  @Override
  public JpsModule addModule(@NotNull JpsModuleType<?> moduleType, @NotNull final String name) {
    final JpsElementCollectionImpl<JpsModuleImpl> collection = myContainer.getChild(JpsModuleKind.MODULE_COLLECTION_KIND);
    return collection.addChild(new JpsModuleImpl(myModel, getEventDispatcher(), moduleType, name, collection));
  }

  @NotNull
  @Override
  public JpsLibrary addLibrary(@NotNull JpsLibraryType<?> libraryType, @NotNull final String name) {
    final JpsElementCollectionImpl<JpsLibraryImpl> collection = myContainer.getChild(JpsLibraryKind.LIBRARIES_COLLECTION_KIND);
    return collection.addChild(new JpsLibraryImpl(name, libraryType, myModel, getEventDispatcher(), collection));
  }

  @NotNull
  @Override
  public List<? extends JpsLibrary> getLibraries() {
    return myContainer.getChild(JpsLibraryKind.LIBRARIES_COLLECTION_KIND).getElements();
  }

  @NotNull
  @Override
  public List<? extends JpsModule> getModules() {
    return myContainer.getChild(JpsModuleKind.MODULE_COLLECTION_KIND).getElements();
  }

  @NotNull
  @Override
  public JpsElementReference<JpsProject> createReference(JpsParentElement parent) {
    return new JpsProjectElementReference(myModel, getEventDispatcher(), parent);
  }

  @NotNull
  @Override
  public JpsProjectImpl createCopy(@NotNull JpsModel model, @NotNull JpsEventDispatcher eventDispatcher, JpsParentElement parent) {
    return new JpsProjectImpl(this, model, eventDispatcher);
  }
}
