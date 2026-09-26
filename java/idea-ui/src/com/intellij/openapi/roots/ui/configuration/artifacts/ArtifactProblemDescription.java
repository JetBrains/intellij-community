// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration.artifacts;

import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.ConfigurationErrorQuickFix;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.ProjectStructureProblemDescription;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.ProjectStructureProblemType;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.packaging.elements.PackagingElement;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ArtifactProblemDescription extends ProjectStructureProblemDescription {
  private final List<PackagingElement<?>> myPathToPlace;

  public ArtifactProblemDescription(@NotNull @Nls(capitalization = Nls.Capitalization.Sentence) String message, @NotNull ProjectStructureProblemType problemType,
                                    @Nullable List<PackagingElement<?>> pathToPlace, @NotNull PlaceInArtifact place,
                                    final List<ConfigurationErrorQuickFix> quickFixList) {
    super(message, HtmlChunk.empty(), place, problemType, quickFixList);
    myPathToPlace = pathToPlace;
  }

  public @Nullable List<PackagingElement<?>> getPathToPlace() {
    return myPathToPlace;
  }
}
