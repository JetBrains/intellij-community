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

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInspection.*;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.packageDependencies.DependenciesBuilder;
import com.intellij.packageDependencies.DependencyRule;
import com.intellij.packageDependencies.DependencyValidationManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class DependencyInspectionBase extends AbstractBaseJavaLocalInspectionTool {
  private static final String GROUP_DISPLAY_NAME = "";
  private static final String DISPLAY_NAME = InspectionsBundle.message("illegal.package.dependencies");
  @NonNls private static final String SHORT_NAME = "Dependency";

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  @NotNull
  public String getGroupDisplayName() {
    return GROUP_DISPLAY_NAME;
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return DISPLAY_NAME;
  }

  @Override
  @NotNull
  public String getShortName() {
    return SHORT_NAME;
  }

  @Override
  @Nullable
  public ProblemDescriptor[] checkFile(@NotNull final PsiFile file, @NotNull final InspectionManager manager, final boolean isOnTheFly) {
    if (file.getViewProvider().getPsi(JavaLanguage.INSTANCE) == null) return null;
    final DependencyValidationManager validationManager = DependencyValidationManager.getInstance(file.getProject());
    if (!validationManager.hasRules()) return null;
    if (validationManager.getApplicableRules(file).length == 0) return null;
    final List<ProblemDescriptor> problems =  new ArrayList<ProblemDescriptor>();
    DependenciesBuilder.analyzeFileDependencies(file, new DependenciesBuilder.DependencyProcessor() {
      @Override
      public void process(PsiElement place, PsiElement dependency) {
        PsiFile dependencyFile = dependency.getContainingFile();
        if (dependencyFile != null && dependencyFile.isPhysical() && dependencyFile.getVirtualFile() != null) {
          final DependencyRule[] rule = validationManager.getViolatorDependencyRules(file, dependencyFile);
          for (DependencyRule dependencyRule : rule) {
            problems.add(manager.createProblemDescriptor(place, InspectionsBundle
              .message("inspection.dependency.violator.problem.descriptor", dependencyRule.getDisplayText()), isOnTheFly,
                                                         createEditDependencyFixes(dependencyRule),
                                                         ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
          }
        }
      }
    });
    return problems.isEmpty() ? null : problems.toArray(new ProblemDescriptor[problems.size()]);
  }

  protected LocalQuickFix[] createEditDependencyFixes(DependencyRule dependencyRule) {
    return null;
  }

  @Override
  @NotNull
  public HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.ERROR;
  }
}
