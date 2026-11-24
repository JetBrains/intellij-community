// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json

import com.intellij.json.psi.JsonFile
import com.intellij.json.syntax.SUFFIX_LOOKAHEAD
import com.intellij.json.syntax.canReplaceJsonToken
import com.intellij.lang.ASTFactory
import com.intellij.lang.ASTNode
import com.intellij.lang.tree.util.parents
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.platform.syntax.SyntaxElementType
import com.intellij.psi.TokenType
import com.intellij.psi.impl.source.tree.LazyParseableElement
import com.intellij.psi.impl.source.tree.LeafElement
import com.intellij.psi.tree.IReparseableLeafElementType
import org.jetbrains.annotations.ApiStatus

/**
 * A JSON token whose leaf gets a new text without a reparse of the file, if the lexer reads the new text as the same token.
 * See [canReplaceJsonToken] for the check.
 * The check uses the lexer of the JSON dialect of the file, so a JSON5 string can continue on the next line.
 * A leaf that an error message can name keeps the reparse of its container, see [isNamedByError].
 */
@ApiStatus.Internal
class JsonReparseableTokenType(debugName: String, private val syntaxType: SyntaxElementType) :
  JsonTokenType(debugName), IReparseableLeafElementType<ASTNode> {

  override fun reparseLeaf(leaf: ASTNode, newText: CharSequence): ASTNode? {
    if (!RegistryManager.getInstance().`is`("json.reparse.leaves")) return null
    val file = leaf.psi?.containingFile as? JsonFile ?: return null
    if (file.viewProvider.languages.size != 1) return null
    if (isNamedByError(leaf)) return null
    val lexer = JsonElementFactory.findLexer(JsonDialectUtil.getLanguageOrDefaultJson(file))
    if (!canReplaceJsonToken(lexer, syntaxType, previousTokenText(leaf), newText, nextTokenText(leaf))) return null

    val newLeaf = ASTFactory.leaf(this, newText.toString())
    return if (newLeaf.javaClass == leaf.javaClass) newLeaf else null
  }
}

/**
 * Returns true if the message of an error element can hold the text of [leaf].
 * The parser names the current token in its errors, for example `':' expected, got '"b"'`.
 * Such an error is an empty error element just before the token, or an error element around it.
 * Only whitespace and comments can be between the empty error element and the token.
 */
private fun isNamedByError(leaf: ASTNode): Boolean {
  if (leaf.parents(false).any { it.elementType == TokenType.ERROR_ELEMENT }) return true
  var node: ASTNode = leaf
  while (true) {
    ProgressManager.checkCanceled()
    val previous = node.treePrev
    if (previous == null) {
      node = node.treeParent ?: return false
      continue
    }
    var last: ASTNode = previous
    while (!(last is LazyParseableElement && !last.isParsed)) {
      last = last.lastChildNode ?: break
    }
    if (last.elementType == TokenType.ERROR_ELEMENT) return true
    if (last.textLength > 0 && last.elementType != TokenType.WHITE_SPACE && last.elementType !in JsonTokenSets.JSON_COMMENTARIES) {
      return false
    }
    node = last
  }
}

/** Returns the text of the token before [leaf], or an empty text at the file start. */
private fun previousTokenText(leaf: ASTNode): CharSequence {
  var node: ASTNode = leaf
  while (true) {
    val previous = node.treePrev
    if (previous == null) {
      node = node.treeParent ?: return ""
    }
    else if (previous.textLength == 0) {
      node = previous
    }
    else {
      return lastTokenText(previous)
    }
  }
}

/** Returns the first [SUFFIX_LOOKAHEAD] chars of the token after [leaf], or an empty text at the file end. */
private fun nextTokenText(leaf: ASTNode): CharSequence {
  var node: ASTNode = leaf
  while (true) {
    val next = node.treeNext
    if (next == null) {
      node = node.treeParent ?: return ""
    }
    else if (next.textLength == 0) {
      node = next
    }
    else {
      return firstTokenText(next)
    }
  }
}

/**
 * Returns the text of the last token of [node], which has a text.
 * An unparsed object or array stays unparsed: its last token is its closing brace.
 * Without the closing brace, it ends at the file end, and the result is its full text.
 */
private fun lastTokenText(node: ASTNode): CharSequence {
  var current = node
  while (true) {
    if (current is LazyParseableElement && !current.isParsed) {
      val chars = current.chars
      val last = chars[chars.length - 1]
      return if (last == '}' || last == ']') chars.subSequence(chars.length - 1, chars.length) else chars
    }
    if (current is LeafElement) return current.chars
    var child = current.lastChildNode
    while (child != null && child.textLength == 0) child = child.treePrev
    current = child ?: return ""
  }
}

/**
 * Returns the first [SUFFIX_LOOKAHEAD] chars of the first token of [node], which has a text.
 * An unparsed object or array stays unparsed.
 */
private fun firstTokenText(node: ASTNode): CharSequence {
  var current = node
  while (true) {
    if (current is LazyParseableElement && !current.isParsed || current is LeafElement) {
      val chars = current.chars
      return chars.subSequence(0, minOf(chars.length, SUFFIX_LOOKAHEAD))
    }
    var child = current.firstChildNode
    while (child != null && child.textLength == 0) child = child.treeNext
    current = child ?: return ""
  }
}
