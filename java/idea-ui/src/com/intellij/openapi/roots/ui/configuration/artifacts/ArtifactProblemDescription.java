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
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.ProjectStructureProblemDescription;
import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.ui.ArtifactEditor;
import com.intellij.packaging.ui.ArtifactProblemQuickFix;
import com.intellij.ui.navigation.Place;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author nik
 */
public class ArtifactProblemDescription extends ProjectStructureProblemDescription {
  private final List<ArtifactProblemQuickFix> myQuickFixes;
  private final List<PackagingElement<?>> myPathToPlace;

  public ArtifactProblemDescription(@NotNull String message,
                                    @NotNull Severity severity,
                                    @Nullable List<PackagingElement<?>> pathToPlace,
                                    @NotNull List<ArtifactProblemQuickFix> quickFixes,
                                    @NotNull Place place,
                                    @NotNull ArtifactEditor artifactEditor) {
    super(message, null, severity, place, convertQuickFixed(quickFixes, artifactEditor));
    myPathToPlace = pathToPlace;
    myQuickFixes = quickFixes;
  }

  private static List<ConfigurationErrorQuickFix> convertQuickFixed(List<ArtifactProblemQuickFix> quickFixes, final ArtifactEditor artifactEditor) {
    final List<ConfigurationErrorQuickFix> result = new SmartList<ConfigurationErrorQuickFix>();
    for (final ArtifactProblemQuickFix fix : quickFixes) {
      result.add(new ConfigurationErrorQuickFix(fix.getActionName()) {
        @Override
        public void performFix() {
          fix.performFix(artifactEditor);
        }
      });
    }
    return result;
  }

  @NotNull
  public List<ArtifactProblemQuickFix> getQuickFixes() {
    return myQuickFixes;
  }

  @Nullable 
  public List<PackagingElement<?>> getPathToPlace() {
    return myPathToPlace;
  }
}
