/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.FacetsProvider;
import com.intellij.openapi.roots.ui.configuration.ModuleEditor;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.roots.ui.configuration.projectRoot.StructureConfigurableContext;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.ProjectStructureDaemonAnalyzerListener;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.ProjectStructureElement;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.ProjectStructureProblemsHolderImpl;
import com.intellij.openapi.util.Disposer;
import com.intellij.packaging.artifacts.*;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.elements.ManifestFileProvider;
import com.intellij.packaging.impl.artifacts.ArtifactUtil;
import com.intellij.packaging.impl.artifacts.DefaultPackagingElementResolvingContext;
import com.intellij.packaging.ui.ManifestFileConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
* @author nik
*/
public class ArtifactsStructureConfigurableContextImpl implements ArtifactsStructureConfigurableContext {
  private ModifiableArtifactModel myModifiableModel;
  private final ManifestFilesInfo myManifestFilesInfo = new ManifestFilesInfo();
  private final ArtifactAdapter myModifiableModelListener;
  private final StructureConfigurableContext myContext;
  private final Project myProject;
  private final Map<Artifact, CompositePackagingElement<?>> myModifiableRoots = new HashMap<>();
  private final Map<Artifact, ArtifactEditorImpl> myArtifactEditors = new HashMap<>();
  private final Map<ArtifactPointer, ArtifactEditorSettings> myEditorSettings = new HashMap<>();
  private final Map<Artifact, ArtifactProjectStructureElement> myArtifactElements = new HashMap<>();
  private final ArtifactEditorSettings myDefaultSettings;
  private final ManifestFileProvider myManifestFileProvider = new ArtifactEditorManifestFileProvider(this);

  public ArtifactsStructureConfigurableContextImpl(StructureConfigurableContext context, Project project,
                                                   ArtifactEditorSettings defaultSettings, final ArtifactAdapter modifiableModelListener) {
    myDefaultSettings = defaultSettings;
    myModifiableModelListener = modifiableModelListener;
    myContext = context;
    myProject = project;
    context.getDaemonAnalyzer().addListener(new ProjectStructureDaemonAnalyzerListener() {
      @Override
      public void problemsChanged(@NotNull ProjectStructureElement element) {
        if (element instanceof ArtifactProjectStructureElement) {
          final Artifact originalArtifact = ((ArtifactProjectStructureElement)element).getOriginalArtifact();
          final ArtifactEditorImpl artifactEditor = myArtifactEditors.get(originalArtifact);
          if (artifactEditor != null) {
            updateProblems(originalArtifact, artifactEditor);
          }
        }
      }
    });
  }

  private void updateProblems(Artifact originalArtifact, ArtifactEditorImpl artifactEditor) {
    final ProjectStructureProblemsHolderImpl holder = myContext.getDaemonAnalyzer().getProblemsHolder(getOrCreateArtifactElement(originalArtifact));
    artifactEditor.getValidationManager().updateProblems(holder);
  }

  @Override
  @NotNull
  public Project getProject() {
    return myProject;
  }

  @Override
  @NotNull
  public ArtifactModel getArtifactModel() {
    if (myModifiableModel != null) {
      return myModifiableModel;
    }
    return ArtifactManager.getInstance(myProject);
  }

  @Override
  @NotNull
  public Artifact getOriginalArtifact(@NotNull Artifact artifact) {
    if (myModifiableModel != null) {
      return myModifiableModel.getOriginalArtifact(artifact);
    }
    return artifact;
  }

  @Override
  public ModifiableModuleModel getModifiableModuleModel() {
    return myContext.getModulesConfigurator().getModuleModel();
  }

  @Override
  public void queueValidation(Artifact artifact) {
    myContext.getDaemonAnalyzer().queueUpdate(getOrCreateArtifactElement(artifact));
  }

  @Override
  public CompositePackagingElement<?> getRootElement(@NotNull Artifact artifact) {
    artifact = getOriginalArtifact(artifact);
    if (myModifiableModel != null) {
      final Artifact modifiableArtifact = myModifiableModel.getModifiableCopy(artifact);
      if (modifiableArtifact != null) {
        myModifiableRoots.put(artifact, modifiableArtifact.getRootElement());
      }
    }
    return getOrCreateModifiableRootElement(artifact);
  }

