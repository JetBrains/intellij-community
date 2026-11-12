// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.ide

import com.google.common.net.UrlEscapers
import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.components.service
import com.intellij.openapi.module.EmptyModuleType
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TrustedProjectsTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.testFramework.refreshVfs
import com.intellij.testFramework.useProject
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpResponseStatus
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.builtInWebServer.BuiltInWebServerAuth
import org.jetbrains.builtInWebServer.TOKEN_HEADER_NAME
import org.jetbrains.builtInWebServer.TOKEN_PARAM_NAME
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createParentDirectories
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.writeText

@TestApplication
internal class BuiltInWebServerTest : BuiltInServerTestCase() {
  override val urlPathPrefix: String
    get() = "/${project.name}"

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
    val newPath = tempDir
    newPath.resolve(manager.filePath!!).createParentDirectories().writeText("hello")
    VirtualFileManager.getInstance().refreshAndFindFileByNioPath(newPath)

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
    ModuleRootModificationUtil.addContentRoot(module, projectDir.invariantSeparatorsPathString)
  }
}

@TestApplication
internal class HeavyBuiltInWebServerTest {
  companion object {
    @BeforeAll
    @JvmStatic
    fun runServer() {
      BuiltInServerManager.getInstance().waitForStart()
    }
  }

  private val tempDir by tempPathFixture()

  @TestDisposable
  lateinit var disposable: Disposable

  @Test
  fun `path outside of project`() {
    val projectDir = tempDir.resolve("project")
    PlatformTestUtil.loadAndOpenProject(projectDir, disposable).useProject { project ->
      projectDir.createDirectories()
      createModule(projectDir, project)

      val path = tempDir.resolve("doNotExposeMe.txt").apply { writeText("doNotExposeMe") }
      val relativePath = Path.of(project.basePath!!).relativize(path).invariantSeparatorsPathString
      val webPath = StringUtil.replace(UrlEscapers.urlPathSegmentEscaper().escape("${project.name}/${relativePath}"), "%2F", "/")
      testUrl("http://localhost:${BuiltInServerManager.getInstance().port}/$webPath", HttpResponseStatus.NOT_FOUND, asSignedRequest = true)
    }
  }

  @Test
  fun `file in hidden folder`() {
    val projectDir = tempDir.resolve("project")
    PlatformTestUtil.loadAndOpenProject(projectDir, disposable).useProject { project ->
      projectDir.createDirectories()
      createModule(projectDir, project)

      // DefaultWebServerPathHandler uses module roots as virtual file - must be refreshed
      VirtualFileManager.getInstance().refreshAndFindFileByNioPath(projectDir)

      val dir = projectDir.resolve(".coverage").createDirectories()
      val path = dir.resolve("foo").apply { writeText("exposeMe") }
      val relativePath = Path.of(project.basePath!!).relativize(path).invariantSeparatorsPathString
      val webPath = UrlEscapers.urlPathSegmentEscaper().escape("${project.name}/${relativePath}").replace("%2F", "/")
      testUrl("http://localhost:${BuiltInServerManager.getInstance().port}/${webPath}", HttpResponseStatus.FORBIDDEN, asSignedRequest = true)
    }
  }

