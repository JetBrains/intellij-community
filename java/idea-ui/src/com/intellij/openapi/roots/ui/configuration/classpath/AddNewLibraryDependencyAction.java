// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration.classpath;

import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryType;
import com.intellij.openapi.roots.ui.configuration.libraries.LibraryEditingUtil;
import com.intellij.openapi.roots.ui.configuration.projectRoot.StructureConfigurableContext;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

class AddNewLibraryDependencyAction extends ChooseAndAddAction<Library> {
  private final StructureConfigurableContext myContext;
  private final LibraryType<?> myLibraryType;

  AddNewLibraryDependencyAction(final ClasspathPanel classpathPanel,
                                       StructureConfigurableContext context, LibraryType<?> libraryType) {
    super(classpathPanel);
    myContext = context;
    myLibraryType = libraryType;
  }

  @Override
  protected ClasspathTableItem<?> createTableItem(final Library item) {
    final OrderEntry[] entries = myClasspathPanel.getRootModel().getOrderEntries();
    for (OrderEntry entry : entries) {
      if (entry instanceof LibraryOrderEntry libraryOrderEntry && item.equals(libraryOrderEntry.getLibrary())) {
        return ClasspathTableItem.createLibItem(libraryOrderEntry, myContext);
      }
    }
    return ClasspathTableItem.createLibItem(myClasspathPanel.getRootModel().addLibraryEntry(item), myContext);
  }

  @Override
  protected ClasspathElementChooser<Library> createChooser() {
    return new NewLibraryChooser(myClasspathPanel.getProject(), myClasspathPanel.getRootModel(), myLibraryType, myContext, myClasspathPanel.getComponent());
  }

  public static void chooseTypeAndCreate(final ClasspathPanel classpathPanel,
                                         final StructureConfigurableContext context,
                                         final JButton contextButton, final @NotNull LibraryCreatedCallback callback) {
    if (LibraryEditingUtil.hasSuitableTypes(classpathPanel)) {
      final ListPopup popup = JBPopupFactory.getInstance().createListPopup(LibraryEditingUtil.createChooseTypeStep(classpathPanel,
                                                                                                                   libraryType -> doCreateLibrary(classpathPanel, context, callback, contextButton, libraryType)));
      popup.showUnderneathOf(contextButton);
    }
    else {
      doCreateLibrary(classpathPanel, context, callback, contextButton, null);
    }
  }

  private static void doCreateLibrary(ClasspathPanel classpathPanel,
                                      StructureConfigurableContext context,
                                      LibraryCreatedCallback callback, final JComponent component, final @Nullable LibraryType<?> libraryType) {
    final NewLibraryChooser chooser = new NewLibraryChooser(classpathPanel.getProject(), classpathPanel.getRootModel(), libraryType, context, component);
    final Library library = chooser.createLibrary();
    if (library != null) {
      callback.libraryCreated(library);
    }
  }

  interface LibraryCreatedCallback {
    void libraryCreated(@NotNull Library library);
  }
}
