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

import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.projectRoot.StructureConfigurableContext;

/**
* @author nik
*/
class AddNewLibraryItemAction extends ChooseAndAddAction<Library> {
  private final StructureConfigurableContext myContext;

  public AddNewLibraryItemAction(final ClasspathPanel classpathPanel,
                                 StructureConfigurableContext context) {
    super(classpathPanel);
    myContext = context;
  }

  protected ClasspathTableItem createTableItem(final Library item) {
    final OrderEntry[] entries = myClasspathPanel.getRootModel().getOrderEntries();
    for (OrderEntry entry : entries) {
      if (entry instanceof LibraryOrderEntry) {
        final LibraryOrderEntry libraryOrderEntry = (LibraryOrderEntry)entry;
        if (item.equals(libraryOrderEntry.getLibrary())) {
          return ClasspathTableItem.createLibItem(libraryOrderEntry);
        }
      }
    }
    return ClasspathTableItem.createLibItem(myClasspathPanel.getRootModel().addLibraryEntry(item));
  }

  protected ClasspathElementChooser<Library> createChooser() {
    return new NewLibraryChooser(myClasspathPanel.getProject(), myClasspathPanel.getRootModel(), myContext, myClasspathPanel.getComponent());
  }
}
