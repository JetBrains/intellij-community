package com.intellij.configurationStore

import com.intellij.ide.highlighter.ModuleFileType
import com.intellij.openapi.application.invokeAndWaitIfNeed
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.impl.stores.FileBasedStorage
import com.intellij.openapi.components.impl.stores.StoreUtil
import com.intellij.openapi.components.stateStore
import com.intellij.openapi.module.ModifiableModuleModel
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.util.io.systemIndependentPath
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.FixtureRule
import com.intellij.testFramework.builders.EmptyModuleFixtureBuilder
import com.intellij.testFramework.exists
import com.intellij.testFramework.fixtures.ModuleFixture
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.not
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Rule
import org.junit.Test
import java.io.File
import kotlin.properties.Delegates

class ModuleStoreTest {
  var moduleFixture: ModuleFixture by Delegates.notNull()

  private val fixtureManager = FixtureRule {
    moduleFixture = addModule(javaClass<EmptyModuleFixtureBuilder<*>>()).getFixture()
  }

  public Rule fun getFixtureManager(): FixtureRule = fixtureManager

  fun Module.change(task: ModifiableModuleModel.() -> Unit) {
    invokeAndWaitIfNeed {
      val model = ModuleManager.getInstance(fixtureManager.projectFixture.getProject()).getModifiableModel()
      runWriteAction {
        model.task()
        model.commit()
      }
    }
  }

  // project structure
  public Test fun `rename module using model`() {
    val module = moduleFixture.getModule()
    invokeAndWaitIfNeed { StoreUtil.save(module.stateStore, null) }
    val storageManager = module.stateStore.getStateStorageManager()
    val storage = storageManager.getStateStorage(StoragePathMacros.MODULE_FILE, RoamingType.PER_USER) as FileBasedStorage
    val oldFile = storage.getFile()
    assertThat(oldFile, exists())

    val newName = "foo"
    module.change { renameModule(module, newName) }
    assertRename(newName, oldFile)
  }

  // project view
  public Test fun `rename module using rename virtual file`() {
    val module = moduleFixture.getModule()
    invokeAndWaitIfNeed { StoreUtil.save(module.stateStore, null) }
    val storageManager = module.stateStore.getStateStorageManager()
    var storage = storageManager.getStateStorage(StoragePathMacros.MODULE_FILE, RoamingType.PER_USER) as FileBasedStorage
    val oldFile = storage.getFile()
    assertThat(oldFile, exists())

    val newName = "foo"
    invokeAndWaitIfNeed { runWriteAction { LocalFileSystem.getInstance().refreshAndFindFileByIoFile(oldFile)!!.rename(null, "$newName${ModuleFileType.DOT_DEFAULT_EXTENSION}") } }
    assertRename(newName, oldFile)
  }

  // we cannot test external rename yet, because it is not supported - ModuleImpl doesn't support delete and create events (in case of external change we don't get move event, but get "delete old" and "create new")

  private fun assertRename(newName: String, oldFile: File) {
    val storageManager = moduleFixture.getModule().stateStore.getStateStorageManager()
    val newFile = (storageManager.getStateStorage(StoragePathMacros.MODULE_FILE, RoamingType.PER_USER) as FileBasedStorage).getFile()
    assertThat(newFile.getName(), equalTo("$newName${ModuleFileType.DOT_DEFAULT_EXTENSION}"))
    assertThat(oldFile, not(exists()))
    assertThat(oldFile, not(equalTo(newFile)))
    assertThat(newFile, exists())

    // ensure that macro value updated
    assertThat(storageManager.expandMacros(StoragePathMacros.MODULE_FILE), equalTo(newFile.systemIndependentPath))
  }
}