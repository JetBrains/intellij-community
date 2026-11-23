// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.impl

import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.JavaTestUtil
import com.intellij.codeInsight.daemon.DaemonAnalyzerFixture.Companion.daemonAnalyzerFixture
import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase.CanChangeDocumentDuringHighlighting
import com.intellij.codeInspection.GlobalInspectionTool
import com.intellij.codeInspection.InspectionEP
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx
import com.intellij.codeInsight.daemon.impl.DaemonProgressIndicator
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInspection.LanguageInspectionSuppressors
import com.intellij.codeInspection.LocalInspectionEP
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.RedundantSuppressInspection
import com.intellij.codeInspection.RedundantSuppressionDetector
import com.intellij.codeInspection.ex.GlobalInspectionToolWrapper
import com.intellij.codeInspection.ex.InspectionProfileWrapper
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.use
import com.intellij.psi.JavaElementVisitor
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiLiteralExpression
import com.intellij.testFramework.DumbModeTestUtils
import com.intellij.testFramework.enableInspectionTool
import com.intellij.testFramework.enableInspectionTools
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

@TestApplication
@CanChangeDocumentDuringHighlighting
class LocalInspectionsInDumbModeTest {
  private val fixture by daemonAnalyzerFixture(JavaTestUtil.getJavaTestDataPath())

  private fun runTest(test: () -> Unit) {
    DaemonProgressIndicator.runInDebugMode<Exception> {
      test()
    }
  }

  @Test
  fun testLocalInspectionInDumbMode(): Unit = runTest {
    enableInspectionTools(fixture.project, fixture.testRootDisposable, DumbInspection(), SmartInspection())

    @Language("JAVA")
    val text = """
      // comment
    """
    fixture.configureByText(JavaFileType.INSTANCE, text)

    // only dumb inspection runs in dumb mode
    val dumbInfos = doHighlightingInDumbMode()
    assertThat(dumbInfos).hasSize(1)
    assertExistsInfo(dumbInfos, "Dumb0")

    DaemonCodeAnalyzerEx.getInstanceEx(fixture.project).restart(fixture.getTestName(false))
    // dumb and smart inspections run in dumb mode
    val smartInfos = fixture.doHighlighting()
    assertThat(smartInfos).hasSize(2)
    assertExistsInfo(smartInfos, "Dumb1")
    assertExistsInfo(smartInfos, "Smart0")

    DaemonCodeAnalyzerEx.getInstanceEx(fixture.project).restart(fixture.getTestName(false))
    // only dumb inspection runs in dumb mode, but the results of smart inspection are frozen from the previous run
    val dumbInfos2 = doHighlightingInDumbMode()
    assertThat(dumbInfos2).hasSize(2)
    assertExistsInfo(dumbInfos2, "Dumb2")
    assertExistsInfo(dumbInfos2, "Smart0")
  }

  @Test
  fun testLocalInspectionsInSmartModeThenInDumbMode(): Unit = runTest {
    enableInspectionTools(fixture.project, fixture.testRootDisposable, DumbInspection(), SmartInspection())

    @Language("JAVA")
    val text = """
      // comment
    """
    fixture.configureByText(JavaFileType.INSTANCE, text)

    // dumb and smart inspections run in dumb mode
    val smartInfos = fixture.doHighlighting()
    assertThat(smartInfos).hasSize(2)
    assertExistsInfo(smartInfos, "Dumb0")
    assertExistsInfo(smartInfos, "Smart0")

    DaemonCodeAnalyzerEx.getInstanceEx(fixture.project).restart(fixture.getTestName(false))
    // only dumb inspection runs in dumb mode, but the results of smart inspection are frozen from the previous run
    val dumbInfos = doHighlightingInDumbMode()
    assertThat(dumbInfos).hasSize(2)
    assertExistsInfo(dumbInfos, "Dumb1")
    assertExistsInfo(dumbInfos, "Smart0")
  }

