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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.CreateNewLibraryDialog;
import com.intellij.openapi.roots.ui.configuration.projectRoot.StructureConfigurableContext;

import javax.swing.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
* @author nik
*/
class CreateLibraryDialog implements ClasspathElementChooserDialog<Library> {
  private boolean myIsOk;
  private final ModifiableRootModel myRootModel;
  private Library myChosenLibrary;
  private StructureConfigurableContext myContext;
  private final JComponent myParentComponent;
  private final Project myProject;

  public CreateLibraryDialog(final Project project,
                             final ModifiableRootModel rootModel,
                             StructureConfigurableContext context, final JComponent parentComponent) {
    myRootModel = rootModel;
    myContext = context;
    myParentComponent = parentComponent;
    myProject = project;
  }

  public List<Library> getChosenElements() {
    return myChosenLibrary == null? Collections.<Library>emptyList() : Collections.singletonList(myChosenLibrary);
  }

  public void doChoose() {
    final LibraryTablesRegistrar registrar = LibraryTablesRegistrar.getInstance();
    List<LibraryTable> tables = Arrays.asList(myRootModel.getModuleLibraryTable(),
                                              registrar.getLibraryTable(myProject),
                                              registrar.getLibraryTable());
    CreateNewLibraryDialog dialog = CreateNewLibraryDialog.createDialog(myParentComponent, myProject, this, tables, 1);
    final Module contextModule = DataKeys.MODULE_CONTEXT.getData(DataManager.getInstance().getDataContext(myParentComponent));
    dialog.addFileChooserContext(LangDataKeys.MODULE_CONTEXT, contextModule);
    dialog.show();
    myIsOk = dialog.isOK();
    if (myIsOk) {
      myChosenLibrary = dialog.createLibrary(myContext.getModifiableLibraryTable(dialog.getSelectedTable()));
    }
    else {
      myChosenLibrary = null;
    }
  }

  public boolean isOK() {
    return myIsOk;
  }

  public void dispose() {
  }
}
