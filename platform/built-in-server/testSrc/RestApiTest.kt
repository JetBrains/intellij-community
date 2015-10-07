package org.jetbrains.ide

import com.google.gson.stream.JsonWriter
import io.netty.handler.codec.http.HttpResponseStatus
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.ide.TestManager.TestDescriptor
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL

private class RestApiTest : BuiltInServerTestCase() {
  override val urlPathPrefix = "/api/file"

  @Test
  @TestDescriptor(filePath = "", status = 400)
  fun fileEmptyRequest() {
    doTest()
  }

  @Test
  @TestDescriptor(filePath = "foo.txt", relativeToProject = true, status = 200)
  fun relativeToProject() {
    doTest()
  }

  @Test
  @TestDescriptor(filePath = "foo.txt", relativeToProject = true, line = 1, status = 200)
  fun relativeToProjectWithLine() {
    doTest()
  }

  @Test
  @TestDescriptor(filePath = "foo.txt", relativeToProject = true, line = 1, column = 13, status = 200)
  fun relativeToProjectWithLineAndColumn() {
    doTest()
  }

  @TestDescriptor(filePath = "fileInExcludedDir.txt", excluded = true, status = 200)
  @Test
  fun inExcludedDir() {
    doTest()
  }

  @Test
  @TestDescriptor(filePath = "bar/42/foo.txt", doNotCreate = true, status = 404)
  fun relativeNonExistent() {
    doTest()
  }

  @Test
  @TestDescriptor(filePath = "_tmp_", doNotCreate = true, status = 404)
  fun absoluteNonExistent() {
    doTest()
  }

  @Test
  @TestDescriptor(filePath = "_tmp_", status = 200)
  fun absolute() {
    doTest()
  }

  override fun check(serviceUrl: String, expectedStatus: HttpResponseStatus) {
    val line = manager.annotation?.line ?: -1
    val column = manager.annotation?.column ?: -1

    var connection = URL("$serviceUrl?file=${manager.filePath ?: ""}&line=$line&column=$column").openConnection() as HttpURLConnection
    assertThat(HttpResponseStatus.valueOf(connection.responseCode)).isEqualTo(expectedStatus)

    connection = URL("$serviceUrl").openConnection() as HttpURLConnection
    connection.requestMethod = "POST"
    connection.doOutput = true
    JsonWriter(connection.outputStream.bufferedWriter()).use {
      it.beginObject()
      it.name("file").value(manager.filePath)
      it.name("line").value(line)
      it.name("column").value(column)
      it.endObject()
    }
    assertThat(HttpResponseStatus.valueOf(connection.responseCode)).isEqualTo(expectedStatus)
  }
}