  @Test
  fun testLocalInspectionInDumbModeDontInitializeUnrelatedTools(): Unit = runTest {
    enableInspectionTools(fixture.project, fixture.testRootDisposable, DumbInspection(), SmartInspection())

    val unrelatedToolWrapper = createUnrelatedToolWrapper()
    enableInspectionTool(fixture.project, unrelatedToolWrapper, fixture.testRootDisposable)
    InspectionProfileWrapper.runWithNoDuplicateCheckInTests {
      @Language("JAVA")
      val text = """
      // comment
    """
      fixture.configureByText(JavaFileType.INSTANCE, text)

      doHighlightingInDumbMode()

      assertFalse(unrelatedToolWrapper.isToolInstantiated())
    }
  }

  @Test
  fun testLocalInspectionDontInitializeUnrelatedTools(): Unit = runTest {
    enableInspectionTools(fixture.project, fixture.testRootDisposable, DumbInspection(), SmartInspection())

    val unrelatedToolWrapper = createUnrelatedToolWrapper()
    enableInspectionTool(fixture.project, unrelatedToolWrapper, fixture.testRootDisposable)
    InspectionProfileWrapper.runWithNoDuplicateCheckInTests {
      @Language("JAVA")
      val text = """
      // comment
    """
      fixture.configureByText(JavaFileType.INSTANCE, text)

      fixture.doHighlighting()

      assertFalse(unrelatedToolWrapper.isToolInstantiated())
    }
  }

  @Test
  fun testLocalInspectionDontInitializeUnrelatedGlobalTools(): Unit = runTest {
    enableInspectionTools(fixture.project, fixture.testRootDisposable, DumbInspection(), SmartInspection())

    val unrelatedToolWrapper = createGlobalToolWrapper("UnrelatedGlobal", "TEXT")
    enableInspectionTool(fixture.project, unrelatedToolWrapper, fixture.testRootDisposable)
    InspectionProfileWrapper.runWithNoDuplicateCheckInTests {
      @Language("JAVA")
      val text = """
      // comment
    """
      fixture.configureByText(JavaFileType.INSTANCE, text)

      fixture.doHighlighting()

      assertFalse(unrelatedToolWrapper.isToolInstantiated())
    }
  }

  @Test
  fun testIntentionsDontInitializeUnrelatedDoNotShowGlobalTool(): Unit = runTest {
    val unrelatedToolWrapper =
      createGlobalToolWrapper("UnrelatedDoNotShowGlobal", "TEXT", HighlightDisplayLevel.DO_NOT_SHOW)
    enableInspectionTool(fixture.project, unrelatedToolWrapper, fixture.testRootDisposable)
    InspectionProfileWrapper.runWithNoDuplicateCheckInTests {
      @Language("JAVA")
      val text = """
      // comment
    """
      fixture.configureByText(JavaFileType.INSTANCE, text)

      fixture.doHighlighting()

      assertFalse(unrelatedToolWrapper.isToolInstantiated())
    }
  }

  @Test
  fun testIntentionsInitializeApplicableDoNotShowGlobalTool(): Unit = runTest {
    val globalToolWrapper = createGlobalToolWrapper("ApplicableDoNotShowGlobal", "JAVA", HighlightDisplayLevel.DO_NOT_SHOW)
    enableInspectionTool(fixture.project, globalToolWrapper, fixture.testRootDisposable)
    InspectionProfileWrapper.runWithNoDuplicateCheckInTests {
      @Language("JAVA")
      val text = """
      // comment
    """
      fixture.configureByText(JavaFileType.INSTANCE, text)

      fixture.doHighlighting()

      assertTrue(globalToolWrapper.isToolInstantiated())
    }
  }

  @Test
  fun testLocalInspectionInitializesApplicableGlobalToolOnFirstPass(): Unit = runTest {
    val globalToolWrapper = createGlobalToolWrapper("ApplicableGlobal", "JAVA")
    enableInspectionTool(fixture.project, globalToolWrapper, fixture.testRootDisposable)
    InspectionProfileWrapper.runWithNoDuplicateCheckInTests {
      @Language("JAVA")
      val text = """
      // comment
    """
      fixture.configureByText(JavaFileType.INSTANCE, text)

      val infos = fixture.doHighlighting()

      assertTrue(globalToolWrapper.isToolInstantiated())
      assertExistsInfo(infos, "Global")
    }
  }

