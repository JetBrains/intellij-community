// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json

import com.intellij.json.psi.JsonFile
import com.intellij.lang.ASTNode
import com.intellij.openapi.application.EDT
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightFixture
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightProjectFixture
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.impl.BlockSupportImpl
import com.intellij.psi.impl.ChangedPsiRangeUtil
import com.intellij.psi.impl.DebugUtil
import com.intellij.psi.impl.source.PsiFileImpl
import com.intellij.psi.impl.source.tree.LeafElement
import com.intellij.psi.tree.IReparseableLeafElementType
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * A string, number, identifier or comment leaf of a JSON file gets a new text without a reparse of the file.
 * The condition is that the lexer reads the new text as the same token, see [JsonReparseableTokenType].
 * The direct tests call the check on a leaf. The typing tests compare the tree after each edit with a new parse.
 */
@TestApplication
class JsonLeafReparseTest {
  private val projectFixture = codeInsightProjectFixture()
  private val myFixture by codeInsightFixture(projectFixture)

  @Test
  fun testStringValueAcceptsAChar() = onEdt {
    assertAccepts("""{"a": "val<caret>ue"}""", "\"valxue\"")
  }

  @Test
  fun testStringKeyAcceptsAChar() = onEdt {
    assertAccepts("""{"ke<caret>y": 1}""", "\"kexy\"")
  }

  @Test
  fun testStringRefusesABackslashBeforeTheQuote() = onEdt {
    assertRefuses("""{"a": "value<caret>"}""", "\"value\\\"")
  }

  @Test
  fun testStringRefusesALineBreak() = onEdt {
    assertRefuses("""{"a": "val<caret>ue"}""", "\"val\nue\"")
  }

  @Test
  fun testStringRefusesAnEmptyText() = onEdt {
    assertRefuses("""{"a": "val<caret>ue"}""", "")
  }

  @Test
  fun testNumberAcceptsADigit() = onEdt {
    assertAccepts("""[1, 2<caret>, 3]""", "25")
  }

  @Test
  fun testNumberRefusesATypeChange() = onEdt {
    // The lexer reads "2x" as one identifier, not as a number.
    assertRefuses("""[1, 2<caret>, 3]""", "2x")
  }

  @Test
  fun testNumberAtFileEndAcceptsADigit() = onEdt {
    assertAccepts("""4<caret>2""", "412")
  }

  @Test
  fun testIdentifierRefusesAKeyword() = onEdt {
    assertRefuses("""[tru<caret>]""", "true")
  }

  @Test
  fun testIdentifierAcceptsASlashBeforeASpace() = onEdt {
    // An identifier takes a slash, but the space keeps it apart from the comment.
    assertAccepts("""[ab<caret> // c
    ]""", "ab/")
  }

  @Test
  fun testNumberRefusesAMergeWithTheNextChar() = onEdt {
    // "1" and "+" are two tokens, but "1e+5" is one number.
    assertRefuses("""[1<caret>+5]""", "1e")
  }

  @Test
  fun testLineCommentAcceptsAChar() = onEdt {
    assertAccepts("""[1 // c<caret>
    ]""", "// cx")
  }

  @Test
  fun testBlockCommentRefusesAnOpenEnd() = onEdt {
    assertRefuses("""[1 /* c<caret> */, 2]""", "/* c *")
  }

  @Test
  fun testBlockCommentRefusesARemovedEnd() = onEdt {
    // Without "*/", the comment takes "**" and the "]" after it.
    assertRefuses("""[/* c<caret> */**]""", "/* c ")
  }

  @Test
  fun testTokenBeforeAnUnparsedObjectAcceptsAChar() = onEdt {
    assertAccepts("""{"a": 1<caret>, "b": {"c": {"d": 2}}}""", "12")
  }

  @Test
  fun testTokenAfterAnUnparsedObjectAcceptsAChar() = onEdt {
    assertAccepts("""{"b": {"c": {"d": 2}}, "a": 1<caret>}""", "12")
  }

  @Test
  fun testJson5StringAcceptsALineContinuation() = onEdt {
    assertAccepts("{a: 'x<caret>\\\ny'}", "'xz\\\ny'", "a.json5")
  }

  @Test
  fun testJsonStringRefusesALineContinuation() = onEdt {
    assertRefuses("{\"a\": \"x<caret>y\"}", "\"xz\\\ny\"")
  }

