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
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.options.ex.SingleConfigurableEditor;
import com.intellij.openapi.options.newEditor.OptionsEditorDialog;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.ui.border.CustomLineBorder;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;

public class ShowStructureSettingsAction extends AnAction implements DumbAware {
  public void actionPerformed(AnActionEvent e) {
    Project project = CommonDataKeys.PROJECT.getData(e.getDataContext());
    if (project == null) {
      project = ProjectManager.getInstance().getDefaultProject();
    }

    if (Registry.is("ide.new.project.settings")) {
      new SingleConfigurableEditor(project, ProjectStructureConfigurable.getInstance(project), OptionsEditorDialog.DIMENSION_KEY) {
        @Nullable
        @Override
        protected Border createContentPaneBorder() {
          return new EmptyBorder(0,0,0,0);
        }

        @Nullable
        @Override
        protected JComponent createSouthPanel() {
          JComponent panel = super.createSouthPanel();
          assert panel != null;
          CustomLineBorder line = new CustomLineBorder(new JBColor(Gray._153, Gray._80), 1, 0, 0, 0);
          panel.setBorder(new CompoundBorder(line, new EmptyBorder(10, 5, 5, 5)));
          return panel;
        }
      }.show();
    } else {
      ShowSettingsUtil
        .getInstance().editConfigurable(project, OptionsEditorDialog.DIMENSION_KEY, ProjectStructureConfigurable.getInstance(project));
    }
  }
}