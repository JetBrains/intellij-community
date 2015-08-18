package com.intellij.configurationStore

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleTypeId
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectEx
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.impl.storage.ClasspathStorage
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import java.nio.file.Path

@RunsInEdt class ModuleStoreTest {
  companion object {
     ClassRule val projectRule = ProjectRule()

    val MODULE_DIR = "\$MODULE_DIR$"

    private inline fun <T> Module.useAndDispose(task: Module.() -> T): T {
      try {
        return task()
      }
      finally {
        ModuleManager.getInstance(projectRule.project).disposeModule(this)
      }
    }

    private fun VirtualFile.loadModule() = runWriteAction { ModuleManager.getInstance(projectRule.project).loadModule(getPath()) }

    private fun Path.createModule() = runWriteAction { ModuleManager.getInstance(projectRule.project).newModule(systemIndependentPath, ModuleTypeId.JAVA_MODULE) }
  }

  private val tempDirManager = TemporaryDirectory()

  private val ruleChain = RuleChain(tempDirManager, EdtRule())

  public Rule fun getChain(): RuleChain = ruleChain

  @Test fun `set option`() {
    val moduleFile = runWriteAction {
      VfsTestUtil.createFile(tempDirManager.newVirtualDirectory("module"), "test.iml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
        "<module type=\"JAVA_MODULE\" foo=\"bar\" version=\"4\" />")
    }

    projectRule.project.runInStoreLoadMode {
      moduleFile.loadModule().useAndDispose {
        assertThat(getOptionValue("foo")).isEqualTo("bar")

        setOption("foo", "not bar")
        saveStore()
      }

      moduleFile.loadModule().useAndDispose {
        assertThat(getOptionValue("foo")).isEqualTo("not bar")

        setOption("foo", "not bar")
        saveStore()
      }
    }
  }

  @Test fun `must be empty if classpath storage`() {
    // we must not use VFS here, file must not be created
    val moduleFile = tempDirManager.newPath("module").resolve("test.iml")
    projectRule.project.runInStoreLoadMode {
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
  }
}

inline fun <T> Project.runInStoreLoadMode(task: () -> T): T {
  val isModeDisabled = (this as ProjectEx).isOptimiseTestLoadSpeed()
  if (isModeDisabled) {
    setOptimiseTestLoadSpeed(false)
  }
  try {
    return task()
  }
  finally {
    if (isModeDisabled) {
      setOptimiseTestLoadSpeed(true)
    }
  }
}