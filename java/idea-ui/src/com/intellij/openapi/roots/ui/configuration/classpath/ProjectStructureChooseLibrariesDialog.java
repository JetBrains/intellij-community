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
import com.intellij.openapi.application.Application;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.impl.libraries.LibraryTableImplUtil;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.ui.configuration.libraries.LibraryPresentationManager;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesModifiableModel;
import com.intellij.openapi.roots.ui.configuration.projectRoot.StructureConfigurableContext;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.containers.Predicate;
import com.intellij.util.ui.classpath.ChooseLibrariesFromTablesDialog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public class ProjectStructureChooseLibrariesDialog extends ChooseLibrariesFromTablesDialog {
  private final ClasspathPanel myClasspathPanel;
  private final StructureConfigurableContext myContext;
  private final Predicate<Library> myAcceptedLibraries;
  private final List<Library> myCreatedModuleLibraries = new ArrayList<>();
  private JButton myCreateLibraryButton;

  public ProjectStructureChooseLibrariesDialog(ClasspathPanel classpathPanel,
                                               StructureConfigurableContext context,
                                               Predicate<Library> acceptedLibraries) {
    super(classpathPanel.getComponent(), "Choose Libraries", classpathPanel.getProject(), true);
    myClasspathPanel = classpathPanel;
    myContext = context;
    myAcceptedLibraries = acceptedLibraries;
    setOKButtonText("Add Selected");
    init();
  }

  @Override
  protected void doOKAction() {
    super.doOKAction();
    removeCreatedModuleLibraries(getSelectedLibraries());
  }

  @Override
  public void doCancelAction() {
    super.doCancelAction();
    removeCreatedModuleLibraries(Collections.<Library>emptyList());
  }

  private void removeCreatedModuleLibraries(Collection<Library> selected) {
    for (Library library : myCreatedModuleLibraries) {
      if (!selected.contains(library)) {
        myClasspathPanel.getRootModel().getModuleLibraryTable().removeLibrary(library);
      }
    }
  }

  @Override
  protected void collectChildren(Object element, List<Object> result) {
    if (element instanceof Application && !myCreatedModuleLibraries.isEmpty()) {
      result.add(myClasspathPanel.getRootModel().getModuleLibraryTable());
    }
    super.collectChildren(element, result);
  }

  @NotNull
  @Override
  protected Library[] getLibraries(@NotNull LibraryTable table) {
    if (table.getTableLevel().equals(LibraryTableImplUtil.MODULE_LEVEL)) {
      return myCreatedModuleLibraries.toArray(new Library[myCreatedModuleLibraries.size()]);
    }
    final LibrariesModifiableModel model = getLibrariesModifiableModel(table);
    if (model == null) return Library.EMPTY_ARRAY;
    return model.getLibraries();
  }

  @Nullable
  private LibrariesModifiableModel getLibrariesModifiableModel(LibraryTable table) {
    return table != null ? myContext.myLevel2Providers.get(table.getTableLevel()) : null;
  }

  @Override
  protected boolean acceptsElement(Object element) {
    if (element instanceof Library) {
      final Library library = (Library)element;
      return myAcceptedLibraries.apply(library);
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

  @NotNull
  @Override
  protected Action[] createActions() {
    if (SystemInfo.isMac) {
      return new Action[]{getCancelAction(), new CreateNewLibraryAction(), getOKAction()};
    }
    return new Action[]{getOKAction(), new CreateNewLibraryAction(), getCancelAction()};
  }

  @Override
  protected JButton createJButtonForAction(Action action) {
    final JButton button = super.createJButtonForAction(action);
    if (action instanceof CreateNewLibraryAction) {
      myCreateLibraryButton = button;
    }
    return button;
  }

  @Override
  protected LibrariesTreeNodeBase<Library> createLibraryDescriptor(NodeDescriptor parentDescriptor,
                                                                   Library library) {
    final String libraryName = getLibraryName(library);
    return new LibraryEditorDescriptor(getProject(), parentDescriptor, library, libraryName, myContext);
  }

  private static class LibraryEditorDescriptor extends LibrariesTreeNodeBase<Library> {
    protected LibraryEditorDescriptor(final Project project, final NodeDescriptor parentDescriptor, final Library element,
                                      String libraryName, StructureConfigurableContext context) {
      super(project, parentDescriptor, element);
      final PresentationData templatePresentation = getTemplatePresentation();
      Icon icon = LibraryPresentationManager.getInstance().getNamedLibraryIcon(element, context);
      templatePresentation.setIcon(icon);
      templatePresentation.addText(libraryName, SimpleTextAttributes.REGULAR_ATTRIBUTES);
    }
  }

  private class CreateNewLibraryAction extends DialogWrapperAction {
    private CreateNewLibraryAction() {
      super("New Library...");
      putValue(MNEMONIC_KEY, KeyEvent.VK_N);
    }

    @Override
    protected void doAction(ActionEvent e) {
      AddNewLibraryDependencyAction.chooseTypeAndCreate(myClasspathPanel, myContext, myCreateLibraryButton,
                                                        new AddNewLibraryDependencyAction.LibraryCreatedCallback() {
                                                          @Override
                                                          public void libraryCreated(@NotNull Library library) {
                                                            if (library.getTable() == null) {
                                                              myCreatedModuleLibraries.add(library);
                                                            }
                                                            queueUpdateAndSelect(library);
                                                          }
                                                        });
    }
  }
}
