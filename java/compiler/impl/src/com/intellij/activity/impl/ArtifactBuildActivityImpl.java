/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.activity.impl;

import com.intellij.activity.ArtifactBuildActivity;
import com.intellij.packaging.artifacts.Artifact;
import org.jetbrains.annotations.NotNull;

/**
 * @author Vladislav.Soroka
 * @since 5/14/2016
 */
public class ArtifactBuildActivityImpl extends AbstractBuildActivity implements ArtifactBuildActivity {
  private final Artifact myArtifact;

  public ArtifactBuildActivityImpl(Artifact artifact, boolean isIncrementalBuild) {
    super(isIncrementalBuild);
    myArtifact = artifact;
  }

  @Override
  public Artifact getArtifact() {
    return myArtifact;
  }

  @NotNull
  @Override
  public String getPresentableName() {
    return "Artifact '" + myArtifact.getName() + "' build activity";
  }
}
