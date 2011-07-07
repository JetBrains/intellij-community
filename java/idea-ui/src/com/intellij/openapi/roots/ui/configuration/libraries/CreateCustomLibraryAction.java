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
package com.intellij.openapi.roots.ui.configuration.libraries;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.Collections2;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.CreateNewLibraryDialog;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.NewLibraryEditor;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ModuleStructureConfigurable;
import com.intellij.openapi.roots.ui.configuration.projectRoot.StructureConfigurableContext;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.*;

/**
 * @author nik
 */
public class CreateCustomLibraryAction extends CustomLibraryActionBase {
  public CreateCustomLibraryAction(final String name, CustomLibraryCreator creator,
                                   StructureConfigurableContext context,
                                   ModuleStructureConfigurable moduleStructureConfigurable, Module module) {
    super(name, null, creator.getIcon(), context, moduleStructureConfigurable, creator, module);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final NewLibraryConfiguration libraryConfiguration = myCreator.getDescription().createNewLibrary(myModuleStructureConfigurable.getTree(),
                                                                                                     null);
    if (libraryConfiguration == null) {
      return;
    }

    final NewLibraryEditor libraryEditor = new NewLibraryEditor(libraryConfiguration.getLibraryType(), libraryConfiguration.getProperties());
    libraryEditor.setName(libraryConfiguration.getDefaultLibraryName());
    libraryConfiguration.addRoots(libraryEditor);
    LibraryTablesRegistrar registrar = LibraryTablesRegistrar.getInstance();
    final Project project = myContext.getProject();
    final List<LibraryTable> tables = Arrays.asList(registrar.getLibraryTable(project), registrar.getLibraryTable());
    final CreateNewLibraryDialog dialog = new CreateNewLibraryDialog(myModuleStructureConfigurable.getTree(), myContext, libraryEditor, tables, 0);
    dialog.show();
    if (dialog.isOK()) {
      final Library library = dialog.createLibrary();
      final ModifiableRootModel rootModel = myContext.getModulesConfigurator().getOrCreateModuleEditor(myModule).getModifiableRootModelProxy();
      if (!askAndRemoveDuplicatedLibraryEntry(myCreator.getDescription(), rootModel)) {
        return;
      }
      final LibraryOrderEntry orderEntry = rootModel.addLibraryEntry(library);
      myModuleStructureConfigurable.selectOrderEntry(myModule, orderEntry);
    }
  }

  public static List<AnAction> getActions(@NotNull final StructureConfigurableContext context, @NotNull ModuleStructureConfigurable moduleStructureConfigurable) {
    final Module module = moduleStructureConfigurable.getSelectedModule();
    if (module == null) return Collections.emptyList();

    final List<AnAction> actions = new ArrayList<AnAction>();
    for (CustomLibraryCreator creator : CustomLibraryCreator.EP_NAME.getExtensions()) {
      List<Library> libraries = new ArrayList<Library>();
      Collections.addAll(libraries, context.getProjectLibrariesProvider().getModifiableModel().getLibraries());
      Collections.addAll(libraries, context.getGlobalLibrariesProvider().getModifiableModel().getLibraries());

      final LibraryFilter condition = creator.getDescription().getSuitableLibraryFilter();
      Predicate<Library> suitablePredicate = new Predicate<Library>() {
        @Override
        public boolean apply(Library input) {
          return condition.isSuitableLibrary(Arrays.asList(context.getLibraryFiles(input, OrderRootType.CLASSES)), ((LibraryEx)input).getType());
        }
      };
      final Predicate<Library> notAddedLibrariesCondition = LibraryEditingUtil.getNotAddedLibrariesCondition(context.getModulesConfigurator().getRootModel(module));
      final Collection<Library> librariesToAdd = Collections2.filter(libraries, Predicates.and(suitablePredicate, notAddedLibrariesCondition));
      if (librariesToAdd.isEmpty()) {
        actions.add(new CreateCustomLibraryAction(creator.getDisplayName(), creator, context, moduleStructureConfigurable, module));
      }
      else {
        final DefaultActionGroup group = new DefaultActionGroup(creator.getDisplayName(), true);
        group.getTemplatePresentation().setIcon(creator.getIcon());
        group.add(new CreateCustomLibraryAction("New...", creator, context, moduleStructureConfigurable, module));
        for (Library library : librariesToAdd) {
          Icon icon = LibraryPresentationManager.getInstance().getNamedLibraryIcon(library, context);
          group.add(new AddExistingCustomLibraryAction(library, icon, creator, context, moduleStructureConfigurable, module));
        }
        actions.add(group);
      }
    }
    return actions;
  }
}
