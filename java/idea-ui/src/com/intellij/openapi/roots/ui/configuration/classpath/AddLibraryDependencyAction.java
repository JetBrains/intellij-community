// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration.classpath;

import com.intellij.facet.impl.ProjectFacetsConfigurator;
import com.intellij.ide.JavaUiBundle;
import com.intellij.openapi.roots.LibraryDependencyScopeSuggester;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.ui.configuration.libraries.LibraryEditingUtil;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesModifiableModel;
import com.intellij.openapi.roots.ui.configuration.projectRoot.StructureConfigurableContext;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.util.PlatformIcons;
import com.intellij.util.ui.classpath.ChooseLibrariesFromTablesDialog;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;

class AddLibraryDependencyAction extends AddItemPopupAction<Library> {
  private final StructureConfigurableContext myContext;

  AddLibraryDependencyAction(ClasspathPanel classpathPanel,
                             final int index,
                             final @Nls(capitalization = Nls.Capitalization.Title) String title,
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
    return LibraryEditingUtil.createChooseTypeStep(myClasspathPanel,
                                                   libraryType -> new AddNewLibraryDependencyAction(myClasspathPanel, myContext, libraryType).execute());
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
          if (condition.test(library)) {
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
  protected @Nullable ClasspathTableItem<?> createTableItem(final Library item) {
    // clear invalid order entry corresponding to added library if any
    final ModifiableRootModel rootModel = myClasspathPanel.getRootModel();
    final OrderEntry[] orderEntries = rootModel.getOrderEntries();
    for (OrderEntry orderEntry : orderEntries) {
      if (orderEntry instanceof LibraryOrderEntry libraryOrderEntry) {
        if (item.equals(libraryOrderEntry.getLibrary())) {
          return ClasspathTableItem.createLibItem(libraryOrderEntry, myContext);
        }
        String name = item.getName();
        if (name != null && name.equals(libraryOrderEntry.getLibraryName())) {
          if (orderEntry.isValid()) {
            Messages.showErrorDialog(JavaUiBundle.message("classpath.message.library.already.added", item.getName()),
                                     JavaUiBundle.message("classpath.title.adding.dependency"));
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
    public @NotNull List<Library> chooseElements() {
      ProjectStructureChooseLibrariesDialog dialog = new ProjectStructureChooseLibrariesDialog(myClasspathPanel, myContext,
                                                                                               getNotAddedSuitableLibrariesCondition());
      dialog.show();
      return dialog.getSelectedLibraries();
    }
  }
}
