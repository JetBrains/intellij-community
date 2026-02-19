// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("JavaCompileTestUtil")

package com.intellij.openapi.externalSystem.test

import com.intellij.compiler.impl.ModuleCompileScope
import com.intellij.openapi.compiler.CompileScope
import com.intellij.openapi.compiler.CompilerMessageCategory
import com.intellij.openapi.externalSystem.util.runReadAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.packaging.artifacts.Artifact
import com.intellij.packaging.artifacts.ArtifactManager
import com.intellij.packaging.impl.compiler.ArtifactCompileScope
import com.intellij.task.ProjectTaskManager
import com.intellij.testFramework.CompilerTester
import com.intellij.testFramework.concurrency.waitForPromiseAndPumpEdt
import com.intellij.util.concurrency.annotations.RequiresReadLock
import kotlin.time.Duration.Companion.minutes


fun compileModules(project: Project, useProjectTaskManager: Boolean, vararg moduleNames: String) {
  val modules = runReadAction { collectModules(project, *moduleNames) }
  if (useProjectTaskManager) {
    val projectTaskManager = ProjectTaskManager.getInstance(project)
    val promise = projectTaskManager.build(*modules.toTypedArray())
    promise.waitForPromiseAndPumpEdt(2.minutes)
  }
  else {
    compile(project, ModuleCompileScope(project, modules.toTypedArray(), false))
  }
}

fun buildArtifacts(project: Project, useProjectTaskManager: Boolean, vararg artifactNames: String) {
  val artifacts = runReadAction { collectArtifacts(project, *artifactNames) }
  if (useProjectTaskManager) {
    val projectTaskManager = ProjectTaskManager.getInstance(project)
    val promise = projectTaskManager.build(*artifacts.toTypedArray())
    promise.waitForPromiseAndPumpEdt(2.minutes)
  }
  else {
    compile(project, ArtifactCompileScope.createArtifactsScope(project, artifacts))
  }
}

private fun compile(project: Project, scope: CompileScope) {
  val tester = CompilerTester(project, scope.affectedModules.toList(), null)
  try {
    val messages = tester.make(scope)
    for (message in messages) {
      if (message.category == CompilerMessageCategory.ERROR) {
        throw AssertionError("Compilation failed with error: " + message.message)
      }
      if (message.category == CompilerMessageCategory.WARNING) {
        println("Compilation warning: " + message.message)
      }
    }
  }
  finally {
    tester.tearDown()
  }
}

@RequiresReadLock
private fun collectModules(project: Project, vararg moduleNames: String): List<Module> {
  val moduleManager = ModuleManager.getInstance(project)
  val modules = ArrayList<Module>()
  for (moduleName in moduleNames) {
    val module = moduleManager.findModuleByName(moduleName)
    if (module == null) {
      throw AssertionError("Cannot find $moduleName module")
    }
    modules.add(module)
  }
  return modules
}

@RequiresReadLock
private fun collectArtifacts(project: Project, vararg artifactNames: String): List<Artifact> {
  val artifactManager = ArtifactManager.getInstance(project)
  val artifacts = ArrayList<Artifact>()
  for (artifactName in artifactNames) {
    val artifact = artifactManager.findArtifact(artifactName)
    if (artifact == null) {
      throw AssertionError("Cannot find $artifactName artifact")
    }
    artifacts.add(artifact)
  }
  return artifacts
}
