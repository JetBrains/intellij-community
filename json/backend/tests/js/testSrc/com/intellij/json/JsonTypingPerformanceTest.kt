// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json

import com.intellij.json.psi.JsonFile
import com.intellij.openapi.application.EDT
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightFixture
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightProjectFixture
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.PerformanceUnitTest
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.StressTestApplication
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.tools.ide.metrics.benchmark.Benchmark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import java.lang.reflect.Method
import kotlin.time.Duration.Companion.minutes

/**
 * Measures the typing in a large JSON file.
 * Each attempt types characters in the editor and commits the document after each character.
 * So it measures the editor highlighter update, the typed handlers and the reparse of the PSI.
 * The flat tests type in the middle of a file with one container over all of the file.
 * The nested test types in a small object of a large file, as in a lock file.
 */
@StressTestApplication
@TestApplication
@PerformanceUnitTest
class JsonTypingPerformanceTest {
  private val projectFixture = codeInsightProjectFixture()
  private val myFixture by codeInsightFixture(projectFixture)
  private lateinit var testMethod: Method

  @BeforeEach
  fun rememberTestMethod(testInfo: TestInfo) {
    testMethod = testInfo.testMethod.get()
  }

  @Test
  fun testTypingInStringValueOfFlatObject() = doTest(flatObjectText(FLAT_ENTRIES), "\"value of the key ${FLAT_ENTRIES / 2} ")

  @Test
  fun testTypingInStringOfFlatArray() = doTest(flatArrayText(FLAT_ENTRIES), "\"element ${FLAT_ENTRIES / 2} ")

  @Test
  fun testTypingInNumberOfFlatArray() = doTest(flatNumberArrayText(FLAT_ENTRIES), " ${FLAT_ENTRIES / 2}")

  @Test
  fun testTypingInStringValueOfNestedFile() = doTest(lockFileText(PACKAGES), "\"resolved\": \"https://registry/pkg-${PACKAGES / 2}/")

  /** A new char at the line end is in no leaf of the line, so the platform reparses the object. */
  @Test
  fun testTypingANewPropertyInFlatObject() =
    doTest(flatObjectText(FLAT_ENTRIES), "\"value of the key ${FLAT_ENTRIES / 2} of the file\",", "\"k\": 1,")

  /**
   * The paste adds more than 20 items to the range, so the diff replaces the root of the range.
   * Then the merge inserts each new item after the item before it.
   */
  @Test
  fun testPastingManyPropertiesInFlatObject() = onEdt {
    val text = flatObjectText(FLAT_ENTRIES)
    myFixture.configureByText("big.json", text)
    val document = myFixture.editor.document
    val project = myFixture.project
    val caretMarker = "\"value of the key ${FLAT_ENTRIES / 2} of the file\",\n  "
    val pasted = (0 until PASTED_ENTRIES).joinToString("") { "\"pasted-$it\": $it,\n  " }
    val name = testMethod.name.removePrefix("test")
    Benchmark.newBenchmark(name) {
      WriteCommandAction.runWriteCommandAction(project) {
        document.insertString(myFixture.caretOffset, pasted)
      }
      PsiDocumentManager.getInstance(project).commitDocument(document)
    }.setup {
      WriteCommandAction.runWriteCommandAction(project) { document.setText(text) }
      PsiDocumentManager.getInstance(project).commitDocument(document)
      myFixture.editor.caretModel.moveToOffset(text.indexOf(caretMarker) + caretMarker.length)
    }.warmupIterations(1).start(testMethod, name)
  }

  @Test
  fun testTypingInStringValueOfSmallFlatObject() = doTest(flatObjectText(SMALL_ENTRIES), "\"value of the key ${SMALL_ENTRIES / 2} ")

  /** Places the caret after the first [caretMarker] of [text] and types [typedText], one char at a time, with a commit after each. */
  private fun doTest(text: String, caretMarker: String, typedText: String = "x".repeat(10)) = onEdt {
    myFixture.configureByText("big.json", text)
    assertInstanceOf(JsonFile::class.java, myFixture.file)
    val document = myFixture.editor.document

    Benchmark.newBenchmark(testMethod.name.removePrefix("test")) {
      for (char in typedText) {
        myFixture.type(char)
        PsiDocumentManager.getInstance(myFixture.project).commitDocument(document)
      }
    }.setup {
      val offset = document.charsSequence.indexOf(caretMarker)
      check(offset >= 0) { "no '$caretMarker'" }
      myFixture.editor.caretModel.moveToOffset(offset + caretMarker.length)
    }.warmupIterations(1).start(testMethod, testMethod.name.removePrefix("test"))
  }

  private fun onEdt(action: () -> Unit) {
    timeoutRunBlocking(timeout = 10.minutes) {
      withContext(Dispatchers.EDT) { action() }
    }
  }
}

/** One object with [entries] string properties, about 40 chars each. */
private fun flatObjectText(entries: Int): String = buildString {
  append("{\n")
  for (i in 0 until entries) {
    append("  \"key-$i\": \"value of the key $i of the file\"")
    append(if (i < entries - 1) ",\n" else "\n")
  }
  append("}\n")
}

/** One array with [entries] string elements. */
private fun flatArrayText(entries: Int): String = buildString {
  append("[\n")
  for (i in 0 until entries) {
    append("  \"element $i of the array\"")
    append(if (i < entries - 1) ",\n" else "\n")
  }
  append("]\n")
}

/** One array with [entries] numbers, 20 per line. */
private fun flatNumberArrayText(entries: Int): String = buildString {
  append("[\n  ")
  for (i in 0 until entries) {
    append(i)
    if (i < entries - 1) append(if (i % 20 == 19) ",\n  " else ", ")
  }
  append("\n]\n")
}

/** A lock file with [packages] packages, each a small object. */
private fun lockFileText(packages: Int): String = buildString {
  append("{\n  \"name\": \"big\",\n  \"lockfileVersion\": 3,\n  \"packages\": {\n")
  for (i in 0 until packages) {
    append("    \"node_modules/pkg-$i\": {\n")
    append("      \"version\": \"1.0.$i\",\n")
    append("      \"resolved\": \"https://registry/pkg-$i/-/pkg-$i-1.0.$i.tgz\",\n")
    append("      \"integrity\": \"sha512-abcdefghijklmnopqrstuvwxyz0123456789abcdefghijklmnopqrstuvwxyz0123456789==\",\n")
    append("      \"dependencies\": {\n        \"pkg-${i + 1}\": \"^1.0.0\"\n      }\n")
    append("    }")
    append(if (i < packages - 1) ",\n" else "\n")
  }
  append("  }\n}\n")
}

/** The entries of the flat files, about 1.3 MB for the object. */
private const val FLAT_ENTRIES = 30_000
private const val SMALL_ENTRIES = 100

/** The properties of the paste in the flat object. */
private const val PASTED_ENTRIES = 1000

/** The packages of the lock file, about 1.4 MB. */
private const val PACKAGES = 5000
