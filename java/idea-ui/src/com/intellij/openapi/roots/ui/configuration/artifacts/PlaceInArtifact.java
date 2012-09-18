/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.PlaceInProjectStructure;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.ProjectStructureElement;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.elements.PackagingElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public class PlaceInArtifact extends PlaceInProjectStructure {
  private final Artifact myArtifact;
  private final ArtifactsStructureConfigurableContext myContext;
  private final String myParentPath;
  private final PackagingElement<?> myPackagingElement;

  public PlaceInArtifact(Artifact artifact, ArtifactsStructureConfigurableContext context, @Nullable String parentPath,
                         @Nullable PackagingElement<?> packagingElement) {
    myArtifact = artifact;
    myContext = context;
    myParentPath = parentPath;
    myPackagingElement = packagingElement;
  }

  @NotNull
  @Override
  public ProjectStructureElement getContainingElement() {
    return myContext.getOrCreateArtifactElement(myArtifact);
  }

  @Override
  public String getPlacePath() {
    if (myParentPath != null && myPackagingElement != null) {
      //todo[nik] use id of element?
      return myParentPath + "/" + myPackagingElement.getType().getId();
    }
    return null;
  }

  @NotNull
  @Override
  public ActionCallback navigate() {
    final Artifact artifact = myContext.getArtifactModel().getArtifactByOriginal(myArtifact);
    return ProjectStructureConfigurable.getInstance(myContext.getProject()).select(myArtifact, true).doWhenDone(new Runnable() {
      @Override
      public void run() {
        final ArtifactEditorEx artifactEditor = (ArtifactEditorEx)myContext.getOrCreateEditor(artifact);
        if (myParentPath != null && myPackagingElement != null) {
          artifactEditor.getLayoutTreeComponent().selectNode(myParentPath, myPackagingElement);
        }
      }
    });
  }
}
