// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

import com.intellij.ide.highlighter.ModuleFileType
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.impl.stores.stateStore
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.project.stateStore
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assumptions.assumeThat
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.Test
import java.nio.file.Path

@TestApplication
class TwoSimultaneousProjectsTest {
  private companion object {
    const val SHARED_NAME = "shared.name"

    val projectA = projectFixture(openAfterCreation = true)
    val moduleFromProjectAFixture = projectA.persistentModuleFixture(SHARED_NAME)

    val projectB = projectFixture(openAfterCreation = true)
    val moduleFromProjectBFixture = projectB.persistentModuleFixture(SHARED_NAME)

    /**
     * Base TestFixture<Project>.moduleFixture uses NonPersistentStateStorageManager as storageManager
     */
    @TestOnly
    fun TestFixture<Project>.persistentModuleFixture(name: String): TestFixture<Module> =
      testFixture(name) {
        val project = this@persistentModuleFixture.init()
        val manager = ModuleManager.getInstance(project)
        val module = writeAction {
          val projectDir = project.guessProjectDir()?.toNioPath() ?: throw RuntimeException("Cannot guess project dir for $project")
          assumeThat(projectDir).isNotNull().exists()
          manager.newModule(projectDir.resolve("$name${ModuleFileType.DOT_DEFAULT_EXTENSION}"), "")
        }
        initialized(module) {
          writeAction {
            manager.disposeModule(module)
          }
        }
      }
  }

  @Test
  fun `rename module using rename iml in one project does not change similarly named module in another project`() = runBlocking {
    val moduleA = moduleFromProjectAFixture.get()
    val moduleB = moduleFromProjectBFixture.get()
    val originalModuleAFile = checkModuleAndGetFile(moduleA)
    val originalModuleBFile = checkModuleAndGetFile(moduleB)
    val newName = "$SHARED_NAME.v2"
    writeAction {
      LocalFileSystem.getInstance().refreshAndFindFileByNioFile(originalModuleAFile)!!.rename(null, "${newName}${ModuleFileType.DOT_DEFAULT_EXTENSION}")
    }
    assertModuleFileRenamed(moduleA, newName, originalModuleAFile)
    assertModuleFileNotRenamed(moduleB, originalModuleBFile)
  }

  private suspend fun checkModuleAndGetFile(module: Module): Path {
    module.project.stateStore.save()

    val moduleFile = module.storage.file
    assertThat(moduleFile).isRegularFile.hasFileName("$SHARED_NAME${ModuleFileType.DOT_DEFAULT_EXTENSION}")
    return moduleFile
  }

  private fun assertModuleFileRenamed(renamedModule: Module, newName: String, originalModuleFile: Path) {
    val newFile = renamedModule.storage.file
    assertThat(newFile).isRegularFile.hasFileName("${newName}${ModuleFileType.DOT_DEFAULT_EXTENSION}")
    assertThat(originalModuleFile).doesNotExist().isNotEqualTo(newFile)

    // ensure that macro value is updated
    assertThat(renamedModule.stateStore.storageManager.expandMacro(StoragePathMacros.MODULE_FILE)).isEqualTo(newFile)
    assertThat(renamedModule.moduleNioFile).isEqualTo(newFile)
  }

  private fun assertModuleFileNotRenamed(module: Module, originalModuleFile: Path) {
    val currentModuleFile = module.storage.file
    assertThat(currentModuleFile).isRegularFile.hasFileName("$SHARED_NAME${ModuleFileType.DOT_DEFAULT_EXTENSION}").isEqualTo(originalModuleFile)

    // ensure that macro value is NOT updated
    assertThat(module.stateStore.storageManager.expandMacro(StoragePathMacros.MODULE_FILE)).isEqualTo(originalModuleFile)
    assertThat(module.moduleNioFile).isEqualTo(originalModuleFile)
  }
}
