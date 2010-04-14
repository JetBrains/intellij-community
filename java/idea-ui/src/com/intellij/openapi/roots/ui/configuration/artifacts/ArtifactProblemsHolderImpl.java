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
package com.intellij.openapi.roots.ui.configuration.artifacts;

import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.ProjectStructureProblemDescription;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.ProjectStructureProblemsHolder;
import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.impl.ui.ArtifactProblemsHolderBase;
import com.intellij.packaging.ui.ArtifactEditorContext;
import com.intellij.packaging.ui.ArtifactProblemQuickFix;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

/**
 * @author nik
 */
public class ArtifactProblemsHolderImpl extends ArtifactProblemsHolderBase {
  private final ProjectStructureProblemsHolder myProblemsHolder;

  public ArtifactProblemsHolderImpl(ArtifactEditorContext context, ProjectStructureProblemsHolder problemsHolder) {
    super(context);
    myProblemsHolder = problemsHolder;
  }

  public void registerError(@NotNull String message, @Nullable List<PackagingElement<?>> pathToPlace, @NotNull ArtifactProblemQuickFix... quickFixes) {
    registerProblem(message, pathToPlace, ProjectStructureProblemDescription.Severity.ERROR, quickFixes);
  }

  private void registerProblem(@NotNull String message, @Nullable List<PackagingElement<?>> pathToPlace,
                               final ProjectStructureProblemDescription.Severity severity, @NotNull ArtifactProblemQuickFix... quickFixes) {
    myProblemsHolder.registerProblem(new ArtifactProblemDescription(message, severity, pathToPlace, Arrays.asList(quickFixes)));
  }

  public void registerWarning(@NotNull String message,
                              @Nullable List<PackagingElement<?>> pathToPlace,
                              @NotNull ArtifactProblemQuickFix... quickFixes) {
    registerProblem(message, pathToPlace, ProjectStructureProblemDescription.Severity.WARNING, quickFixes);
  }
}
