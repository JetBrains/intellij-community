/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectStructureElementConfigurable;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.ProjectStructureElement;
import com.intellij.packaging.artifacts.Artifact;
import org.jetbrains.annotations.Nls;

import javax.swing.*;

/**
 * @author nik
 */
public abstract class ArtifactConfigurableBase extends ProjectStructureElementConfigurable<Artifact> {
  protected final Artifact myOriginalArtifact;
  protected final ArtifactsStructureConfigurableContextImpl myArtifactsStructureContext;
  private final ProjectStructureElement myProjectStructureElement;

  protected ArtifactConfigurableBase(Artifact originalArtifact,
                                     ArtifactsStructureConfigurableContextImpl artifactsStructureContext,
                                     Runnable updateTree,
                                     final boolean nameEditable) {
    super(nameEditable, updateTree);
    myOriginalArtifact = originalArtifact;
    myArtifactsStructureContext = artifactsStructureContext;
    myProjectStructureElement = myArtifactsStructureContext.getOrCreateArtifactElement(myOriginalArtifact);
  }

  @Override
  public ProjectStructureElement getProjectStructureElement() {
    return myProjectStructureElement;
  }

  protected Artifact getArtifact() {
    return myArtifactsStructureContext.getArtifactModel().getArtifactByOriginal(myOriginalArtifact);
  }

  @Override
  public Artifact getEditableObject() {
    return getArtifact();
  }

  @Override
  public String getBannerSlogan() {
    return ProjectBundle.message("banner.slogan.artifact.0", getDisplayName());
  }

  @Override
  @Nls
  public String getDisplayName() {
    return getArtifact().getName();
  }

  @Override
  public Icon getIcon(boolean open) {
    return getArtifact().getArtifactType().getIcon();
  }

  @Override
  public void disposeUIResources() {
  }
}
