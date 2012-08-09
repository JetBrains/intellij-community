package org.jetbrains.jps.model.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.*;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.JpsLibraryCollection;
import org.jetbrains.jps.model.library.JpsLibraryType;
import org.jetbrains.jps.model.library.impl.JpsLibraryCollectionImpl;
import org.jetbrains.jps.model.library.impl.JpsLibraryRole;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.module.JpsModuleType;
import org.jetbrains.jps.model.module.JpsSdkReferencesTable;
import org.jetbrains.jps.model.module.impl.JpsModuleImpl;
import org.jetbrains.jps.model.module.impl.JpsModuleRole;
import org.jetbrains.jps.model.module.impl.JpsSdkReferencesTableImpl;

import java.util.List;

/**
 * @author nik
 */
public class JpsProjectImpl extends JpsRootElementBase<JpsProjectImpl> implements JpsProject {
  private static final JpsElementCollectionRole<JpsElementReference<?>> EXTERNAL_REFERENCES_COLLECTION_ROLE =
    JpsElementCollectionRole.create(JpsElementChildRoleBase.<JpsElementReference<?>>create("external reference"));
  private final JpsLibraryCollection myLibraryCollection;

  public JpsProjectImpl(JpsModel model, JpsEventDispatcher eventDispatcher) {
    super(model, eventDispatcher);
    myContainer.setChild(JpsModuleRole.MODULE_COLLECTION_ROLE);
    myContainer.setChild(EXTERNAL_REFERENCES_COLLECTION_ROLE);
    myContainer.setChild(JpsSdkReferencesTableImpl.ROLE);
    myLibraryCollection = new JpsLibraryCollectionImpl(myContainer.setChild(JpsLibraryRole.LIBRARIES_COLLECTION_ROLE));
  }

  public JpsProjectImpl(JpsProjectImpl original, JpsModel model, JpsEventDispatcher eventDispatcher) {
    super(original, model, eventDispatcher);
    myLibraryCollection = new JpsLibraryCollectionImpl(myContainer.getChild(JpsLibraryRole.LIBRARIES_COLLECTION_ROLE));
  }

  public void addExternalReference(@NotNull JpsElementReference<?> reference) {
    myContainer.getChild(EXTERNAL_REFERENCES_COLLECTION_ROLE).addChild(reference);
  }

  @NotNull
  @Override
  public
  <P extends JpsElement, ModuleType extends JpsModuleType<P> & JpsElementTypeWithDefaultProperties<P>>
  JpsModule addModule(@NotNull final String name, @NotNull ModuleType moduleType) {
    final JpsElementCollectionImpl<JpsModule> collection = myContainer.getChild(JpsModuleRole.MODULE_COLLECTION_ROLE);
    return collection.addChild(new JpsModuleImpl<P>(moduleType, name, moduleType.createDefaultProperties()));
  }

  @NotNull
  @Override
  public <P extends JpsElement, LibraryType extends JpsLibraryType<P> & JpsElementTypeWithDefaultProperties<P>>
  JpsLibrary addLibrary(@NotNull String name, @NotNull LibraryType libraryType) {
    return myLibraryCollection.addLibrary(name, libraryType);
  }

  @NotNull
  @Override
  public List<JpsModule> getModules() {
    return myContainer.getChild(JpsModuleRole.MODULE_COLLECTION_ROLE).getElements();
  }

  @Override
  public void addModule(@NotNull JpsModule module) {
    myContainer.getChild(JpsModuleRole.MODULE_COLLECTION_ROLE).addChild(module);
  }

  @NotNull
  @Override
  public JpsLibraryCollection getLibraryCollection() {
    return myLibraryCollection;
  }

  @Override
  @NotNull
  public JpsSdkReferencesTable getSdkReferencesTable() {
    return myContainer.getChild(JpsSdkReferencesTableImpl.ROLE);
  }

  @NotNull
  @Override
  public JpsElementReference<JpsProject> createReference() {
    return new JpsProjectElementReference();
  }
}
