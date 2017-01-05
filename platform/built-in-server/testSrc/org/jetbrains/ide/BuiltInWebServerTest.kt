package org.jetbrains.ide

import com.google.common.net.UrlEscapers
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.module.EmptyModuleType
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.refreshVfs
import com.intellij.testFramework.*
import com.intellij.util.io.createDirectories
import com.intellij.util.io.systemIndependentPath
import com.intellij.util.io.write
import com.intellij.util.io.writeChild
import io.netty.handler.codec.http.HttpResponseStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test

internal class BuiltInWebServerTest : BuiltInServerTestCase() {
  override val urlPathPrefix: String
    get() = "/${projectRule.project.name}"

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
    val project = projectRule.project
    val newPath = tempDirManager.newPath()
    newPath.writeChild(manager.filePath!!, "hello")
    newPath.refreshVfs()

    createModule(newPath.systemIndependentPath, project)

    for (path in paths) {
      doTest(path) {
        assertThat(it.inputStream.reader().readText()).isEqualTo("hello")
      }
    }
  }
}

private fun createModule(systemIndependentPath: String, project: Project) {
  runInEdtAndWait {
    runWriteAction {
      val module = ModuleManager.getInstance(project).newModule("$systemIndependentPath/test.iml", EmptyModuleType.EMPTY_MODULE)
      ModuleRootModificationUtil.addContentRoot(module, systemIndependentPath)
    }
  }
}

internal class HeavyBuiltInWebServerTest {
  companion object {
    @JvmField
    @ClassRule val appRule = ProjectRule()
  }

  @Rule
  @JvmField
  val tempDirManager = TemporaryDirectory()

  @Test
  fun `path outside of project`() {
    val projectDir = tempDirManager.newPath().resolve("foo/bar")
    val projectDirPath = projectDir.systemIndependentPath
    createHeavyProject("$projectDirPath/test.ipr").use { project ->
      projectDir.createDirectories()
      LocalFileSystem.getInstance().refreshAndFindFileByPath(projectDirPath)
      createModule(projectDirPath, project)

      val path = tempDirManager.newPath("doNotExposeMe.txt").write("doNotExposeMe").systemIndependentPath
      val relativePath = FileUtil.getRelativePath(project.basePath!!, path, '/')
      val webPath = StringUtil.replace(UrlEscapers.urlPathSegmentEscaper().escape("${project.name}/$relativePath"), "%2F", "/")
      testUrl("http://localhost:${BuiltInServerManager.getInstance().port}/$webPath", HttpResponseStatus.NOT_FOUND)
    }
  }

  @Test
  fun `file in hidden folder`() {
    val projectDir = tempDirManager.newPath().resolve("foo/bar")
    val projectDirPath = projectDir.systemIndependentPath
    createHeavyProject("$projectDirPath/test.ipr").use { project ->
      projectDir.createDirectories()
      LocalFileSystem.getInstance().refreshAndFindFileByPath(projectDirPath)
      createModule(projectDirPath, project)

      val dir = projectDir.resolve(".coverage")
      dir.createDirectories()
      val path = dir.resolve("foo").write("exposeMe").systemIndependentPath
      val relativePath = FileUtil.getRelativePath(project.basePath!!, path, '/')
      val webPath = StringUtil.replace(UrlEscapers.urlPathSegmentEscaper().escape("${project.name}/$relativePath"), "%2F", "/")
      testUrl("http://localhost:${BuiltInServerManager.getInstance().port}/$webPath", HttpResponseStatus.OK)
    }
  }
}