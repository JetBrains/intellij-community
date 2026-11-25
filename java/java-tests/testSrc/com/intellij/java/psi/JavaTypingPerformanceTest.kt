// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi

import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.SyntaxTraverser
import com.intellij.psi.reparseFixture
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.PerformanceUnitTest
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.StressTestApplication
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.tools.ide.metrics.benchmark.Benchmark
import org.junit.jupiter.api.Test

/**
 * Measures the commit after each typed char in a large Java file.
 * The large class cases change a member outside of a method body, so no code block holds the change.
 * The large method cases change a statement of one method with many statements.
 * The large array cases change an item of one array initializer with many items, in a field and in a local variable.
 * The small cases keep the cost of the reparse checks visible in a file of a usual size.
 * The full parse case keeps the cost of the safe points of the parser visible.
 */
@PerformanceUnitTest
@StressTestApplication
class JavaTypingPerformanceTest {
  private val reparseFixture = reparseFixture(projectFixture)

  @Test
  fun testTypingInFieldNameOfLargeClass() = typing(classText(MEMBERS), "int field${MEMBERS / 2}", "x".repeat(10))

  @Test
  fun testTypingInFieldInitializerOfLargeClass() = typing(classText(MEMBERS), "int field${MEMBERS / 2} = ${MEMBERS / 2}", " + 1".repeat(3))

  /** A new char after a member is in no member, so only the class holds the change. */
  @Test
  fun testTypingANewFieldInLargeClass() = typing(classText(MEMBERS), "int field${MEMBERS / 2} = ${MEMBERS / 2};", "\n  int k = 1;")

  @Test
  fun testTypingInMethodParametersOfLargeClass() = typing(classText(MEMBERS), "void method${MEMBERS / 2}(int a", ", int b")

  @Test
  fun testPastingAMethodInLargeClass() =
    typing(classText(MEMBERS), "int field${MEMBERS / 2} = ${MEMBERS / 2};", "\n  void pasted(int a) {\n    System.out.println(a);\n  }\n", byChar = false)

  @Test
  fun testTypingInStatementOfLargeMethod() = typing(methodText(STATEMENTS), "int value${STATEMENTS / 2}", "x".repeat(10))

  @Test
  fun testTypingANewStatementInLargeMethod() = typing(methodText(STATEMENTS), "int value${STATEMENTS / 2} = ${STATEMENTS / 2};", "\n    k(1);")

  @Test
  fun testTypingInItemOfLargeArrayField() = typing(arrayFieldText(ITEMS), "\n    ${ITEMS / 2}", "1".repeat(10))

  @Test
  fun testTypingANewItemInLargeArrayField() = typing(arrayFieldText(ITEMS), "\n    ${ITEMS / 2},", " 7,")

  @Test
  fun testTypingInItemOfLargeLocalArray() = typing(arrayLocalText(ITEMS), "\n    ${ITEMS / 2}", "1".repeat(10))

  @Test
  fun testTypingInFieldNameOfSmallClass() = typing(classText(SMALL_MEMBERS), "int field${SMALL_MEMBERS / 2}", "x".repeat(10))

  @Test
  fun testTypingInStatementOfSmallMethod() = typing(methodText(SMALL_STATEMENTS), "int value${SMALL_STATEMENTS / 2}", "x".repeat(10))

  /** Parses the large class with each code block, so it measures the cost of the safe points in a full parse. */
  @Test
  fun testFullParseOfLargeClass() {
    val project = reparseFixture.get().project
    val text = classText(MEMBERS)
    runInEdtAndWait {
      Benchmark.newBenchmark("FullParseOfLargeClass") {
        val file = PsiFileFactory.getInstance(project).createFileFromText("Big.java", JavaLanguage.INSTANCE, text)
        check(SyntaxTraverser.astTraverser(file.node).traverse().size() > MEMBERS)
      }.warmupIterations(2).attempts(10).start(JavaTypingPerformanceTest::class.java.name + ".testFullParseOfLargeClass")
    }
  }

  /**
   * Inserts [typedText] after the first [marker] of the text, with a commit after each char.
   * With [byChar] false, it inserts [typedText] in one change with one commit.
   */
  private fun typing(text: String, marker: String, typedText: String, byChar: Boolean = true) {
    val fixture = reparseFixture.get().fixture
    val name = reparseFixture.get().name.removePrefix("test")
    lateinit var document: Document
    runInEdtAndWait {
      fixture.configureByText("Big.java", text)
      document = fixture.editor.document
    }
    var offset = 0
    val chunks = if (byChar) typedText.map { it.toString() } else listOf(typedText)
    runInEdtAndWait {
      Benchmark.newBenchmark(name) {
        var at = offset
        for (chunk in chunks) {
          WriteCommandAction.runWriteCommandAction(fixture.project) { document.insertString(at, chunk) }
          PsiDocumentManager.getInstance(fixture.project).commitDocument(document)
          at += chunk.length
        }
      }.setup {
        WriteCommandAction.runWriteCommandAction(fixture.project) { document.setText(text) }
        PsiDocumentManager.getInstance(fixture.project).commitDocument(document)
        val markerOffset = document.charsSequence.indexOf(marker)
        check(markerOffset >= 0) { "no '$marker'" }
        offset = markerOffset + marker.length
      }.warmupIterations(2).attempts(10).start(JavaTypingPerformanceTest::class.java.name + ".test" + name)
    }
  }

  companion object {
    // No mock JDK: the reparse needs none, and the JDK annotations are not in the runfiles of a plain JVM launch.
    private val projectFixture = lightJavaProjectFixture(LightProjectDescriptor.EMPTY_PROJECT_DESCRIPTOR)
  }
}

/** The members of the large class: about 1.2 MB. */
private const val MEMBERS = 10_000
private const val SMALL_MEMBERS = 20

/** The statements of the large method: about 0.6 MB. */
private const val STATEMENTS = 20_000
private const val SMALL_STATEMENTS = 40

/** The items of the large array: about 1.1 MB. */
private const val ITEMS = 100_000

/** One class with one field whose array initializer has [items] items, and a few members after it. */
private fun arrayFieldText(items: Int): String = buildString {
  append("class Big {\n  static final int[] DATA = {")
  for (i in 0 until items) append("\n    $i,")
  append("\n  };\n  int a;\n  void m() {}\n}\n")
}

/** One class with one method whose local array initializer has [items] items. */
private fun arrayLocalText(items: Int): String = buildString {
  append("class Big {\n  void big() {\n    int[] data = {")
  for (i in 0 until items) append("\n    $i,")
  append("\n    };\n    System.out.println(data);\n  }\n}\n")
}

/** One class with [members] fields and [members] methods. */
private fun classText(members: Int): String = buildString {
  append("class Big {\n")
  for (i in 0 until members) {
    append("  int field$i = $i;\n")
    append("  void method$i(int a) {\n    int value$i = a + 1;\n    System.out.println(value$i);\n  }\n")
  }
  append("}\n")
}

/** One class with one method of [statements] statements. */
private fun methodText(statements: Int): String = buildString {
  append("class Big {\n  void big(int a) {\n")
  for (i in 0 until statements) {
    append("    int value$i = $i;\n")
    if (i % 10 == 9) append("    System.out.println(value$i);\n")
  }
  append("  }\n}\n")
}
