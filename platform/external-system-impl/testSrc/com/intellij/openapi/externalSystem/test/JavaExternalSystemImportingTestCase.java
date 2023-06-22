// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.test;

import com.intellij.compiler.artifacts.ArtifactsTestUtil;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.compiler.CompilerPaths;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactManager;
import com.intellij.platform.externalSystem.testFramework.ExternalSystemImportingTestCase;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.TestFileSystemItem;

import java.io.File;
import java.util.List;

import static com.intellij.testFramework.assertions.Assertions.assertThat;
import static com.intellij.util.PathUtil.toSystemIndependentName;

public abstract class JavaExternalSystemImportingTestCase extends ExternalSystemImportingTestCase {

  protected boolean useProjectTaskManager;

  protected void compileModules(final String... moduleNames) {
    JavaCompileTestUtil.compileModules(myProject, useProjectTaskManager, moduleNames);
  }

  protected void buildArtifacts(String... artifactNames) {
    JavaCompileTestUtil.buildArtifacts(myProject, useProjectTaskManager, artifactNames);
  }

  protected Artifact findArtifact(Project project, String artifactName) {
    return ReadAction.compute(() -> ArtifactsTestUtil.findArtifact(project, artifactName));
  }

  protected void assertArtifactOutputPath(final String artifactName, final String expected) {
    Artifact artifact = findArtifact(myProject, artifactName);
    assertThat(toSystemIndependentName(artifact.getOutputPath())).isEqualTo(expected);
  }

  protected void assertArtifactOutput(String artifactName, TestFileSystemItem fs) {
    final Artifact artifact = findArtifact(myProject, artifactName);
    final String outputFile = artifact.getOutputFilePath();
    assert outputFile != null;
    final File file = new File(outputFile);
    assert file.exists();
    fs.assertFileEqual(file);
  }

  protected void assertModuleOutputs(String moduleName, String... outputs) {
    Module module = getModule(moduleName);
    String[] outputPaths = ContainerUtil.map2Array(CompilerPaths.getOutputPaths(new Module[]{module}), String.class,
                                                   s -> getAbsolutePath(s));
    assertUnorderedElementsAreEqual(outputPaths, outputs);
    String[] outputPathsFromEnumerator = ContainerUtil.map2Array(
      OrderEnumerator.orderEntries(module).withoutSdk().withoutLibraries().withoutDepModules().classes().getUrls(),
      String.class,
      VfsUtilCore::urlToPath
    );
    assertUnorderedElementsAreEqual(outputPathsFromEnumerator, outputs);
  }

  protected void assertModuleOutput(String moduleName, String output, String testOutput) {
    CompilerModuleExtension e = getCompilerExtension(moduleName);

    assertFalse(e.isCompilerOutputPathInherited());
    assertEquals(output, getAbsolutePath(e.getCompilerOutputUrl()));
    assertEquals(testOutput, getAbsolutePath(e.getCompilerOutputUrlForTests()));
  }

  protected void assertModuleInheritedOutput(String moduleName) {
    CompilerModuleExtension e = getCompilerExtension(moduleName);
    assertTrue(e.isCompilerOutputPathInherited());
  }

  protected CompilerModuleExtension getCompilerExtension(String module) {
    return CompilerModuleExtension.getInstance(getModule(module));
  }

  protected void assertArtifacts(String... expectedNames) {
    final List<String> actualNames = ContainerUtil.map(
      ReadAction.compute(() -> ArtifactManager.getInstance(myProject).getAllArtifactsIncludingInvalid()),
      artifact -> artifact.getName());

    assertUnorderedElementsAreEqual(actualNames, expectedNames);
  }
}
