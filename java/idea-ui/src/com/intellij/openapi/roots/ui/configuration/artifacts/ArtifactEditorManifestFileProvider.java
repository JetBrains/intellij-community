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

import com.intellij.packaging.artifacts.ArtifactType;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.elements.ManifestFileProvider;
import com.intellij.packaging.ui.ManifestFileConfiguration;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
* @author nik
*/
public class ArtifactEditorManifestFileProvider implements ManifestFileProvider {
  private final ArtifactsStructureConfigurableContext myArtifactsStructurContext;

  public ArtifactEditorManifestFileProvider(ArtifactsStructureConfigurableContext artifactsStructurContext) {
    myArtifactsStructurContext = artifactsStructurContext;
  }

  @Override
  public List<String> getClasspathFromManifest(@NotNull CompositePackagingElement<?> archiveRoot, @NotNull ArtifactType artifactType) {
    final ManifestFileConfiguration manifestFile = myArtifactsStructurContext.getManifestFile(archiveRoot, artifactType);
    return manifestFile != null ? manifestFile.getClasspath() : null;
  }
}
