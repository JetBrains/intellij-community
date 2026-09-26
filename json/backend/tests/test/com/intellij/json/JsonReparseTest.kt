// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json

import com.intellij.json.json5.Json5Language
import com.intellij.json.psi.JsonArray
import com.intellij.json.psi.JsonFile
import com.intellij.json.psi.JsonObject
import com.intellij.json.psi.JsonProperty
import com.intellij.json.syntax.JsonLazyParsing
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.tree.IReparseableElementType
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtilCore
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import com.intellij.testFramework.junit5.fixture.virtualFileFixture
import java.util.concurrent.CopyOnWriteArrayList
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test

@TestApplication
internal class JsonReparseTest {
  companion object {
    val p = projectFixture(openAfterCreation = true)
    val module = p.moduleFixture()
    val root = module.sourceRootFixture()
  }

  val file by root.virtualFileFixture("foo.json", "")
  val json5File by root.virtualFileFixture("foo.json5", "")
  val project get() = p.get()

  @Test
  fun testReparse() = timeoutRunBlocking {
    checkReparse("{\"a\": <caret>1}", "2")
  }

  @Test
  fun testRemoveRBrace() = timeoutRunBlocking {
    checkReparse("{\"a\": <selection>1}</selection>", "2")
  }

  @Test
  fun testRemoveLBrace() = timeoutRunBlocking {
    checkReparse(
      """
        {
          "x": <selection>{</selection>
            "a": 2,
            "b": [
              1,
              2,
              3
            ]
          }
          }        
      """.trimIndent(), ""
    )
  }

  @Test
  fun testPsiSurvive() = timeoutRunBlocking {
    @Language("JSON")
    val text = """
      {
        "a": {
          "x": 1,
          "y": 2
        }
      }
    """.trimIndent()

    edtWriteAction {
      file.setBinaryContent(text.toByteArray())
      PsiDocumentManager.getInstance(project).commitAllDocuments()
    }

    val document = readAction { FileDocumentManager.getInstance().getDocument(file)!! }
    val psiFile = readAction { PsiManager.getInstance(project).findFile(file)!! as JsonFile }

    data class X(val a: JsonProperty, val b: JsonObject, val c: JsonObject)

    val (xProp, aObject, rootObject) = readAction {
      val rootObject = psiFile.topLevelValue!! as JsonObject
      val aObject = rootObject.findProperty("a")!!.value as JsonObject
      val xProp = aObject.findProperty("x")!!
      X(xProp, aObject, rootObject)
    }

    edtWriteAction {
      CommandProcessor.getInstance().executeCommand(project, Runnable {
        val firstRbrace = document.text.indexOf("}")
        document.replaceString(firstRbrace + 1, firstRbrace + 1, ", \"b\": 2")
        PsiDocumentManager.getInstance(project).commitAllDocuments()
      }, "insert text", null)
    }

    readAction {
      PsiUtilCore.ensureValid(rootObject)
      PsiUtilCore.ensureValid(aObject)
      PsiUtilCore.ensureValid(xProp)
    }
  }

  @Test
  fun testReparseArray() = timeoutRunBlocking {
    checkReparse("[<caret>1, 2, 3]", "42")
  }

  @Test
  fun testRemoveArrayRBracket() = timeoutRunBlocking {
    checkReparse("[1, 2, <selection>3]</selection>", "4")
  }

  @Test
  fun testRemoveArrayLBracket() = timeoutRunBlocking {
    checkReparse(
      """
        {
          "x": <selection>[</selection>
            1,
            2,
            3
          ]
        }
      """.trimIndent(), ""
    )
  }

  @Test
  fun testArrayPsiSurvive() = timeoutRunBlocking {
    @Language("JSON")
    val text = """
      {
        "a": [
          1,
          2,
          3
        ]
      }
    """.trimIndent()

    edtWriteAction {
      file.setBinaryContent(text.toByteArray())
      PsiDocumentManager.getInstance(project).commitAllDocuments()
    }

    val document = readAction { FileDocumentManager.getInstance().getDocument(file)!! }
    val psiFile = readAction { PsiManager.getInstance(project).findFile(file)!! as JsonFile }

    val (aArray, rootObject) = readAction {
      val rootObject = psiFile.topLevelValue!! as JsonObject
      val aArray = rootObject.findProperty("a")!!.value as JsonArray
      Pair(aArray, rootObject)
    }

    edtWriteAction {
      CommandProcessor.getInstance().executeCommand(project, Runnable {
        val firstRbracket = document.text.indexOf("]")
        document.replaceString(firstRbracket, firstRbracket, ", 4")
        PsiDocumentManager.getInstance(project).commitAllDocuments()
      }, "insert text", null)
    }

    readAction {
      PsiUtilCore.ensureValid(rootObject)
      PsiUtilCore.ensureValid(aArray)
    }
  }

