/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.openapi.roots.ui.configuration.projectRoot;

import com.intellij.ide.JavaUiBundle;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.libraries.LibraryEditingUtil;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.LibraryProjectStructureElement;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.ProjectStructureElement;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.ProjectStructureValidator;
import org.jetbrains.annotations.NotNull;

public class AddLibraryToModuleDependenciesAction extends DumbAwareAction {
  @NotNull private final BaseLibrariesConfigurable myConfigurable;

  public AddLibraryToModuleDependenciesAction(@NotNull BaseLibrariesConfigurable configurable) {
    super(JavaUiBundle.message("action.text.add.to.modules"), JavaUiBundle.message(
      "action.description.add.the.library.to.the.dependencies.list.of.chosen.modules"), null);
    myConfigurable = configurable;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    ProjectStructureElement element = e.getUpdateSession()
      .compute(this, "getSelection", ActionUpdateThread.EDT, () -> myConfigurable.getSelectedElement());
    boolean visible = false;
    if (element instanceof LibraryProjectStructureElement) {
      final LibraryEx library = (LibraryEx)((LibraryProjectStructureElement)element).getLibrary();
      visible = !LibraryEditingUtil.getSuitableModules(myConfigurable.getProjectStructureConfigurable().getModulesConfig(), library.getKind(), library).isEmpty();
    }
    e.getPresentation().setVisible(visible);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final LibraryProjectStructureElement element = (LibraryProjectStructureElement)myConfigurable.getSelectedElement();
    if (element == null) return;
    final Library library = element.getLibrary();
    ProjectStructureValidator.showDialogAndAddLibraryToDependencies(library, myConfigurable.getProjectStructureConfigurable(),
                                                                    false);
  }
}