  @Test
  fun testJavaSuppressor(): Unit = runTest {
    enableInspectionTools(fixture.project, fixture.testRootDisposable, RedundantSuppressInspection(), StringInspection())

    val javaRedundantSuppressor = LanguageInspectionSuppressors.INSTANCE.allForLanguage(JavaLanguage.INSTANCE)
      .filterIsInstance<RedundantSuppressionDetector>()
      .firstOrNull() // Java Redundant Suppressor is expected to exist
    requireNotNull(javaRedundantSuppressor) // Java Redundant Suppressor is expected to exist
    assertFalse(javaRedundantSuppressor.isDumbAware) // update the test if JavaRedundantSuppressor has become dumb-aware

    @Language("JAVA")
    val text = """
      class A {
        void foo() {
          //noinspection String
          Object s = "abc";
        }
      }
    """
    fixture.configureByText(JavaFileType.INSTANCE, text)

    // smart infos don't contain String because Suppressor works in smart mode and suppresses it
    val smartInfos = fixture.doHighlighting()
    assertThat(smartInfos.map { it.description }).doesNotContain("String")

    if (Registry.`is`("ide.dumb.mode.check.awareness")) {
      // dumb infos contain String because Suppressor does not work in dumb mode
      val dumbInfos = doHighlightingInDumbMode()
      assertThat(dumbInfos.map { it.description }).contains("String")
    }
  }

  @Test
  fun testRedundantJavaSuppression(): Unit = runTest {
    enableInspectionTools(fixture.project, fixture.testRootDisposable, RedundantSuppressInspection(), StringInspection())

    val javaRedundantSuppressor = LanguageInspectionSuppressors.INSTANCE.allForLanguage(JavaLanguage.INSTANCE)
      .filterIsInstance<RedundantSuppressionDetector>()
      .firstOrNull()
    assertNotNull(javaRedundantSuppressor) // Java Redundant Suppressor is expected to exist

    @Language("JAVA")
    val text = """
      class A {
        void foo() {
          //noinspection String
          Object s;
        }
      }
    """
    fixture.configureByText(JavaFileType.INSTANCE, text)

    if (Registry.`is`("ide.dumb.mode.check.awareness")) {
      // dumb infos contain a redundant suppression because it's not removed as java suppressor does not work in dumb mode
      val initialDumbInfos = doHighlightingInDumbMode().map { it.description }
      assertThat(initialDumbInfos).doesNotContain("Redundant suppression")
    }

    // smart infos contain a redundant suppression, because suppression is in fact redundant,
    // and redundant suppressor for Java works in smart mode
    val smartInfos = fixture.doHighlighting().map { it.description }
    assertThat(smartInfos).contains("Redundant suppression")

    // dumb infos contain a redundant suppression because it's not removed as java suppressor does not work in dumb mode
    val dumbInfos = doHighlightingInDumbMode().map { it.description }
    assertThat(dumbInfos).contains("Redundant suppression")
  }


  @Test
  fun testSuppressorInDumbMode2(): Unit = runTest {
    enableInspectionTools(fixture.project, fixture.testRootDisposable, RedundantSuppressInspection(), StringInspection())

    @Language("JAVA")
    val text = """
      class A {
        void foo() {
          Object s = "abc";
        }
      }
    """
    fixture.configureByText(JavaFileType.INSTANCE, text)

    val dumbInfos = doHighlightingInDumbMode()
    assertFalse((dumbInfos as List<*>).contains("String"))
  }

  private fun assertExistsInfo(infos: List<HighlightInfo>, text: String) {
    assert(infos.any { it.description == text }) {
      "List [${infos.joinToString { it.description }}] does not contain `$text`"
    }
  }

