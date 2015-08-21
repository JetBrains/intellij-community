package org.jetbrains.ide

import com.google.gson.stream.JsonWriter
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.TemporaryDirectory
import io.netty.handler.codec.http.HttpResponseStatus
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.ide.TestManager.TestDescriptor
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import java.io.BufferedOutputStream
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

public class RestApiTest {
  companion object {
    @ClassRule val projectRule = ProjectRule()
  }

  private val tempDirManager = TemporaryDirectory()
  private val manager = TestManager(projectRule, tempDirManager)

  private val ruleChain = RuleChain(tempDirManager, manager)
  public Rule fun getChain(): RuleChain = ruleChain

  Test(timeout = 60000)
  TestDescriptor(filePath = "", status = 400)
  public fun fileEmptyRequest() {
    doTest()
  }

  Test(timeout = 60000)
  TestDescriptor(filePath = "foo.txt", relativeToProject = true, status = 200)
  public fun relativeToProject() {
    doTest()
  }

  Test(timeout = 60000)
  TestDescriptor(filePath = "foo.txt", relativeToProject = true, line = 1, status = 200)
  public fun relativeToProjectWithLine() {
    doTest()
  }

  Test(timeout = 60000)
  TestDescriptor(filePath = "foo.txt", relativeToProject = true, line = 1, column = 13, status = 200)
  public fun relativeToProjectWithLineAndColumn() {
    doTest()
  }

  @TestDescriptor(filePath = "fileInExcludedDir.txt", excluded = true, status = 200)
  @Test(timeout = 60000)
  fun inExcludedDir() {
    doTest()
  }

  Test(timeout = 60000)
  TestDescriptor(filePath = "bar/42/foo.txt", doNotCreate = true, status = 404)
  public fun relativeNonExistent() {
    doTest()
  }

  Test(timeout = 60000)
  TestDescriptor(filePath = "_tmp_", doNotCreate = true, status = 404)
  public fun absoluteNonExistent() {
    doTest()
  }

  Test(timeout = 60000)
  TestDescriptor(filePath = "_tmp_", status = 200)
  public fun absolute() {
    doTest()
  }

  private fun doTest() {
    val serviceUrl = "http://localhost:" + BuiltInServerManager.getInstance().getPort() + "/api/file"
    var url = serviceUrl + (if (manager.filePath == null) "" else ("/${manager.filePath}"))
    val line = manager.annotation?.line ?: -1
    if (line != -1) {
      url += ":$line"
    }
    val column = manager.annotation?.column ?: -1
    if (column != -1) {
      url += ":$column"
    }

    var connection = URL(url).openConnection() as HttpURLConnection
    val expectedStatus = HttpResponseStatus.valueOf(manager.annotation?.status ?: 200)
    assertThat(HttpResponseStatus.valueOf(connection.getResponseCode())).isEqualTo(expectedStatus)

    connection = URL("$serviceUrl?file=${manager.filePath ?: ""}&line=$line&column=$column").openConnection() as HttpURLConnection
    assertThat(HttpResponseStatus.valueOf(connection.getResponseCode())).isEqualTo(expectedStatus)

    connection = URL("$serviceUrl").openConnection() as HttpURLConnection
    connection.setRequestMethod("POST")
    connection.setDoOutput(true)
    val writer = JsonWriter(OutputStreamWriter(BufferedOutputStream(connection.getOutputStream()), CharsetToolkit.UTF8_CHARSET))
    writer.beginObject()
    writer.name("file").value(manager.filePath)
    writer.name("line").value(line)
    writer.name("column").value(column)
    writer.endObject()
    writer.close()
    assertThat(HttpResponseStatus.valueOf(connection.getResponseCode())).isEqualTo(expectedStatus)
  }
}
