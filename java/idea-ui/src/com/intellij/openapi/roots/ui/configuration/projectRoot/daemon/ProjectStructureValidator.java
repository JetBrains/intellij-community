/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.roots.ui.configuration.projectRoot.daemon;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.ChooseModulesDialog;
import com.intellij.openapi.roots.ui.configuration.libraries.LibraryEditingUtil;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ModuleStructureConfigurable;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * User: ksafonov
 */
public abstract class ProjectStructureValidator {

  private static final ExtensionPointName<ProjectStructureValidator> EP_NAME =
    ExtensionPointName.create("com.intellij.projectStructureValidator");

  public static List<ProjectStructureElementUsage> getUsagesInElement(final ProjectStructureElement element) {
    for (ProjectStructureValidator validator : EP_NAME.getExtensions()) {
      List<ProjectStructureElementUsage> usages = validator.getUsagesIn(element);
      if (usages != null) {
        return usages;
      }
    }
    return element.getUsagesInElement();
  }

  public static void check(ProjectStructureElement element, ProjectStructureProblemsHolder problemsHolder) {
    for (ProjectStructureValidator validator : EP_NAME.getExtensions()) {
      if (validator.checkElement(element, problemsHolder)) {
        return;
      }
    }
    element.check(problemsHolder);
  }

  public static void showDialogAndAddLibraryToDependencies(final Library library, final Project project, boolean allowEmptySelection) {
    for (ProjectStructureValidator validator : EP_NAME.getExtensions()) {
      if (validator.addLibraryToDependencies(library, project, allowEmptySelection)) {
        return;
      }
    }

    final ModuleStructureConfigurable moduleStructureConfigurable = ModuleStructureConfigurable.getInstance(project);
    final List<Module> modules =
      LibraryEditingUtil.getSuitableModules(moduleStructureConfigurable, ((LibraryEx)library).getKind(), library);
    if (modules.isEmpty()) return;
    final ChooseModulesDialog
      dlg = new ChooseModulesDialog(moduleStructureConfigurable.getProject(), modules, ProjectBundle.message("choose.modules.dialog.title"),
                                    ProjectBundle
                                      .message("choose.modules.dialog.description", library.getName()));
    if (dlg.showAndGet()) {
      final List<Module> chosenModules = dlg.getChosenElements();
      for (Module module : chosenModules) {
        moduleStructureConfigurable.addLibraryOrderEntry(module, library);
      }
    }
  }

  /**
   * @return <code>true</code> if handled
   */
  protected boolean addLibraryToDependencies(final Library library, final Project project, final boolean allowEmptySelection) {
    return false;
  }


  /**
   * @return <code>true</code> if it handled this element
   */
  protected boolean checkElement(ProjectStructureElement element, ProjectStructureProblemsHolder problemsHolder) {
    return false;
  }

  /**
   * @return list of usages or <code>null</code> when it does not handle such element
   */
  @Nullable
  protected List<ProjectStructureElementUsage> getUsagesIn(final ProjectStructureElement element) {
    return null;
  }
}
