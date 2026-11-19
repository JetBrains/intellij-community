// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion

import com.intellij.codeInsight.daemon.DaemonAnalyzerFixture
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil
import com.intellij.testFramework.common.runAll
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.runInEdtAndWait
import org.jetbrains.annotations.TestOnly
import java.util.function.Supplier

/**
 * A [DaemonAnalyzerFixture] for a completion test, as [JavaCompletionTestCase] gives it.
 *
 * [configureByFile] runs the completion after it opens the file. The lookup items are in [items].
 *
 * Create it with [heavyJavaCompletionFixture].
 */
@TestOnly
open class HeavyJavaCompletionFixture(
  context: Context,
  testDataPath: String,
  localInspectionTools: Array<LocalInspectionTool>,
) : DaemonAnalyzerFixture(context, testDataPath, localInspectionTools) {
  /**
   * The prefix of the first lookup item after the last completion, or an empty string when no lookup shows.
   */
  var prefix: String? = null
    private set

  /**
   * The lookup items after the last completion, or `null` when no lookup shows.
   */
  var items: Array<LookupElement>? = null
    private set

  /**
   * The type of the completion of [complete].
   */
  var type: CompletionType = CompletionType.BASIC

  override fun tearDown() {
    runAll(
      {
        items = null
        runInEdtAndWait { LookupManager.hideActiveLookup(project) }
      },
      { super.tearDown() },
    )
  }

  /**
   * Opens the file [filePath] of the test data, as the base does, and runs the completion once.
   */
  override fun configureByFile(filePath: String): VirtualFile {
    val result = super.configureByFile(filePath)
    complete()
    return result
  }

  /**
   * Opens the file [filePath] of the test data without the completion.
   */
  fun configureByFileNoCompletion(filePath: String): VirtualFile = super.configureByFile(filePath)

  @JvmOverloads
  fun complete(time: Int = 1) {
    runInEdtAndWait {
      PsiDocumentManager.getInstance(project).commitAllDocuments()
      val currentEditor = editor!!
      CodeCompletionHandlerBase(type).invokeCompletion(project, InjectedLanguageUtil.getEditorForInjectedLanguageNoCommit(currentEditor, file), time)

      val lookup = LookupManager.getActiveLookup(currentEditor) as LookupImpl?
      items = lookup?.items?.toTypedArray()
      prefix = lookup?.itemPattern(lookup.items[0]) ?: ""
    }
  }

  companion object {
    /**
     * Creates a [HeavyJavaCompletionFixture] with a new project for each test. Declare it as an instance field.
     * See [DaemonAnalyzerFixture.daemonAnalyzerFixture] for the parameters.
     */
    @TestOnly
    @JvmStatic
    @JvmOverloads
    fun heavyJavaCompletionFixture(
      testDataPath: String,
      localInspectionTools: Supplier<Array<LocalInspectionTool>> = Supplier { LocalInspectionTool.EMPTY_ARRAY },
    ): TestFixture<HeavyJavaCompletionFixture> =
      heavyJavaCodeInsightSubFixture("heavyJavaCompletionFixture") { context ->
        HeavyJavaCompletionFixture(context, testDataPath, localInspectionTools.get())
      }
  }
}
