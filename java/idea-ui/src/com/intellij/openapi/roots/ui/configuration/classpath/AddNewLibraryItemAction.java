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

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryType;
import com.intellij.openapi.roots.ui.configuration.projectRoot.StructureConfigurableContext;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
* @author nik
*/
class AddNewLibraryItemAction extends ChooseAndAddAction<Library> {
  private final StructureConfigurableContext myContext;
  private LibraryType myLibraryType;

  public AddNewLibraryItemAction(final ClasspathPanel classpathPanel,
                                 StructureConfigurableContext context, LibraryType libraryType) {
    super(classpathPanel);
    myContext = context;
    myLibraryType = libraryType;
  }

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
    return ClasspathTableItem.createLibItem(myClasspathPanel.getRootModel().addLibraryEntry(item), myContext);
  }

  protected ClasspathElementChooser<Library> createChooser() {
    return new NewLibraryChooser(myClasspathPanel.getProject(), myClasspathPanel.getRootModel(), myLibraryType, myContext, myClasspathPanel.getComponent());
  }

  public static void chooseTypeAndExecute(final ClasspathPanel classpathPanel,
                                          final StructureConfigurableContext context,
                                          final DialogWrapper parentDialog,
                                          JButton contextButton) {
    if (hasSuitableTypes(classpathPanel)) {
      final ListPopup popup = JBPopupFactory.getInstance().createListPopup(createChooseTypeStep(classpathPanel, context, parentDialog));
      popup.showUnderneathOf(contextButton);
    }
    else {
      if (parentDialog != null) parentDialog.close(DialogWrapper.CANCEL_EXIT_CODE);
      new AddNewLibraryItemAction(classpathPanel, context, null).execute();
    }
  }

  public static BaseListPopupStep<LibraryType> createChooseTypeStep(final ClasspathPanel classpathPanel,
                                                                    final StructureConfigurableContext context,
                                                                    final DialogWrapper parentDialog) {
    return new BaseListPopupStep<LibraryType>("Select Library Type", getSuitableTypes(classpathPanel)) {
          @NotNull
          @Override
          public String getTextFor(LibraryType value) {
            return value != null ? value.getCreateActionName() : IdeBundle.message("create.default.library.type.action.name");
          }

          @Override
          public Icon getIconFor(LibraryType aValue) {
            return aValue != null ? aValue.getIcon() : PlatformIcons.LIBRARY_ICON;
          }

          @Override
          public PopupStep onChosen(final LibraryType selectedValue, boolean finalChoice) {
            return doFinalStep(new Runnable() {
              @Override
              public void run() {
                if (parentDialog != null) parentDialog.close(DialogWrapper.CANCEL_EXIT_CODE);
                new AddNewLibraryItemAction(classpathPanel, context, selectedValue).execute();
              }
            });
          }
        };
  }

  public static boolean hasSuitableTypes(ClasspathPanel panel) {
    return getSuitableTypes(panel).size() > 1;
  }

  private static List<LibraryType> getSuitableTypes(ClasspathPanel classpathPanel) {
    List<LibraryType> suitableTypes = new ArrayList<LibraryType>();
    suitableTypes.add(null);
    final Module module = classpathPanel.getRootModel().getModule();
    for (LibraryType libraryType : LibraryType.EP_NAME.getExtensions()) {
      if (libraryType.isSuitableModule(module, classpathPanel.getModuleConfigurationState().getFacetsProvider())) {
        suitableTypes.add(libraryType);
      }
    }
    return suitableTypes;
  }
}
