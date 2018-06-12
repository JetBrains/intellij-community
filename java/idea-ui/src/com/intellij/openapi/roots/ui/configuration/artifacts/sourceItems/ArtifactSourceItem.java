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
package com.intellij.openapi.roots.ui.configuration.artifacts.sourceItems;

import com.intellij.openapi.project.Project;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactPointer;
import com.intellij.packaging.artifacts.ArtifactPointerManager;
import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.elements.PackagingElementFactory;
import com.intellij.packaging.elements.PackagingElementOutputKind;
import com.intellij.packaging.impl.artifacts.JarArtifactType;
import com.intellij.packaging.impl.ui.ArtifactElementPresentation;
import com.intellij.packaging.ui.ArtifactEditorContext;
import com.intellij.packaging.ui.PackagingSourceItem;
import com.intellij.packaging.ui.SourceItemPresentation;
import com.intellij.packaging.ui.SourceItemWeights;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public class ArtifactSourceItem extends PackagingSourceItem {
  private final Artifact myArtifact;

  public ArtifactSourceItem(@NotNull Artifact artifact) {
    myArtifact = artifact;
  }

  @NotNull
  @Override
  public SourceItemPresentation createPresentation(@NotNull ArtifactEditorContext context) {
    final ArtifactPointer pointer = ArtifactPointerManager.getInstance(context.getProject()).createPointer(myArtifact, context.getArtifactModel());
    return new DelegatedSourceItemPresentation(new ArtifactElementPresentation(pointer, context)) {
      @Override
      public int getWeight() {
        return SourceItemWeights.ARTIFACT_WEIGHT;
      }
    };
  }

  @Override
  @NotNull
  public List<? extends PackagingElement<?>> createElements(@NotNull ArtifactEditorContext context) {
    final Project project = context.getProject();
    final ArtifactPointer pointer = ArtifactPointerManager.getInstance(project).createPointer(myArtifact, context.getArtifactModel());
    return Collections.singletonList(PackagingElementFactory.getInstance().createArtifactElement(pointer, project));
  }

  public boolean equals(Object obj) {
    return obj instanceof ArtifactSourceItem && myArtifact.equals(((ArtifactSourceItem)obj).myArtifact);
  }

  @NotNull
  @Override
  public PackagingElementOutputKind getKindOfProducedElements() {
    return myArtifact.getArtifactType() instanceof JarArtifactType ? PackagingElementOutputKind.JAR_FILES : PackagingElementOutputKind.OTHER;
  }

  public Artifact getArtifact() {
    return myArtifact;
  }

  public int hashCode() {
    return myArtifact.hashCode();
  }
}
