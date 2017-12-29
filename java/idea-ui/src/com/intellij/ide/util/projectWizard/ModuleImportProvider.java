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
package com.intellij.ide.util.projectWizard;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.projectImport.ProjectImportProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Avdeev
 */
public class ModuleImportProvider extends ProjectImportProvider {

  public ModuleImportProvider() {
    super(new ModuleImportBuilder());
  }

  @Override
  public boolean canImport(@NotNull VirtualFile fileOrDirectory, Project project) {
    return project != null && !fileOrDirectory.isDirectory() && "iml".equals(fileOrDirectory.getExtension());
  }

  @Override
  public String getPathToBeImported(VirtualFile file) {
    return file.getPath();
  }

  @Override
  public boolean canCreateNewProject() {
    return false;
  }

  @Override
  public ModuleWizardStep[] createSteps(WizardContext context) {
    return ModuleWizardStep.EMPTY_ARRAY;
  }

  @Nullable
  @Override
  public String getFileSample() {
    return "Intellij IDEA module file (*.iml)";
  }
}
