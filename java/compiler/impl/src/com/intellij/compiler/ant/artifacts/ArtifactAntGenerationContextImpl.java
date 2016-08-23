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
package com.intellij.compiler.ant.artifacts;

import com.intellij.compiler.ant.BuildProperties;
import com.intellij.compiler.ant.GenerationOptions;
import com.intellij.compiler.ant.GenerationUtils;
import com.intellij.compiler.ant.Generator;
import com.intellij.compiler.ant.taskdefs.Mkdir;
import com.intellij.compiler.ant.taskdefs.Property;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.elements.ArtifactAntGenerationContext;
import com.intellij.packaging.impl.artifacts.ArtifactUtil;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author nik
 */
public class ArtifactAntGenerationContextImpl implements ArtifactAntGenerationContext {
  @NonNls public static final String ARTIFACTS_TEMP_DIR_PROPERTY = "artifacts.temp.dir";
  private final Map<Artifact, String> myArtifact2Target = new THashMap<>();
  private final List<Generator> myBeforeBuildGenerators = new ArrayList<>();
  private final List<Generator> myAfterBuildGenerators = new ArrayList<>();
  private final Set<String> myTempFileNames = new THashSet<>();
  private final Set<String> myCreatedTempSubdirs = new THashSet<>();
  private final Set<String> myProperties = new LinkedHashSet<>();
  private final Project myProject;
  private final GenerationOptions myGenerationOptions;
  private final List<Generator> myBeforeCurrentArtifact = new ArrayList<>();
  private final Set<Artifact> myArtifactsToClean = new THashSet<>();

  public ArtifactAntGenerationContextImpl(Project project, GenerationOptions generationOptions, List<Artifact> allArtifacts) {
    myProject = project;
    myGenerationOptions = generationOptions;
    for (Artifact artifact : allArtifacts) {
      if (ArtifactUtil.shouldClearArtifactOutputBeforeRebuild(artifact)) {
        myArtifactsToClean.add(artifact);
      }
    }
  }

  @Override
  public Project getProject() {
    return myProject;
  }

  @Override
  public GenerationOptions getGenerationOptions() {
    return myGenerationOptions;
  }

  @Override
  public String getConfiguredArtifactOutputProperty(@NotNull Artifact artifact) {
    return "artifact.output." + BuildProperties.convertName(artifact.getName());
  }

  public String getArtifactOutputProperty(@NotNull Artifact artifact) {
    if (shouldBuildIntoTempDirectory(artifact)) {
      return "artifact.temp.output." + BuildProperties.convertName(artifact.getName());
    }
    return getConfiguredArtifactOutputProperty(artifact);
  }

  public boolean shouldBuildIntoTempDirectory(@NotNull Artifact artifact) {
    return !myArtifactsToClean.contains(artifact);
  }

  public String getCleanTargetName(@NotNull Artifact artifact) {
    return "clean.artifact." + BuildProperties.convertName(artifact.getName());
  }

  public String getTargetName(@NotNull Artifact artifact) {
    String target = myArtifact2Target.get(artifact);
    if (target == null) {
      target = generateTargetName(artifact.getName());
      myArtifact2Target.put(artifact, target);
    }
    return target;
  }

  private static String generateTargetName(String artifactName) {
    return "artifact." + BuildProperties.convertName(artifactName);
  }

  public String getSubstitutedPath(String path) {
    return GenerationUtils.toRelativePath(path, VfsUtil.virtualToIoFile(myProject.getBaseDir()), BuildProperties.getProjectBaseDirProperty(), myGenerationOptions);
  }

  public void runBeforeCurrentArtifact(Generator generator) {
    myBeforeCurrentArtifact.add(generator);
  }

  public void runBeforeBuild(Generator generator) {
    myBeforeBuildGenerators.add(generator);
  }

  public void runAfterBuild(Generator generator) {
    myAfterBuildGenerators.add(generator);
  }

  public String createNewTempFileProperty(String basePropertyName, String fileName) {
    String tempFileName = fileName;
    int i = 1;
    String tempSubdir = null;
    while (myTempFileNames.contains(tempFileName)) {
      tempSubdir = String.valueOf(i++);
      tempFileName = tempSubdir + "/" + fileName;
    }

    String propertyName = basePropertyName;
    i = 2;
    while (myProperties.contains(propertyName)) {
      propertyName = basePropertyName + i++;
    }

    runBeforeBuild(new Property(propertyName, BuildProperties.propertyRelativePath(ARTIFACTS_TEMP_DIR_PROPERTY, tempFileName)));
    if (tempSubdir != null && myCreatedTempSubdirs.add(tempSubdir)) {
      runBeforeBuild(new Mkdir(BuildProperties.propertyRelativePath(ARTIFACTS_TEMP_DIR_PROPERTY, tempSubdir)));
    }
    myTempFileNames.add(tempFileName);
    myProperties.add(propertyName);
    return propertyName;
  }

  public Generator[] getAndClearBeforeCurrentArtifact() {
    final Generator[] generators = myBeforeCurrentArtifact.toArray(new Generator[myBeforeCurrentArtifact.size()]);
    myBeforeCurrentArtifact.clear();
    return generators;
  }

  public String getModuleOutputPath(String moduleName) {
    return BuildProperties.getOutputPathProperty(moduleName);
  }

  @Override
  public String getModuleTestOutputPath(@NonNls String moduleName) {
    return BuildProperties.getOutputPathForTestsProperty(moduleName);
  }

  public List<Generator> getBeforeBuildGenerators() {
    return myBeforeBuildGenerators;
  }

  public List<Generator> getAfterBuildGenerators() {
    return myAfterBuildGenerators;
  }
}
