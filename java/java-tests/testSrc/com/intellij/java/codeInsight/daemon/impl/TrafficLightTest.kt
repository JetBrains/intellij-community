// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.impl

import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase
import com.intellij.codeInsight.daemon.impl.SeverityRegistrar
import com.intellij.codeInsight.daemon.impl.TrafficLightRenderer
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.lang.LanguageAnnotators
import com.intellij.lang.LanguageExtensionPoint
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.ex.EditorMarkupModel
import com.intellij.openapi.editor.impl.EditorMarkupModelImpl
import com.intellij.openapi.editor.markup.AnalyzerStatus
import com.intellij.openapi.editor.markup.ErrorStripeRenderer
import com.intellij.openapi.editor.markup.UIController
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.util.TimeoutUtil
import com.intellij.util.application
import com.intellij.util.ui.EmptyIcon
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

  fun `test installing a renderer updates the inspection widget status`() {
    configureByText(JavaFileType.INSTANCE, "class Main {}")
    val markupModel = editor.markupModel as EditorMarkupModelImpl
    markupModel.isErrorStripeVisible = true
    doHighlighting()
    PlatformTestUtil.waitWithEventsDispatching(
      "The daemon must install its renderer and publish its status",
      { markupModel.errorStripeRenderer is TrafficLightRenderer && !markupModel.getCurrentStatus().isEmpty() },
      10,
    )
    // The daemon repaints the widget with a delay. Let these repaints finish, so that only the install below can change the status.
    repeat(5) {
      PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
      TimeoutUtil.sleep(100)
    }

    val title = "Installed renderer"
    markupModel.setErrorStripeRenderer(ErrorStripeRenderer { AnalyzerStatus(EmptyIcon.ICON_16, title, "", UIController.EMPTY) })

    PlatformTestUtil.waitWithEventsDispatching(
      "The inspection widget must take the status of the installed renderer",
      { markupModel.getCurrentStatus().title == title },
      10,
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
    val errorCountForFus = trafficLightRenderer.errorCountsForFus
    expectedSeverities.forEach { severity ->
      val severityIndex = severityRegistrar.getSeverityIdx(severity.severity)
      assertEquals(severity.value, errorCount[severityIndex])
      // the test uses one code insight context, so the count of all contexts must be the same
      assertEquals(severity.value, errorCountForFus[severityIndex])
    }
  }

  private class SeverityValue(
    val severity: HighlightSeverity,
    val value: Int,
  )

  private infix fun HighlightSeverity.to(value: Int) = SeverityValue(this, value)
}