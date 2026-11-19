// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection

import com.intellij.codeInspection.jps.ClassOverriddenAtRuntimeInspection
import com.intellij.openapi.application.EDT
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.roots.ModuleOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

/**
 * Tests for [ClassOverriddenAtRuntimeInspection] quick fix — verifies that the fix correctly reorders
 * module dependencies so the compile-visible class is loaded first at runtime.
 *
 * Uses its own project, built as the project of the highlighting tests.
 */
@TestApplication
class ClassOverriddenAtRuntimeFixTest {
  companion object {
    private val projectFixture = classOverriddenAtRuntimeProjectFixture(deep = false)
  }

  private val myFixture by classOverriddenAtRuntimeFixture(projectFixture)

  @AfterEach
  fun resetOrder() {
    resetClassOverriddenAtRuntimeOrder(projectFixture.get().project)
  }

  /** Fix correctly moves the compile-side entry (Av2Module) before the shadowing entry (DepModule). */
  @Test
  fun testFixReordersDependencies(): Unit = timeoutRunBlocking(60.seconds) {
    myFixture.configureByText("Main.java", """
      import my.example.A;
      public class Main { A<caret> a; }
      """.trimIndent())
    myFixture.doHighlighting()

    val mainModule = findMainModule()
    assertOrder(mainModule, first = "DepModule", before = "Av2Module") // shadowing order before fix

    withContext(Dispatchers.EDT) {
      myFixture.launchAction(myFixture.findSingleIntention("Move 'Av2Module' before 'DepModule'"))

      assertOrder(mainModule, first = "Av2Module", before = "DepModule") // corrected order after fix
    }
  }

  /** After the fix, running the inspection again should produce no warnings. */
  @Test
  fun testNoWarningAfterFix(): Unit = timeoutRunBlocking(60.seconds) {
    myFixture.configureByText("Main.java", """
      import my.example.A;
      public class Main { A<caret> a; }
      """.trimIndent())
    myFixture.doHighlighting()
    withContext(Dispatchers.EDT) {
      myFixture.launchAction(myFixture.findSingleIntention("Move 'Av2Module' before 'DepModule'"))
    }

    IndexingTestUtil.suspendUntilIndexesAreReady(myFixture.project)

    myFixture.configureByText("Main.java", """
      import my.example.A;
      public class Main { A a; }
      """.trimIndent())
    myFixture.testHighlighting() // no <warning> markers expected
  }

  // ── helpers ──────────────────────────────────────────────────────────────

  private fun findMainModule(): Module =
    ModuleManager.getInstance(myFixture.project).findModuleByName(LightProjectDescriptor.TEST_MODULE_NAME)!!

  private fun assertOrder(module: Module, first: String, before: String) {
    val entries = ModuleRootManager.getInstance(module).orderEntries
    val firstIdx = entries.indexOfFirst { it is ModuleOrderEntry && it.moduleName == first }
    val secondIdx = entries.indexOfFirst { it is ModuleOrderEntry && it.moduleName == before }
    assertTrue(
      firstIdx in 0 until secondIdx,
      "Expected '$first' (idx=$firstIdx) to appear before '$before' (idx=$secondIdx)"
    )
  }
}