  // Edge cases: cross-brace inconsistency
  // The reparseable check only validates balance of one brace kind.
  // These tests verify correctness when the "other" brace kind is unbalanced.

  @Test
  fun testObjectRemoveNestedRBracket() = timeoutRunBlocking {
    // Removing ] from a nested array inside an object.
    // { } balance is broken (was ok, now extra } at end), but the edit is inside an object node
    // whose { } balance remains fine — while [ ] becomes unbalanced.
    checkReparse(
      """
        {
          "a": [1, 2, <selection>3]</selection>
        }
      """.trimIndent(), "3"
    )
  }

  @Test
  fun testObjectRemoveNestedLBracket() = timeoutRunBlocking {
    // Removing [ from a nested array inside an object.
    checkReparse(
      """
        {
          "a": <selection>[</selection>1, 2, 3]
        }
      """.trimIndent(), ""
    )
  }

  @Test
  fun testArrayRemoveNestedRBrace() = timeoutRunBlocking {
    // Removing } from a nested object inside an array.
    // [ ] balance stays fine, but { } becomes unbalanced.
    checkReparse(
      """
        [
          {"a": 1, "b": <selection>2}</selection>,
          {"c": 3}
        ]
      """.trimIndent(), "2"
    )
  }

  @Test
  fun testArrayRemoveNestedLBrace() = timeoutRunBlocking {
    // Removing { from a nested object inside an array.
    checkReparse(
      """
        [
          <selection>{</selection>"a": 1},
          {"b": 2}
        ]
      """.trimIndent(), ""
    )
  }

  @Test
  fun testObjectReplaceBraceWithBracket() = timeoutRunBlocking {
    // Replace closing } of a nested object with ] — cross-brace swap.
    checkReparse(
      """
        {
          "a": {"x": <selection>1}</selection>
        }
      """.trimIndent(), "1]"
    )
  }

  @Test
  fun testArrayReplaceBracketWithBrace() = timeoutRunBlocking {
    // Replace closing ] of a nested array with } — cross-brace swap.
    checkReparse(
      """
        {
          "a": [1, 2, <selection>3]</selection>
        }
      """.trimIndent(), "3}"
    )
  }

  @Test
  fun testObjectInsertExtraRBracket() = timeoutRunBlocking {
    // Insert an extra ] inside an object — { } balanced, [ ] has surplus ].
    checkReparse(
      """
        {
          "a": <caret>1
        }
      """.trimIndent(), "]"
    )
  }

  @Test
  fun testArrayInsertExtraRBrace() = timeoutRunBlocking {
    // Insert an extra } inside an array — [ ] balanced, { } has surplus }.
    checkReparse(
      """
        [
          <caret>1,
          2
        ]
      """.trimIndent(), "}"
    )
  }

  @Test
  fun testDeeplyNestedCrossBraceImbalance() = timeoutRunBlocking {
    // Remove ] from deeply nested structure — object's { } fine, [ ] broken at depth.
    checkReparse(
      """
        {
          "a": {
            "b": [
              {"c": [1, 2, <selection>3]</selection>}
            ]
          }
        }
      """.trimIndent(), "3"
    )
  }

  @Test
  fun testSwapBracesInNestedStructure() = timeoutRunBlocking {
    // Replace { with [ in nested object — both brace kinds become inconsistent.
    checkReparse(
      """
        [
          <selection>{</selection>"a": 1},
          [2, 3]
        ]
      """.trimIndent(), "["
    )
  }

  // IJPL-256275: a host language can embed a JSON fragment. The fragment then reports the language
  // of the host file, which is no JSON dialect.

