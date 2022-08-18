// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.ide

import com.google.common.net.UrlEscapers
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.module.EmptyModuleType
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.*
import com.intellij.util.io.createDirectories
import com.intellij.util.io.systemIndependentPath
import com.intellij.util.io.write
import com.intellij.util.io.writeChild
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpResponseStatus
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.builtInWebServer.TOKEN_HEADER_NAME
import org.jetbrains.builtInWebServer.acquireToken
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Path

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
    LocalFileSystem.getInstance().refreshAndFindFileByNioFile(newPath)

    createModule(newPath, project)

    for (path in paths) {
      doTest(urlSuffix = "/$path") {
        assertThat(it.body().reader().readText()).isEqualTo("hello")
      }
    }
  }
}

private fun createModule(projectDir: Path, project: Project) {
  runWriteActionAndWait {
    val module = ModuleManager.getInstance(project).newModule(projectDir.resolve("test.iml"), EmptyModuleType.EMPTY_MODULE)
    ModuleRootModificationUtil.addContentRoot(module, projectDir.systemIndependentPath)
  }
}

internal class HeavyBuiltInWebServerTest {
  companion object {
    @JvmField
    @ClassRule
    val appRule = ApplicationRule()

    @BeforeClass
    @JvmStatic
    fun runServer() {
      BuiltInServerManager.getInstance().waitForStart()
    }
  }

  @Rule
  @JvmField
  val tempDirManager = TemporaryDirectory()

  @Test
  fun `path outside of project`() {
    val projectDir = tempDirManager.newPath()
    PlatformTestUtil.loadAndOpenProject(projectDir, disposableRule.disposable).useProject { project ->
      projectDir.createDirectories()
      createModule(projectDir, project)

      val path = tempDirManager.newPath("doNotExposeMe.txt").write("doNotExposeMe").systemIndependentPath
      val relativePath = FileUtil.getRelativePath(project.basePath!!, path, '/')
      val webPath = StringUtil.replace(UrlEscapers.urlPathSegmentEscaper().escape("${project.name}/$relativePath"), "%2F", "/")
      testUrl("http://localhost:${BuiltInServerManager.getInstance().port}/$webPath", HttpResponseStatus.NOT_FOUND, asSignedRequest = true)
    }
  }

  @Rule
  @JvmField
  val disposableRule = DisposableRule()

  @Test
  fun `file in hidden folder`() {
    val projectDir = tempDirManager.newPath()
    PlatformTestUtil.loadAndOpenProject(projectDir, disposableRule.disposable).useProject { project ->
      projectDir.createDirectories()
      createModule(projectDir, project)

      // DefaultWebServerPathHandler uses module roots as virtual file - must be refreshed
      LocalFileSystem.getInstance().refreshAndFindFileByNioFile(projectDir)

      val dir = projectDir.resolve(".coverage")
      dir.createDirectories()
      val path = dir.resolve("foo").write("exposeMe").systemIndependentPath
      val relativePath = FileUtil.getRelativePath(project.basePath!!, path, '/')
      val webPath = UrlEscapers.urlPathSegmentEscaper().escape("${project.name}/$relativePath").replace("%2F", "/")
      testUrl("http://localhost:${BuiltInServerManager.getInstance().port}/$webPath", HttpResponseStatus.OK, asSignedRequest = true)
    }
  }
}

internal class BuiltInWebServerAbsolutePathTest : BuiltInServerTestCase() {

  @Test
  fun `absolute path reference`() {
    val project = projectRule.project
    val newPath = tempDirManager.newPath()
    newPath.writeChild("script.js", "hello")
    LocalFileSystem.getInstance().refreshAndFindFileByNioFile(newPath)
    createModule(newPath, project)

    val host = "http://localhost:${BuiltInServerManager.getInstance().port}"
    val builder = HttpRequest.newBuilder(URI("$host/script.js"))
    builder.header(TOKEN_HEADER_NAME, acquireToken())
    builder.header(HttpHeaderNames.REFERER.toString(), "${host}/${project.name}/index.html")

    val client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build()
    val response = client.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream())
    assertThat(HttpResponseStatus.valueOf(response.statusCode())).isEqualTo(HttpResponseStatus.OK)
    assertThat(response.body().reader().readText()).isEqualTo("hello")
  }
}