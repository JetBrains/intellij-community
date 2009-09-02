package com.intellij.openapi.roots.ui.configuration.artifacts;

import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactType;
import com.intellij.packaging.artifacts.ModifiableArtifactModel;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.elements.PackagingElementResolvingContext;
import com.intellij.packaging.ui.ArtifactEditor;
import com.intellij.packaging.ui.ManifestFileConfiguration;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public interface ArtifactsStructureConfigurableContext extends PackagingElementResolvingContext {
  @NotNull
  ModifiableArtifactModel getModifiableArtifactModel();

  @NotNull
  ManifestFileConfiguration getManifestFile(CompositePackagingElement<?> element, ArtifactType artifactType);

  CompositePackagingElement<?> getRootElement(@NotNull Artifact originalArtifact);

  void ensureRootIsWritable(@NotNull Artifact originalArtifact);

  ArtifactEditor getOrCreateEditor(Artifact originalArtifact);
}
