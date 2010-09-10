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
import com.intellij.openapi.roots.ui.configuration.LibraryTableModifiableModelProvider;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryTableEditor;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.Icons;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
* @author nik
*/
class ChooseNamedLibraryAction extends AddItemPopupAction<Library> {
  private final LibraryTableModifiableModelProvider myLibraryTableModelProvider;

  public ChooseNamedLibraryAction(ClasspathPanel classpathPanel,
                                  final int index,
                                  final String title,
                                  final LibraryTableModifiableModelProvider libraryTable) {
    super(classpathPanel, index, title, Icons.LIBRARY_ICON);
    myLibraryTableModelProvider = libraryTable;
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
          result.add(library.getSource());
        }
      }
    }
    return result;
  }

  class MyChooserDialog implements ClasspathElementChooserDialog<Library> {
    private final LibraryTableEditor myEditor;
    private Library[] myLibraries;

    MyChooserDialog(){
      myEditor = LibraryTableEditor.editLibraryTable(myLibraryTableModelProvider, myClasspathPanel.getProject());
      Disposer.register(this, myEditor);
    }

    public List<Library> getChosenElements() {
      final List<Library> chosen = new ArrayList<Library>(Arrays.asList(myLibraries));
      chosen.removeAll(getAlreadyAddedLibraries());
      return chosen;
    }

    public void doChoose() {
      final Iterator iter = myLibraryTableModelProvider.getModifiableModel().getLibraryIterator();
      myLibraries = myEditor.openDialog(myClasspathPanel.getComponent(), iter.hasNext()? Collections.singleton((Library)iter.next()) : Collections.<Library>emptyList(), false);
    }

    public boolean isOK() {
      return myLibraries != null;
    }

    public void dispose() {
    }
  }
}
