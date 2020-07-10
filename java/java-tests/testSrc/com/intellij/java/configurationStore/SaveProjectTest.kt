// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.configurationStore

import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.components.stateStore
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.util.io.systemIndependentPath
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.rules.ProjectModelRule
import com.intellij.util.io.assertMatches
import com.intellij.util.io.directoryContentOf
import com.intellij.util.io.systemIndependentPath
import kotlinx.coroutines.runBlocking
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import java.nio.file.Path
import java.nio.file.Paths

/**
 * This class has no specific Java test. It's located in intellij.java.tests module because if Java plugin is enabled additional elements
 * are added to iml file (e.g. 'exclude-output' tag) so if this test is located in a platform module it'll give different results dependening
 * on whether there is Java plugin in runtime classpath or not.
 */
class SaveProjectTest {
  companion object {
    @JvmField
    @ClassRule
    val appRule = ApplicationRule()
  }

  @Rule
  @JvmField
  val projectModel = ProjectModelRule()

  @Test
  fun `save module`() = runBlocking {
    projectModel.createModule("foo")
    saveProjectState()
    projectModel.baseProjectDir.root.assertMatches(directoryContentOf(testDataRoot.resolve("single-module")))
  }

  @Test
  fun `save library`() = runBlocking {
    projectModel.addProjectLevelLibrary("foo") {
      it.addRoot(VfsUtil.pathToUrl(projectModel.baseProjectDir.rootPath.resolve("lib/classes").systemIndependentPath), OrderRootType.CLASSES)
    }
    saveProjectState()
    projectModel.baseProjectDir.root.assertMatches(directoryContentOf(testDataRoot.resolve("single-library")))
  }

  @Test
  fun `save renamed module`() = runBlocking {
    val model = runReadAction { projectModel.moduleManager.modifiableModel }
    val module = projectModel.createModule("foo", model)
    model.renameModule(module, "bar")
    runWriteActionAndWait { model.commit() }
    saveProjectState()
    projectModel.baseProjectDir.root.assertMatches(directoryContentOf(testDataRoot.resolve("single-module-renamed")))
  }

  private suspend fun saveProjectState() {
    projectModel.project.stateStore.save()
  }

  private val testDataRoot: Path
    get() = Paths.get(PathManagerEx.getCommunityHomePath()).resolve("java/java-tests/testData/configurationStore/saveProjectTest")
}