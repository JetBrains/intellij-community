// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.formatter.common

import com.intellij.formatting.Alignment
import com.intellij.formatting.Block
import com.intellij.formatting.BlockEx
import com.intellij.formatting.ChildAttributes
import com.intellij.formatting.DependantSpacingImpl
import com.intellij.formatting.Indent
import com.intellij.formatting.IndentImpl
import com.intellij.formatting.Spacing
import com.intellij.formatting.Wrap
import com.intellij.injected.editor.DocumentWindow
import com.intellij.lang.ASTNode
import com.intellij.lang.Language
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.formatter.WhiteSpaceFormattingStrategy
import com.intellij.psi.formatter.WhiteSpaceFormattingStrategyFactory
import com.intellij.util.SmartList
import com.intellij.util.asSafely
import com.intellij.util.text.TextRangeUtil
import com.intellij.util.text.escLBr
import org.jetbrains.annotations.ApiStatus

/**
 * Builds formatter blocks for an injection that can have more than one fragment in the host.
 *
 * The builder maps each injected block to its own host range with [InjectedLanguageManager.injectedToHost].
 * It does not apply one offset to all blocks.
 *
 * Host text between two fragments is not whitespace in every host, for example, `> ` in a Markdown blockquote.
 * The builder covers each such text with a host block that has read-only spacing.
 *
 * @param hostBlocks host blocks inside the injection, for example, template blocks.
 * The builder puts them between the injected blocks.
 * @param rootLanguage the language of the top injected block. When it is `null`, the builder uses the injected language.
 */
