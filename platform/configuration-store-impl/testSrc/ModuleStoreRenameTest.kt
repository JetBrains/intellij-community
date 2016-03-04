package com.intellij.configurationStore

import com.intellij.ProjectTopics
import com.intellij.ide.highlighter.ModuleFileType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.stateStore
import com.intellij.openapi.module.ModifiableModuleModel
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.ModuleAdapter
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.systemIndependentPath
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.*
import com.intellij.util.Function
import com.intellij.util.SmartList
import org.assertj.core.api.Assertions.assertThat
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import java.io.File
import java.nio.file.Paths
import java.util.*
import kotlin.properties.Delegates

internal class ModuleStoreRenameTest {
  companion object {
    @JvmField
    @ClassRule val projectRule = ProjectRule()

    private val Module.storage: FileBasedStorage
      get() = (stateStore.stateStorageManager as StateStorageManagerImpl).getCachedFileStorages(listOf(StoragePathMacros.MODULE_FILE)).first()
  }

  var module: Module by Delegates.notNull()

  // we test fireModuleRenamedByVfsEvent
  private val oldModuleNames = SmartList<String>()

  private val tempDirManager = TemporaryDirectory()

  private val ruleChain = RuleChain(
    tempDirManager,
    object : ExternalResource() {
      override fun before() {
        runInEdtAndWait {
          module = projectRule.createModule(tempDirManager.newPath(refreshVfs = true).resolve("m.iml"))
        }

        module.messageBus.connect().subscribe(ProjectTopics.MODULES, object : ModuleAdapter() {
          override fun modulesRenamed(project: Project, modules: MutableList<Module>, oldNameProvider: Function<Module, String>) {
            assertThat(modules).containsOnly(module)
            oldModuleNames.add(oldNameProvider.`fun`(module))
          }
        })
      }

      // should be invoked after project tearDown
      override fun after() {
        (ApplicationManager.getApplication().stateStore.stateStorageManager as StateStorageManagerImpl).getVirtualFileTracker()!!.remove {
          if (it.storageManager.componentManager == module) {
            throw AssertionError("Storage manager is not disposed, module $module, storage $it")
          }
          false
        }
      }
    },
    DisposeModulesRule(projectRule)
  )

  @Rule fun getChain() = ruleChain

  fun changeModule(task: ModifiableModuleModel.() -> Unit) {
    runInEdtAndWait {
      val model = ModuleManager.getInstance(projectRule.project).modifiableModel
      runWriteAction {
        model.task()
        model.commit()
      }
    }
  }

  // project structure
  @Test fun `rename module using model`() {
    runInEdtAndWait { module.saveStore() }
    val storage = module.storage
    val oldFile = storage.file
    assertThat(oldFile).isFile()

    val oldName = module.name
    val newName = "foo"
    changeModule { renameModule(module, newName) }
    assertRename(newName, oldFile)
    assertThat(oldModuleNames).containsOnly(oldName)
  }

  // project view
  @Test fun `rename module using rename virtual file`() {
    runInEdtAndWait { module.saveStore() }
    var storage = module.storage
    val oldFile = storage.file
    assertThat(oldFile).isFile()

    val oldName = module.name
    val newName = "foo"
    runInEdtAndWait { runWriteAction { LocalFileSystem.getInstance().refreshAndFindFileByIoFile(oldFile)!!.rename(null, "$newName${ModuleFileType.DOT_DEFAULT_EXTENSION}") } }
    assertRename(newName, oldFile)
    assertThat(oldModuleNames).containsOnly(oldName)
  }

  // we cannot test external rename yet, because it is not supported - ModuleImpl doesn't support delete and create events (in case of external change we don't get move event, but get "delete old" and "create new")

  private fun assertRename(newName: String, oldFile: File) {
    val newFile = module.storage.file
    assertThat(newFile.name).isEqualTo("$newName${ModuleFileType.DOT_DEFAULT_EXTENSION}")
    assertThat(oldFile)
      .doesNotExist()
      .isNotEqualTo(newFile)
    assertThat(newFile).isFile()

    // ensure that macro value updated
    assertThat(module.stateStore.stateStorageManager.expandMacros(StoragePathMacros.MODULE_FILE)).isEqualTo(newFile.systemIndependentPath)
  }

  @Test fun `rename module parent virtual dir`() {
    runInEdtAndWait { module.saveStore() }
    val storage = module.storage
    val oldFile = storage.file
    val parentVirtualDir = storage.getVirtualFile()!!.parent
    runInEdtAndWait { runWriteAction { parentVirtualDir.rename(null, UUID.randomUUID().toString()) } }

    val newFile = Paths.get(parentVirtualDir.path, "${module.name}${ModuleFileType.DOT_DEFAULT_EXTENSION}")
    try {
      assertThat(newFile).isRegularFile()
      assertRename(module.name, oldFile)
      assertThat(oldModuleNames).isEmpty()
    }
    finally {
      runInEdtAndWait { runWriteAction { parentVirtualDir.delete(this) } }
    }
  }
}