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
public class JpsProjectImpl extends JpsRootElementBase<JpsProjectImpl> implements JpsProject {
  private static final JpsElementCollectionKind<JpsElementReference<?>> EXTERNAL_REFERENCES_COLLECTION_KIND =
    new JpsElementCollectionKind<JpsElementReference<?>>(new JpsElementKindBase<JpsElementReference<?>>("external reference"));

  public JpsProjectImpl(JpsModel model, JpsEventDispatcher eventDispatcher) {
    super(model, eventDispatcher);
    myContainer.setChild(JpsModuleKind.MODULE_COLLECTION_KIND);
    myContainer.setChild(JpsLibraryKind.LIBRARIES_COLLECTION_KIND);
    myContainer.setChild(EXTERNAL_REFERENCES_COLLECTION_KIND);
  }

  public JpsProjectImpl(JpsProjectImpl original, JpsModel model, JpsEventDispatcher eventDispatcher) {
    super(original, model, eventDispatcher);
  }

  public void addExternalReference(@NotNull JpsElementReference<?> reference) {
    myContainer.getChild(EXTERNAL_REFERENCES_COLLECTION_KIND).addChild(reference);
  }

  @NotNull
  @Override
  public JpsModule addModule(@NotNull JpsModuleType<?> moduleType, @NotNull final String name) {
    final JpsElementCollectionImpl<JpsModule> collection = myContainer.getChild(JpsModuleKind.MODULE_COLLECTION_KIND);
    return collection.addChild(new JpsModuleImpl(moduleType, name));
  }

  @NotNull
  @Override
  public JpsLibrary addLibrary(@NotNull JpsLibraryType<?> libraryType, @NotNull final String name) {
    final JpsElementCollectionImpl<JpsLibrary> collection = myContainer.getChild(JpsLibraryKind.LIBRARIES_COLLECTION_KIND);
    return collection.addChild(new JpsLibraryImpl(name, libraryType));
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

  @Override
  public void addModule(JpsModule module) {
    myContainer.getChild(JpsModuleKind.MODULE_COLLECTION_KIND).addChild(module);
  }

  @Override
  public void addLibrary(JpsLibrary library) {
    myContainer.getChild(JpsLibraryKind.LIBRARIES_COLLECTION_KIND).addChild(library);
  }

  @NotNull
  @Override
  public JpsElementReference<JpsProject> createReference() {
    return new JpsProjectElementReference();
  }
}
