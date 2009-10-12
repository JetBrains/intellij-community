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
package com.intellij.packaging.ui;

import com.intellij.facet.Facet;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactType;
import com.intellij.packaging.artifacts.ModifiableArtifactModel;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.elements.PackagingElementResolvingContext;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author nik
 */
public interface ArtifactEditorContext extends PackagingElementResolvingContext {

  void queueValidation();

  @NotNull
  ArtifactType getArtifactType();

  @NotNull
  ModifiableArtifactModel getModifiableArtifactModel();

  @NotNull
  ManifestFileConfiguration getManifestFile(CompositePackagingElement<?> element, ArtifactType artifactType);

  CompositePackagingElement<?> getRootElement(@NotNull Artifact artifact);

  void ensureRootIsWritable(@NotNull Artifact artifact);

  ArtifactEditor getOrCreateEditor(Artifact originalArtifact);


  void selectArtifact(@NotNull Artifact artifact);

  void selectFacet(@NotNull Facet<?> facet);

  void selectModule(@NotNull Module module);

  void selectLibrary(@NotNull Library library);


  List<Artifact> chooseArtifacts(List<? extends Artifact> artifacts, String title);

  List<Module> chooseModules(List<Module> modules, final String title);

  List<Library> chooseLibraries(List<Library> libraries, String title);
}
