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
package com.intellij.codeInspection.dependencyViolation;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.BaseLocalInspectionTool;
import com.intellij.ide.DataManager;
import com.intellij.lang.StdLanguages;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.packageDependencies.DependenciesBuilder;
import com.intellij.packageDependencies.DependencyRule;
import com.intellij.packageDependencies.DependencyValidationManager;
import com.intellij.packageDependencies.ForwardDependenciesBuilder;
import com.intellij.packageDependencies.ui.DependencyConfigurable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.MessageFormat;
import java.util.ArrayList;

/**
 * User: anna
 * Date: Feb 6, 2005
 */
public class DependencyInspection extends BaseLocalInspectionTool {

  public static final String GROUP_DISPLAY_NAME = "";
  public static final String DISPLAY_NAME = InspectionsBundle.message("illegal.package.dependencies");
  @NonNls public static final String SHORT_NAME = "Dependency";

  @NotNull
  public String getGroupDisplayName() {
    return DependencyInspection.GROUP_DISPLAY_NAME;
  }

  @NotNull
  public String getDisplayName() {
    return DependencyInspection.DISPLAY_NAME;
  }

  @NotNull
  public String getShortName() {
    return DependencyInspection.SHORT_NAME;
  }

  public JComponent createOptionsPanel() {
    final JButton editDependencies = new JButton(InspectionsBundle.message("inspection.dependency.configure.button.text"));
    editDependencies.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        Project project = PlatformDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(editDependencies));
        if (project == null) project = ProjectManager.getInstance().getDefaultProject();
        ShowSettingsUtil.getInstance().editConfigurable(editDependencies, new DependencyConfigurable(project));
      }
    });

    JPanel depPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
    depPanel.add(editDependencies);
    return depPanel;
  }

  @Nullable
  public ProblemDescriptor[] checkFile(@NotNull final PsiFile file, @NotNull final InspectionManager manager, final boolean isOnTheFly) {
    if (file == null) return null;
    if (file.getViewProvider().getPsi(StdLanguages.JAVA) == null) return null;
    final DependencyValidationManager validationManager = DependencyValidationManager.getInstance(file.getProject());
    if (!validationManager.hasRules()) return null;
    if (validationManager.getApplicableRules(file).length == 0) return null;
    final ArrayList<ProblemDescriptor> problems =  new ArrayList<ProblemDescriptor>();
    ForwardDependenciesBuilder builder = new ForwardDependenciesBuilder(file.getProject(), new AnalysisScope(file));
        builder.analyzeFileDependencies(file, new DependenciesBuilder.DependencyProcessor() {
        public void process(PsiElement place, PsiElement dependency) {
          PsiFile dependencyFile = dependency.getContainingFile();
          if (dependencyFile != null && dependencyFile.isPhysical() && dependencyFile.getVirtualFile() != null) {
            final DependencyRule[] rule = validationManager.getViolatorDependencyRules(file, dependencyFile);
            for (DependencyRule dependencyRule : rule) {
              StringBuffer message = new StringBuffer();
              message.append(MessageFormat.format(InspectionsBundle.message("inspection.dependency.violator.problem.descriptor"), dependencyRule.getDisplayText()));
              problems.add(manager.createProblemDescriptor(place, message.toString(), isOnTheFly, new LocalQuickFix[]{new EditDependencyRulesAction(dependencyRule)}, ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
            }
          }
        }
    });
    return problems.isEmpty() ? null : problems.toArray(new ProblemDescriptor[problems.size()]);
  }

  @NotNull
  public HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.ERROR;
  }

  private static class EditDependencyRulesAction implements LocalQuickFix {
    private final DependencyRule myRule;
    public EditDependencyRulesAction(DependencyRule rule) {
      myRule = rule;
    }

    @NotNull
    public String getName() {
      return InspectionsBundle.message("edit.dependency.rules.text", myRule.getDisplayText());
    }

    @NotNull
    public String getFamilyName() {
      return InspectionsBundle.message("edit.dependency.rules.family");
    }

    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      ShowSettingsUtil.getInstance().editConfigurable(project, new DependencyConfigurable(project));
    }

  }

}
