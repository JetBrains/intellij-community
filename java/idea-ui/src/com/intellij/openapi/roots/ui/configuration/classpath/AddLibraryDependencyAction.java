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

import com.intellij.facet.impl.ProjectFacetsConfigurator;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryType;
import com.intellij.openapi.roots.ui.configuration.libraries.LibraryEditingUtil;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesModifiableModel;
import com.intellij.openapi.roots.ui.configuration.projectRoot.StructureConfigurableContext;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.util.ParameterizedRunnable;
import com.intellij.util.PlatformIcons;
import com.intellij.util.containers.Predicate;
import com.intellij.util.ui.classpath.ChooseLibrariesFromTablesDialog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
* @author nik
*/
class AddLibraryDependencyAction extends AddItemPopupAction<Library> {
  private final StructureConfigurableContext myContext;

  public AddLibraryDependencyAction(ClasspathPanel classpathPanel, final int index, final String title,
                                    final StructureConfigurableContext context) {
    super(classpathPanel, index, title, PlatformIcons.LIBRARY_ICON);
    myContext = context;
  }

  @Override
  public boolean hasSubStep() {
    return !hasLibraries() && LibraryEditingUtil.hasSuitableTypes(myClasspathPanel);
  }

  @Override
  public PopupStep createSubStep() {
    return LibraryEditingUtil.createChooseTypeStep(myClasspathPanel, new ParameterizedRunnable<LibraryType>() {
      @Override
      public void run(LibraryType libraryType) {
        new AddNewLibraryDependencyAction(myClasspathPanel, myContext, libraryType).execute();
      }
    });
  }

  @Override
  public void run() {
    if (hasLibraries()) {
      super.run();
    }
    else {
      new AddNewLibraryDependencyAction(myClasspathPanel, myContext, null).run();
    }
  }

  private boolean hasLibraries() {
    final Predicate<Library> condition = getNotAddedSuitableLibrariesCondition();
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

  private Predicate<Library> getNotAddedSuitableLibrariesCondition() {
    ProjectFacetsConfigurator facetsConfigurator = myContext.getModulesConfigurator().getFacetsConfigurator();
    return LibraryEditingUtil.getNotAddedSuitableLibrariesCondition(myClasspathPanel.getRootModel(), facetsConfigurator);
  }

  @Override
  @Nullable
  protected ClasspathTableItem<?> createTableItem(final Library item) {
    // clear invalid order entry corresponding to added library if any
    final ModifiableRootModel rootModel = myClasspathPanel.getRootModel();
    final OrderEntry[] orderEntries = rootModel.getOrderEntries();
    for (OrderEntry orderEntry : orderEntries) {
      if (orderEntry instanceof LibraryOrderEntry) {
        final LibraryOrderEntry libraryOrderEntry = (LibraryOrderEntry)orderEntry;
        if (item.equals(libraryOrderEntry.getLibrary())) {
          return ClasspathTableItem.createLibItem(libraryOrderEntry, myContext);
        }
        String name = item.getName();
        if (name != null && name.equals(libraryOrderEntry.getLibraryName())) {
          if (orderEntry.isValid()) {
            Messages.showErrorDialog(ProjectBundle.message("classpath.message.library.already.added", item.getName()),
                                     ProjectBundle.message("classpath.title.adding.dependency"));
            return null;
          }
          else {
            rootModel.removeOrderEntry(orderEntry);
          }
        }
      }
    }
    final LibraryOrderEntry orderEntry = rootModel.addLibraryEntry(item);
    orderEntry.setScope(LibraryDependencyScopeSuggester.getDefaultScope(item));
    return ClasspathTableItem.createLibItem(orderEntry, myContext);
  }

  @Override
  protected ClasspathElementChooser<Library> createChooser() {
    return new ExistingLibraryChooser();
  }

  class ExistingLibraryChooser implements ClasspathElementChooser<Library> {
    @Override
    @NotNull
    public List<Library> chooseElements() {
      ProjectStructureChooseLibrariesDialog dialog = new ProjectStructureChooseLibrariesDialog(myClasspathPanel, myContext,
                                                                                               getNotAddedSuitableLibrariesCondition());
      dialog.show();
      return dialog.getSelectedLibraries();
    }
  }
}
