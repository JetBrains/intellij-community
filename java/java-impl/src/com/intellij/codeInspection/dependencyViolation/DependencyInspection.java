// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dependencyViolation;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.*;
import com.intellij.ide.DataManager;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.packageDependencies.DependenciesBuilder;
import com.intellij.packageDependencies.DependencyRule;
import com.intellij.packageDependencies.DependencyValidationManager;
import com.intellij.packageDependencies.ui.DependencyConfigurable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.SmartList;
import com.intellij.util.containers.FactoryMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.Map;

public class DependencyInspection extends AbstractBaseJavaLocalInspectionTool {

  private static LocalQuickFix[] createEditDependencyFixes(DependencyRule dependencyRule) {
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

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  @NotNull
  public HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.ERROR;
  }

  @Override
  @NotNull
  public String getGroupDisplayName() {
    return GroupNames.DEPENDENCY_GROUP_NAME;
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionsBundle.message("illegal.package.dependencies");
  }

  @Override
  @NotNull
  public String getShortName() {
    return "Dependency";
  }

  @Override
  @Nullable
  public ProblemDescriptor[] checkFile(@NotNull final PsiFile file, @NotNull final InspectionManager manager, final boolean isOnTheFly) {
    if (file.getViewProvider().getPsi(JavaLanguage.INSTANCE) == null) {
      return null;
    }

    final DependencyValidationManager validationManager = DependencyValidationManager.getInstance(file.getProject());
    if (!validationManager.hasRules() || validationManager.getApplicableRules(file).length == 0) {
      return null;
    }

    final List<ProblemDescriptor> problems = new SmartList<>();
    DependenciesBuilder.analyzeFileDependencies(file, new DependenciesBuilder.DependencyProcessor() {
      private final Map<PsiFile, DependencyRule[]> violations =
        FactoryMap.create(dependencyFile -> validationManager.getViolatorDependencyRules(file, dependencyFile));

      @Override
      public void process(PsiElement place, PsiElement dependency) {
        PsiFile dependencyFile = dependency.getContainingFile();
        if (dependencyFile != null && dependencyFile.isPhysical() && dependencyFile.getVirtualFile() != null) {
          for (DependencyRule dependencyRule : violations.get(dependencyFile)) {
            String message = InspectionsBundle.message("inspection.dependency.violator.problem.descriptor", dependencyRule.getDisplayText());
            LocalQuickFix[] fixes = createEditDependencyFixes(dependencyRule);
            problems.add(manager.createProblemDescriptor(place, message, isOnTheFly, fixes, ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
          }
        }
      }
    });

    return problems.isEmpty() ? null : problems.toArray(ProblemDescriptor.EMPTY_ARRAY);
  }

  private static class EditDependencyRulesAction implements LocalQuickFix {
    private final DependencyRule myRule;
    EditDependencyRulesAction(DependencyRule rule) {
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
    public boolean startInWriteAction() {
      return false;
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      ShowSettingsUtil.getInstance().editConfigurable(project, new DependencyConfigurable(project));
    }
  }
}
