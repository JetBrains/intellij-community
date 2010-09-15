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

import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.impl.libraries.LibraryImpl;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.projectRoot.StructureConfigurableContext;
import com.intellij.openapi.ui.Messages;
import com.intellij.util.Icons;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
* @author nik
*/
class ChooseExistingLibraryAction extends AddItemPopupAction<Library> {
  private StructureConfigurableContext myContext;

  public ChooseExistingLibraryAction(ClasspathPanel classpathPanel, final int index, final String title,
                                     final StructureConfigurableContext context) {
    super(classpathPanel, index, title, Icons.LIBRARY_ICON);
    myContext = context;
  }

  @Nullable
  protected ClasspathTableItem createTableItem(final Library item) {
    // clear invalid order entry corresponding to added library if any
    final ModifiableRootModel rootModel = myClasspathPanel.getRootModel();
    final OrderEntry[] orderEntries = rootModel.getOrderEntries();
    for (OrderEntry orderEntry : orderEntries) {
      if (orderEntry instanceof LibraryOrderEntry) {
        if (item.getName().equals(((LibraryOrderEntry)orderEntry).getLibraryName())) {
          if (orderEntry.isValid()) {
            Messages.showErrorDialog(ProjectBundle.message("classpath.message.library.already.added",item.getName()),
                                     ProjectBundle.message("classpath.title.adding.dependency"));
            return null;
          } else {
            rootModel.removeOrderEntry(orderEntry);
          }
        }
      }
    }
    return ClasspathTableItem.createLibItem(rootModel.addLibraryEntry(item));
  }

  protected ClasspathElementChooserDialog<Library> createChooserDialog() {
    return new MyChooserDialog();
  }

  private Collection<Library> getAlreadyAddedLibraries() {
    final OrderEntry[] orderEntries = myClasspathPanel.getRootModel().getOrderEntries();
    final Set<Library> result = new HashSet<Library>(orderEntries.length);
    for (OrderEntry orderEntry : orderEntries) {
      if (orderEntry instanceof LibraryOrderEntry && orderEntry.isValid()) {
        final LibraryImpl library = (LibraryImpl)((LibraryOrderEntry)orderEntry).getLibrary();
        if (library != null) {
          ContainerUtil.addIfNotNull(result, library.getSource());
        }
      }
    }
    return result;
  }

  class MyChooserDialog implements ClasspathElementChooserDialog<Library> {
    private List<Library> mySelectedLibraries;

    public List<Library> getChosenElements() {
      return mySelectedLibraries;
    }

    public void doChoose() {
      ProjectStructureChooseLibrariesDialog dialog = new ProjectStructureChooseLibrariesDialog(myClasspathPanel.getComponent(), myClasspathPanel.getProject(), myContext,
                                                                                               getAlreadyAddedLibraries());
      dialog.show();
      mySelectedLibraries = dialog.getSelectedLibraries();
    }

    public boolean isOK() {
      return mySelectedLibraries != null && !mySelectedLibraries.isEmpty();
    }

    public void dispose() {
    }
  }
}
