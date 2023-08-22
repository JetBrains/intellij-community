// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration.classpath;

import com.intellij.ide.JavaUiBundle;
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
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ui.classpath.ChooseLibrariesFromTablesDialog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.*;
import java.util.function.Predicate;

public class ProjectStructureChooseLibrariesDialog extends ChooseLibrariesFromTablesDialog {
  private final ClasspathPanel myClasspathPanel;
  private final StructureConfigurableContext myContext;
  private final Predicate<? super Library> myAcceptedLibraries;
  private final List<Library> myCreatedModuleLibraries = new ArrayList<>();
  private JButton myCreateLibraryButton;

  public ProjectStructureChooseLibrariesDialog(ClasspathPanel classpathPanel,
                                               StructureConfigurableContext context,
                                               Predicate<? super Library> acceptedLibraries) {
    super(classpathPanel.getComponent(), JavaUiBundle.message("project.structure.dialog.title.choose.libraries"), classpathPanel.getProject(), true);
    myClasspathPanel = classpathPanel;
    myContext = context;
    myAcceptedLibraries = acceptedLibraries;
    setOKButtonText(JavaUiBundle.message("button.add.selected"));
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
    removeCreatedModuleLibraries(Collections.emptyList());
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

  @Override
  protected Library @NotNull [] getLibraries(@NotNull LibraryTable table) {
    if (table.getTableLevel().equals(LibraryTableImplUtil.MODULE_LEVEL)) {
      return myCreatedModuleLibraries.toArray(Library.EMPTY_ARRAY);
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
    return !(element instanceof Library library) || myAcceptedLibraries.test(library);
  }

  @NotNull
  private String getLibraryName(@NotNull Library library) {
    final LibrariesModifiableModel model = getLibrariesModifiableModel(library.getTable());
    if (model != null) {
      if (model.hasLibraryEditor(library)) {
        return model.getLibraryEditor(library).getName();
      }
    }
    return Objects.toString(library.getName());
  }

  @Override
  protected Action @NotNull [] createActions() {
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
                                      @NlsSafe String libraryName, StructureConfigurableContext context) {
      super(project, parentDescriptor, element);
      final PresentationData templatePresentation = getTemplatePresentation();
      Icon icon = LibraryPresentationManager.getInstance().getNamedLibraryIcon(element, context);
      templatePresentation.setIcon(icon);
      templatePresentation.addText(libraryName, SimpleTextAttributes.REGULAR_ATTRIBUTES);
    }
  }

  private final class CreateNewLibraryAction extends DialogWrapperAction {
    private CreateNewLibraryAction() {
      super(JavaUiBundle.message("dialog.title.new.library"));
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
