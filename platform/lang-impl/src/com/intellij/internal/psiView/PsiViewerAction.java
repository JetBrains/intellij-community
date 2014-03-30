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
package com.intellij.internal.psiView;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;

/**
 * @author Konstantin Bulenkov
 */
public class PsiViewerAction extends AnAction implements DumbAware {
  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = CommonDataKeys.PROJECT.getData(e.getDataContext());
    new PsiViewerDialog(project, false, null, null).show();
  }

  @Override
  public void update(AnActionEvent e) {
    final Project project = CommonDataKeys.PROJECT.getData(e.getDataContext());
    final Presentation p = e.getPresentation();
    if (project == null) {
      p.setVisible(false);
      p.setEnabled(false);
      return;
    }

    if (ApplicationManagerEx.getApplicationEx().isInternal()) {
      p.setVisible(true);
      p.setEnabled(true);
      return;
    }

    final Module[] modules = ModuleManager.getInstance(project).getModules();
    for (Module module : modules) {
      if ("PLUGIN_MODULE".equals(ModuleType.get(module).getId())) {
        p.setVisible(true);
        p.setEnabled(true);
        return;
      }
    }
    
    p.setVisible(false);
    p.setEnabled(false);
  }
}
