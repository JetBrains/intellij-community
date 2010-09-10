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

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablePresentation;
import com.intellij.openapi.roots.ui.configuration.LibraryTableModifiableModelProvider;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryTableEditor;

import java.util.Collections;
import java.util.List;

/**
* @author nik
*/
class CreateModuleLibraryDialog implements ClasspathElementChooserDialog<Library> {
  private boolean myIsOk;
  private final ClasspathPanel myClasspathPanel;
  private final LibraryTable myLibraryTable;
  private Library myChosenLibrary;

  public CreateModuleLibraryDialog(ClasspathPanel classpathPanel, final LibraryTable libraryTable) {
    myClasspathPanel = classpathPanel;
    myLibraryTable = libraryTable;
  }

  public List<Library> getChosenElements() {
    return myChosenLibrary == null? Collections.<Library>emptyList() : Collections.singletonList(myChosenLibrary);
  }

  public void doChoose() {
    final LibraryTable.ModifiableModel libraryModifiableModel = myLibraryTable.getModifiableModel();
    final LibraryTableModifiableModelProvider provider = new LibraryTableModifiableModelProvider() {
      public LibraryTable.ModifiableModel getModifiableModel() {
        return libraryModifiableModel;
      }

      public String getTableLevel() {
        return myLibraryTable.getTableLevel();
      }

      public LibraryTablePresentation getLibraryTablePresentation() {
        return myLibraryTable.getPresentation();
      }

      public boolean isLibraryTableEditable() {
        return false;
      }
    };
    final Library library = myLibraryTable.createLibrary();
    final LibraryTableEditor editor = LibraryTableEditor.editLibrary(provider, library, myClasspathPanel.getProject());
    final Module contextModule = DataKeys.MODULE_CONTEXT.getData(DataManager.getInstance().getDataContext(myClasspathPanel.getComponent()));
    editor.addFileChooserContext(LangDataKeys.MODULE_CONTEXT, contextModule);
    myIsOk = editor.openDialog(myClasspathPanel.getComponent(), Collections.singletonList(library), true) != null;
    if (myIsOk) {
      myChosenLibrary = library;
    }
    else {
      myChosenLibrary = null;
      libraryModifiableModel.removeLibrary(library);
    }
  }

  public boolean isOK() {
    return myIsOk;
  }

  public void dispose() {
  }
}
