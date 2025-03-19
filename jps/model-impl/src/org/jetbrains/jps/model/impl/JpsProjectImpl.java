// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.*;
import org.jetbrains.jps.model.ex.JpsElementChildRoleBase;
import org.jetbrains.jps.model.ex.JpsElementCollectionRole;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.JpsLibraryCollection;
import org.jetbrains.jps.model.library.JpsLibraryType;
import org.jetbrains.jps.model.library.impl.JpsLibraryCollectionImpl;
import org.jetbrains.jps.model.library.impl.JpsLibraryRole;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.module.JpsModuleType;
import org.jetbrains.jps.model.module.JpsSdkReferencesTable;
import org.jetbrains.jps.model.module.JpsTypedModule;
import org.jetbrains.jps.model.module.impl.JpsModuleImpl;
import org.jetbrains.jps.model.module.impl.JpsModuleRole;
import org.jetbrains.jps.model.module.impl.JpsSdkReferencesTableImpl;

import java.util.List;
import java.util.Objects;

final class JpsProjectImpl extends JpsProjectBase {
  private static final JpsElementCollectionRole<JpsElementReference<?>> EXTERNAL_REFERENCES_COLLECTION_ROLE =
    JpsElementCollectionRole.create(JpsElementChildRoleBase.create("external reference"));
  private final JpsLibraryCollection myLibraryCollection;
  private String myName = "";

  JpsProjectImpl(@NotNull JpsModel model) {
    super(model);
    myContainer.setChild(JpsModuleRole.MODULE_COLLECTION_ROLE);
    myContainer.setChild(EXTERNAL_REFERENCES_COLLECTION_ROLE);
    myContainer.setChild(JpsSdkReferencesTableImpl.ROLE);
    myLibraryCollection = new JpsLibraryCollectionImpl(myContainer.setChild(JpsLibraryRole.LIBRARIES_COLLECTION_ROLE));
  }

  @Override
  public @NotNull String getName() {
    return myName;
  }

  @Override
  public void setName(@NotNull String name) {
    if (!Objects.equals(myName, name)) {
      myName = name;
    }
  }

  public void addExternalReference(@NotNull JpsElementReference<?> reference) {
    myContainer.getChild(EXTERNAL_REFERENCES_COLLECTION_ROLE).addChild(reference);
  }

  @Override
  public @NotNull
  <P extends JpsElement, ModuleType extends JpsModuleType<P> & JpsElementTypeWithDefaultProperties<P>>
  JpsModule addModule(final @NotNull String name, @NotNull ModuleType moduleType) {
    final JpsElementCollection<JpsModule> collection = myContainer.getChild(JpsModuleRole.MODULE_COLLECTION_ROLE);
    return collection.addChild(new JpsModuleImpl<>(moduleType, name, moduleType.createDefaultProperties()));
  }

  @Override
  public @NotNull <P extends JpsElement, LibraryType extends JpsLibraryType<P> & JpsElementTypeWithDefaultProperties<P>>
  JpsLibrary addLibrary(@NotNull String name, @NotNull LibraryType libraryType) {
    return myLibraryCollection.addLibrary(name, libraryType);
  }

  @Override
  public @NotNull List<JpsModule> getModules() {
    return myContainer.getChild(JpsModuleRole.MODULE_COLLECTION_ROLE).getElements();
  }

  @Override
  public @NotNull <P extends JpsElement> Iterable<JpsTypedModule<P>> getModules(JpsModuleType<P> type) {
    return myContainer.getChild(JpsModuleRole.MODULE_COLLECTION_ROLE).getElementsOfType(type);
  }

  @Override
  public void addModule(@NotNull JpsModule module) {
    myContainer.getChild(JpsModuleRole.MODULE_COLLECTION_ROLE).addChild(module);
  }

  @Override
  public void removeModule(@NotNull JpsModule module) {
    myContainer.getChild(JpsModuleRole.MODULE_COLLECTION_ROLE).removeChild(module);
  }

  @Override
  public @NotNull JpsLibraryCollection getLibraryCollection() {
    return myLibraryCollection;
  }

  @Override
  public @NotNull JpsSdkReferencesTable getSdkReferencesTable() {
    return myContainer.getChild(JpsSdkReferencesTableImpl.ROLE);
  }
}