  private CompositePackagingElement<?> getOrCreateModifiableRootElement(Artifact originalArtifact) {
    CompositePackagingElement<?> root = myModifiableRoots.get(originalArtifact);
    if (root == null) {
      root = ArtifactUtil.copyFromRoot(originalArtifact.getRootElement(), myProject);
      myModifiableRoots.put(originalArtifact, root);
    }
    return root;
  }

  @Override
  public void editLayout(@NotNull final Artifact artifact, final Runnable action) {
    final Artifact originalArtifact = getOriginalArtifact(artifact);
    new WriteAction() {
      @Override
      protected void run(@NotNull final Result result) {
        final ModifiableArtifact modifiableArtifact = getOrCreateModifiableArtifactModel().getOrCreateModifiableArtifact(originalArtifact);
        if (modifiableArtifact.getRootElement() == originalArtifact.getRootElement()) {
          modifiableArtifact.setRootElement(getOrCreateModifiableRootElement(originalArtifact));
        }
        action.run();
      }
    }.execute();
    myContext.getDaemonAnalyzer().queueUpdate(getOrCreateArtifactElement(originalArtifact));
  }

  @Nullable 
  public ArtifactEditorImpl getArtifactEditor(Artifact artifact) {
    return myArtifactEditors.get(getOriginalArtifact(artifact));
  }

  @Override
  public ArtifactEditorImpl getOrCreateEditor(Artifact artifact) {
    artifact = getOriginalArtifact(artifact);
    ArtifactEditorImpl artifactEditor = myArtifactEditors.get(artifact);
    if (artifactEditor == null) {
      final ArtifactEditorSettings settings = myEditorSettings.get(ArtifactPointerManager.getInstance(myProject).createPointer(artifact, getArtifactModel()));
      artifactEditor = new ArtifactEditorImpl(this, artifact, settings != null ? settings : myDefaultSettings);
      myArtifactEditors.put(artifact, artifactEditor);
    }
    return artifactEditor;
  }

  @Nullable
  public ModifiableArtifactModel getActualModifiableModel() {
    return myModifiableModel;
  }

  @Override
  @NotNull
  public ModifiableArtifactModel getOrCreateModifiableArtifactModel() {
    if (myModifiableModel == null) {
      myModifiableModel = ArtifactManager.getInstance(myProject).createModifiableModel();
      myModifiableModel.addListener(myModifiableModelListener);
    }
    return myModifiableModel;
  }

  @Override
  public ArtifactEditorSettings getDefaultSettings() {
    return myDefaultSettings;
  }

  @Override
  @NotNull
  public ModulesProvider getModulesProvider() {
    return myContext.getModulesConfigurator();
  }

  @Override
  @NotNull
  public FacetsProvider getFacetsProvider() {
    return myContext.getModulesConfigurator().getFacetsConfigurator();
  }

  @Override
  public Library findLibrary(@NotNull String level, @NotNull String libraryName) {
    final Library library = DefaultPackagingElementResolvingContext.findLibrary(myProject, level, libraryName);
    return library != null ? myContext.getLibraryModel(library) : myContext.getLibrary(libraryName, level);
  }

  @NotNull
  @Override
  public ManifestFileProvider getManifestFileProvider() {
    return myManifestFileProvider;
  }

  @Override
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
    if (myModifiableModel != null) {
      myModifiableModel.dispose();
    }
    myArtifactElements.clear();
  }

  public Collection<? extends ArtifactEditorImpl> getArtifactEditors() {
    return Collections.unmodifiableCollection(myArtifactEditors.values());
  }

  public void saveEditorSettings() {
    myEditorSettings.clear();
    for (ArtifactEditorImpl artifactEditor : myArtifactEditors.values()) {
      final ArtifactPointer pointer = ArtifactPointerManager.getInstance(myProject).createPointer(artifactEditor.getArtifact(), getArtifactModel());
      myEditorSettings.put(pointer, artifactEditor.createSettings());
    }
  }

  @Override
  @NotNull
  public ArtifactProjectStructureElement getOrCreateArtifactElement(@NotNull Artifact artifact) {
    ArtifactProjectStructureElement element = myArtifactElements.get(getOriginalArtifact(artifact));
    if (element == null) {
      element = new ArtifactProjectStructureElement(myContext, this, artifact);
      myArtifactElements.put(artifact, element);
    }
    return element;
  }

  @Override
  public ModifiableRootModel getOrCreateModifiableRootModel(Module module) {
    final ModuleEditor editor = myContext.getModulesConfigurator().getOrCreateModuleEditor(module);
    return editor.getModifiableRootModelProxy();
  }
}
