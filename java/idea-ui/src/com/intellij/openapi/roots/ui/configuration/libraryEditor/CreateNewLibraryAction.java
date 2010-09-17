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

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable;
import com.intellij.openapi.roots.ui.configuration.projectRoot.BaseLibrariesConfigurable;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesModifiableModel;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ModuleStructureConfigurable;
import com.intellij.openapi.roots.ui.configuration.projectRoot.StructureLibraryTableModifiableModelProvider;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.MasterDetailsComponent;
import com.intellij.openapi.ui.Messages;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import java.util.ArrayList;
import java.util.List;

/**
* @author nik
*/
public class CreateNewLibraryAction extends AnAction {
  private StructureLibraryTableModifiableModelProvider myModelProvider;
  private Project myProject;

  public CreateNewLibraryAction(String text, StructureLibraryTableModifiableModelProvider modelProvider, final Project project) {
    super(text);
    myModelProvider = modelProvider;
    myProject = project;
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final LibrariesModifiableModel modifiableModel = myModelProvider.getModifiableModel();
    final String initial = LibraryRootsComponent.suggestNewLibraryName(modifiableModel);
    final String prompt = ProjectBundle.message("library.name.prompt");
    final String title = ProjectBundle.message("library.create.library.action").replaceAll(String.valueOf(UIUtil.MNEMONIC), "");
    final Icon icon = Messages.getQuestionIcon();
    final String libraryName = Messages.showInputDialog(myProject, prompt, title, icon, initial, new InputValidator() {
      public boolean checkInput(final String inputString) {
        return true;
      }
      public boolean canClose(final String inputString) {
        if (inputString.length() == 0)  {
          Messages.showErrorDialog(ProjectBundle.message("library.name.not.specified.error"), ProjectBundle.message("library.name.not.specified.title"));
          return false;
        }
        if (LibraryRootsComponent.libraryAlreadyExists(modifiableModel, inputString)) {
          Messages.showErrorDialog(ProjectBundle.message("library.name.already.exists.error", inputString), ProjectBundle.message("library.name.already.exists.title"));
          return false;
        }
        return true;
      }
    });
    if (libraryName == null) return;
    final Library library = modifiableModel.createLibrary(libraryName);
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
    final List<Module> modules = new ArrayList<Module>();
    ContainerUtil.addAll(modules, rootConfigurable.getModules());
    final ChooseModulesDialog dlg = new ChooseModulesDialog(myProject,
                                                            modules, ProjectBundle.message("choose.modules.dialog.title"),
                                                            ProjectBundle
                                                              .message("choose.modules.dialog.description", libraryToSelect.getName()));
    dlg.show();
    if (dlg.isOK()) {
      final List<Module> choosenModules = dlg.getChosenElements();
      for (Module module : choosenModules) {
        rootConfigurable.addLibraryOrderEntry(module, libraryToSelect);
      }
    }
  }
}
