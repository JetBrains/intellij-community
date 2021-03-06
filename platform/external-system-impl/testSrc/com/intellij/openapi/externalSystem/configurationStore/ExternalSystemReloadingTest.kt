// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.configurationStore

import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.TemporaryDirectory
import com.intellij.testFramework.configurationStore.copyFilesAndReloadProject
import com.intellij.testFramework.loadProjectAndCheckResults
import org.assertj.core.api.Assertions.assertThat
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import java.nio.file.Paths
import kotlin.io.path.div

class ExternalSystemReloadingTest {
  companion object {
    @JvmField
    @ClassRule
    val appRule = ApplicationRule()
  }

  @JvmField
  @Rule
  val tempDirManager = TemporaryDirectory()

  private val testDataRoot
    get() = Paths.get(PathManagerEx.getCommunityHomePath()).resolve("platform/external-system-impl/testData/projectReloading")

  @Test
  fun `change iml of imported module`() {
    loadProjectAndCheckResults(listOf(testDataRoot / "changeIml/initial"), tempDirManager) { project ->
      val module = ModuleManager.getInstance(project).modules.single()
      assertThat(ModuleRootManager.getInstance(module).orderEntries.filterIsInstance<LibraryOrderEntry>()).isEmpty()
      copyFilesAndReloadProject(project, testDataRoot / "changeIml/update")
      assertThat(ModuleRootManager.getInstance(module).orderEntries.filterIsInstance<LibraryOrderEntry>().single().libraryName).isEqualTo("lib")
    }
  }
}