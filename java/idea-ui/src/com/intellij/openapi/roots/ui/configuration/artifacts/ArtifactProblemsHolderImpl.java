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

import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.ConfigurationErrorQuickFix;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.ProjectStructureProblemType;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.ProjectStructureProblemsHolder;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.impl.artifacts.PackagingElementPath;
import com.intellij.packaging.impl.ui.ArtifactProblemsHolderBase;
import com.intellij.packaging.ui.ArtifactEditor;
import com.intellij.packaging.ui.ArtifactProblemQuickFix;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author nik
 */
public class ArtifactProblemsHolderImpl extends ArtifactProblemsHolderBase {
  private final ArtifactsStructureConfigurableContext myContext;
  private final Artifact myOriginalArtifact;
  private final ProjectStructureProblemsHolder myProblemsHolder;

  public ArtifactProblemsHolderImpl(ArtifactsStructureConfigurableContext context,
                                    Artifact originalArtifact,
                                    ProjectStructureProblemsHolder problemsHolder) {
    super(context);
    myContext = context;
    myOriginalArtifact = originalArtifact;
    myProblemsHolder = problemsHolder;
  }

  @Override
  public void registerError(@NotNull String message,
                            @NotNull String problemTypeId,
                            @Nullable List<PackagingElement<?>> pathToPlace,
                            @NotNull ArtifactProblemQuickFix... quickFixes) {
    registerProblem(message, pathToPlace, ProjectStructureProblemType.error(problemTypeId), quickFixes);
  }

  @Override
  public void registerWarning(@NotNull String message,
                              @NotNull String problemTypeId, @Nullable List<PackagingElement<?>> pathToPlace,
                              @NotNull ArtifactProblemQuickFix... quickFixes) {
    registerProblem(message, pathToPlace, ProjectStructureProblemType.warning(problemTypeId), quickFixes);
  }

  private void registerProblem(@NotNull String message, @Nullable List<PackagingElement<?>> pathToPlace,
                               final ProjectStructureProblemType problemType, @NotNull ArtifactProblemQuickFix... quickFixes) {
    String parentPath;
    PackagingElement<?> element;
    if (pathToPlace != null && !pathToPlace.isEmpty()) {
      parentPath = PackagingElementPath.createPath(pathToPlace.subList(1, pathToPlace.size()-1)).getPathString();
      element = pathToPlace.get(pathToPlace.size() - 1);
    }
    else {
      parentPath = null;
      element = null;
    }
    final Artifact artifact = myContext.getArtifactModel().getArtifactByOriginal(myOriginalArtifact);
    final PlaceInArtifact place = new PlaceInArtifact(artifact, myContext, parentPath, element);
    myProblemsHolder.registerProblem(new ArtifactProblemDescription(message, problemType, pathToPlace, place, convertQuickFixes(quickFixes)));
  }

  private List<ConfigurationErrorQuickFix> convertQuickFixes(ArtifactProblemQuickFix[] quickFixes) {
    final List<ConfigurationErrorQuickFix> result = new SmartList<>();
    for (final ArtifactProblemQuickFix fix : quickFixes) {
      result.add(new ConfigurationErrorQuickFix(fix.getActionName()) {
        @Override
        public void performFix() {
          final ArtifactEditor editor = myContext.getOrCreateEditor(myOriginalArtifact);
          fix.performFix(((ArtifactEditorEx)editor).getContext());
        }
      });
    }
    return result;
  }
}
