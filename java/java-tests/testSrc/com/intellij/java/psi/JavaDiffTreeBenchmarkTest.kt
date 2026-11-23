// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.PerformanceUnitTest
import com.intellij.testFramework.junit5.StressTestApplication
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.psi.reparseFixture
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.tools.ide.metrics.benchmark.Benchmark
import org.junit.jupiter.api.Test

/** A local benchmark of the commit after a typed char in Java files, for a change of `DiffTree`. */
@PerformanceUnitTest
@StressTestApplication
class JavaDiffTreeBenchmarkTest {
  private val reparseFixture = reparseFixture(projectFixture)

  @Test
  fun testTypingInMethodBody() = typing("TypingInMethodBody", javaText(MEMBERS), "int value${MEMBERS / 2}")

  @Test
  fun testTypingInFieldName() = typing("TypingInFieldName", javaText(MEMBERS), "int field${MEMBERS / 2}")

  @Test
  fun testTypingInMethodBodyInSmallFile() = typing("TypingInMethodBodyInSmallFile", javaText(SMALL_MEMBERS), "int value${SMALL_MEMBERS / 2}")

  @Test
  fun testTypingInDeepTree() {
    val call1 = "a('b')."
    val call2 = "c(new Some())."
    val suffix = "x(); } }"
    typing("TypingInDeepTree", "class Foo { { u." + (call1 + call2).repeat(500) + suffix) { it.lastIndexOf(suffix) }
  }

  /** Types 10 chars after [marker], with a commit after each char. */
  private fun typing(name: String, text: String, marker: String) =
    typing(name, text) { it.indexOf(marker) + marker.length }

  /** Types 10 chars at the offset of [offsetOf] in the text before each attempt, with a commit after each char. */
  private fun typing(name: String, text: String, offsetOf: (CharSequence) -> Int) {
    val fixture = reparseFixture.get().fixture
    lateinit var document: Document
    runInEdtAndWait {
      fixture.configureByText("Big.java", text)
      document = fixture.editor.document
    }
    var offset = 0
    runInEdtAndWait {
      Benchmark.newBenchmark(name) {
        for (i in 0 until 10) {
          WriteCommandAction.runWriteCommandAction(fixture.project) { document.insertString(offset + i, "x") }
          PsiDocumentManager.getInstance(fixture.project).commitDocument(document)
        }
      }.setup {
        offset = offsetOf(document.charsSequence)
      }.warmupIterations(2).attempts(10).start(JavaDiffTreeBenchmarkTest::class.java.name + ".test" + name)
    }
  }

  companion object {
    // No mock JDK: the reparse needs none, and the JDK annotations are not in the runfiles of a plain JVM launch.
    private val projectFixture = lightJavaProjectFixture(LightProjectDescriptor.EMPTY_PROJECT_DESCRIPTOR)
  }
}

private const val MEMBERS = 15_000
private const val SMALL_MEMBERS = 40

private fun javaText(members: Int): String = buildString {
  append("class Big {\n")
  for (i in 0 until members) {
    append("  int field$i = $i;\n")
    append("  void method$i(int a) {\n    int value$i = a + 1;\n    System.out.println(value$i);\n  }\n")
  }
  append("}\n")
}
