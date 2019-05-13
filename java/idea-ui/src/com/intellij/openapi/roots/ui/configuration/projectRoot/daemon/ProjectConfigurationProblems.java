/*
 * Copyright 2000-2010 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.roots.ui.configuration.projectRoot.daemon;

import com.intellij.openapi.roots.ui.configuration.ConfigurationError;
import com.intellij.openapi.roots.ui.configuration.ConfigurationErrors;
import com.intellij.openapi.roots.ui.configuration.projectRoot.StructureConfigurableContext;
import com.intellij.openapi.util.MultiValuesMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * @author nik
 */
public class ProjectConfigurationProblems {
  private final MultiValuesMap<ProjectStructureElement, ConfigurationError> myErrors = new MultiValuesMap<>();
  private final ProjectStructureDaemonAnalyzer myAnalyzer;
  private final StructureConfigurableContext myContext;

  public ProjectConfigurationProblems(ProjectStructureDaemonAnalyzer analyzer, StructureConfigurableContext context) {
    myAnalyzer = analyzer;
    myContext = context;
    analyzer.addListener(new ProjectStructureDaemonAnalyzerListener() {
      @Override
      public void problemsChanged(@NotNull ProjectStructureElement element) {
        updateErrors(element);
      }
    });
  }

  public void clearProblems() {
    removeErrors(myErrors.values());
    myErrors.clear();
  }

  private void updateErrors(ProjectStructureElement element) {
    removeErrors(myErrors.removeAll(element));
    final ProjectStructureProblemsHolderImpl problemsHolder = myAnalyzer.getProblemsHolder(element);
    if (problemsHolder != null) {
      final List<ProjectStructureProblemDescription> descriptions = problemsHolder.getProblemDescriptions();
      if (descriptions != null) {
        for (ProjectStructureProblemDescription description : descriptions) {
          final ProjectConfigurationProblem error = new ProjectConfigurationProblem(description, myContext.getProject());
          myErrors.put(element, error);
          ConfigurationErrors.Bus.addError(error, myContext.getProject());
        }
      }
    }
  }

  private void removeErrors(@Nullable Collection<ConfigurationError> errors) {
    if (errors == null) return;
    for (ConfigurationError error : errors) {
      ConfigurationErrors.Bus.removeError(error, myContext.getProject());
    }
  }
}
