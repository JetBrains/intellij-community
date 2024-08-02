// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl

import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase
import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase.CanChangeDocumentDuringHighlighting
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.use
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElementVisitor
import com.intellij.testFramework.*
import java.util.*

@SkipSlowTestLocally
@CanChangeDocumentDuringHighlighting
class LocalInspectionsInDumbModeTest : DaemonAnalyzerTestCase() {

  @Throws(Exception::class)
  override fun setUp() {
    super.setUp()
    DaemonProgressIndicator.setDebug(true)
    enableInspectionTools(project, testRootDisposable, DumbInspection(), SmartInspection())
  }

  private val daemon get() = DaemonCodeAnalyzer.getInstance(project) as DaemonCodeAnalyzerImpl

  fun testLocalInspectionInDumbMode() {
    configureByText(JavaFileType.INSTANCE, """
      // comment
    """)

    // only dumb inspection runs in dumb mode
    val dumbInfos = doHighlightingInDumbMode()
    assertOneElement(dumbInfos)
    assertHighlightInfo(dumbInfos, "Dumb0")

    // dumb and smart inspections run in dumb mode
    val smartInfos = doHighlighting()
    assertSize(2, smartInfos)
    assertHighlightInfo(smartInfos, "Dumb1")
    assertHighlightInfo(smartInfos, "Smart0")

    // only dumb inspection runs in dumb mode, but the results of smart inspection are frozen from the previous run
    val dumbInfos2 = doHighlightingInDumbMode()
    assertSize(2, smartInfos)
    assertHighlightInfo(dumbInfos2, "Dumb2")
    assertHighlightInfo(dumbInfos2, "Smart0")
  }

  fun testLocalInspectionsInSmartModeThenInDumbMode() {
    configureByText(JavaFileType.INSTANCE, """
      // comment
    """)

    // dumb and smart inspections run in dumb mode
    val smartInfos = doHighlighting()
    assertSize(2, smartInfos)
    assertHighlightInfo(smartInfos, "Dumb0")
    assertHighlightInfo(smartInfos, "Smart0")

    // only dumb inspection runs in dumb mode, but the results of smart inspection are frozen from the previous run
    val dumbInfos = doHighlightingInDumbMode()
    assertSize(2, smartInfos)
    assertHighlightInfo(dumbInfos, "Dumb1")
    assertHighlightInfo(dumbInfos, "Smart0")
  }

  private fun assertHighlightInfo(infos: List<HighlightInfo>, text: String) {
    assert(infos.any { it.description == text }) {
      "List [${infos.joinToString { it.description }}] does not contain `$text`"
    }
  }

  private fun doHighlightingInDumbMode(): List<HighlightInfo> {
    var result: MutableList<HighlightInfo>? = null
    DumbModeTestUtils.runInDumbModeSynchronously(project) {
      Disposer.newDisposable(testRootDisposable).use { disposable ->
        daemon.mustWaitForSmartMode(false, disposable)
        result = doHighlighting()
      }
    }
    return result!!
  }

  private class DumbInspection : LocalInspectionTool(), DumbAware {
    @Volatile
    var counter = 0

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
      return object : PsiElementVisitor() {
        override fun visitComment(comment: PsiComment) {
          holder.registerProblem(comment, "Dumb$counter")
          counter++
        }
      }
    }
  }

  private class SmartInspection : LocalInspectionTool() {
    @Volatile
    var counter = 0

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
      return object : PsiElementVisitor() {
        override fun visitComment(comment: PsiComment) {
          holder.registerProblem(comment, "Smart$counter")
          counter++
        }
      }
    }
  }

}