  @Test
  fun `untrusted project is not served`() {
    val projectDir = tempDir.resolve("project")
    PlatformTestUtil.loadAndOpenProject(projectDir, disposable).useProject { project ->
      projectDir.createDirectories()
      projectDir.resolve("index.html").writeText("hello")
      projectDir.resolve("sw.js").writeText("")
      VirtualFileManager.getInstance().refreshAndFindFileByNioPath(projectDir)
      createModule(projectDir, project)

      val host = "http://localhost:${BuiltInServerManager.getInstance().port}"
      val pageUrl = "$host/${project.name}/index.html"
      val serviceWorkerRequest = HttpRequest.newBuilder(URI("$host/${project.name}/sw.js"))
        .header(TOKEN_HEADER_NAME, service<BuiltInWebServerAuth>().acquireToken())
        .header("Service-Worker", "script")
        .build()
      val client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build()
      fun serviceWorkerStatus(): HttpResponseStatus =
        HttpResponseStatus.valueOf(client.send(serviceWorkerRequest, HttpResponse.BodyHandlers.discarding()).statusCode())

      TrustedProjectsTestUtil.withTrustedProjectsCheckEnabled {
        TrustedProjects.setProjectTrusted(project, true)
        testUrl(pageUrl, HttpResponseStatus.OK, asSignedRequest = true).body().use {
          assertThat(it.reader().readText()).isEqualTo("hello")
        }
        assertThat(serviceWorkerStatus()).isEqualTo(HttpResponseStatus.OK)

        TrustedProjects.setProjectTrusted(project, false)
        val denied = testUrl(pageUrl, HttpResponseStatus.FORBIDDEN, asSignedRequest = true)
        denied.body().close()
        assertThat(denied.headers().firstValue(HttpHeaderNames.SET_COOKIE.toString())).isEmpty()
        assertThat(serviceWorkerStatus()).isEqualTo(HttpResponseStatus.FORBIDDEN)

        TrustedProjects.setProjectTrusted(project, true)
        testUrl(pageUrl, HttpResponseStatus.OK, asSignedRequest = true).body().close()
      }
    }
  }

  @Test
  fun `cross-project requests resolve target project regardless of open project order`() {
    for (openProjectAFirst in listOf(true, false)) {
      val rootDir = Files.createTempDirectory(tempDir, "")
      val projectADir = rootDir.resolve("project-a").createDirectories()
      projectADir.resolve("index.html").writeText("index-from-a")
      projectADir.resolve("script.js").writeText("script-from-a")
      projectADir.refreshVfs()

      val projectBDir = rootDir.resolve("project-b").createDirectories()
      projectBDir.resolve("secret.txt").writeText("secret-from-b")
      projectBDir.resolve("config.json").writeText("root-config")
      projectBDir.resolve("project-b/config.json").createParentDirectories().writeText("nested-config")
      projectBDir.refreshVfs()

      withTwoProjects(projectADir, projectBDir, openProjectAFirst) { projectA, projectB ->
        val host = "http://localhost:${BuiltInServerManager.getInstance().port}"
        val client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build()
        val token = service<BuiltInWebServerAuth>().acquireToken()

        val previewRequest = HttpRequest.newBuilder(URI("$host/${projectA.name}/index.html?$TOKEN_PARAM_NAME=$token")).build()
        val previewResponse = client.send(previewRequest, HttpResponse.BodyHandlers.ofString())
        assertThat(HttpResponseStatus.valueOf(previewResponse.statusCode())).isEqualTo(HttpResponseStatus.OK)
        assertThat(previewResponse.body()).isEqualTo("index-from-a")
        val cookie = previewResponse.headers().firstValue(HttpHeaderNames.SET_COOKIE.toString()).orElseThrow().substringBefore(';')

        val referer = "$host/${projectA.name}/index.html?$TOKEN_PARAM_NAME=$token"
        assertResponseBody(client, "$host/${projectB.name}/secret.txt", cookie = cookie, referer = referer, expectedBody = "secret-from-b")
        assertResponseBody(client, "$host/${projectB.name}/config.json", cookie = cookie, referer = referer, expectedBody = "root-config")
        assertResponseBody(client, "$host/${projectB.name}/secret.txt", cookie = cookie, referer = null, expectedBody = "secret-from-b")
        assertResponseBody(client, "$host/script.js", cookie = cookie, referer = referer, expectedBody = "script-from-a")
      }
    }
  }

