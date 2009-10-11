/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.ide.projectView.impl.nodes;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public abstract class AbstractModuleNode extends ProjectViewNode<Module> {
  protected AbstractModuleNode(Project project, Module module, ViewSettings viewSettings) {
    super(project, module, viewSettings);
  }

  public void update(PresentationData presentation) {
    if (getValue().isDisposed()) {
      setValue(null);
      return;
    }
    presentation.setPresentableText(getValue().getName());
    presentation.setOpenIcon(getValue().getModuleType().getNodeIcon(true));
    presentation.setClosedIcon(getValue().getModuleType().getNodeIcon(false));
  }


  public String getTestPresentation() {
    return "Module";
  }

  public boolean contains(@NotNull VirtualFile file) {
    Module module = getValue();
    return module != null && !module.isDisposed() &&
           (ModuleUtil.moduleContainsFile(module, file, false) || ModuleUtil.moduleContainsFile(module, file, true));
  }

  public String getToolTip() {
    final Module module = getValue();
    return module.getModuleType().getName();
  }

  public void navigate(final boolean requestFocus) {
    ProjectSettingsService.getInstance(myProject).openModuleSettings(getValue());
  }

  public boolean canNavigate() {
    return true;
  }

  public boolean canNavigateToSource() {
    return false;
  }
}
