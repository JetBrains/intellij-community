/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.roots.ui.configuration.classpath;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.ui.configuration.projectRoot.StructureConfigurableContext;
import com.intellij.util.PlatformIcons;

/**
* @author nik
*/
class AddNewModuleLibraryAction extends AddItemPopupAction<Library> {
  private static final Logger LOG = Logger.getInstance(AddNewModuleLibraryAction.class);
  private final StructureConfigurableContext myContext;

  public AddNewModuleLibraryAction(final ClasspathPanel classpathPanel,
                                   int actionIndex,
                                   StructureConfigurableContext context) {
    super(classpathPanel, actionIndex, ProjectBundle.message("classpath.add.simple.module.library.action"), PlatformIcons.JAR_ICON);
    myContext = context;
  }

  @Override
  protected ClasspathTableItem<?> createTableItem(final Library item) {
    final OrderEntry[] entries = myClasspathPanel.getRootModel().getOrderEntries();
    for (OrderEntry entry : entries) {
      if (entry instanceof LibraryOrderEntry) {
        final LibraryOrderEntry libraryOrderEntry = (LibraryOrderEntry)entry;
        if (item.equals(libraryOrderEntry.getLibrary())) {
          return ClasspathTableItem.createLibItem(libraryOrderEntry, myContext);
        }
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
