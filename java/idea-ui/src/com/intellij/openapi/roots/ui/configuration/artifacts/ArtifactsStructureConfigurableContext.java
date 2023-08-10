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

import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactType;
import com.intellij.packaging.artifacts.ModifiableArtifactModel;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.elements.PackagingElementResolvingContext;
import com.intellij.packaging.ui.ArtifactEditor;
import com.intellij.packaging.ui.ManifestFileConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface ArtifactsStructureConfigurableContext extends PackagingElementResolvingContext {
  @NotNull
  ModifiableArtifactModel getOrCreateModifiableArtifactModel();

  @Nullable
  ManifestFileConfiguration getManifestFile(CompositePackagingElement<?> element, ArtifactType artifactType);

  CompositePackagingElement<?> getRootElement(@NotNull Artifact artifact);

  void editLayout(@NotNull Artifact artifact, Runnable action);

  ArtifactEditor getOrCreateEditor(Artifact artifact);

  @NotNull
  Artifact getOriginalArtifact(@NotNull Artifact artifact);

  @Nullable
  ModifiableModuleModel getModifiableModuleModel();

  void queueValidation(Artifact artifact);

  @NotNull
  ArtifactProjectStructureElement getOrCreateArtifactElement(@NotNull Artifact artifact);

  ModifiableRootModel getOrCreateModifiableRootModel(Module module);

  ArtifactEditorSettings getDefaultSettings();

  @NotNull ProjectStructureConfigurable getProjectStructureConfigurable();
}
