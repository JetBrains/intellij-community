package com.intellij.packaging.elements;

import com.intellij.compiler.ant.Generator;
import com.intellij.packaging.artifacts.Artifact;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public interface ArtifactGenerationContext {

  void runBeforeCurrentArtifact(Generator generator);

  void runBeforeBuild(Generator generator);

  void runAfterBuild(Generator generator);

  String createNewTempFileProperty(String basePropertyName, String baseFileName);

  String getModuleOutputPath(String moduleName);

  String getSubstitutedPath(String path);

  String getArtifactOutputProperty(@NotNull Artifact artifact);
}
