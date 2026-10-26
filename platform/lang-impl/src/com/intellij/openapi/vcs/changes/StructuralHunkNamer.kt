// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes

import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder
import com.intellij.ide.util.treeView.smartTree.SortableTreeElement
import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.lang.LanguageStructureViewBuilder
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.FileStatus
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.impl.source.PsiFileImpl
import com.intellij.ui.DeferredIcon
import com.intellij.ui.icons.RowIcon
import com.intellij.util.IconUtil
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.annotations.ApiStatus
import java.util.Collections
import java.util.IdentityHashMap
import javax.swing.Icon

/** A hunk of a line diff. The ranges are 0-based and half-open: `[vcsLine1, vcsLine2)` in the base, `[line1, line2)` in the current text. */
@ApiStatus.Internal
data class HunkLines(val vcsLine1: Int, val vcsLine2: Int, val line1: Int, val line2: Int)

/** An element or a line in the name of a hunk. A renamed element has its previous text. */
@ApiStatus.Internal
data class HunkNamePart(val text: @NlsSafe String, val status: FileStatus, val previousText: @NlsSafe String? = null)

/**
 * The name of a hunk: its changed elements and lines in source order. The detail is the first changed line of a modified element when
 * the presentation of the element does not show the change. The icon belongs to the first element. A hunk of blank lines has no parts.
 * A whitespace-only hunk changes nothing but spaces, tabs, and line breaks.
 */
@ApiStatus.Internal
data class HunkName(val parts: List<HunkNamePart>, val detail: HunkNamePart?, val icon: Icon?, val isWhitespaceOnly: Boolean = false)

/**
 * Names the hunks of a file diff by the innermost changed Structure View elements on their lines.
 *
 * The current lines of a hunk select added and modified elements; its base lines select removed elements. An element counts only when it
 * starts its line, so an object literal property or a lambda inside a line belongs to its parent. A container counts only for a change in
 * its header, before its first child. A changed line outside all named elements names the hunk by its own text; the first line with a word
 * that the other side of the hunk does not have comes first.
 */
@ApiStatus.Internal
object StructuralHunkNamer {
  private val LINE_PREFIX = Regex("[\\s\\-*+]*([A-Za-z_][\\w-]*\\s+)*")
  private val TRIVIAL_LINE = Regex("[{}()\\[\\];,<>/]*")
  private val LIST_MARKER = Regex("^[-*+]\\s+")
  private val WORD = Regex("[\\p{L}\\p{N}_]+")
  private const val MAX_LABEL_LENGTH = 200
  private const val RENAME_SIMILARITY = 0.5
  private const val MAX_RENAME_PAIRS = 100_000

  /** Creates a snapshot with its own virtual file and the original file context. */
  @JvmStatic
  @RequiresReadLock
  fun createSnapshot(original: PsiFile, text: String): PsiFile? {
    val snapshot = PsiFileFactory.getInstance(original.project)
      .createFileFromText(original.name, original.language, StringUtil.convertLineSeparators(text), true, false, false,
                          original.viewProvider.virtualFile) ?: return null
    for (language in snapshot.viewProvider.languages) {
      val root = snapshot.viewProvider.getPsi(language) as? PsiFileImpl ?: continue
      val originalRoot = original.viewProvider.getPsi(language) ?: continue
      root.setOriginalFile(originalRoot)
    }
    return snapshot
  }

  /** A null baseline marks every element as added. */
  @JvmStatic
  @RequiresReadLock
  fun name(before: PsiFile?, after: PsiFile, hunks: List<HunkLines>): List<HunkName> {
    val base = before?.let(::readSide) ?: Side(LineTable(""), emptyList())
    val current = readSide(after)
    assignIds(base.roots, current.roots, "", LineMapping(hunks))
    base.index()
    current.index()
    return hunks.map { HunkNameBuilder(base, current, it).build() }
  }

  private fun readSide(file: PsiFile): Side {
    val text = file.viewProvider.contents
    val lines = LineTable(text)
    val builder = LanguageStructureViewBuilder.getInstance().getStructureViewBuilder(file) as? TreeBasedStructureViewBuilder
                  ?: return Side(lines, emptyList())
    val model = builder.createStructureViewModel(null)
    try {
      val root = model.root
      val visited = Collections.newSetFromMap(IdentityHashMap<PsiElement, Boolean>())
      val elements = if (root.value is PsiFile) root.children.asList() else listOf(root)
      return Side(lines, readElements(elements, file, lines, visited, null, null))
    }
    finally {
      Disposer.dispose(model)
    }
  }