  @Test
  fun testTypingInAStringValue() = onEdt {
    checkTyping("""{"a": "val<caret>ue", "b": [1, 2]}""", "xyz", leafReparse = true)
  }

  @Test
  fun testTypingInAStringThatAnErrorNames() = onEdt {
    // The property "k" has no colon, and its error names the next token: ':' expected, got '"b"'.
    checkTyping("""{"a": 1, "k" "b<caret>": 2}""", "x", leafReparse = false)
  }

  @Test
  fun testTypingAQuoteInAStringValue() = onEdt {
    checkTyping("""{"a": "val<caret>ue", "b": [1, 2]}""", "\"", leafReparse = false)
  }

  @Test
  fun testTypingABackslashInAStringValue() = onEdt {
    checkTyping("""{"a": "value<caret>", "b": [1, 2]}""", "\\", leafReparse = false)
  }

  @Test
  fun testTypingInANumber() = onEdt {
    checkTyping("""[1, 2<caret>, 3]""", "5e+", leafReparse = true)
  }

  @Test
  fun testTypingInAComment() = onEdt {
    checkTyping("""[1, // c<caret>
      2]""", " more", leafReparse = true)
  }

  private fun assertAccepts(text: String, newText: String, fileName: String = "a.json") {
    val leaf = leafAtCaret(text, fileName)
    val newLeaf = reparse(leaf, newText)
    assertNotNull(newLeaf, "the leaf must get the text $newText")
    assertEquals(newText, newLeaf!!.text)
    assertEquals(leaf.elementType, newLeaf.elementType)
  }

  private fun assertRefuses(text: String, newText: String, fileName: String = "a.json") {
    assertNull(reparse(leafAtCaret(text, fileName), newText), "the leaf must not get the text $newText")
  }

  private fun reparse(leaf: ASTNode, newText: String): ASTNode? {
    @Suppress("UNCHECKED_CAST")
    val type = leaf.elementType as IReparseableLeafElementType<ASTNode>
    return type.reparseLeaf(leaf, newText)
  }

  private fun leafAtCaret(text: String, fileName: String): ASTNode {
    myFixture.configureByText(fileName, text)
    // The caret is after a char of the leaf, so the leaf holds the char before the caret.
    return myFixture.file.findElementAt(myFixture.caretOffset - 1)!!.node
  }

  /**
   * Types [typedText] at the caret, one char at a time.
   * Before each char, it checks that the platform reparses only the leaf at the caret, if [leafReparse] is true.
   * After each char, it compares the tree with a new parse of the text.
   */
  private fun checkTyping(text: String, typedText: String, leafReparse: Boolean) {
    myFixture.configureByText("a.json", text)
    val file = myFixture.file as JsonFile
    for (char in typedText) {
      assertEquals(leafReparse, reparsesLeaf(file, char), "the leaf reparse of '$char'")
      myFixture.type(char)
      PsiDocumentManager.getInstance(myFixture.project).commitDocument(myFixture.editor.document)
      PsiTestUtil.checkFileStructure(file)
      val fresh = PsiFileFactory.getInstance(myFixture.project).createFileFromText("fresh.json", JsonLanguage.INSTANCE, file.text)
      assertEquals(treeText(fresh), treeText(file), "the tree after typing '$char'")
    }
  }

  /** Returns true if the platform reparses only a leaf when [char] is inserted at the caret. */
  private fun reparsesLeaf(file: JsonFile, char: Char): Boolean {
    val offset = myFixture.caretOffset
    val newText = StringBuilder(file.text).insert(offset, char)
    val fileImpl = file as PsiFileImpl
    val changedRange = ChangedPsiRangeUtil.getChangedPsiRange(file, fileImpl.treeElement!!, newText)
    assertNotNull(changedRange, "the text must change")
    val replaced = BlockSupportImpl.reparseIncrementally(fileImpl, file.node, changedRange!!, newText)
    return replaced?.oldNode is LeafElement
  }

  /** The tree of [file] without its first line, which holds the file name. */
  private fun treeText(file: PsiFile): String = DebugUtil.psiToString(file, false).substringAfter('\n')

  private fun onEdt(action: () -> Unit) {
    timeoutRunBlocking {
      withContext(Dispatchers.EDT) { action() }
    }
  }
}
