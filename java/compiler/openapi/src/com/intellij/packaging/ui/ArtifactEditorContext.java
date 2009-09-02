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

  CompositePackagingElement<?> getRootElement(@NotNull Artifact originalArtifact);

  void ensureRootIsWritable(@NotNull Artifact originalArtifact);

  ArtifactEditor getOrCreateEditor(Artifact originalArtifact);


  void selectArtifact(@NotNull Artifact artifact);

  void selectFacet(@NotNull Facet<?> facet);

  void selectModule(@NotNull Module module);

  void selectLibrary(@NotNull Library library);


  List<Artifact> chooseArtifacts(List<? extends Artifact> artifacts, String title);

  List<Module> chooseModules(List<Module> modules, final String title);

  List<Library> chooseLibraries(List<Library> libraries, String title);
}
