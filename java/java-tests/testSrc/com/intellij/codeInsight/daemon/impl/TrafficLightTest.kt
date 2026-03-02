// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl

import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.lang.LanguageAnnotators
import com.intellij.lang.LanguageExtensionPoint
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.ex.EditorMarkupModel
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.util.application
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

  fun `test error counter after adding and removing file level annotation`() {
    class AnnotatorRegisteringFileLevelErrorIfEmptyFile : Annotator {
      override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element is PsiFile && element.textLength == 0) {
          holder.newAnnotation(HighlightSeverity.ERROR, "Empty file").fileLevel().create()
        }
      }
    }
    val annotator = AnnotatorRegisteringFileLevelErrorIfEmptyFile()
    val keyedLazyInstance = LanguageExtensionPoint<Annotator>("JAVA", annotator.javaClass.name, PluginManagerCore.getPlugin(PluginManagerCore.CORE_ID)!!)
    LanguageAnnotators.EP_NAME.point.registerExtension(keyedLazyInstance, testRootDisposable)

    doTrafficRendererTest(
      listOf(
        "class Main {}", // no error
        "",              // file level error is added by AnnotatorRegisteringFileLevelErrorIfEmptyFile
        "class Main {}"  // no error again
        ),
      listOf(HighlightSeverity.ERROR to 0),
    )
  }

  private fun doTrafficRendererTest(
    @Language("JAVA") text: String,
    vararg expectedSeverities: SeverityValue,
  ) {
    doTrafficRendererTest(listOf(text), listOf(*expectedSeverities))
  }

  private fun doTrafficRendererTest(
    fileTextSnapshots: List<String>,
    expectedSeverities: List<SeverityValue>,
  ) {
    require(fileTextSnapshots.isNotEmpty())

    configureByText(JavaFileType.INSTANCE, fileTextSnapshots.first())

    val editorMarkupModel = editor.markupModel as EditorMarkupModel

    editorMarkupModel.isErrorStripeVisible = true

    doHighlighting()

    fileTextSnapshots.drop(1).forEach {
      application.runWriteAction {
        editor.document.setText(it)
      }
      doHighlighting()
    }

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