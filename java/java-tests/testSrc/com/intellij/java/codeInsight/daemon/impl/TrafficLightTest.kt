// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.impl

import com.intellij.JavaTestUtil
import com.intellij.codeInsight.daemon.DaemonAnalyzerFixture.Companion.daemonAnalyzerFixture
import com.intellij.codeInsight.daemon.impl.SeverityRegistrar
import com.intellij.codeInsight.daemon.impl.TrafficLightRenderer
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.lang.LanguageAnnotators
import com.intellij.lang.LanguageExtensionPoint
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.EditorMarkupModel
import com.intellij.openapi.editor.impl.EditorMarkupModelImpl
import com.intellij.openapi.editor.markup.AnalyzerStatus
import com.intellij.openapi.editor.markup.ErrorStripeRenderer
import com.intellij.openapi.editor.markup.UIController
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.TimeoutUtil
import com.intellij.util.ui.EmptyIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@TestApplication
class TrafficLightTest {
  private val fixture by daemonAnalyzerFixture(JavaTestUtil.getJavaTestDataPath())

  @Test
  fun testDaemonAnalyzerNoErrors(): Unit = timeoutRunBlocking {
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

  @Test
  fun testDaemonAnalyzerResult(): Unit = timeoutRunBlocking {
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

  @Test
  fun `test error counter after adding and removing file level annotation`(): Unit = timeoutRunBlocking {
    class AnnotatorRegisteringFileLevelErrorIfEmptyFile : Annotator {
      override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element is PsiFile && element.textLength == 0) {
          holder.newAnnotation(HighlightSeverity.ERROR, "Empty file").fileLevel().create()
        }
      }
    }
    val annotator = AnnotatorRegisteringFileLevelErrorIfEmptyFile()
    val keyedLazyInstance = LanguageExtensionPoint<Annotator>("JAVA", annotator.javaClass.name, PluginManagerCore.getPlugin(PluginManagerCore.CORE_ID)!!)
    LanguageAnnotators.EP_NAME.point.registerExtension(keyedLazyInstance, fixture.testRootDisposable)

    doTrafficRendererTest(
      listOf(
        "class Main {}", // no error
        "",              // file level error is added by AnnotatorRegisteringFileLevelErrorIfEmptyFile
        "class Main {}"  // no error again
        ),
      listOf(HighlightSeverity.ERROR to 0),
    )
  }

  @Test
  fun `test installing a renderer updates the inspection widget status`(): Unit = timeoutRunBlocking {
    fixture.configureByText(JavaFileType.INSTANCE, "class Main {}")
    val markupModel = fixture.editor!!.markupModel as EditorMarkupModelImpl
    withContext(Dispatchers.EDT) {
      markupModel.isErrorStripeVisible = true
    }
    fixture.doHighlighting()
    withContext(Dispatchers.EDT) {
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
  }

  @Test
  fun `test the inspection widget reserves its height only in an editor with the error stripe`(): Unit = timeoutRunBlocking {
    fixture.configureByText(JavaFileType.INSTANCE, "class Main {}")
    val editor = fixture.editor!!
    withContext(Dispatchers.EDT) {
      val markupModel = editor.markupModel as EditorMarkupModelImpl
      val statusComponent = ((editor as EditorEx).scrollPane as JBScrollPane).statusComponent!!

      markupModel.isErrorStripeVisible = false
      assertEquals(0, statusComponent.preferredSize.height)

      markupModel.isErrorStripeVisible = true
      assertEquals(JBUIScale.scale(18), statusComponent.preferredSize.height)

      markupModel.setTrafficLightIconVisible(false)
      assertEquals(0, statusComponent.preferredSize.height)
    }
  }

  private suspend fun doTrafficRendererTest(
    @Language("JAVA") text: String,
    vararg expectedSeverities: SeverityValue,
  ) {
    doTrafficRendererTest(listOf(text), listOf(*expectedSeverities))
  }

  private suspend fun doTrafficRendererTest(
    fileTextSnapshots: List<String>,
    expectedSeverities: List<SeverityValue>,
  ) {
    require(fileTextSnapshots.isNotEmpty())

    fixture.configureByText(JavaFileType.INSTANCE, fileTextSnapshots.first())
    val editor = fixture.editor!!

    val editorMarkupModel = editor.markupModel as EditorMarkupModel

    withContext(Dispatchers.EDT) {
      editorMarkupModel.isErrorStripeVisible = true
    }

    fixture.doHighlighting()

    fileTextSnapshots.drop(1).forEach {
      edtWriteAction {
        editor.document.setText(it)
      }
      fixture.doHighlighting()
    }

    val trafficLightRenderer = editorMarkupModel.errorStripeRenderer as TrafficLightRenderer
    val status = readAction {
      trafficLightRenderer.daemonCodeAnalyzerStatus
    }

    assertTrue(status.errorAnalyzingFinished)

    val severityRegistrar = SeverityRegistrar.getSeverityRegistrar(fixture.project)
    val errorCount = readAction { trafficLightRenderer.errorCounts }
    val errorCountForFus = readAction { trafficLightRenderer.errorCountsForFus }
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