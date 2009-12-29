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

package com.intellij.moduleDependencies;

import com.intellij.analysis.AnalysisScope;
import com.intellij.analysis.AnalysisScopeBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;

import javax.swing.*;
import java.awt.*;

/**
 * User: anna
 * Date: Feb 9, 2005
 */
public class ShowModuleDependenciesAction extends AnAction{
  public void actionPerformed(AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    if (project == null){
      return;
    }
    ModulesDependenciesPanel panel;
    AnalysisScope scope = new AnalysisScope(project);
    final Module[] modules = LangDataKeys.MODULE_CONTEXT_ARRAY.getData(dataContext);
    if (modules != null){
      panel = new ModulesDependenciesPanel(project, modules);
      scope = new AnalysisScope(modules);
    } else {
      final PsiElement element = LangDataKeys.PSI_FILE.getData(dataContext);
      final Module module = element != null ? ModuleUtil.findModuleForPsiElement(element) : null;
      if (module != null && ModuleManager.getInstance(project).getModules().length > 1){
        MyModuleOrProjectScope dlg = new MyModuleOrProjectScope(module.getName());
        dlg.show();
        if (dlg.isOK()){
          if (!dlg.useProjectScope()){
            panel = new ModulesDependenciesPanel(project, new Module[]{module});
            scope = new AnalysisScope(module);
          } else {
            panel = new ModulesDependenciesPanel(project);
          }
        } else {
          return;
        }
      } else {
        panel = new ModulesDependenciesPanel(project);
      }
    }

    Content content = ContentFactory.SERVICE.getInstance().createContent(panel,
                                                                                  AnalysisScopeBundle.message(
                                                                                    "module.dependencies.toolwindow.title",
                                                                                    StringUtil.capitalize(scope.getDisplayName())),
                                                                                  false);
    content.setDisposer(panel);
    panel.setContent(content);
    DependenciesAnalyzeManager.getInstance(project).addContent(content);
  }

  public void update(AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    e.getPresentation().setEnabled(project != null);
  }

  private static class MyModuleOrProjectScope extends DialogWrapper{
    private final JRadioButton myProjectScope;
    private final JRadioButton myModuleScope;
    protected MyModuleOrProjectScope(String moduleName) {
      super(false);
      setTitle(AnalysisScopeBundle.message("module.dependencies.scope.dialog.title"));
      ButtonGroup group = new ButtonGroup();
      myProjectScope = new JRadioButton(AnalysisScopeBundle.message("module.dependencies.scope.dialog.project.button"));
      myModuleScope = new JRadioButton(AnalysisScopeBundle.message("module.dependencies.scope.dialog.module.button", moduleName));
      group.add(myProjectScope);
      group.add(myModuleScope);
      myProjectScope.setSelected(true);
      init();
    }

    protected JComponent createCenterPanel() {
      JPanel panel = new JPanel(new GridLayout(2, 1));
      panel.add(myProjectScope);
      panel.add(myModuleScope);
      return panel;
    }

    public boolean useProjectScope(){
      return myProjectScope.isSelected();
    }
  }
}
