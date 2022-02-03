// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.artifacts.workspaceModel

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.module.ConfigurationErrorDescription
import com.intellij.openapi.module.impl.ProjectLoadingErrorsHeadlessNotifier
import com.intellij.openapi.project.Project
import com.intellij.packaging.artifacts.ArtifactManager
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.TemporaryDirectory
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import java.nio.file.Path
import java.nio.file.Paths

class LoadingInvalidProjectTest {

  @JvmField
  @Rule
  val tempDirectory = TemporaryDirectory()

  @JvmField
  @Rule
  val disposable = DisposableRule()
  private val errors = ArrayList<ConfigurationErrorDescription>()

  @Before
  fun setUp() {
    ProjectLoadingErrorsHeadlessNotifier.setErrorHandler(disposable.disposable, errors::add)
  }

  @Test
  fun `test duplicating artifacts`() {
    loadProjectAndCheckResults("duplicating-artifacts") { project ->
      val artifactsName = ReadAction.compute<String, Exception> { ArtifactManager.getInstance(project).artifacts.single().name }
      assertThat(artifactsName).isEqualTo("foo")
      assertThat(errors).isEmpty()
    }
  }

  private fun loadProjectAndCheckResults(testDataDirName: String, checkProject: suspend (Project) -> Unit) {
    return com.intellij.testFramework.loadProjectAndCheckResults(
      listOf(testDataRoot.resolve("common"), testDataRoot.resolve(testDataDirName)), tempDirectory, checkProject)
  }

  companion object {
    @JvmField
    @ClassRule
    val appRule = ApplicationRule()

    private val testDataRoot: Path
      get() = Paths.get(PathManagerEx.getCommunityHomePath()).resolve("platform/platform-tests/testData/configurationStore/invalid")
  }
}