  private fun readElements(
    treeElements: List<TreeElement>,
    file: PsiFile,
    lines: LineTable,
    visited: MutableSet<PsiElement>,
    parent: Element?,
    group: Int?,
  ): List<Element> {
    val result = ArrayList<Element>()
    for (treeElement in treeElements) {
      ProgressManager.checkCanceled()
      val structureElement = treeElement as? StructureViewTreeElement
      val element = structureElement?.value as? PsiElement
      if (element == null || element is PsiFile) {
        result.addAll(readElements(treeElement.children.asList(), file, lines, visited, parent, group))
        continue
      }
      if (!element.isValid || element.containingFile?.viewProvider !== file.viewProvider || !visited.add(element)) continue
      val comparison = structureElement.valueForComparison as? PsiElement
      val range = comparison?.takeIf { it.isValid && it.containingFile?.viewProvider === file.viewProvider }?.textRange
      val presentation = treeElement.presentation
      val text = presentation.presentableText ?: (element as? PsiNamedElement)?.name
      if (range == null || text == null) {
        // An element without a name, such as a YAML sequence item, lends its children to the parent as one group.
        result.addAll(readElements(treeElement.children.asList(), file, lines, visited, parent, result.size))
        continue
      }
      val name = (treeElement as? SortableTreeElement)?.alphaSortKey ?: (element as? PsiNamedElement)?.name ?: text
      val startLine = lines.lineOf(range.startOffset)
      val endLine = lines.lineOf(maxOf(range.startOffset, range.endOffset - 1)) + 1
      val node = Element(
        kind = element.javaClass.name,
        key = element.javaClass.name + ":" + name,
        presentation = text,
        text = structureElement.presentableTextForComparison ?: text,
        source = lines.text.subSequence(range.startOffset, range.endOffset).toString(),
        icon = presentation.getIcon(false),
        startLine = startLine,
        endLine = endLine,
        eligible = startsLine(lines.text.subSequence(lines.lineStart(startLine), range.startOffset), text),
        group = group,
        parent = parent,
      )
      node.children.addAll(readElements(treeElement.children.asList(), file, lines, visited, node, null))
      result.add(node)
    }
    return result
  }

  /**
   * True when only whitespace, list markers, and plain words such as modifiers come before the element on its line, or the name of the
   * element with a separator, as in `onPay: () => void`.
   */
  private fun startsLine(prefix: CharSequence, text: String): Boolean {
    if (LINE_PREFIX.matches(prefix)) return true
    val name = prefix.trim().trimEnd(':', '=').trim()
    return name.isNotEmpty() && WORD.matches(name) && text.startsWith(name)
  }

  /**
   * Matches elements by kind and name. Duplicate names fall back to the presentation. The presentation leaves out the compared value, so a
   * duplicate property keeps its identity when its value changes. A current duplicate takes the identity of the base duplicate on the same
   * line of the line diff, and the other duplicates pair in their order. So an inserted duplicate gets a new identity.
   */
  private fun assignIds(before: List<Element>, after: List<Element>, parentId: String, lines: LineMapping) {
    val oldGroups = before.groupBy { it.key }
    val groups = after.groupBy { it.key }
    for (key in oldGroups.keys + groups.keys) {
      val oldGroup = oldGroups[key].orEmpty()
      val group = groups[key].orEmpty()
      val usePresentation = oldGroup.size > 1 || group.size > 1
      fun identity(element: Element) = if (usePresentation) key + ":" + element.presentation else key
      val occurrences = HashMap<String, Int>()
      fun newId(element: Element): String {
        val identity = identity(element)
        val occurrence = occurrences.getOrDefault(identity, 0)
        occurrences[identity] = occurrence + 1
        return "$parentId/${identity.length}:$identity:$occurrence"
      }
      for (element in oldGroup) element.id = newId(element)
      val unpaired = oldGroup.toMutableList()
      val byCurrentLine = oldGroup.associateBy { lines.current(it.startLine) }
      val rest = ArrayList<Element>()
      for (element in group) {
        val old = byCurrentLine[element.startLine]?.takeIf { it in unpaired && identity(it) == identity(element) }
        if (old == null) {
          rest.add(element)
          continue
        }
        element.id = old.id
        unpaired.remove(old)
      }
      for (element in rest) {
        val old = unpaired.firstOrNull { identity(it) == identity(element) }
        if (old != null) unpaired.remove(old)
        element.id = old?.id ?: newId(element)
      }
      val previous = oldGroup.associateBy { it.id }
      for (element in group) {
        assignIds(previous[element.id]?.children.orEmpty(), element.children, element.id, lines)
      }
      val currentIds = group.mapTo(HashSet()) { it.id }
      for (element in oldGroup) {
        if (element.id !in currentIds) assignIds(element.children, emptyList(), element.id, lines)
      }
    }
  }

