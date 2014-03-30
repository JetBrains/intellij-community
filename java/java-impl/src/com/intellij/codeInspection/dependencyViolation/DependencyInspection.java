/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.codeInspection.dependencyViolation;

import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.packageDependencies.DependencyRule;
import com.intellij.packageDependencies.ui.DependencyConfigurable;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * User: anna
 * Date: Feb 6, 2005
 */
public class DependencyInspection extends DependencyInspectionBase {

  @Override
  protected LocalQuickFix[] createEditDependencyFixes(DependencyRule dependencyRule) {
    return new LocalQuickFix[]{
      new EditDependencyRulesAction(dependencyRule)};
  }

  @Override
  public JComponent createOptionsPanel() {
    final JButton editDependencies = new JButton(InspectionsBundle.message("inspection.dependency.configure.button.text"));
    editDependencies.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        Project project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(editDependencies));
        if (project == null) project = ProjectManager.getInstance().getDefaultProject();
        ShowSettingsUtil.getInstance().editConfigurable(editDependencies, new DependencyConfigurable(project));
      }
    });

    JPanel depPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
    depPanel.add(editDependencies);
    return depPanel;
  }

  private static class EditDependencyRulesAction implements LocalQuickFix {
    private final DependencyRule myRule;
    public EditDependencyRulesAction(DependencyRule rule) {
      myRule = rule;
    }

    @Override
    @NotNull
    public String getName() {
      return InspectionsBundle.message("edit.dependency.rules.text", myRule.getDisplayText());
    }

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionsBundle.message("edit.dependency.rules.family");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      ShowSettingsUtil.getInstance().editConfigurable(project, new DependencyConfigurable(project));
    }
  }
}
