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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.FacetsProvider;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.roots.ui.configuration.projectRoot.StructureConfigurableContext;
import com.intellij.openapi.util.Disposer;
import com.intellij.packaging.artifacts.*;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.impl.artifacts.ArtifactModelImpl;
import com.intellij.packaging.impl.artifacts.ArtifactUtil;
import com.intellij.packaging.ui.ManifestFileConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
* @author nik
*/
class ArtifactsStructureConfigurableContextImpl implements ArtifactsStructureConfigurableContext {
  private ModifiableArtifactModel myModifiableModel;
  private final ManifestFilesInfo myManifestFilesInfo = new ManifestFilesInfo();
  private ArtifactAdapter myModifiableModelListener;
  private StructureConfigurableContext myContext;
  private Project myProject;
  private Map<Artifact, CompositePackagingElement<?>> myModifiableRoots = new HashMap<Artifact, CompositePackagingElement<?>>();
  private Map<Artifact, ArtifactEditorImpl> myArtifactEditors = new HashMap<Artifact, ArtifactEditorImpl>();
  private Map<ArtifactPointer, ArtifactEditorSettings> myEditorSettings = new HashMap<ArtifactPointer, ArtifactEditorSettings>();
  private ArtifactEditorSettings myDefaultSettings = new ArtifactEditorSettings();

  public ArtifactsStructureConfigurableContextImpl(StructureConfigurableContext context, Project project, final ArtifactAdapter modifiableModelListener) {
    myModifiableModelListener = modifiableModelListener;
    myContext = context;
    myProject = project;
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  @NotNull
  public ArtifactModel getArtifactModel() {
    if (myModifiableModel != null) {
      return myModifiableModel;
    }
    return ArtifactManager.getInstance(myProject);
  }

  @NotNull
  public Artifact getOriginalArtifact(@NotNull Artifact artifact) {
    if (myModifiableModel != null) {
      return ((ArtifactModelImpl)myModifiableModel).getOriginalArtifact(artifact);
    }
    return artifact;
  }

  public CompositePackagingElement<?> getRootElement(@NotNull Artifact artifact) {
    artifact = getOriginalArtifact(artifact);
    if (myModifiableModel != null) {
      final CompositePackagingElement<?> rootElement = myModifiableModel.getArtifactByOriginal(artifact).getRootElement();
      if (rootElement != artifact.getRootElement()) {
        myModifiableRoots.put(artifact, rootElement);
      }
    }
    CompositePackagingElement<?> root = myModifiableRoots.get(artifact);
    if (root == null) {
      root = ArtifactUtil.copyFromRoot(artifact.getRootElement(), myProject);
      myModifiableRoots.put(artifact, root);
    }
    return root;
  }

  public void editLayout(@NotNull Artifact artifact, Runnable action) {
    artifact = getOriginalArtifact(artifact);
    final ModifiableArtifact modifiableArtifact = getModifiableArtifactModel().getOrCreateModifiableArtifact(artifact);
    if (modifiableArtifact.getRootElement() == artifact.getRootElement()) {
      modifiableArtifact.setRootElement(getRootElement(artifact));
    }
    action.run();
    myContext.getDaemonAnalyzer().queueUpdate(new ArtifactProjectStructureElement(myContext, this, artifact));
  }

  public ArtifactEditorImpl getOrCreateEditor(Artifact artifact) {
    artifact = getOriginalArtifact(artifact);
    ArtifactEditorImpl artifactEditor = myArtifactEditors.get(artifact);
    if (artifactEditor == null) {
      final ArtifactEditorSettings settings = myEditorSettings.get(ArtifactPointerManager.getInstance(myProject).create(artifact));
      artifactEditor = new ArtifactEditorImpl(this, artifact, settings != null ? settings : myDefaultSettings);
      myArtifactEditors.put(artifact, artifactEditor);
    }
    return artifactEditor;
  }

  @Nullable
  public ModifiableArtifactModel getActualModifiableModel() {
    return myModifiableModel;
  }

  @NotNull
  public ModifiableArtifactModel getModifiableArtifactModel() {
    if (myModifiableModel == null) {
      myModifiableModel = ArtifactManager.getInstance(myProject).createModifiableModel();
      ((ArtifactModelImpl)myModifiableModel).addListener(myModifiableModelListener);
    }
    return myModifiableModel;
  }

  @NotNull
  public ModulesProvider getModulesProvider() {
    return myContext.getModulesConfigurator();
  }

  @NotNull
  public FacetsProvider getFacetsProvider() {
    return myContext.getModulesConfigurator().getFacetsConfigurator();
  }

  @NotNull
  public ManifestFileConfiguration getManifestFile(CompositePackagingElement<?> element, ArtifactType artifactType) {
    return myManifestFilesInfo.getManifestFile(element, artifactType, this);
  }

  public ManifestFilesInfo getManifestFilesInfo() {
    return myManifestFilesInfo;
  }

  public void resetModifiableModel() {
    disposeUIResources();
    myModifiableModel = null;
    myModifiableRoots.clear();
    myManifestFilesInfo.clear();
  }

  public void disposeUIResources() {
    for (ArtifactEditorImpl editor : myArtifactEditors.values()) {
      Disposer.dispose(editor);
    }
    myArtifactEditors.clear();
  }

  public void saveEditorSettings() {
    myEditorSettings.clear();
    for (ArtifactEditorImpl artifactEditor : myArtifactEditors.values()) {
      final ArtifactPointer pointer = ArtifactPointerManager.getInstance(myProject).create(artifactEditor.getArtifact());
      myEditorSettings.put(pointer, artifactEditor.createSettings());
    }
  }
}
