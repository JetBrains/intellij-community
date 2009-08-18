package com.intellij.packaging.elements;

import com.intellij.compiler.ant.Generator;
import com.intellij.packaging.artifacts.Artifact;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

/**
 * @author nik
 */
public interface ArtifactAntGenerationContext {

  void runBeforeCurrentArtifact(Generator generator);

  void runBeforeBuild(Generator generator);

  void runAfterBuild(Generator generator);

  String createNewTempFileProperty(@NonNls String basePropertyName, @NonNls String fileName);

  String getModuleOutputPath(@NonNls String moduleName);

  String getSubstitutedPath(@NonNls String path);

  String getArtifactOutputProperty(@NotNull Artifact artifact);
}
