// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon

import com.intellij.codeInsight.daemon.impl.EditorTracker
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.awaitPendingNavigationAndEditorOpenIfEnabled
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.ThrowableRunnable
import org.jetbrains.annotations.TestOnly
import java.util.function.Function
import java.util.function.Supplier

/**
 * A [DaemonAnalyzerFixture] for a test that uses only the production methods of the daemon, as [ProductionDaemonAnalyzerTestCase] gives it.
 * The test waits for the daemon itself, for example with [com.intellij.codeInsight.daemon.impl.TestDaemonCodeAnalyzerImpl.waitHighlighting].
 *
 * Put each test body in [runTest]. A `configureBy*` call makes its editors the active editors of the daemon before the EDT is free again,
 * as the base does it on the EDT.
 *
 * Create it with [productionDaemonAnalyzerFixture].
 */
@TestOnly
open class ProductionDaemonAnalyzerFixture(
  context: Context,
  testDataPath: String,
  localInspectionTools: Array<LocalInspectionTool>,
  private val jdk: Supplier<Sdk>?,
  private val languageLevel: LanguageLevel?,
) : DaemonAnalyzerFixture(context, testDataPath, localInspectionTools) {
  override fun getTestProjectJdk(): Sdk = jdk?.get() ?: super.getTestProjectJdk()

  override fun getProjectLanguageLevel(): LanguageLevel = languageLevel ?: super.getProjectLanguageLevel()

  override fun existingFileConfigured() {
    super.existingFileConfigured()
    EditorTracker.getInstance(project).setActiveEditorsInTests(listOf(editor!!))
  }

  override fun filesConfigured() {
    super.filesConfigured()
    EditorTracker.getInstance(project).setActiveEditorsInTests(EditorFactory.getInstance().allEditors.toList())
  }

  /**
   * Runs [test] on the thread of the caller with the daemon update by timer on, a zero reparse delay and the daemon debug mode,
   * as each test of [ProductionDaemonAnalyzerTestCase] runs.
   * [DaemonAnalyzerFixture.doHighlighting] fails in [test].
   */
  fun runTest(test: ThrowableRunnable<Throwable>) {
    ProductionDaemonTestUtil.runTestInProduction(isStressTest, daemonCodeAnalyzer, test)
  }

  /**
   * Makes [editors] the active editors of the daemon. Runs on the EDT.
   */
  fun setActiveEditors(vararg editors: Editor) {
    val list = editors.toList()
    runInEdtAndWait { EditorTracker.getInstance(project).setActiveEditorsInTests(list) }
  }

  /**
   * Waits on the EDT until the daemon finishes [file], and returns the highlighting of [file] with [minSeverity] or a higher severity.
   */
  fun waitHighlighting(minSeverity: HighlightSeverity): List<HighlightInfo> {
    val psiFile = file!!
    return runInEdtAndGet { testDaemonCodeAnalyzer.waitHighlighting(psiFile, minSeverity) }
  }

  /**
   * Waits on the EDT as [waitHighlighting] does, and restarts the wait after each cancellation of the daemon.
   */
  fun waitHighlightingSurviveCancellations(minSeverity: HighlightSeverity): List<HighlightInfo> {
    val psiFile = file!!
    return runInEdtAndGet { testDaemonCodeAnalyzer.waitHighlightingSurviveCancellations(psiFile, minSeverity) }
  }

  /**
   * Runs the editor action [actionId] in [editor] in a command, as `executeAction` of
   * [com.intellij.testFramework.LightPlatformCodeInsightTestCase] does.
   */
  fun executeAction(actionId: String, editor: Editor) {
    runInEdtAndWait {
      CommandProcessor.getInstance().executeCommand(project, { EditorTestUtil.executeAction(editor, actionId, true) }, "", null, editor.document)
      awaitPendingNavigationAndEditorOpenIfEnabled(project)
    }
  }

  fun caretLeft() {
    executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_LEFT)
  }

  fun caretRight() {
    executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT)
  }

  fun caretRight(editor: Editor) {
    executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT, editor)
  }

  fun delete() {
    executeAction(IdeActions.ACTION_EDITOR_DELETE)
  }

  fun ctrlW() {
    executeAction(IdeActions.ACTION_EDITOR_SELECT_WORD_AT_CARET)
  }

  fun backspace(editor: Editor) {
    executeAction(IdeActions.ACTION_EDITOR_BACKSPACE, editor)
  }

  /**
   * Undoes the last command in the text editor of [editor].
   */
  fun undo() {
    runInEdtAndWait {
      UndoManager.getInstance(project).undo(TextEditorProvider.getInstance().getTextEditor(editor!!))
    }
  }

  companion object {
    private const val ML_INDEXING_PROPERTY = "intellij.ml.llm.embeddings.start.indexing.on.project.open"

    /**
     * Creates a [ProductionDaemonAnalyzerFixture] with a new project for each test. Declare it as an instance field.
     *
     * The fixture sets the `intellij.ml.llm.embeddings.start.indexing.on.project.open` system property to `false` before it creates the project,
     * and restores the property after the test.
     * When [jdk] or [languageLevel] is `null`, the module gets the default of [com.intellij.codeInsight.HeavyJavaCodeInsightFixture].
     * See [DaemonAnalyzerFixture.daemonAnalyzerFixture] for the other parameters.
     */
    @TestOnly
    @JvmStatic
    @JvmOverloads
    fun productionDaemonAnalyzerFixture(
      testDataPath: String,
      localInspectionTools: Supplier<Array<LocalInspectionTool>> = Supplier { LocalInspectionTool.EMPTY_ARRAY },
      jdk: Supplier<Sdk>? = null,
      languageLevel: LanguageLevel? = null,
      testDataProjectDir: Function<String, String>? = null,
    ): TestFixture<ProductionDaemonAnalyzerFixture> =
      productionDaemonAnalyzerSubFixture("productionDaemonAnalyzerFixture", testDataProjectDir) { context ->
        ProductionDaemonAnalyzerFixture(context, testDataPath, localInspectionTools.get(), jdk, languageLevel)
      }

    /**
     * Creates a sub-fixture of [ProductionDaemonAnalyzerFixture] with [create], with a new project for each test.
     * It sets the system property as [productionDaemonAnalyzerFixture] does.
     */
    @TestOnly
    @JvmStatic
    fun <T : ProductionDaemonAnalyzerFixture> productionDaemonAnalyzerSubFixture(
      debugName: String,
      testDataProjectDir: Function<String, String>?,
      create: (Context) -> T,
    ): TestFixture<T> =
      testFixture(debugName) {
        val oldValue = System.setProperty(ML_INDEXING_PROPERTY, "false")
        val fixture = try {
          heavyJavaCodeInsightSubFixture(debugName, testDataProjectDir, create).init()
        }
        catch (e: Throwable) {
          restoreProperty(oldValue)
          throw e
        }
        initialized(fixture) {
          restoreProperty(oldValue)
        }
      }

    private fun restoreProperty(oldValue: String?) {
      if (oldValue == null) {
        System.clearProperty(ML_INDEXING_PROPERTY)
      }
      else {
        System.setProperty(ML_INDEXING_PROPERTY, oldValue)
      }
    }
  }
}
