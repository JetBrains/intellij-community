/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.*;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.packageDependencies.DependenciesBuilder;
import com.intellij.packageDependencies.DependencyRule;
import com.intellij.packageDependencies.DependencyValidationManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FactoryMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public class DependencyInspectionBase extends BaseJavaBatchLocalInspectionTool {
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

    final List<ProblemDescriptor> problems = ContainerUtil.newSmartList();
    DependenciesBuilder.analyzeFileDependencies(file, new DependenciesBuilder.DependencyProcessor() {
      @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
      private final Map<PsiFile, DependencyRule[]> violations = new FactoryMap<PsiFile, DependencyRule[]>() {
        @Nullable
        @Override
        protected DependencyRule[] create(PsiFile dependencyFile) {
          return validationManager.getViolatorDependencyRules(file, dependencyFile);
        }
      };

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

    return problems.isEmpty() ? null : problems.toArray(new ProblemDescriptor[problems.size()]);
  }

  protected LocalQuickFix[] createEditDependencyFixes(DependencyRule dependencyRule) {
    return null;
  }
}
