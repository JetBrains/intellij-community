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
package com.intellij.packaging.impl.artifacts;

import com.intellij.openapi.project.Project;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactManager;
import com.intellij.packaging.artifacts.ArtifactModel;
import com.intellij.packaging.artifacts.ArtifactPointer;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class ArtifactPointerImpl implements ArtifactPointer {
  private final Project myProject;
  private String myName;
  private Artifact myArtifact;

  public ArtifactPointerImpl(@NotNull Project project, @NotNull String name) {
    myProject = project;
    myName = name;
  }

  @NotNull
  public String getName() {
    return myName;
  }

  public Artifact getArtifact() {
    if (myArtifact == null) {
      myArtifact = findArtifact(ArtifactManager.getInstance(myProject));
    }
    return myArtifact;
  }

  public Artifact findArtifact(@NotNull ArtifactModel artifactModel) {
    if (myArtifact != null) {
      final Artifact artifact = artifactModel.getArtifactByOriginal(myArtifact);
      if (!artifact.equals(myArtifact)) {
        return artifact;
      }
    }
    return artifactModel.findArtifact(myName);
  }

  void setArtifact(Artifact artifact) {
    myArtifact = artifact;
  }

  void setName(String name) {
    myName = name;
  }
}
