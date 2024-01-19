// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

import com.intellij.ide.highlighter.ModuleFileType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.components.StateStorageOperation
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.stateStore
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.ModuleListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modifyModules
import com.intellij.openapi.project.rootManager
import com.intellij.openapi.roots.ModuleRootManagerEx
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.project.stateStore
import com.intellij.testFramework.*
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.util.Function
import com.intellij.util.io.Ksuid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.properties.Delegates

private val Module.storage: FileBasedStorage
  get() = (stateStore.storageManager as StateStorageManagerImpl).getCachedFileStorages(listOf(StoragePathMacros.MODULE_FILE)).first()

@RunsInActiveStoreMode
class ModuleStoreRenameTest {
  companion object {
    @JvmField @ClassRule val projectRule = ProjectRule()
  }

  var module: Module by Delegates.notNull()
  var dependentModule: Module by Delegates.notNull()

  // we test fireModuleRenamedByVfsEvent
  private val oldModuleNames = mutableListOf<String>()

  private val tempDirManager = TemporaryDirectory()

  @Rule
  @JvmField
  val ruleChain = RuleChain(
    tempDirManager,
    ActiveStoreRule(projectRule),
    object : ExternalResource() {
      override fun before() {
        runBlocking {
          val moduleFileParent = tempDirManager.newPath()
          module = projectRule.createModule(moduleFileParent.resolve("m.iml"))
          dependentModule = projectRule.createModule(moduleFileParent.resolve("dependent-module.iml"))
          ModuleRootModificationUtil.addDependency(dependentModule, module)
        }

        projectRule.project.messageBus.connect(module).subscribe(ModuleListener.TOPIC, object : ModuleListener {
          override fun modulesRenamed(project: Project, modules: List<Module>, oldNameProvider: Function<in Module, String>) {
            assertThat(modules).containsOnly(module)
            oldModuleNames.add(oldNameProvider.`fun`(module))
          }
        })
      }

      // should be invoked after project tearDown
      override fun after() {
        checkStorageIsNotTracked(module)
      }
    },
    DisposeModulesRule(projectRule)
  )

  // project structure
  @Test
  fun `rename module using model`() = runBlocking<Unit> {
    saveProjectState()

    val storage = module.storage
    val oldFile = storage.file
    assertThat(oldFile).isRegularFile

    val oldName = module.name
    val newName = "foo"

    writeAction {
      projectRule.project.modifyModules { renameModule(module, newName) }
    }
    assertModuleFileRenamed(newName, oldFile)
    assertThat(oldModuleNames).containsOnly(oldName)
  }

  // project view
  @Test
  fun `rename module using rename virtual file`() = runBlocking {
    testRenameModule()
  }

  private suspend fun testRenameModule() {
    saveProjectState()
    val storage = module.storage
    val oldFile = storage.file
    assertThat(oldFile).isRegularFile

    val oldName = module.name
    val newName = "foo.dot"
    withContext(Dispatchers.EDT) {
      ApplicationManager.getApplication().runWriteAction {
        LocalFileSystem.getInstance().refreshAndFindFileByNioFile(oldFile)!!.rename(null, "${newName}${ModuleFileType.DOT_DEFAULT_EXTENSION}")
      }
    }
    assertModuleFileRenamed(newName, oldFile)
    assertThat(oldModuleNames).containsOnly(oldName)
  }

  // we cannot test external rename yet, because it is not supported - ModuleImpl doesn't support deleting and create events
  // (in case of external change we don't get move event, but get "delete old" and "create new")
  private suspend fun assertModuleFileRenamed(newName: String, oldFile: Path) {
    val newFile = module.storage.file
    assertThat(newFile.fileName.toString()).isEqualTo("$newName${ModuleFileType.DOT_DEFAULT_EXTENSION}")
    assertThat(oldFile)
      .doesNotExist()
      .isNotEqualTo(newFile)
    assertThat(newFile).isRegularFile

    // ensure that macro value updated
    assertThat(module.stateStore.storageManager.expandMacro(StoragePathMacros.MODULE_FILE)).isEqualTo(newFile)
    assertThat(module.moduleNioFile).isEqualTo(newFile)

    saveProjectState()
    assertThat(dependentModule.storage.file.readText()).contains("""<orderEntry type="module" module-name="$newName" />""")
    assertThat(projectRule.project.stateStore.projectFilePath.readText()).contains(
      """<module fileurl="file://${'$'}PROJECT_DIR${'$'}/${module.storage.file.parent.fileName}/$newName${ModuleFileType.DOT_DEFAULT_EXTENSION}"""")
  }

  @Test
  fun `rename module parent virtual dir`() = runBlocking {
    saveProjectState()
    val storage = module.storage
    val oldFile = storage.file
    val parentVirtualDir = storage.getVirtualFile(StateStorageOperation.WRITE)!!.parent
    withContext(Dispatchers.EDT) {
      ApplicationManager.getApplication().runWriteAction {
        parentVirtualDir.rename(null, Ksuid.generate())
      }
    }

    val newFile = parentVirtualDir.toNioPath().resolve("${module.name}${ModuleFileType.DOT_DEFAULT_EXTENSION}")
    assertThat(newFile).isRegularFile
    assertModuleFileRenamed(module.name, oldFile)
    assertThat(oldModuleNames).isEmpty()

    testRenameModule()
  }

  @Test
  fun `rename module source root`() = runBlocking<Unit>(Dispatchers.EDT) {
    saveProjectState()
    val storage = module.storage
    val parentVirtualDir = storage.getVirtualFile(StateStorageOperation.WRITE)!!.parent
    val src = VfsTestUtil.createDir(parentVirtualDir, "foo")
    writeAction {
      PsiTestUtil.addSourceContentToRoots(module, src, false)
    }

    saveProjectState()

    val rootManager = module.rootManager as ModuleRootManagerEx
    val stateModificationCount = rootManager.modificationCountForTests

    writeAction {
      src.rename(null, "bar.dot")
    }

    assertThat(stateModificationCount).isLessThan(rootManager.modificationCountForTests)
  }

  private suspend fun saveProjectState() {
    projectRule.project.stateStore.save()
  }
}
