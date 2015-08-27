package com.intellij.configurationStore

import com.intellij.ProjectTopics
import com.intellij.ide.highlighter.ModuleFileType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.RoamingType
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
import java.util.UUID
import kotlin.properties.Delegates

class ModuleStoreRenameTest {
  companion object {
    @ClassRule val projectRule = ProjectRule()
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
          module = projectRule.createModule(tempDirManager.newPath().resolve("m.iml"))
        }

        module.getMessageBus().connect().subscribe(ProjectTopics.MODULES, object : ModuleAdapter() {
          override fun modulesRenamed(project: Project, modules: MutableList<Module>, oldNameProvider: Function<Module, String>) {
            assertThat(modules).containsOnly(module)
            oldModuleNames.add(oldNameProvider.`fun`(module))
          }
        })
      }

      // should be invoked after project tearDown
      override fun after() {
        (ApplicationManager.getApplication().stateStore.getStateStorageManager() as StateStorageManagerImpl).getVirtualFileTracker()!!.remove {
          if (it.storageManager.componentManager == module) {
            throw AssertionError("Storage manager is not disposed, module $module, storage $it")
          }
          false
        }
      }
    },
    DisposeModulesRule(projectRule)
  )

  public Rule fun getChain(): RuleChain = ruleChain

  fun changeModule(task: ModifiableModuleModel.() -> Unit) {
    runInEdtAndWait {
      val model = ModuleManager.getInstance(projectRule.project).getModifiableModel()
      runWriteAction {
        model.task()
        model.commit()
      }
    }
  }

  // project structure
  @Test fun `rename module using model`() {
    runInEdtAndWait { module.saveStore() }
    val storage = module.stateStore.getStateStorageManager().getStateStorage(StoragePathMacros.MODULE_FILE, RoamingType.DEFAULT) as FileBasedStorage
    val oldFile = storage.file
    assertThat(oldFile).isFile()

    val oldName = module.getName()
    val newName = "foo"
    changeModule { renameModule(module, newName) }
    assertRename(newName, oldFile)
    assertThat(oldModuleNames).containsOnly(oldName)
  }

  // project view
  @Test fun `rename module using rename virtual file`() {
    runInEdtAndWait { module.saveStore() }
    var storage = module.stateStore.getStateStorageManager().getStateStorage(StoragePathMacros.MODULE_FILE, RoamingType.DEFAULT) as FileBasedStorage
    val oldFile = storage.file
    assertThat(oldFile).isFile()

    val oldName = module.getName()
    val newName = "foo"
    runInEdtAndWait { runWriteAction { LocalFileSystem.getInstance().refreshAndFindFileByIoFile(oldFile)!!.rename(null, "$newName${ModuleFileType.DOT_DEFAULT_EXTENSION}") } }
    assertRename(newName, oldFile)
    assertThat(oldModuleNames).containsOnly(oldName)
  }

  // we cannot test external rename yet, because it is not supported - ModuleImpl doesn't support delete and create events (in case of external change we don't get move event, but get "delete old" and "create new")

  private fun assertRename(newName: String, oldFile: File) {
    val storageManager = module.stateStore.getStateStorageManager()
    val newFile = (storageManager.getStateStorage(StoragePathMacros.MODULE_FILE, RoamingType.DEFAULT) as FileBasedStorage).file
    assertThat(newFile.getName()).isEqualTo("$newName${ModuleFileType.DOT_DEFAULT_EXTENSION}")
    assertThat(oldFile)
      .doesNotExist()
      .isNotEqualTo(newFile)
    assertThat(newFile).isFile()

    // ensure that macro value updated
    assertThat(storageManager.expandMacros(StoragePathMacros.MODULE_FILE)).isEqualTo(newFile.systemIndependentPath)
  }

  @Test fun `rename module parent virtual dir`() {
    runInEdtAndWait { module.saveStore() }
    val storageManager = module.stateStore.getStateStorageManager()
    val storage = storageManager.getStateStorage(StoragePathMacros.MODULE_FILE, RoamingType.DEFAULT) as FileBasedStorage

    val oldFile = storage.file
    val parentVirtualDir = storage.getVirtualFile()!!.getParent()
    runInEdtAndWait { runWriteAction { parentVirtualDir.rename(null, UUID.randomUUID().toString()) } }

    val newFile = Paths.get(parentVirtualDir.getPath(), "${module.getName()}${ModuleFileType.DOT_DEFAULT_EXTENSION}")
    try {
      assertThat(newFile).isRegularFile()
      assertRename(module.getName(), oldFile)
      assertThat(oldModuleNames).isEmpty()
    }
    finally {
      runInEdtAndWait { runWriteAction { parentVirtualDir.delete(this) } }
    }
  }
}