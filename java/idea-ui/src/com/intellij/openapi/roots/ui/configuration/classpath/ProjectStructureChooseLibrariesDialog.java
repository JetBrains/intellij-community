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

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.impl.libraries.LibraryImpl;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesModifiableModel;
import com.intellij.openapi.roots.ui.configuration.projectRoot.StructureConfigurableContext;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.Icons;
import com.intellij.util.ui.classpath.ChooseLibrariesFromTablesDialog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;

/**
 * @author nik
 */
public class ProjectStructureChooseLibrariesDialog extends ChooseLibrariesFromTablesDialog {
  private StructureConfigurableContext myContext;
  private Collection<Library> myAlreadyAddedLibraries;

  public ProjectStructureChooseLibrariesDialog(JComponent parentComponent,
                                               @Nullable Project project,
                                               StructureConfigurableContext context,
                                               Collection<Library> alreadyAddedLibraries) {
    super(parentComponent, "Choose Libraries", project, true);
    myContext = context;
    myAlreadyAddedLibraries = alreadyAddedLibraries;
    init();
  }

  @NotNull
  @Override
  protected Library[] getLibraries(@NotNull LibraryTable table) {
    final LibrariesModifiableModel model = getLibrariesModifiableModel(table);
    if (model == null) return Library.EMPTY_ARRAY;
    return model.getLibraries();
  }

  private LibrariesModifiableModel getLibrariesModifiableModel(LibraryTable table) {
    return myContext.myLevel2Providers.get(table.getTableLevel());
  }

  @Override
  protected boolean acceptsElement(Object element) {
    if (element instanceof Library) {
      final Library library = (Library)element;
      if (myAlreadyAddedLibraries.contains(library)) return false;
      if (library instanceof LibraryImpl) {
        final Library source = ((LibraryImpl)library).getSource();
        if (source != null && myAlreadyAddedLibraries.contains(source)) return false;
      }
    }
    return true;
  }

  @NotNull
  private String getLibraryName(@NotNull Library library) {
    final LibrariesModifiableModel model = getLibrariesModifiableModel(library.getTable());
    if (model != null) {
      if (model.hasLibraryEditor(library)) {
        return model.getLibraryEditor(library).getName();
      }
    }
    return library.getName();
  }

  @Override
  protected LibrariesTreeNodeBase<Library> createLibraryDescriptor(NodeDescriptor parentDescriptor,
                                                                   Library library) {
    final String libraryName = getLibraryName(library);
    return new LibraryEditorDescriptor(getProject(), parentDescriptor, library, libraryName);
  }

  private static class LibraryEditorDescriptor extends LibrariesTreeNodeBase<Library> {
    protected LibraryEditorDescriptor(final Project project, final NodeDescriptor parentDescriptor, final Library element,
                                      String libraryName) {
      super(project, parentDescriptor, element);
      final PresentationData templatePresentation = getTemplatePresentation();
      templatePresentation.setIcons(Icons.LIBRARY_ICON);
      templatePresentation.addText(libraryName, SimpleTextAttributes.REGULAR_ATTRIBUTES);
    }
  }

}