  @Test
  fun `renamed project resolves by base directory name regardless of open project order`() {
    for (openProjectAFirst in listOf(true, false)) {
      val rootDir = Files.createTempDirectory(tempDir, "")
      val projectADir = rootDir.resolve("project-a").createDirectories()
      projectADir.resolve("index.html").writeText("index-from-a")
      projectADir.refreshVfs()

      val projectBDir = rootDir.resolve("project-b-dir").createDirectories()
      projectBDir.resolve(".idea/.name").createParentDirectories().writeText("My Service")
      projectBDir.resolve("secret.txt").writeText("secret-from-b")
      projectBDir.resolve("only-in-b.js").writeText("script-from-b")
      projectBDir.refreshVfs()

      withTwoProjects(projectADir, projectBDir, openProjectAFirst) { projectA, projectB ->
        assertThat(projectB.name).isEqualTo("My Service")
        val host = "http://localhost:${BuiltInServerManager.getInstance().port}"
        val client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build()
        val token = service<BuiltInWebServerAuth>().acquireToken()
        val refererFromA = "$host/${projectA.name}/index.html"
        val refererFromBDir = "$host/project-b-dir/index.html"

        assertResponseBody(client, "$host/project-b-dir/secret.txt", token = token, referer = refererFromA, expectedBody = "secret-from-b")
        assertResponseBody(client, "$host/only-in-b.js", token = token, referer = refererFromBDir, expectedBody = "script-from-b")
      }
    }
  }

  private fun withTwoProjects(
    projectADir: Path,
    projectBDir: Path,
    openProjectAFirst: Boolean,
    action: (projectA: Project, projectB: Project) -> Unit,
  ) {
    val firstDir = if (openProjectAFirst) projectADir else projectBDir
    val secondDir = if (openProjectAFirst) projectBDir else projectADir
    PlatformTestUtil.loadAndOpenProject(firstDir, disposable).useProject { first ->
      createModule(firstDir, first)
      PlatformTestUtil.loadAndOpenProject(secondDir, disposable).useProject { second ->
        createModule(secondDir, second)
        val openProjects = ProjectManager.getInstance().openProjects
        assertThat(openProjects.indexOf(first)).isLessThan(openProjects.indexOf(second))
        if (openProjectAFirst) {
          action(first, second)
        }
        else {
          action(second, first)
        }
      }
    }
  }

  private fun assertResponseBody(
    client: HttpClient,
    url: String,
    token: String? = null,
    cookie: String? = null,
    referer: String?,
    expectedBody: String,
  ) {
    val builder = HttpRequest.newBuilder(URI(url))
    token?.let { builder.header(TOKEN_HEADER_NAME, it) }
    cookie?.let { builder.header(HttpHeaderNames.COOKIE.toString(), it) }
    referer?.let { builder.header(HttpHeaderNames.REFERER.toString(), it) }
    val response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    assertThat(HttpResponseStatus.valueOf(response.statusCode())).isEqualTo(HttpResponseStatus.OK)
    assertThat(response.body()).isEqualTo(expectedBody)
  }
}

@TestApplication
internal class BuiltInWebServerAbsolutePathTest : BuiltInServerTestCase() {
  @Test
  fun `absolute path reference`() {
    val newPath = tempDir
    newPath.resolve("script.js").writeText("hello")
    VirtualFileManager.getInstance().refreshAndFindFileByNioPath(newPath)
    createModule(newPath, project)

    val host = "http://localhost:${BuiltInServerManager.getInstance().port}"
    val builder = HttpRequest.newBuilder(URI("$host/script.js"))
    builder.header(TOKEN_HEADER_NAME, service<BuiltInWebServerAuth>().acquireToken())
    builder.header(HttpHeaderNames.REFERER.toString(), "${host}/${project.name}/index.html")

    val client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build()
    val response = client.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream())
    assertThat(HttpResponseStatus.valueOf(response.statusCode())).isEqualTo(HttpResponseStatus.OK)
    assertThat(response.body().reader().readText()).isEqualTo("hello")
  }
}
