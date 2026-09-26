// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection

import com.intellij.codeInspection.jps.ClassOverriddenAtRuntimeInspection
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.roots.ModuleOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

/**
 * Tests for [ClassOverriddenAtRuntimeInspection] quick fix — verifies that the fix correctly reorders
 * module dependencies so the compile-visible class is loaded first at runtime.
 *
 * Uses a dedicated descriptor to keep the project isolated from the highlighting tests
 * (both share the same test infrastructure but use distinct TempFileSystem paths).
 */
class ClassOverriddenAtRuntimeFixTest : LightJavaCodeInsightFixtureTestCase() {

  override fun getProjectDescriptor(): LightProjectDescriptor = ClassOverriddenAtRuntimeProjectDescriptor

  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(ClassOverriddenAtRuntimeInspection::class.java)
    VfsTestUtil.createFile(ClassOverriddenAtRuntimeProjectDescriptor.av1SourceRoot()!!, "my/example/A.java",
                           ClassOverriddenAtRuntimeInspectionTest.A_SOURCE)
    VfsTestUtil.createFile(ClassOverriddenAtRuntimeProjectDescriptor.av2SourceRoot()!!, "my/example/A.java",
                           ClassOverriddenAtRuntimeInspectionTest.A_SOURCE)
    IndexingTestUtil.waitUntilIndexesAreReady(project)
  }

  override fun tearDown() {
    try {
      ClassOverriddenAtRuntimeProjectDescriptor.reset(project)
      ClassOverriddenAtRuntimeProjectDescriptor.cleanupSourceRoots()
    }
    catch (e: Throwable) {
      addSuppressedException(e)
    }
    finally {
      super.tearDown()
    }
  }

  /** Fix correctly moves the compile-side entry (Av2Module) before the shadowing entry (DepModule). */
  fun testFixReordersDependencies() {
    myFixture.configureByText("Main.java", """
      import my.example.A;
      public class Main { A<caret> a; }
      """.trimIndent())
    myFixture.doHighlighting()

    val mainModule = findMainModule()
    assertOrder(mainModule, first = "DepModule", before = "Av2Module") // shadowing order before fix

    myFixture.launchAction(myFixture.findSingleIntention("Move 'Av2Module' before 'DepModule'"))

    assertOrder(mainModule, first = "Av2Module", before = "DepModule") // corrected order after fix
  }

  /** After the fix, running the inspection again should produce no warnings. */
  fun testNoWarningAfterFix() {
    myFixture.configureByText("Main.java", """
      import my.example.A;
      public class Main { A<caret> a; }
      """.trimIndent())
    myFixture.doHighlighting()
    myFixture.launchAction(myFixture.findSingleIntention("Move 'Av2Module' before 'DepModule'"))

    IndexingTestUtil.waitUntilIndexesAreReady(project)

    myFixture.configureByText("Main.java", """
      import my.example.A;
      public class Main { A a; }
      """.trimIndent())
    myFixture.testHighlighting() // no <warning> markers expected
  }

  // ── helpers ──────────────────────────────────────────────────────────────

  private fun findMainModule(): Module =
    ModuleManager.getInstance(project).findModuleByName(LightProjectDescriptor.TEST_MODULE_NAME)!!

  private fun assertOrder(module: Module, first: String, before: String) {
    val entries = ModuleRootManager.getInstance(module).orderEntries
    val firstIdx = entries.indexOfFirst { it is ModuleOrderEntry && it.moduleName == first }
    val secondIdx = entries.indexOfFirst { it is ModuleOrderEntry && it.moduleName == before }
    assertTrue(
      "Expected '$first' (idx=$firstIdx) to appear before '$before' (idx=$secondIdx)",
      firstIdx in 0 until secondIdx
    )
  }
}
