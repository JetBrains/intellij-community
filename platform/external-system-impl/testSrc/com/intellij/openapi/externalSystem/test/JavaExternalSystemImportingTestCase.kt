// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.test

import com.intellij.compiler.artifacts.ArtifactsTestUtil
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.compiler.CompilerPaths
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.CompilerModuleExtension
import com.intellij.openapi.roots.OrderEnumerator
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.packaging.artifacts.Artifact
import com.intellij.packaging.artifacts.ArtifactManager
import com.intellij.platform.externalSystem.testFramework.ExternalSystemImportingTestCase
import com.intellij.testFramework.assertions.Assertions
import com.intellij.util.PathUtil
import com.intellij.util.io.TestFileSystemItem
import junit.framework.TestCase
import java.io.File

abstract class JavaExternalSystemImportingTestCase : ExternalSystemImportingTestCase() {

  @JvmField
  protected var useProjectTaskManager: Boolean = false

  protected fun compileModules(vararg moduleNames: String) {
    compileModules(myProject, useProjectTaskManager, *moduleNames)
  }

  protected fun buildArtifacts(vararg artifactNames: String) {
    buildArtifacts(myProject, useProjectTaskManager, *artifactNames)
  }

  protected fun findArtifact(project: Project, artifactName: String): Artifact {
    return ReadAction.compute<Artifact, RuntimeException?>(ThrowableComputable { ArtifactsTestUtil.findArtifact(project, artifactName) })
  }

  protected open fun assertArtifactOutputPath(artifactName: String, expected: String) {
    val artifact = findArtifact(myProject, artifactName)
    Assertions.assertThat(PathUtil.toSystemIndependentName(artifact.getOutputPath())).isEqualTo(expected)
  }

  protected fun assertArtifactOutput(artifactName: String, fs: TestFileSystemItem) {
    val artifact = findArtifact(myProject, artifactName)
    val outputFile: String = checkNotNull(artifact.getOutputFilePath())
    val file = File(outputFile)
    assert(file.exists())
    fs.assertFileEqual(file)
  }

  protected fun assertModuleOutputs(moduleName: String, vararg outputs: String) {
    val module = getModule(moduleName)
    val outputPaths = CompilerPaths.getOutputPaths(arrayOf(module))
      .map { getAbsolutePath(it) }
      .toTypedArray()
    assertUnorderedElementsAreEqual(outputPaths, *outputs)
    val outputPathsFromEnumerator = OrderEnumerator.orderEntries(module)
      .withoutSdk()
      .withoutLibraries()
      .withoutDepModules()
      .classes()
      .getUrls()
      .map { VfsUtilCore.urlToPath(it) }.toTypedArray()
    assertUnorderedElementsAreEqual(outputPathsFromEnumerator, *outputs)
  }

  protected fun assertModuleOutput(moduleName: String, output: String, testOutput: String) {
    val e = getCompilerExtension(moduleName)

    assertFalse(e.isCompilerOutputPathInherited())
    TestCase.assertEquals(output, getAbsolutePath(e.getCompilerOutputUrl()))
    TestCase.assertEquals(testOutput, getAbsolutePath(e.getCompilerOutputUrlForTests()))
  }

  protected fun assertModuleInheritedOutput(moduleName: String) {
    val e = getCompilerExtension(moduleName)
    assertTrue(e.isCompilerOutputPathInherited())
  }

  protected fun getCompilerExtension(module: String): CompilerModuleExtension {
    return CompilerModuleExtension.getInstance(getModule(module))!!
  }

  protected fun assertArtifacts(vararg expectedNames: String) {
    val artifacts = ReadAction.compute(
      ThrowableComputable {
        ArtifactManager.getInstance(myProject).getAllArtifactsIncludingInvalid()
      }
    )
    val actualNames = artifacts.map { it.name }
    assertUnorderedElementsAreEqual(actualNames, *expectedNames)
  }
}