  private fun doHighlightingInDumbMode(): List<HighlightInfo> {
    var result: List<HighlightInfo>? = null
    DumbModeTestUtils.runInDumbModeSynchronously(fixture.project) {
      Disposer.newDisposable(fixture.testRootDisposable).use { disposable ->
        CodeInsightTestFixtureImpl.mustWaitForSmartMode(false, disposable)
        result = fixture.doHighlighting()
      }
    }
    return result!!
  }

  private class DumbInspection : LocalInspectionTool(), DumbAware {
    val counter = AtomicInteger()

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
      return object : PsiElementVisitor() {
        override fun visitComment(comment: PsiComment) {
          holder.registerProblem(comment, "Dumb${counter.andIncrement}")
        }
      }
    }
  }

  private class SmartInspection : LocalInspectionTool() {
    val counter = AtomicInteger()

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
      return object : PsiElementVisitor() {
        override fun visitComment(comment: PsiComment) {
          if (DumbService.isDumb(comment.project)) throw IndexNotReadyException.create()
          else holder.registerProblem(comment, "Smart${counter.andIncrement}")
        }
      }
    }
  }

  private class StringInspection : LocalInspectionTool(), DumbAware {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
      return object : JavaElementVisitor() {
        override fun visitLiteralExpression(expression: PsiLiteralExpression) {
          holder.registerProblem(expression, "String")
        }
      }
    }
  }


  private fun createUnrelatedToolWrapper(): UnrelatedToolWrapper {
    val ep = LocalInspectionEP()
    ep.dumbAware = true
    ep.implementationClass = "foo.bar.Baz"
    ep.id = "Baz"
    ep.language = "TEXT"
    ep.displayName = "Baz"
    return UnrelatedToolWrapper(ep)
  }

  private fun createGlobalToolWrapper(
    shortName: String,
    language: String,
    level: HighlightDisplayLevel = HighlightDisplayLevel.WARNING,
  ): TestGlobalToolWrapper {
    val pluginDescriptor = requireNotNull(PluginManagerCore.getPlugin(PluginManagerCore.CORE_ID))
    val ep = InspectionEP(MyGlobalInspection::class.java.name, pluginDescriptor)
    ep.shortName = shortName
    ep.language = language
    ep.displayName = shortName
    ep.groupDisplayName = shortName
    ep.enabledByDefault = true
    ep.level = level.name
    return TestGlobalToolWrapper(ep)
  }

  private class UnrelatedToolWrapper(ep: LocalInspectionEP) : LocalInspectionToolWrapper(ep) {
    private val toolIsInstantiated: AtomicBoolean = AtomicBoolean(false)

    override fun getTool(): LocalInspectionTool {
      toolIsInstantiated.set(true)
      return MyLocalInspection()
    }

    fun isToolInstantiated(): Boolean {
      return toolIsInstantiated.get()
    }

    private class MyLocalInspection : LocalInspectionTool() {
      override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
        return PsiElementVisitor.EMPTY_VISITOR
      }
    }
  }

  private class TestGlobalToolWrapper(ep: InspectionEP) : GlobalInspectionToolWrapper(ep) {
    private val shortName: String = ep.shortName
    private val toolIsInstantiated: AtomicBoolean = AtomicBoolean(false)

    override fun getTool(): GlobalInspectionTool {
      toolIsInstantiated.set(true)
      return MyGlobalInspection(shortName)
    }

    fun isToolInstantiated(): Boolean {
      return toolIsInstantiated.get()
    }
  }

  private class MyGlobalInspection(private val shortName: String) : GlobalInspectionTool() {
    override fun getSharedLocalInspectionTool(): LocalInspectionTool {
      return MySharedLocalInspection(shortName)
    }
  }

  private class MySharedLocalInspection(private val shortName: String) : LocalInspectionTool() {
    override fun getShortName(): String {
      return shortName
    }

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
      return object : PsiElementVisitor() {
        override fun visitComment(comment: PsiComment) {
          holder.registerProblem(comment, "Global")
        }
      }
    }
  }
}
