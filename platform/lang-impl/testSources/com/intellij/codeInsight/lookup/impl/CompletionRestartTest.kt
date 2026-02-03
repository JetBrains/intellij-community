// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.lookup.impl

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.completion.CompletionContributor.EP
import com.intellij.codeInsight.completion.CompletionPhase.*
import com.intellij.codeInsight.completion.impl.CompletionServiceImpl
import com.intellij.codeInsight.editorActions.CompletionAutoPopupHandler
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.lang.Language
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.openapi.extensions.LoadingOrder
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.DumbAware
import com.intellij.testFramework.TestApplicationManager
import com.intellij.testFramework.TestDataProvider
import com.intellij.testFramework.TestModeFlags
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.common.ThreadUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.EditorTestFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds

@TestApplication
internal class CompletionRestartTest {
  private companion object {
    val projectFixture = projectFixture(/*openAfterCreation = true*/)
    val sourceRootFixture = projectFixture.moduleFixture().sourceRootFixture()

    @JvmStatic
    @BeforeAll
    fun setUp(@TestDisposable disposable: Disposable) {
      registerCompletionContributor(SlowContributor::class.java, PlainTextLanguage.INSTANCE, disposable, LoadingOrder.FIRST)
      TestModeFlags.set(CompletionAutoPopupHandler.ourTestingAutopopup, true, disposable)
      CodeCompletionHandlerBase.setAutoInsertTimeout(syncCompletionTimeout.inWholeMilliseconds.toInt(), disposable)
    }

    @Suppress("unused")
    private val testDataContextInstaller = testFixture {
      val project = projectFixture.init()
      val application = TestApplicationManager.getInstance()
      application.setDataProvider(TestDataProvider(project))

      initialized(Unit) {
        application.setDataProvider(null)
      }
    }
  }

  private val psiFileFixture = sourceRootFixture.psiFileFixture("a.txt", "Hello, World!")
  private val editorFixture = psiFileFixture.editorFixture()
  private val editorTestFixture by editorFixture.editorTestFixture()

  private val editor by editorFixture
  private val project by projectFixture

  @Test
  fun `test completion restarts if user presses RightArrow if items are being computed`() =
    timeoutRunBlocking<Unit>(context = Dispatchers.Default) {
      launch {
        complete(CompletionType.BASIC, 1)
      }

      launch {
        delay(rightArrowDelay)
        withContext(Dispatchers.EDT) {
          check(editorTestFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT))
        }
      }

      waitForCompletionToFinish()

      val lookup = requireNotNull(LookupManager.getInstance(project).activeLookup)
      val items = lookup.items
      assert(items.size == 1) {
        """
          It's expected that SlowContributor will return one item that matches the current prefix. It did not happen for some reason.
          See CompletionProgressIndicator#isRestartRequired
        """.trimIndent()
      }

      val lookupString = items.single().lookupString
      assertThat(lookupString).isEqualTo("H")
    }

  suspend fun complete(type: CompletionType, invocationCount: Int) {
    withContext(Dispatchers.EDT) {
      CommandProcessor.getInstance().executeCommand(project, Runnable {
        val handler = CodeCompletionHandlerBase(type)
        handler.invokeCompletion(project, editor, invocationCount)
      }, null, null, editor.document)
    }
  }
}

private class SlowContributor : CompletionContributor(), DumbAware {
  override fun fillCompletionVariants(
    parameters: CompletionParameters,
    result: CompletionResultSet,
  ) {
    runBlockingCancellable {
      delay(rightArrowDelay + 100.milliseconds) // we want to finish completion after we move caret
    }

    ProgressManager.checkCanceled()

    val currentPrefix = result.prefixMatcher.prefix

    result.addElement(LookupElementBuilder.create(currentPrefix))
  }
}

private fun registerCompletionContributor(
  contributor: Class<*>,
  language: Language,
  parentDisposable: Disposable,
  order: LoadingOrder,
) {
  val pluginDescriptor = DefaultPluginDescriptor("registerCompletionContributor")
  val extension = CompletionContributorEP(language.id, contributor.getName(), pluginDescriptor)
  EP.point.registerExtension(extension, order, parentDisposable)
}

private fun TestFixture<Editor>.editorTestFixture(): TestFixture<EditorTestFixture> = testFixture {
  val editor = init()
  val fixture = readAction {
    val file = FileDocumentManager.getInstance().getFile(editor.document)!!
    EditorTestFixture(editor.project!!, editor, file)
  }
  initialized(fixture) {}
}

suspend fun waitForCompletionToFinish() {
  waitPhase { phase ->
    !(phase is CommittingDocuments ||
      phase is Synchronous ||
      phase is BgCalculation)
  }
}

private suspend fun waitPhase(condition: (CompletionPhase) -> Boolean) {
  repeat(1000) { j ->
    // in EDT to avoid getting the phase in the middle of a complex EDT operation that changes the phase several times
    val phase = withContext(Dispatchers.EDT) { CompletionServiceImpl.completionPhase }
    if (condition(phase)) {
      return
    }
    if (j >= 400 && j % 100 == 0) {
      println("Free memory: ${Runtime.getRuntime().freeMemory()} of ${Runtime.getRuntime().totalMemory()}\n")
      ThreadUtil.printThreadDump()
      println("\n\n----------------------------\n\n")
    }

    delay(10)
  }

  throw IllegalStateException("Too long completion: ${CompletionServiceImpl.completionPhase}")
}

private val syncCompletionTimeout = 400.milliseconds
private val rightArrowDelay = 400.milliseconds