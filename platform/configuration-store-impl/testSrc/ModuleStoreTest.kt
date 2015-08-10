package com.intellij.configurationStore

import com.intellij.ProjectTopics
import com.intellij.ide.highlighter.ModuleFileType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.impl.stores.StoreUtil
import com.intellij.openapi.components.stateStore
import com.intellij.openapi.module.ModifiableModuleModel
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.ModuleAdapter
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.systemIndependentPath
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.FixtureRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.builders.EmptyModuleFixtureBuilder
import com.intellij.testFramework.fixtures.ModuleFixture
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.Function
import com.intellij.util.SmartList
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.not
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.collection.IsEmptyCollection.empty
import org.hamcrest.io.FileMatchers.anExistingFile
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import java.io.File
import java.util.UUID
import kotlin.properties.Delegates

class ModuleStoreTest {
  var moduleFixture: ModuleFixture by Delegates.notNull()
  val module by Delegates.lazy { moduleFixture.getModule() }

  // we test fireModuleRenamedByVfsEvent
  private val oldModuleNames = SmartList<String>()

  private val fixtureManager = FixtureRule {
    moduleFixture = addModule(javaClass<EmptyModuleFixtureBuilder<*>>()).getFixture()
  }

  private val ruleChain = RuleChain(
    object : ExternalResource() {
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
    fixtureManager,
    object : ExternalResource() {
      override fun before() {
        module.getMessageBus().connect().subscribe(ProjectTopics.MODULES, object : ModuleAdapter() {
          override fun modulesRenamed(project: Project, modules: MutableList<Module>, oldNameProvider: Function<Module, String>) {
            assertThat(modules.size(), equalTo(1))
            assertThat(modules.get(0), equalTo(module))
            oldModuleNames.add(oldNameProvider.`fun`(module))
          }
        })
      }
    }
  )

  public Rule fun getChain(): RuleChain = ruleChain

  fun Module.change(task: ModifiableModuleModel.() -> Unit) {
    runInEdtAndWait {
      val model = ModuleManager.getInstance(fixtureManager.projectFixture.getProject()).getModifiableModel()
      runWriteAction {
        model.task()
        model.commit()
      }
    }
  }

  // project structure
  public Test fun `rename module using model`() {
    runInEdtAndWait { StoreUtil.save(module.stateStore, null) }
    val storage = module.stateStore.getStateStorageManager().getStateStorage(StoragePathMacros.MODULE_FILE, RoamingType.PER_USER) as FileBasedStorage
    val oldFile = storage.getFile()
    assertThat(oldFile, anExistingFile())

    val oldName = module.getName()
    val newName = "foo"
    module.change { renameModule(module, newName) }
    assertRename(newName, oldFile)
    assertThat(oldModuleNames, equalTo(listOf(oldName)))
  }

  // project view
  public Test fun `rename module using rename virtual file`() {
    runInEdtAndWait { StoreUtil.save(module.stateStore, null) }
    var storage = module.stateStore.getStateStorageManager().getStateStorage(StoragePathMacros.MODULE_FILE, RoamingType.PER_USER) as FileBasedStorage
    val oldFile = storage.getFile()
    assertThat(oldFile, anExistingFile())

    val oldName = module.getName()
    val newName = "foo"
    runInEdtAndWait { runWriteAction { LocalFileSystem.getInstance().refreshAndFindFileByIoFile(oldFile)!!.rename(null, "$newName${ModuleFileType.DOT_DEFAULT_EXTENSION}") } }
    assertRename(newName, oldFile)
    assertThat(oldModuleNames, equalTo(listOf(oldName)))
  }

  // we cannot test external rename yet, because it is not supported - ModuleImpl doesn't support delete and create events (in case of external change we don't get move event, but get "delete old" and "create new")

  private fun assertRename(newName: String, oldFile: File) {
    val storageManager = moduleFixture.getModule().stateStore.getStateStorageManager()
    val newFile = (storageManager.getStateStorage(StoragePathMacros.MODULE_FILE, RoamingType.PER_USER) as FileBasedStorage).getFile()
    assertThat(newFile.getName(), equalTo("$newName${ModuleFileType.DOT_DEFAULT_EXTENSION}"))
    assertThat(oldFile, not(anExistingFile()))
    assertThat(oldFile, not(equalTo(newFile)))
    assertThat(newFile, anExistingFile())

    // ensure that macro value updated
    assertThat(storageManager.expandMacros(StoragePathMacros.MODULE_FILE), equalTo(newFile.systemIndependentPath))
  }

  public Test fun `rename module parent virtual dir`() {
    runInEdtAndWait { StoreUtil.save(module.stateStore, null) }
    val storageManager = module.stateStore.getStateStorageManager()
    val storage = storageManager.getStateStorage(StoragePathMacros.MODULE_FILE, RoamingType.PER_USER) as FileBasedStorage

    val oldFile = storage.getFile()
    val parentVirtualDir = storage.getVirtualFile()!!.getParent()
    runInEdtAndWait { runWriteAction { parentVirtualDir.rename(null, UUID.randomUUID().toString()) } }

    val newFile = File(parentVirtualDir.getPath(), module.getName() + ModuleFileType.DOT_DEFAULT_EXTENSION)
    try {
      assertThat(newFile, anExistingFile())
      assertRename(module.getName(), oldFile)
      assertThat(oldModuleNames, empty())
    }
    finally {
      runInEdtAndWait { runWriteAction { parentVirtualDir.delete(this) } }
    }
  }
}