  /** Maps a base line outside the hunks to its line in the current version. A base line inside a hunk has no counterpart. */
  private class LineMapping(hunks: List<HunkLines>) {
    private val hunks = hunks.sortedBy { it.vcsLine1 }

    fun current(baseLine: Int): Int? {
      // The last hunk that starts at or before the line gives the shift.
      val found = hunks.binarySearch { it.vcsLine1.compareTo(baseLine) }
      val index = if (found >= 0) found else -found - 2
      if (index < 0) return baseLine
      val hunk = hunks[index]
      return if (baseLine < hunk.vcsLine2) null else baseLine + hunk.line2 - hunk.vcsLine2
    }
  }

  private class HunkNameBuilder(private val base: Side, private val current: Side, private val hunk: HunkLines) {
    private val status = when {
      hunk.vcsLine1 == hunk.vcsLine2 -> FileStatus.ADDED
      hunk.line1 == hunk.line2 -> FileStatus.DELETED
      else -> FileStatus.MODIFIED
    }

    /** The words of the current lines that the base lines of the hunk do not have, and the other way round. */
    private val newWords: Set<String> = words(current, hunk.line1, hunk.line2) - words(base, hunk.vcsLine1, hunk.vcsLine2)
    private val removedWords: Set<String> = words(base, hunk.vcsLine1, hunk.vcsLine2) - words(current, hunk.line1, hunk.line2)

    fun build(): HunkName {
      val parts = ArrayList<Part>()
      val shown = HashSet<Element>()
      for (element in changedElements(current, current.roots, hunk.line1, hunk.line2)) {
        parts.add(Part(element, statusOf(element) ?: continue, element.startLine - hunk.line1, isBase = false))
        shown.add(element)
      }
      for (element in changedElements(base, base.roots, hunk.vcsLine1, hunk.vcsLine2)) {
        val line = element.startLine - hunk.vcsLine1
        // A modified element reports its current version, so that a deletion inside a method names the method.
        val counterpart = current.byId[element.id]
        if (counterpart == null) parts.add(Part(element, FileStatus.DELETED, line, isBase = true))
        else if (shown.add(counterpart)) parts.add(Part(counterpart, FileStatus.MODIFIED, line, isBase = false))
      }
      val named = withoutImpliedParts(parts)
      val entries = ArrayList<Entry>()
      val renamed = HashSet<Element>()
      for ((removed, added) in renames(named)) {
        if (removed.element in renamed || added.element in renamed) continue
        renamed.add(added.element)
        renamed.add(removed.element)
        entries.add(Entry(minOf(removed.line, added.line), HunkNamePart(added.element.text, FileStatus.MODIFIED, removed.element.text), added))
      }
      for (part in named) {
        if (part.element !in renamed) entries.add(Entry(part.line, HunkNamePart(label(part), part.status), part))
      }
      looseLine(named)?.let { entries.add(it) }
      entries.sortBy { it.line }
      // Two elements on one line can show the same text, as a Vue component option and its property do.
      val labels = HashSet<Pair<String, String?>>()
      entries.retainAll { labels.add(it.name.text to it.name.previousText) }
      val first = entries.firstOrNull { it.part != null }?.part
      val whitespaceOnly = entries.isNotEmpty() && newWords.isEmpty() && removedWords.isEmpty() &&
                           text(base, hunk.vcsLine1, hunk.vcsLine2).filterNot(Char::isWhitespace) ==
                           text(current, hunk.line1, hunk.line2).filterNot(Char::isWhitespace)
      val detail = if (whitespaceOnly) null else detail(entries)
      return HunkName(entries.map { it.name }, detail, first?.element?.icon?.let(::snapshotIcon), whitespaceOnly)
    }