@ApiStatus.Internal
open class MultiFragmentInjectedLanguageBlockBuilder @JvmOverloads constructor(
  settings: CodeStyleSettings,
  private val hostBlocks: List<Block> = emptyList(),
  private val rootLanguage: Language? = null,
) : DefaultInjectedLanguageBlockBuilder(settings) {

  override fun supportsMultipleFragments(): Boolean = true

  private lateinit var injectionHost: ASTNode
  private lateinit var injectedFile: PsiFile
  private lateinit var injectionLanguage: Language
  private lateinit var whiteSpaceStrategy: WhiteSpaceFormattingStrategy
  private var injectionHostBlocks: List<Block> = hostBlocks
  private val readOnlyHostBlocks: MutableSet<Block> = HashSet()

  override fun addInjectedBlocks(result: MutableList<in Block>,
                                 injectionHost: ASTNode,
                                 wrap: Wrap?,
                                 alignment: Alignment?,
                                 indent: Indent?): Boolean {
    this.injectionHost = injectionHost
    whiteSpaceStrategy = WhiteSpaceFormattingStrategyFactory.getStrategy(injectionHost.psi.language)
    return super.addInjectedBlocks(result, injectionHost, wrap, alignment, indent)
  }

  override fun addInjectedLanguageBlocks(result: MutableList<in Block>,
                                         injectedFile: PsiFile,
                                         indent: Indent?,
                                         offset: Int,
                                         injectedEditableRange: TextRange?,
                                         shreds: List<PsiLanguageInjectionHost.Shred>) {
    this.injectedFile = injectedFile
    injectionHostBlocks = (hostBlocks + createBlocksBetweenFragments(shreds)).sortedBy { it.textRange.startOffset }
    return super.addInjectedLanguageBlocks(result, injectedFile, indent, offset, injectedEditableRange, shreds)
  }

  private fun createBlocksBetweenFragments(shreds: List<PsiLanguageInjectionHost.Shred>): List<Block> {
    val fragmentRanges = shreds.mapNotNull { shred -> shred.host?.let { shred.rangeInsideHost.shiftRight(it.textRange.startOffset) } }
    val hostText = injectionHost.chars
    val hostStart = injectionHost.startOffset
    return fragmentRanges.zipWithNext { previous, next -> TextRange(previous.endOffset, next.startOffset) }
      .filterNot { it.isEmpty }
      .flatMap { gap -> TextRangeUtil.excludeRanges(gap, hostBlocks.map { it.textRange }) }
      .flatMap { gap -> nonWhitespaceRanges(hostText, gap.shiftLeft(hostStart)) }
      .mapNotNull { range ->
        val leaf = injectionHost.findLeafElementAt(range.startOffset) ?: return@mapNotNull null
        createBlockAfterInjection(leaf, null, null, Indent.getNoneIndent(), range.shiftRight(hostStart))
          ?.also { readOnlyHostBlocks.add(it) }
      }
  }

  private fun nonWhitespaceRanges(text: CharSequence, range: TextRange): List<TextRange> {
    val result = SmartList<TextRange>()
    var start = -1
    for (offset in range.startOffset until range.endOffset) {
      val whitespace = text[offset].isFormatterWhiteSpace()
      if (!whitespace && start == -1) start = offset
      if (whitespace && start != -1) {
        result.add(TextRange(start, offset))
        start = -1
      }
    }
    if (start != -1) result.add(TextRange(start, range.endOffset))
    return result
  }

  /**
   * Checks the character with the white space strategy of the host language.
   * The formatter uses this strategy to find the text that a block must cover.
   * [Char.isWhitespace] also accepts characters such as NBSP, and the formatter does not.
   */
  private fun Char.isFormatterWhiteSpace(): Boolean = whiteSpaceStrategy.check(toString(), 0, 1) == 1

  override fun createBlockBeforeInjection(node: ASTNode, wrap: Wrap?, alignment: Alignment?, indent: Indent?, range: TextRange): Block? =
    super.createBlockBeforeInjection(node, wrap, alignment, indent, removeIndentFromRange(range))

  /**
   * Removes the indent of the first injected line from the end of [range].
   * The whitespace at the start of [range] is host text, and the builder keeps it.
   */
  private fun removeIndentFromRange(range: TextRange): TextRange {
    val text = range.shiftLeft(injectionHost.startOffset).subSequence(injectionHost.chars)
    val trailingWhitespace = text.takeLastWhile { it.isFormatterWhiteSpace() }.length
    return if (trailingWhitespace < range.length) TextRange(range.startOffset, range.endOffset - trailingWhitespace) else range
  }

  private fun injectedToHost(textRange: TextRange): TextRange =
    InjectedLanguageManager.getInstance(injectedFile.project).injectedToHost(injectedFile, textRange)

  private fun hostToInjected(textRange: TextRange): TextRange? {
    val documentWindow = PsiDocumentManager.getInstance(injectedFile.project).getCachedDocument(injectedFile) as? DocumentWindow
                         ?: return null
    return TextRange(documentWindow.hostToInjected(textRange.startOffset), documentWindow.hostToInjected(textRange.endOffset))
  }

  override fun createInjectedBlock(node: ASTNode,
                                   originalBlock: Block,
                                   indent: Indent?,
                                   offset: Int,
                                   range: TextRange,
                                   language: Language): Block {
    this.injectionLanguage = language
    val trimmedRange = trimBlank(range, range.substring(node.text))
    return InjectedBlockWrapper(originalBlock, injectedToHost(trimmedRange), trimmedRange, injectionHostBlocks, indent, rootLanguage ?: language, -1)
  }

  private fun trimBlank(range: TextRange, substring: String): TextRange {
    val preWS = substring.takeWhile { it.isFormatterWhiteSpace() }.count()
    val postWS = substring.takeLastWhile { it.isFormatterWhiteSpace() }.count()
    return if (preWS < range.length) range.run { TextRange(startOffset + preWS, endOffset - postWS) } else range
  }

  private inner class InjectedBlockWrapper(val original: Block,
                                           val rangeInHost: TextRange,
                                           val myRange: TextRange,
                                           hostBlocks: Collection<Block>,
                                           private val indent: Indent?,
                                           private val language: Language?,
                                           val originalIndex: Int) : BlockEx {

    override fun toString(): String = "InjectedBlockWrapper($original, $myRange," +
                                      " rangeInRoot = $textRange '${textRange.substring(injectionHost.psi.containingFile.text).escLBr()}')"

    override fun getTextRange(): TextRange {
      val subBlocks = subBlocks
      if (subBlocks.isEmpty()) return rangeInHost
      return TextRange.create(
        subBlocks.first().textRange.startOffset.coerceAtMost(rangeInHost.startOffset),
        subBlocks.last().textRange.endOffset.coerceAtLeast(rangeInHost.endOffset))
    }

    private val myBlocks by lazy(LazyThreadSafetyMode.NONE) {
      SmartList<Block>().also { result ->
        val hostBlocksQueue = ArrayDeque(hostBlocks)
        for ((index, block) in original.subBlocks.withIndex()) {
          myRange.intersection(block.textRange)?.let { blockRange ->
            val blockRangeInHost = injectedToHost(blockRange)

            fun createInnerWrapper(blockRangeInHost: TextRange, blockRange: TextRange, hostBlocks: Collection<Block>) =
              InjectedBlockWrapper(block,
                                   blockRangeInHost,
                                   blockRange,
                                   hostBlocks,
                                   replaceAbsoluteIndent(block),
                                   block.asSafely<BlockEx>()?.language ?: injectionLanguage,
                                   index)

            result.addAll(hostBlocksQueue.popWhile { it.textRange.endOffset <= blockRangeInHost.startOffset })
            if (block.subBlocks.isNotEmpty()) {
              result.add(createInnerWrapper(
                blockRangeInHost,
                blockRange,
                hostBlocksQueue.popWhile { blockRangeInHost.contains(it.textRange) }))
            }
            else {
              val inner = hostBlocksQueue.popWhile { blockRangeInHost.contains(it.textRange) }
              val remainingInjectedRanges = TextRangeUtil.excludeRanges(blockRangeInHost, inner.map { it.textRange })
              val splitInjectedLeaves =
                remainingInjectedRanges.map { part -> createInnerWrapper(part, hostToInjected(part) ?: blockRange, emptyList()) }
              result.addAll((splitInjectedLeaves + inner).sortedBy { it.textRange.startOffset })
            }
          }
        }
        result.addAll(hostBlocksQueue)
      }
    }

    private fun replaceAbsoluteIndent(block: Block): Indent? = block.indent.asSafely<IndentImpl>()?.takeIf { it.isAbsolute }
      ?.run { IndentImpl(type, false, spaces, isRelativeToDirectParent, isEnforceIndentToChildren) } ?: block.indent

    override fun getSubBlocks(): List<Block> = myBlocks

    override fun getWrap(): Wrap? = original.wrap
    override fun getIndent(): Indent? = indent
    override fun getAlignment(): Alignment? = original.alignment
    override fun getChildAttributes(newChildIndex: Int): ChildAttributes = original.getChildAttributes(toOriginalIndex(newChildIndex))
    override fun isIncomplete(): Boolean = original.isIncomplete
    override fun isLeaf(): Boolean = original.isLeaf
    override fun getLanguage(): Language? = language

    override fun getSpacing(child1: Block?, child2: Block): Spacing? {
      if (child1 in readOnlyHostBlocks || child2 in readOnlyHostBlocks) return Spacing.getReadOnlySpacing()
      val spacing = original.getSpacing(child1?.unwrap(), child2.unwrap())
      if (spacing !is DependantSpacingImpl) return spacing
      // The original spacing has the dependent ranges in the injected file.
      return spacing.withDependentRegionRanges(spacing.dependentRegionRanges.map(::injectedToHost))
    }

    /**
     * Converts an index in [getSubBlocks] to an index in the subblocks of [original].
     * The host blocks and the split leaves make these indices different.
     */
    private fun toOriginalIndex(newChildIndex: Int): Int {
      val index = newChildIndex.coerceAtLeast(0)
      val next = subBlocks.drop(index).firstNotNullOfOrNull { it.asSafely<InjectedBlockWrapper>() }
      if (next != null) return next.originalIndex
      val previous = subBlocks.take(index).lastOrNull { it is InjectedBlockWrapper } as InjectedBlockWrapper?
      return previous?.originalIndex?.plus(1) ?: newChildIndex
    }
  }

  private fun Block.unwrap() = this.asSafely<InjectedBlockWrapper>()?.original ?: this

  private fun <T> ArrayDeque<T>.popWhile(pred: (T) -> Boolean): List<T> {
    if (this.isEmpty()) return emptyList()
    val result = SmartList<T>()
    while (this.isNotEmpty() && pred(this.first()))
      result.add(this.removeFirst())
    return result
  }
}
