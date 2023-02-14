// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.test;

import com.intellij.compiler.artifacts.ArtifactsTestUtil;
import com.intellij.compiler.impl.ModuleCompileScope;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.CompilerMessage;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.impl.compiler.ArtifactCompileScope;
import com.intellij.task.ProjectTaskManager;
import com.intellij.testFramework.CompilerTester;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.ExceptionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.Promise;
import org.junit.jupiter.api.Assertions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.intellij.testFramework.EdtTestUtil.runInEdtAndWait;


public class JavaCompileTestUtil {

  public static void compileModules(@NotNull Project project, boolean useProjectTaskManager, String... moduleNames) {
    if (useProjectTaskManager) {
      Module[] modules = Arrays.stream(moduleNames).map(moduleName -> getModule(project, moduleName)).toArray(Module[]::new);
      build(project, modules);
    }
    else {
      compile(project, createModulesCompileScope(project, moduleNames));
    }
  }

  public static void buildArtifacts(@NotNull Project project, boolean useProjectTaskManager, String... artifactNames) {
    if (useProjectTaskManager) {
      Artifact[] artifacts = Arrays.stream(artifactNames)
        .map(artifactName -> findArtifact(project, artifactName)).toArray(Artifact[]::new);
      build(project, artifacts);
    }
    else {
      compile(project, createArtifactsScope(project, artifactNames));
    }
  }

  private static void build(@NotNull Project project, Object @NotNull [] buildableElements) {
    Promise<ProjectTaskManager.Result> promise;
    if (buildableElements instanceof Module[]) {
      promise = ProjectTaskManager.getInstance(project).build((Module[])buildableElements);
    }
    else if (buildableElements instanceof Artifact[]) {
      promise = ProjectTaskManager.getInstance(project).build((Artifact[])buildableElements);
    }
    else {
      throw new AssertionError("Unsupported buildableElements: " + Arrays.toString(buildableElements));
    }
    runInEdtAndWait(() -> PlatformTestUtil.waitForPromise(promise));
  }

  private static void compile(@NotNull Project project, @NotNull CompileScope scope) {
    try {
      CompilerTester tester = new CompilerTester(project, Arrays.asList(scope.getAffectedModules()), null);
      try {
        List<CompilerMessage> messages = tester.make(scope);
        for (CompilerMessage message : messages) {
          switch (message.getCategory()) {
            case ERROR:
              Assertions.fail("Compilation failed with error: " + message.getMessage());
              break;
            case WARNING:
              System.out.println("Compilation warning: " + message.getMessage());
              break;
            case INFORMATION, STATISTICS:
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

  private static CompileScope createModulesCompileScope(@NotNull Project project, String[] moduleNames) {
    final List<Module> modules = new ArrayList<>();
    for (String name : moduleNames) {
      modules.add(getModule(project, name));
    }
    return new ModuleCompileScope(project, modules.toArray(Module.EMPTY_ARRAY), false);
  }

  private static CompileScope createArtifactsScope(@NotNull Project project, String[] artifactNames) {
    List<Artifact> artifacts = new ArrayList<>();
    for (String name : artifactNames) {
      artifacts.add(findArtifact(project, name));
    }
    return ArtifactCompileScope.createArtifactsScope(project, artifacts);
  }

  private static Artifact findArtifact(Project project, String artifactName) {
    return ReadAction.compute(() -> ArtifactsTestUtil.findArtifact(project, artifactName));
  }

  private static Module getModule(Project project, String name) {
    Module m = ReadAction.compute(() -> ModuleManager.getInstance(project).findModuleByName(name));
    Assertions.assertNotNull(m, "Module " + name + " not found");
    return m;
  }
}
