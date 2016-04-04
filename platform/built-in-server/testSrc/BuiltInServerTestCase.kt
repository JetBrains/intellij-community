package org.jetbrains.ide

import com.intellij.testFramework.DisposeModulesRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.TemporaryDirectory
import io.netty.handler.codec.http.HttpResponseStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.ClassRule
import org.junit.Rule
import org.junit.rules.Timeout
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

internal abstract class BuiltInServerTestCase {
  companion object {
    @JvmField
    @ClassRule val projectRule = ProjectRule()
  }

  protected val tempDirManager = TemporaryDirectory()
  protected val manager = TestManager(projectRule, tempDirManager)

  private val ruleChain = RuleChain(
    tempDirManager,
    Timeout(60, TimeUnit.SECONDS),
    manager,
    DisposeModulesRule(projectRule))
  @Rule fun getChain() = ruleChain

  protected open val urlPathPrefix = ""

  protected fun doTest(filePath: String? = manager.filePath, additionalCheck: ((connection: HttpURLConnection) -> Unit)? = null) {
    val serviceUrl = "http://localhost:${BuiltInServerManager.getInstance().port}$urlPathPrefix"
    var url = serviceUrl + (if (filePath == null) "" else ("/$filePath"))
    val line = manager.annotation?.line ?: -1
    if (line != -1) {
      url += ":$line"
    }
    val column = manager.annotation?.column ?: -1
    if (column != -1) {
      url += ":$column"
    }

    val connection = URL(url).openConnection() as HttpURLConnection
    val expectedStatus = HttpResponseStatus.valueOf(manager.annotation?.status ?: 200)
    assertThat(HttpResponseStatus.valueOf(connection.responseCode)).isEqualTo(expectedStatus)

    check(serviceUrl, expectedStatus)
    if (additionalCheck != null) {
      additionalCheck(connection)
    }
  }

  protected open fun check(serviceUrl: String, expectedStatus: HttpResponseStatus) {
  }
}