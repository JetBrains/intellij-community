// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.test;

import com.intellij.compiler.artifacts.ArtifactsTestUtil;
import com.intellij.compiler.impl.ModuleCompileScope;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.CompilerMessage;
import com.intellij.openapi.compiler.CompilerPaths;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactManager;
import com.intellij.packaging.impl.compiler.ArtifactCompileScope;
import com.intellij.task.ProjectTaskManager;
import com.intellij.testFramework.CompilerTester;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.TestFileSystemItem;
import com.intellij.platform.externalSystem.testFramework.ExternalSystemImportingTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.Promise;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.intellij.testFramework.assertions.Assertions.assertThat;
import static com.intellij.util.PathUtil.toSystemIndependentName;

public abstract class JavaExternalSystemImportingTestCase extends ExternalSystemImportingTestCase {
  protected boolean useProjectTaskManager;

  protected void compileModules(final String... moduleNames) {
    if (useProjectTaskManager) {
      Module[] modules = Arrays.stream(moduleNames).map(moduleName -> getModule(moduleName)).toArray(Module[]::new);
      build(modules);
    }
    else {
      compile(createModulesCompileScope(moduleNames));
    }
  }

  protected void buildArtifacts(String... artifactNames) {
    if (useProjectTaskManager) {
      Artifact[] artifacts = Arrays.stream(artifactNames)
        .map(artifactName -> findArtifact(myProject, artifactName)).toArray(Artifact[]::new);
      build(artifacts);
    }
    else {
      compile(createArtifactsScope(artifactNames));
    }
  }

  private void build(Object @NotNull [] buildableElements) {
    Promise<ProjectTaskManager.Result> promise;
    if (buildableElements instanceof Module[]) {
      promise = ProjectTaskManager.getInstance(myProject).build((Module[])buildableElements);
    }
    else if (buildableElements instanceof Artifact[]) {
      promise = ProjectTaskManager.getInstance(myProject).build((Artifact[])buildableElements);
    }
    else {
      throw new AssertionError("Unsupported buildableElements: " + Arrays.toString(buildableElements));
    }
    edt(() -> PlatformTestUtil.waitForPromise(promise));
  }

  private void compile(@NotNull CompileScope scope) {
    try {
      CompilerTester tester = new CompilerTester(myProject, Arrays.asList(scope.getAffectedModules()), null);
      try {
        List<CompilerMessage> messages = tester.make(scope);
        for (CompilerMessage message : messages) {
          switch (message.getCategory()) {
            case ERROR:
              fail("Compilation failed with error: " + message.getMessage());
              break;
            case WARNING:
              System.out.println("Compilation warning: " + message.getMessage());
              break;
            case INFORMATION:
              break;
            case STATISTICS:
              break;
          }
        }
      }
      finally {
        tester.tearDown();
      }
    }
    catch (Exception e) {
      ExceptionUtil.rethrow(e);
    }
  }


  private CompileScope createModulesCompileScope(final String[] moduleNames) {
    final List<Module> modules = new ArrayList<>();
    for (String name : moduleNames) {
      modules.add(getModule(name));
    }
    return new ModuleCompileScope(myProject, modules.toArray(Module.EMPTY_ARRAY), false);
  }

  private CompileScope createArtifactsScope(String[] artifactNames) {
    List<Artifact> artifacts = new ArrayList<>();
    for (String name : artifactNames) {
      artifacts.add(findArtifact(myProject, name));
    }
    return ArtifactCompileScope.createArtifactsScope(myProject, artifacts);
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
    String[] outputPaths = ContainerUtil.map2Array(CompilerPaths.getOutputPaths(new Module[]{getModule(moduleName)}), String.class,
                                                   s -> getAbsolutePath(s));
    assertUnorderedElementsAreEqual(outputPaths, outputs);
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
