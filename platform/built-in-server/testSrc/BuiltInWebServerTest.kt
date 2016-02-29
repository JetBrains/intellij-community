package org.jetbrains.ide

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.module.EmptyModuleType
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.refreshVfs
import com.intellij.util.systemIndependentPath
import com.intellij.util.writeChild
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

private class BuiltInWebServerTest : BuiltInServerTestCase() {
  override val urlPathPrefix: String
    get() = "/${BuiltInServerTestCase.projectRule.project.name}"

  @Test
  @TestManager.TestDescriptor(filePath = "foo/index.html", doNotCreate = true, status = 200)
  fun `get only dir without end slash`() {
    testIndex("foo")
  }

  @Test
  @TestManager.TestDescriptor(filePath = "foo/index.html", doNotCreate = true, status = 200)
  fun `get only dir with end slash`() {
    testIndex("foo/")
  }

  @Test
  @TestManager.TestDescriptor(filePath = "foo/index.html", doNotCreate = true, status = 200)
  fun `get index file and then dir`() {
    testIndex("foo/index.html", "foo")
  }

  private fun testIndex(vararg paths: String) {
    val project = BuiltInServerTestCase.projectRule.project
    val newPath = tempDirManager.newPath()
    newPath.writeChild(manager.filePath!!, "hello")
    newPath.refreshVfs()

    runInEdtAndWait {
      runWriteAction {
        val systemIndependentPath = newPath.systemIndependentPath
        val module = ModuleManager.getInstance(project).newModule("$systemIndependentPath/test.iml", EmptyModuleType.EMPTY_MODULE)
        ModuleRootModificationUtil.addContentRoot(module, systemIndependentPath)
      }
    }

    for (path in paths) {
      doTest(path) {
        assertThat(it.inputStream.reader().readText()).isEqualTo("hello")
      }
    }
  }
}