    private fun statusOf(element: Element): FileStatus? {
      val old = base.byId[element.id] ?: return FileStatus.ADDED
      return if (old.source == element.source) null else FileStatus.MODIFIED
    }

    private fun isChanged(side: Side, element: Element): Boolean =
      if (side === base) current.byId[element.id]?.source != element.source else statusOf(element) != null

    /**
     * The innermost changed elements on lines `[from, to)`. A container counts when the hunk changes its header: the lines before its first
     * child. A comment between two children is not a change of the container.
     *
     * Each call visits all elements of the side. So the names of all hunks of a file cost the hunk count times the element count.
     */
    private fun changedElements(side: Side, elements: List<Element>, from: Int, to: Int): List<Element> {
      if (from >= to) return emptyList()
      val result = ArrayList<Element>()
      for (element in elements) {
        ProgressManager.checkCanceled()
        if (!element.eligible) continue
        val inner = changedElements(side, element.children, from, to)
        if (element.overlaps(from, to) && isChanged(side, element)) {
          val firstChild = element.children.filter { it.eligible }.minOfOrNull { it.startLine }
          val headerEnd = if (firstChild == null) element.endLine else minOf(element.endLine, maxOf(element.startLine + 1, firstChild))
          val changesHeader = (maxOf(from, element.startLine) until minOf(to, headerEnd)).any { !isTrivial(side.lines.line(it)) }
          if (changesHeader || firstChild == null) result.add(element)
        }
        result.addAll(inner)
      }
      return result
    }

    /** Removes the children of added and removed elements, and all but the first element of an added or removed group. */
    private fun withoutImpliedParts(parts: List<Part>): List<Part> {
      val createdBase = HashSet<Element>()
      val createdCurrent = HashSet<Element>()
      val groupStarts = HashMap<GroupKey, Int>()
      for (part in parts) {
        if (part.status == FileStatus.MODIFIED) continue
        (if (part.isBase) createdBase else createdCurrent).add(part.element)
        val group = part.element.group ?: continue
        groupStarts.merge(GroupKey(part.status, part.isBase, part.element.parent, group), part.line, ::minOf)
      }
      return parts.filter { part ->
        ProgressManager.checkCanceled()
        val created = if (part.isBase) createdBase else createdCurrent
        val group = part.element.group
        generateSequence(part.element.parent) { it.parent }.none { it in created } &&
        (group == null || part.status == FileStatus.MODIFIED ||
         part.line <= groupStarts.getValue(GroupKey(part.status, part.isBase, part.element.parent, group)))
      }
    }

    /**
     * The rename candidates: removed and added elements of one kind under one parent, the most similar pairs first, so that a removed
     * neighbor does not take the place of the renamed element. A hunk compares at most [MAX_RENAME_PAIRS] pairs. A group that needs more
     * pairs shows its elements as added and removed.
     */
    private fun renames(parts: List<Part>): List<Candidate> {
      val added = parts.filter { it.status == FileStatus.ADDED }.groupBy { it.element.kind to it.element.parent?.id }
      val words = HashMap<Element, Words>()
      val candidates = ArrayList<Candidate>()
      var remainingPairs = MAX_RENAME_PAIRS
      for ((key, removedGroup) in parts.filter { it.status == FileStatus.DELETED }.groupBy { it.element.kind to it.element.parent?.id }) {
        val addedGroup = added[key] ?: continue
        val pairs = removedGroup.size.toLong() * addedGroup.size
        if (pairs > remainingPairs) continue
        remainingPairs -= pairs.toInt()
        for (removed in removedGroup) {
          ProgressManager.checkCanceled()
          val removedWords = words.getOrPut(removed.element) { Words(removed.element) }
          for (addedPart in addedGroup) {
            val similarity = removedWords.similarity(words.getOrPut(addedPart.element) { Words(addedPart.element) })
            if (similarity >= RENAME_SIMILARITY) candidates.add(Candidate(removed, addedPart, similarity))
          }
        }
      }
      candidates.sortByDescending { it.similarity }
      return candidates
    }

