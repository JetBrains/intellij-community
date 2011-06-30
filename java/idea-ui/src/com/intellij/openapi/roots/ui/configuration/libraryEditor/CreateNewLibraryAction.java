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
package com.intellij.openapi.roots.ui.configuration.libraryEditor;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryType;
import com.intellij.openapi.roots.ui.configuration.ChooseModulesDialog;
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable;
import com.intellij.openapi.roots.ui.configuration.libraries.LibraryEditingUtil;
import com.intellij.openapi.roots.ui.configuration.projectRoot.BaseLibrariesConfigurable;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesModifiableModel;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ModuleStructureConfigurable;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectLibrariesConfigurable;
import com.intellij.openapi.ui.MasterDetailsComponent;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
* @author nik
*/
public class CreateNewLibraryAction extends DumbAwareAction {
  private final @Nullable LibraryType myType;
  private BaseLibrariesConfigurable myLibrariesConfigurable;
  private @Nullable Project myProject;

  private CreateNewLibraryAction(@NotNull String text, @Nullable Icon icon, @Nullable LibraryType type, @NotNull BaseLibrariesConfigurable librariesConfigurable, final @Nullable Project project) {
    super(text, null, icon);
    myType = type;
    myLibrariesConfigurable = librariesConfigurable;
    myProject = project;
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final LibrariesModifiableModel modifiableModel = myLibrariesConfigurable.getModelProvider().getModifiableModel();
    final Library library = modifiableModel.createLibrary(LibraryEditingUtil.suggestNewLibraryName(modifiableModel), myType);
    if (myProject != null){
      final BaseLibrariesConfigurable rootConfigurable = ProjectStructureConfigurable.getInstance(myProject).getConfigurableFor(library);
      final ExistingLibraryEditor libraryEditor = modifiableModel.getLibraryEditor(library);
      if (libraryEditor.hasChanges()) {
        ApplicationManager.getApplication().runWriteAction(new Runnable(){
          public void run() {
            libraryEditor.commit();  //update lib node
          }
        });
      }
      final DefaultMutableTreeNode
        libraryNode = MasterDetailsComponent.findNodeByObject((TreeNode)rootConfigurable.getTree().getModel().getRoot(), library);
      rootConfigurable.selectNodeInTree(libraryNode);
      appendLibraryToModules(ModuleStructureConfigurable.getInstance(myProject), library);
    }
  }

  private void appendLibraryToModules(final ModuleStructureConfigurable rootConfigurable, final Library libraryToSelect) {
    final List<Module> modules = getSuitableModules(rootConfigurable, myType);
    if (modules.isEmpty()) return;
    final ChooseModulesDialog dlg = new ChooseModulesDialog(myProject,
                                                            modules, ProjectBundle.message("choose.modules.dialog.title"),
                                                            ProjectBundle
                                                              .message("choose.modules.dialog.description", libraryToSelect.getName()));
    dlg.show();
    if (dlg.isOK()) {
      final List<Module> chosenModules = dlg.getChosenElements();
      for (Module module : chosenModules) {
        rootConfigurable.addLibraryOrderEntry(module, libraryToSelect);
      }
    }
  }

  private static List<Module> getSuitableModules(@NotNull ModuleStructureConfigurable rootConfigurable, final @Nullable LibraryType type) {
    final List<Module> modules = new ArrayList<Module>();
    for (Module module : rootConfigurable.getModules()) {
      if (type == null || type.isSuitableModule(module, rootConfigurable.getFacetConfigurator())) {
        modules.add(module);
      }
    }
    return modules;
  }

  public static AnAction[] createActionOrGroup(@NotNull String text, @NotNull BaseLibrariesConfigurable librariesConfigurable, final @Nullable Project project) {
    final LibraryType<?>[] extensions = LibraryType.EP_NAME.getExtensions();
    List<LibraryType<?>> suitableTypes = new ArrayList<LibraryType<?>>();
    if (project != null && librariesConfigurable instanceof ProjectLibrariesConfigurable) {
      final ModuleStructureConfigurable configurable = ModuleStructureConfigurable.getInstance(project);
      for (LibraryType<?> extension : extensions) {
        if (!getSuitableModules(configurable, extension).isEmpty()) {
          suitableTypes.add(extension);
        }
      }
    }
    else {
      Collections.addAll(suitableTypes, extensions);
    }

    if (suitableTypes.isEmpty()) {
      return new AnAction[]{new CreateNewLibraryAction(text, PlatformIcons.LIBRARY_ICON, null, librariesConfigurable, project)};
    }
    List<AnAction> actions = new ArrayList<AnAction>();
    actions.add(new CreateNewLibraryAction(IdeBundle.message("create.default.library.type.action.name"), PlatformIcons.LIBRARY_ICON, null, librariesConfigurable, project));
    for (LibraryType<?> type : suitableTypes) {
      final String actionName = type.getCreateActionName();
      if (actionName != null) {
        actions.add(new CreateNewLibraryAction(actionName, type.getIcon(), type, librariesConfigurable, project));
      }
    }
    return actions.toArray(new AnAction[actions.size()]);
  }
}
