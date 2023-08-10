// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.configurationStore

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.rules.ProjectModelRule
import com.intellij.util.io.assertMatches
import com.intellij.util.io.directoryContentOf
import com.intellij.util.io.impl.FileTextMatchers
import com.intellij.util.io.systemIndependentPath
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test

/**
 * This class actually doesn't depend on Java. It's located in intellij.java.tests module because if Java plugin is enabled additional elements
 * are added to iml file (e.g. 'exclude-output' tag) so if this test is located in a platform module it'll give different results depending
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
  fun `save single module`() {
    projectModel.createModule("foo")
    projectModel.saveProjectState()
    assertMatches("single-module")
  }

  @Test
  fun `save module with group`() {
    val module = projectModel.createModule("foo")
    fun setGroupPath(path: Array<String>?) {
      runWriteActionAndWait {
        val model = projectModel.moduleManager.getModifiableModel()
        model.setModuleGroupPath(module, path)
        model.commit()
      }
    }

    setGroupPath(arrayOf("group"))
    projectModel.saveProjectState()
    assertMatches("module-in-group")

    setGroupPath(arrayOf("group", "subGroup"))
    projectModel.saveProjectState()
    assertMatches("module-in-sub-group")

    setGroupPath(null)
    projectModel.saveProjectState()
    assertMatches("single-module")
  }

  @Test
  fun `save detached module`() {
    projectModel.createModule("foo")
    val module = projectModel.createModule("bar")
    projectModel.saveProjectState()
    projectModel.removeModule(module)
    projectModel.saveProjectState()
    assertMatches("detached-module")
  }

  @Test
  fun `save single library`() {
    projectModel.addProjectLevelLibrary("foo") {
      it.addRoot(VfsUtil.pathToUrl(projectModel.baseProjectDir.rootPath.resolve("lib/classes").systemIndependentPath),
                 OrderRootType.CLASSES)
    }
    projectModel.saveProjectState()
    assertMatches("single-library")
  }

  @Test
  fun `save renamed module`() {
    val model = runReadAction { projectModel.moduleManager.getModifiableModel() }
    val module = projectModel.createModule("foo", model)
    model.renameModule(module, "bar")
    runWriteActionAndWait { model.commit() }
    projectModel.saveProjectState()
    assertMatches("single-module-renamed")
  }

  private fun assertMatches(name: String) {
    projectModel.baseProjectDir.root.assertMatches(
      directoryContentOf(configurationStoreTestDataRoot.resolve(name)),
      fileTextMatcher = FileTextMatchers.lines)
  }
}