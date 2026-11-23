// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.impl

import com.intellij.JavaTestUtil
import com.intellij.codeInsight.daemon.ProductionDaemonAnalyzerFixture
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.openapi.editor.EditorMouseHoverPopupManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.impl.source.PostprocessReformattingAspect
import com.intellij.testFramework.common.runAll
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.DocumentUtil
import java.util.function.Supplier

/**
 * A [ProductionDaemonAnalyzerFixture] for the daemon tests that type in an editor and wait for the daemon.
 *
 * After the test, the fixture reloads the document of [editor] from the disk and runs the postponed formatting.
 * The hover popup manager starts before the test, so the leak check does not report its listeners.
 *
 * Create it with [daemonRespondToChangesFixture].
 */
class DaemonRespondToChangesFixture(
  context: Context,
  localInspectionTools: Array<LocalInspectionTool>,
  jdk: Supplier<Sdk>?,
  languageLevel: LanguageLevel?,
) : ProductionDaemonAnalyzerFixture(context, JavaTestUtil.getJavaTestDataPath(), localInspectionTools, jdk, languageLevel) {
  override fun tearDown() {
    runAll(
      {
        val document = editor?.document
        if (document != null) {
          runInEdtAndWait { FileDocumentManager.getInstance().reloadFromDisk(document) }
        }
      },
      {
        runInEdtAndWait {
          DocumentUtil.writeInRunUndoTransparentAction {
            PsiDocumentManager.getInstance(project).commitAllDocuments()
            PostprocessReformattingAspect.getInstance(project).doPostponedFormatting()
          }
        }
      },
      { super.tearDown() },
    )
  }

  companion object {
    /**
     * The internal JDK of the IDE.
     */
    @JvmField
    val INTERNAL_JDK: Supplier<Sdk> = Supplier {
      @Suppress("DEPRECATION")
      JavaAwareProjectJdkTableImpl.getInstanceEx().internalJdk
    }

    /**
     * Creates a [DaemonRespondToChangesFixture] with a new project for each test. Declare it as an instance field.
     * By default, the module gets the internal JDK and the language level 11.
     * When [jdk] is `null`, the module gets the default mock JDK.
     */
    @JvmStatic
    @JvmOverloads
    fun daemonRespondToChangesFixture(
      localInspectionTools: Supplier<Array<LocalInspectionTool>> = Supplier { LocalInspectionTool.EMPTY_ARRAY },
      jdk: Supplier<Sdk>? = INTERNAL_JDK,
      languageLevel: LanguageLevel = LanguageLevel.JDK_11,
    ): TestFixture<DaemonRespondToChangesFixture> =
      productionDaemonAnalyzerSubFixture("daemonRespondToChangesFixture", null) { context ->
        // treat the listeners that it adds as no leaks
        EditorMouseHoverPopupManager.getInstance()
        DaemonRespondToChangesFixture(context, localInspectionTools.get(), jdk, languageLevel)
      }
  }
}