    private fun label(part: Part): String {
      val element = part.element
      if (!element.isSingleLine) return element.text
      val shows = if (part.status == FileStatus.MODIFIED) changedWords(element).all { element.text.contains(it) } else isRich(element.text)
      if (shows) return element.text
      val side = if (part.isBase) base else current
      return lineLabel(side.lines.line(element.startLine))
    }

    /** The words of the current element that its base version does not have. */
    private fun changedWords(element: Element): List<String> {
      val old = base.byId[element.id] ?: return emptyList()
      return wordDifference(element.source, old.source)
    }

    /** The words of the base version that the current element does not have. */
    private fun removedWords(element: Element): List<String> {
      val old = base.byId[element.id] ?: return emptyList()
      return wordDifference(old.source, element.source)
    }

    /**
     * A changed line before all named elements that no named element holds. A line inside an unchanged element is not a change of its own:
     * a comma after an element changes when the next element is added. A line with a new word comes before the other lines.
     */
    private fun looseLine(parts: List<Part>): Entry? {
      val isBase = status == FileStatus.DELETED
      val side = if (isBase) base else current
      val from = if (isBase) hunk.vcsLine1 else hunk.line1
      val to = if (isBase) hunk.vcsLine2 else hunk.line2
      val firstNamed = parts.minOfOrNull { it.line }
      val namedIds = parts.mapTo(HashSet()) { it.element.id }
      val candidates = (from until (if (firstNamed == null) to else minOf(to, from + firstNamed))).filter { line ->
        val owner = innermost(side.roots, line)
        !isTrivial(side.lines.line(line)) &&
        (owner == null || isChanged(side, owner) && generateSequence(owner) { it.parent }.none { it.id in namedIds })
      }
      val line = candidates.firstOrNull { hasWord(side, it, if (isBase) removedWords else newWords) } ?: candidates.firstOrNull() ?: return null
      return Entry(line - from, HunkNamePart(lineLabel(side.lines.line(line)), status), null)
    }

    private fun detail(entries: List<Entry>): HunkNamePart? {
      val entry = entries.singleOrNull() ?: return null
      val part = entry.part ?: return null
      val element = part.element
      if (entry.name.previousText != null || part.status != FileStatus.MODIFIED || element.isSingleLine) return null
      // A presentation can show an added word, but never a removed one.
      if (changedWords(element).all { element.text.contains(it) } && removedWords(element).isEmpty()) return null
      val lines = (maxOf(hunk.line1, element.startLine) until minOf(hunk.line2, element.endLine)).filter { !isTrivial(current.lines.line(it)) }
      val line = lines.firstOrNull { hasWord(current, it, newWords) } ?: lines.firstOrNull()
      if (line != null) return HunkNamePart(lineLabel(current.lines.line(line)), FileStatus.MODIFIED)
      val removedLines = (hunk.vcsLine1 until hunk.vcsLine2).filter { !isTrivial(base.lines.line(it)) }
      val removed = removedLines.firstOrNull { hasWord(base, it, removedWords) } ?: removedLines.firstOrNull() ?: return null
      return HunkNamePart(lineLabel(base.lines.line(removed)), FileStatus.DELETED)
    }

    private fun hasWord(side: Side, line: Int, words: Set<String>): Boolean =
      WORD.findAll(side.lines.line(line)).any { it.value in words }

    private fun innermost(elements: List<Element>, line: Int): Element? {
      val element = elements.firstOrNull { it.eligible && line >= it.startLine && line < it.endLine } ?: return null
      return innermost(element.children, line) ?: element
    }
  }

  private fun wordDifference(text: String, other: String): List<String> {
    val remaining = HashMap<String, Int>()
    for (match in WORD.findAll(other)) remaining.merge(match.value, 1, Int::plus)
    return WORD.findAll(text).map { it.value }.filter { word ->
      val count = remaining[word] ?: 0
      if (count > 0) remaining[word] = count - 1
      count == 0
    }.toList()
  }

