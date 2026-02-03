// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl

import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.ex.EditorMarkupModel
import kotlinx.coroutines.runBlocking
import org.intellij.lang.annotations.Language

class TrafficLightTest : DaemonAnalyzerTestCase() {
  fun testDaemonAnalyzerNoErrors() {
    doTrafficRendererTest(
      text = """
        class Main {
          public static void main(String[] args) {
            System.out.println("Hello, world!");
          }
        }
      """.trimIndent(),
      HighlightSeverity.ERROR to 0,
      HighlightSeverity.WARNING to 0,
      HighlightSeverity.INFORMATION to 0,
    )
  }

  fun testDaemonAnalyzerResult() {
    doTrafficRendererTest(
      text = """
        class Main {
          public static void main(String[] args) {
            System.out.println("Hello, world!");
            MissingIdentifier
          }
        }
      """.trimIndent(),
      HighlightSeverity.ERROR to 1,
      HighlightSeverity.WARNING to 0,
      HighlightSeverity.INFORMATION to 0,
    )
  }

  private fun doTrafficRendererTest(
    @Language("JAVA") text: String,
    vararg expectedSeverities: SeverityValue,
  ) {
    configureByText(JavaFileType.INSTANCE, text)

    val editorMarkupModel = editor.markupModel as EditorMarkupModel

    editorMarkupModel.isErrorStripeVisible = true

    doHighlighting()

    val trafficLightRenderer = editorMarkupModel.errorStripeRenderer as TrafficLightRenderer
    val status = runBlocking {
      readAction {
        trafficLightRenderer.daemonCodeAnalyzerStatus
      }
    }

    assertTrue(status.errorAnalyzingFinished)

    val severityRegistrar = SeverityRegistrar.getSeverityRegistrar(project)
    val errorCount = trafficLightRenderer.errorCounts
    expectedSeverities.forEach { severity ->
      val severityIndex = severityRegistrar.getSeverityIdx(severity.severity)
      assertEquals(severity.value, errorCount[severityIndex])
    }
  }

  private class SeverityValue(
    val severity: HighlightSeverity,
    val value: Int,
  )

  private infix fun HighlightSeverity.to(value: Int) = SeverityValue(this, value)
}