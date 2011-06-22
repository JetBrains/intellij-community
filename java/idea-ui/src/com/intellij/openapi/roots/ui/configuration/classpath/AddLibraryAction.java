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

import com.google.common.base.Predicate;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.impl.libraries.LibraryTableBase;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.ui.configuration.libraries.LibraryEditingUtil;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesModifiableModel;
import com.intellij.openapi.roots.ui.configuration.projectRoot.StructureConfigurableContext;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.util.PlatformIcons;
import com.intellij.util.ui.classpath.ChooseLibrariesFromTablesDialog;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
* @author nik
*/
class AddLibraryAction extends AddItemPopupAction<Library> {
  private StructureConfigurableContext myContext;

  public AddLibraryAction(ClasspathPanel classpathPanel, final int index, final String title,
                          final StructureConfigurableContext context) {
    super(classpathPanel, index, title, PlatformIcons.LIBRARY_ICON);
    myContext = context;
  }

  @Override
  public boolean hasSubStep() {
    return !hasLibraries() && AddNewLibraryItemAction.hasSuitableTypes(myClasspathPanel);
  }

  @Override
  public PopupStep createSubStep() {
    return AddNewLibraryItemAction.createChooseTypeStep(myClasspathPanel, myContext, null);
  }

  @Override
  public void run() {
    if (hasLibraries()) {
      super.run();
    }
    else {
      new AddNewLibraryItemAction(myClasspathPanel, myContext, null).run();
    }
  }

  private boolean hasLibraries() {
    final Predicate<Library> condition = LibraryEditingUtil.getNotAddedLibrariesCondition(myClasspathPanel.getRootModel());
    for (LibraryTable table : ChooseLibrariesFromTablesDialog.getLibraryTables(myClasspathPanel.getProject(), true)) {
      final LibrariesModifiableModel model = myContext.myLevel2Providers.get(table.getTableLevel());
      if (model != null) {
        for (Library library : model.getLibraries()) {
          if (condition.apply(library)) {
            return true;
          }
        }
      }
    }
    return false;
  }

  @Nullable
  protected ClasspathTableItem<?> createTableItem(final Library item) {
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
    final LibraryOrderEntry orderEntry = rootModel.addLibraryEntry(item);
    final LibraryTable table = item.getTable();
    if (table instanceof LibraryTableBase) {
      orderEntry.setScope(((LibraryTableBase)table).getDefaultDependencyScope());
    }
    return ClasspathTableItem.createLibItem(orderEntry, myContext);
  }

  protected ClasspathElementChooser<Library> createChooser() {
    return new ExistingLibraryChooser();
  }

  class ExistingLibraryChooser implements ClasspathElementChooser<Library> {
    private List<Library> mySelectedLibraries;

    public List<Library> getChosenElements() {
      return mySelectedLibraries;
    }

    public void doChoose() {
      final Predicate<Library> condition = LibraryEditingUtil.getNotAddedLibrariesCondition(myClasspathPanel.getRootModel());
      ProjectStructureChooseLibrariesDialog dialog = new ProjectStructureChooseLibrariesDialog(myClasspathPanel, myContext,
                                                                                               condition);
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