  private fun words(side: Side, from: Int, to: Int): Set<String> =
    (from until to).flatMapTo(HashSet()) { line -> WORD.findAll(side.lines.line(line)).map { it.value } }

  private fun text(side: Side, from: Int, to: Int): String = (from until to).joinToString("\n") { side.lines.line(it) }

  private fun isRich(presentation: String): Boolean = presentation.any { it == '(' || it == ':' || it == '=' }

  private fun isTrivial(line: CharSequence): Boolean = TRIVIAL_LINE.matches(line.trim())

  private fun lineLabel(line: CharSequence): String {
    val label = line.trim().toString().removeSuffix(",").removeSuffix(";").replaceFirst(LIST_MARKER, "")
    return if (label.length > MAX_LABEL_LENGTH) label.take(MAX_LABEL_LENGTH) + "…" else label
  }

  /** Keeps the kind of the element and drops the visibility mark, so that the names of all hunks start at one position. */
  private fun snapshotIcon(icon: Icon): Icon? {
    val evaluated = (icon as? DeferredIcon)?.evaluate() ?: icon
    val kind = (evaluated as? RowIcon)?.allIcons?.firstOrNull() ?: evaluated
    return IconLoader.toImage(kind)?.let(IconUtil::createImageIcon)
  }

  private class Part(val element: Element, val status: FileStatus, val line: Int, val isBase: Boolean)

  private class Entry(val line: Int, val name: HunkNamePart, val part: Part?)

  private data class Candidate(val removed: Part, val added: Part, val similarity: Double)

  /** Identifies an added or removed group. The parent compares by identity. */
  private data class GroupKey(val status: FileStatus, val isBase: Boolean, val parent: Element?, val group: Int)

  /** The words of an element with their counts. A multi-line element counts the words of its body, without the first line. */
  private class Words(element: Element) {
    private val counts = HashMap<String, Int>()
    private val size: Int

    init {
      val content = if (element.isSingleLine) element.source else element.source.substringAfter('\n', "")
      for (match in WORD.findAll(content)) counts.merge(match.value, 1, Int::plus)
      size = counts.values.sum()
    }

    /** The share of common words of two elements, from 0 to 1. */
    fun similarity(other: Words): Double {
      if (size == 0 || other.size == 0) return 0.0
      val (fewer, more) = if (counts.size <= other.counts.size) counts to other.counts else other.counts to counts
      var common = 0
      for ((word, count) in fewer) common += minOf(count, more[word] ?: 0)
      return 2.0 * common / (size + other.size)
    }
  }

  /** A Structure View element with its lines. It holds no PSI. The text adds the compared value to the presentation. */
  private class Element(
    val kind: String,
    val key: String,
    val presentation: @NlsSafe String,
    val text: @NlsSafe String,
    val source: String,
    val icon: Icon?,
    val startLine: Int,
    val endLine: Int,
    val eligible: Boolean,
    val group: Int?,
    val parent: Element?,
  ) {
    val children: MutableList<Element> = ArrayList()
    var id: String = ""
    val isSingleLine: Boolean get() = endLine - startLine == 1

    fun overlaps(from: Int, to: Int): Boolean = startLine < to && from < endLine
  }

  private class Side(val lines: LineTable, val roots: List<Element>) {
    val byId: MutableMap<String, Element> = HashMap()

    fun index() {
      fun visit(elements: List<Element>) {
        for (element in elements) {
          byId[element.id] = element
          visit(element.children)
        }
      }
      visit(roots)
    }
  }

  private class LineTable(val text: CharSequence) {
    private val starts: IntArray = run {
      val result = ArrayList<Int>()
      result.add(0)
      for (i in text.indices) {
        if (text[i] == '\n') result.add(i + 1)
      }
      result.toIntArray()
    }

    fun lineOf(offset: Int): Int {
      val index = starts.binarySearch(offset.coerceIn(0, text.length))
      return if (index >= 0) index else -index - 2
    }

    fun lineStart(line: Int): Int = starts[line]

    fun line(line: Int): CharSequence {
      if (line < 0 || line >= starts.size) return ""
      val end = if (line + 1 < starts.size) starts[line + 1] - 1 else text.length
      return text.subSequence(starts[line], end)
    }
  }
}
