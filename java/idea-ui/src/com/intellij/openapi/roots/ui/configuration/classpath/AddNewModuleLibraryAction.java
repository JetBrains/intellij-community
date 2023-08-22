// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration.classpath;

import com.intellij.ide.JavaUiBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.ui.configuration.projectRoot.StructureConfigurableContext;
import com.intellij.util.PlatformIcons;

class AddNewModuleLibraryAction extends AddItemPopupAction<Library> {
  private static final Logger LOG = Logger.getInstance(AddNewModuleLibraryAction.class);
  private final StructureConfigurableContext myContext;

  AddNewModuleLibraryAction(final ClasspathPanel classpathPanel,
                                   int actionIndex,
                                   StructureConfigurableContext context) {
    super(classpathPanel, actionIndex, JavaUiBundle.message("classpath.add.simple.module.library.action"), PlatformIcons.JAR_ICON);
    myContext = context;
  }

  @Override
  protected ClasspathTableItem<?> createTableItem(final Library item) {
    final OrderEntry[] entries = myClasspathPanel.getRootModel().getOrderEntries();
    for (OrderEntry entry : entries) {
      if (entry instanceof LibraryOrderEntry libraryOrderEntry && item.equals(libraryOrderEntry.getLibrary())) {
        return ClasspathTableItem.createLibItem(libraryOrderEntry, myContext);
      }
    }
    LOG.error("Unknown library " + item);
    return null;
  }

  @Override
  protected ClasspathElementChooser<Library> createChooser() {
    final LibraryTable.ModifiableModel moduleLibraryModel = myClasspathPanel.getRootModel().getModuleLibraryTable().getModifiableModel();
    return new CreateModuleLibraryChooser(myClasspathPanel, moduleLibraryModel);
  }
}
