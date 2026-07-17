// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.modules
import com.intellij.openapi.roots.ui.configuration.actions.ModuleDeleteProvider
import com.intellij.project.stateStore
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Path


/**
 * User should be able to delete all modules if not [moduleFilesAreInIdeaDir].
 * Otherwise they should be able to delete all modules but the last one.
 */
@ParameterizedClass
@ValueSource(booleans = [true, false])
@TestApplication
internal class ModuleDeleteProviderTest(private val moduleFilesAreInIdeaDir: Boolean) {
  private val pathFixture = tempPathFixture()
  private val project by projectFixture(pathFixture, openAfterCreation = true)
  private lateinit var dirWithImlFiles: Path

  private fun getDataContext(allModsFromProj: Boolean): DataContext = SimpleDataContext.builder()
    .add(CommonDataKeys.PROJECT, project)
    .add(LangDataKeys.MODULE_CONTEXT_ARRAY,
         if (allModsFromProj) project.modules else project.modules.drop(1).toTypedArray()
    )
    .build()

  @BeforeEach
  fun setUp(@TempDir someRandomDir: Path): Unit = timeoutRunBlocking {
    // Create some modules in `.idea` dir, and save .iml files to make sure module file storage doesn't affect logic
    val ideaDir = project.stateStore.directoryStorePath!!
    dirWithImlFiles = if (moduleFilesAreInIdeaDir) ideaDir else someRandomDir
    val modMan = ModuleManager.getInstance(project)
    val modules = writeAction {
      (0..10).map {
        modMan.newModule(dirWithImlFiles.resolve("$it.iml"), "")
      }
    }
    project.stateStore.save(forceSavingAllSettings = true)
    if (moduleFilesAreInIdeaDir) {
      for (module in modules) {
        val moduleFile = module.moduleNioFile
        assert(moduleFile.startsWith(ideaDir)) {
          "Module file is not in .idea, but this is required for test to make sense"
        }
      }
    }
  }

  @AfterEach
  fun tearDown(): Unit = timeoutRunBlocking {
    writeAction {
      for (module in project.modules) {
        ModuleDeleteProvider.detachModules(project, arrayOf(module))
      }
    }
  }

  @Test
  fun testLastModuleCantBeDeleted(): Unit = timeoutRunBlocking {
    assert(project.modules.isNotEmpty())
    val sut = ModuleDeleteProvider()
    val modulesShouldAlwaysBeDeletable = !moduleFilesAreInIdeaDir
    assertEquals(modulesShouldAlwaysBeDeletable,
                 sut.canDeleteElement(getDataContext(allModsFromProj = true)),
                 "all modules can be deleted expected to be $modulesShouldAlwaysBeDeletable")
    assertTrue(sut.canDeleteElement(getDataContext(allModsFromProj = false)), "several mods should be deletable")

    val allModulesButLast = project.modules.dropLast(1)
    writeAction {
      ModuleDeleteProvider.detachModules(project, allModulesButLast.toTypedArray())
    }
    assert(project.modules.size == 1)
    // All modules are dropped, the last one should be undeletable
    assertEquals(modulesShouldAlwaysBeDeletable,
                 sut.canDeleteElement(getDataContext(allModsFromProj = true)),
                 "Last module is deletable expected to be $modulesShouldAlwaysBeDeletable")
  }
}
