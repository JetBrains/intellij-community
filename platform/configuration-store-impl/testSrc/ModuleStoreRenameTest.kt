package com.intellij.configurationStore

import com.intellij.ProjectTopics
import com.intellij.ide.highlighter.ModuleFileType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.stateStore
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.ModuleListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modifyModules
import com.intellij.openapi.project.rootManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.impl.ModuleRootManagerComponent
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.*
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.util.Function
import com.intellij.util.SmartList
import com.intellij.util.io.readText
import com.intellij.util.io.systemIndependentPath
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import kotlin.properties.Delegates

private val Module.storage: FileBasedStorage
  get() = (stateStore.stateStorageManager as StateStorageManagerImpl).getCachedFileStorages(listOf(StoragePathMacros.MODULE_FILE)).first()

internal class ModuleStoreRenameTest {
  companion object {
    @JvmField
    @ClassRule
    val projectRule = ProjectRule()
  }

  var module: Module by Delegates.notNull()
  var dependentModule: Module by Delegates.notNull()

  // we test fireModuleRenamedByVfsEvent
  private val oldModuleNames = SmartList<String>()

  private val tempDirManager = TemporaryDirectory()

  @Rule
  @JvmField
  val ruleChain = RuleChain(
    tempDirManager,
    object : ExternalResource() {
      override fun before() {
        runInEdtAndWait {
          val moduleFileParent = tempDirManager.newPath(refreshVfs = true)
          module = projectRule.createModule(moduleFileParent.resolve("m.iml"))

          dependentModule = projectRule.createModule(moduleFileParent.resolve("dependent-module.iml"))
          ModuleRootModificationUtil.addDependency(dependentModule, module)
        }

        module.messageBus.connect().subscribe(ProjectTopics.MODULES, object : ModuleListener {
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
    DisposeModulesRule(projectRule),
    EdtRule()
  )

  // project structure
  @Test fun `rename module using model`() {
    saveModules()

    val storage = module.storage
    val oldFile = storage.file
    assertThat(oldFile).isRegularFile

    val oldName = module.name
    val newName = "foo"
    runInEdtAndWait { projectRule.project.modifyModules { renameModule(module, newName) } }
    assertRename(newName, oldFile)
    assertThat(oldModuleNames).containsOnly(oldName)
  }

  // project view
  @Test fun `rename module using rename virtual file`() {
    testRenameModule()
  }

  private fun testRenameModule() {
    saveModules()
    val storage = module.storage
    val oldFile = storage.file
    assertThat(oldFile).isRegularFile

    val oldName = module.name
    val newName = "foo.dot"
    runInEdtAndWait { runWriteAction { LocalFileSystem.getInstance().refreshAndFindFileByPath(oldFile.systemIndependentPath)!!.rename(null, "$newName${ModuleFileType.DOT_DEFAULT_EXTENSION}") } }
    assertRename(newName, oldFile)
    assertThat(oldModuleNames).containsOnly(oldName)
  }

  // we cannot test external rename yet, because it is not supported - ModuleImpl doesn't support delete and create events (in case of external change we don't get move event, but get "delete old" and "create new")

  private fun assertRename(newName: String, oldFile: Path) {
    val newFile = module.storage.file
    assertThat(newFile.fileName.toString()).isEqualTo("$newName${ModuleFileType.DOT_DEFAULT_EXTENSION}")
    assertThat(oldFile)
      .doesNotExist()
      .isNotEqualTo(newFile)
    assertThat(newFile).isRegularFile

    // ensure that macro value updated
    assertThat(module.stateStore.stateStorageManager.expandMacros(StoragePathMacros.MODULE_FILE)).isEqualTo(newFile.systemIndependentPath)

    runInEdtAndWait {
      dependentModule.saveStore()
    }
    assertThat(dependentModule.storage.file.readText()).contains("""<orderEntry type="module" module-name="$newName" />""")
  }

  @Test fun `rename module parent virtual dir`() {
    saveModules()
    val storage = module.storage
    val oldFile = storage.file
    val parentVirtualDir = storage.virtualFile!!.parent
    runInEdtAndWait { runWriteAction { parentVirtualDir.rename(null, UUID.randomUUID().toString()) } }

    val newFile = Paths.get(parentVirtualDir.path, "${module.name}${ModuleFileType.DOT_DEFAULT_EXTENSION}")
    try {
      assertThat(newFile).isRegularFile
      assertRename(module.name, oldFile)
      assertThat(oldModuleNames).isEmpty()

      testRenameModule()
    }
    finally {
      runInEdtAndWait { runWriteAction { parentVirtualDir.delete(this) } }
    }
  }

  @Test
  @RunsInEdt
  fun `rename module source root`() {
    saveModules()
    val storage = module.storage
    val parentVirtualDir = storage.virtualFile!!.parent
    val src = VfsTestUtil.createDir(parentVirtualDir, "foo")
    runWriteAction { PsiTestUtil.addSourceContentToRoots(module, src, false) }
    module.saveStore()

    val rootManager = module.rootManager as ModuleRootManagerComponent
    val stateModificationCount = rootManager.stateModificationCount

    runWriteAction { src.rename(null, "bar.dot") }

    assertThat(stateModificationCount).isLessThan(rootManager.stateModificationCount)
  }

  private fun saveModules() {
    runInEdtAndWait {
      module.saveStore()
      dependentModule.saveStore()
    }
  }
}