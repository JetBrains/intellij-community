package org.jetbrains.ide

import io.netty.handler.codec.http.HttpResponseStatus
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

import java.io.IOException

import org.hamcrest.CoreMatchers.equalTo
import org.jetbrains.ide.TestManager.TestDescriptor
import org.junit.Assert.assertThat

public class RestApiTest {
  private val manager = TestManager()

  Rule
  public val chain: RuleChain = RuleChain.outerRule(FixtureRule()).around(manager)

  Test
  throws(javaClass<IOException>())
  public fun fileEmptyRequest() {
    val response = Request.Get(getFileApiUrl()).execute()
    assertThat(HttpResponseStatus.valueOf(response.returnResponse().getStatusLine().getStatusCode()), equalTo(HttpResponseStatus.BAD_REQUEST))
  }

  Test
  TestDescriptor(filePath = "foo.java", relativeToProject = true)
  throws(javaClass<IOException>())
  public fun relativeToProjectPath() {
    val response = Request.Get(getFileApiUrl()).execute()
    assertThat(HttpResponseStatus.valueOf(response.returnResponse().getStatusLine().getStatusCode()), equalTo(HttpResponseStatus.OK))
  }

  private fun getFileApiUrl(): String {
    return "http://localhost:" + BuiltInServerManager.getInstance().getPort() + "/api/file" + (if (manager.filePath == null) "" else ("/" + manager.filePath))
  }
}