  @Test
  fun testHostFileLanguageLogsNoError() = timeoutRunBlocking {
    assumeTrue(JsonLazyParsing, "The test needs the lazy OBJECT element type")

    edtWriteAction {
      file.setBinaryContent("{\"a\": 1}".toByteArray())
      PsiDocumentManager.getInstance(project).commitAllDocuments()
    }

    val objectNode = readAction {
      val psiFile = PsiManager.getInstance(project).findFile(file)!! as JsonFile
      (psiFile.topLevelValue as JsonObject).node
    }
    val elementType = objectNode.elementType as IReparseableElementType

    val loggedErrors = CopyOnWriteArrayList<String>()
    LoggedErrorProcessor.executeWith<Throwable>(object : LoggedErrorProcessor() {
      override fun processError(category: String, message: String, details: Array<out String>, t: Throwable?): Set<Action> {
        // The processor is global. Keep only the error under test.
        if (message.contains("No syntax definition found")) {
          loggedErrors.add(message)
        }
        return Action.NONE
      }
    }) {
      runReadActionBlocking {
        assertTrue(elementType.isReparseable(objectNode, "{\"a\": 2}", PlainTextLanguage.INSTANCE, project))
      }
    }

    assertEquals(emptyList<String>(), loggedErrors)
  }

  @Test
  fun testReparseJson5() = timeoutRunBlocking {
    assumeTrue(JsonLazyParsing, "The test needs the lazy OBJECT element type")

    // A hexadecimal number is JSON5 only. A plain JSON lexer turns it into an error.
    checkReparse("{a: 0xFF<caret>}", ", b: 0x10", json5File)
    assertNoErrors(json5File)

    // The plain JSON parser turns "0xFF" into an error, so the tree shows which dialect parsed it.
    // The reparse check looks at the brace balance only, so it needs a different JSON5 construct.
    // A line continuation in a string is JSON5 only. A plain JSON lexer ends the string at the line
    // end. It then counts the "}" on the next line as a brace, and finds the block unbalanced.
    val objectNode = readAction {
      val psiFile = PsiManager.getInstance(project).findFile(json5File)!! as JsonFile
      (psiFile.topLevelValue as JsonObject).node
    }
    val elementType = objectNode.elementType as IReparseableElementType
    readAction {
      assertTrue(elementType.isReparseable(objectNode, "{a: \"x\\\n}y\"}", Json5Language.INSTANCE, project))
    }
  }

  private suspend fun assertNoErrors(target: VirtualFile) {
    readAction {
      val psiFile: PsiFile = PsiManager.getInstance(project).findFile(target)!!
      val error = PsiTreeUtil.findChildOfType(psiFile, PsiErrorElement::class.java)
      assertNull(error, "Unexpected parse error: ${error?.errorDescription}")
    }
  }

  private suspend fun checkReparse(text: String, insertion: String, target: VirtualFile = file) {
    val textWithMarkup = text.trimIndent()
    val (cleanText, startOffset, endOffset) = extractMarkup(textWithMarkup)

    edtWriteAction {
      target.setBinaryContent(cleanText.toByteArray())
      PsiDocumentManager.getInstance(project).commitAllDocuments()
    }

    val document = readAction { FileDocumentManager.getInstance().getDocument(target)!! }
    val psiFile = readAction { PsiManager.getInstance(project).findFile(target)!! }

    readAction {
      PsiTestUtil.checkFileStructure(psiFile)
    }

    edtWriteAction {
      CommandProcessor.getInstance().executeCommand(project, Runnable {
        document.replaceString(startOffset, endOffset, insertion)
        PsiDocumentManager.getInstance(project).commitAllDocuments()
      }, "insert text", null)
    }

    readAction {
      PsiTestUtil.checkFileStructure(psiFile)
    }
  }

  private fun extractMarkup(textWithMarkup: String): Triple<String, Int, Int> {
    if (textWithMarkup.contains("<caret>")) {
      val offset = textWithMarkup.indexOf("<caret>")
      val cleanText = textWithMarkup.replace("<caret>", "")
      return Triple(cleanText, offset, offset)
    }

    if (textWithMarkup.contains("<selection>")) {
      val startOffset = textWithMarkup.indexOf("<selection>")
      val endOffset = textWithMarkup.indexOf("</selection>") - "<selection>".length
      val cleanText = textWithMarkup.replace("<selection>", "").replace("</selection>", "")
      return Triple(cleanText, startOffset, endOffset)
    }

    throw IllegalStateException("No markup found in text: $textWithMarkup")
  }
}