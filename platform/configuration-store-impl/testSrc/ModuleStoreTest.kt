package com.intellij.configurationStore

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.impl.stores.BatchUpdateListener
import com.intellij.openapi.components.stateStore
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleTypeId
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.impl.storage.ClasspathStorage
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.*
import com.intellij.util.parentSystemIndependentPath
import com.intellij.util.readText
import com.intellij.util.systemIndependentPath
import gnu.trove.TObjectIntHashMap
import org.assertj.core.api.Assertions.assertThat
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import java.nio.file.Path
import java.nio.file.Paths

@RunsInEdt
@RunsInActiveStoreMode
class ModuleStoreTest {
  companion object {
    @JvmField
    @ClassRule val projectRule = ProjectRule()

    val MODULE_DIR = "\$MODULE_DIR$"

    private inline fun <T> Module.useAndDispose(task: Module.() -> T): T {
      try {
        return task()
      }
      finally {
        ModuleManager.getInstance(projectRule.project).disposeModule(this)
      }
    }

    private fun VirtualFile.loadModule() = runWriteAction { ModuleManager.getInstance(projectRule.project).loadModule(path) }

    fun Path.createModule() = projectRule.createModule(this)
  }

  private val tempDirManager = TemporaryDirectory()

  private val ruleChain = RuleChain(tempDirManager, EdtRule(), ActiveStoreRule(projectRule), DisposeModulesRule(projectRule))
  @Rule fun getChain() = ruleChain

  @Test fun `set option`() {
    val moduleFile = runWriteAction {
      VfsTestUtil.createFile(tempDirManager.newVirtualDirectory("module"), "test.iml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<module type=\"JAVA_MODULE\" foo=\"bar\" version=\"4\" />")
    }

    moduleFile.loadModule().useAndDispose {
      assertThat(getOptionValue("foo")).isEqualTo("bar")

      setOption("foo", "not bar")
      saveStore()
    }

    moduleFile.loadModule().useAndDispose {
      assertThat(getOptionValue("foo")).isEqualTo("not bar")

      setOption("foo", "not bar")
      // ensure that save the same data will not lead to any problems (like "Content equals, but it must be handled not on this level")
      saveStore()
    }
  }

  @Test fun `must be empty if classpath storage`() {
    // we must not use VFS here, file must not be created
    val moduleFile = tempDirManager.newPath("module", refreshVfs = true).resolve("test.iml")
    moduleFile.createModule().useAndDispose {
      ModuleRootModificationUtil.addContentRoot(this, moduleFile.parentSystemIndependentPath)
      saveStore()
      assertThat(moduleFile).isRegularFile()
      assertThat(moduleFile.readText()).startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<module type=\"JAVA_MODULE\" version=\"4\">")

      ClasspathStorage.setStorageType(ModuleRootManager.getInstance(this), "eclipse")
      saveStore()
      assertThat(moduleFile).hasContent("""<?xml version="1.0" encoding="UTF-8"?>
<module classpath="eclipse" classpath-dir="$MODULE_DIR" type="JAVA_MODULE" version="4" />""")
    }
  }

  @Test fun `one batch update session if several modules changed`() {
    val nameToCount = TObjectIntHashMap<String>()
    val root = tempDirManager.newPath(refreshVfs = true)

    fun Module.addContentRoot() {
      val moduleName = name
      var batchUpdateCount = 0
      nameToCount.put(moduleName, batchUpdateCount)

      messageBus.connect().subscribe(BatchUpdateListener.TOPIC, object : BatchUpdateListener {
        override fun onBatchUpdateStarted() {
          nameToCount.put(moduleName, ++batchUpdateCount)
        }

        override fun onBatchUpdateFinished() {
        }
      })

      //
      ModuleRootModificationUtil.addContentRoot(this, root.resolve(moduleName).systemIndependentPath)
      assertThat(contentRootUrls).hasSize(1)
      saveStore()
    }

    fun Module.removeContentRoot() {
      val modulePath = stateStore.stateStorageManager.expandMacros(StoragePathMacros.MODULE_FILE)
      val moduleFile = Paths.get(modulePath)
      assertThat(moduleFile).isRegularFile()

      val virtualFile = LocalFileSystem.getInstance().findFileByPath(modulePath)!!
      val newData = moduleFile.readText().replace("<content url=\"file://\$MODULE_DIR$/$name\" />\n", "").toByteArray()
      runWriteAction {
        virtualFile.setBinaryContent(newData)
      }
    }

    fun Module.assertChangesApplied() {
      assertThat(contentRootUrls).isEmpty()
    }

    val m1 = root.resolve("m1.iml").createModule()
    val m2 = root.resolve("m2.iml").createModule()

    var projectBatchUpdateCount = 0
    projectRule.project.messageBus.connect(m1).subscribe(BatchUpdateListener.TOPIC, object : BatchUpdateListener {
      override fun onBatchUpdateStarted() {
        nameToCount.put("p", ++projectBatchUpdateCount)
      }

      override fun onBatchUpdateFinished() {
      }
    })

    m1.addContentRoot()
    m2.addContentRoot()

    m1.removeContentRoot()
    m2.removeContentRoot()

    (ProjectManager.getInstance() as StoreAwareProjectManager).flushChangedAlarm()

    m1.assertChangesApplied()
    m2.assertChangesApplied()

    assertThat(nameToCount.size()).isEqualTo(3)
    assertThat(nameToCount.get("p")).isEqualTo(1)
    assertThat(nameToCount.get("m1")).isEqualTo(1)
    assertThat(nameToCount.get("m1")).isEqualTo(1)
  }
}

val Module.contentRootUrls: Array<String>
  get() = ModuleRootManager.getInstance(this).contentRootUrls

fun ProjectRule.createModule(path: Path) = runWriteAction { ModuleManager.getInstance(project).newModule(path.systemIndependentPath, ModuleTypeId.JAVA_MODULE) }