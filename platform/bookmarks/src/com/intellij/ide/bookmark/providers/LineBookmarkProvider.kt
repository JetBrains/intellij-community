// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.bookmark.providers

import com.intellij.ide.bookmark.Bookmark
import com.intellij.ide.bookmark.BookmarkProvider
import com.intellij.ide.bookmark.BookmarksManager
import com.intellij.ide.bookmark.BookmarksManagerImpl
import com.intellij.ide.bookmark.FileBookmark
import com.intellij.ide.bookmark.LineBookmark
import com.intellij.ide.bookmark.ui.tree.FileNode
import com.intellij.ide.bookmark.ui.tree.LineNode
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.impl.AbstractUrl
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.impl.event.DocumentEventImpl
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.AsyncFileListener
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.psi.PsiCompiledElement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.util.PsiUtilCore
import com.intellij.testFramework.LightVirtualFile
import com.intellij.ui.tree.project.ProjectFileNode
import com.intellij.util.diff.Diff
import com.intellij.util.diff.FilesTooBigForDiffException
import com.intellij.util.ui.tree.TreeUtil
import com.intellij.util.ui.update.DebouncedUpdates
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import javax.swing.tree.TreePath
import kotlin.coroutines.cancellation.CancellationException

private val LOG = Logger.getInstance(LineBookmarkProvider::class.java)

@Suppress("ExtensionClassShouldBeFinalAndNonPublic")
class LineBookmarkProvider(private val project: Project, coroutineScope: CoroutineScope) : BookmarkProvider {
  override fun getWeight(): Int = Int.MIN_VALUE
  override fun getProject(): Project = project

  override fun compare(bookmark1: Bookmark, bookmark2: Bookmark): Int {
    val fileBookmark1 = bookmark1 as? FileBookmark
    val fileBookmark2 = bookmark2 as? FileBookmark
    if (fileBookmark1 == null && fileBookmark2 == null) return 0
    if (fileBookmark1 == null) return -1
    if (fileBookmark2 == null) return 1
    val file1 = fileBookmark1.file
    val file2 = fileBookmark2.file
    if (file1 == file2) {
      val lineBookmark1 = bookmark1 as? LineBookmark
      val lineBookmark2 = bookmark2 as? LineBookmark
      if (lineBookmark1 == null && lineBookmark2 == null) return 0
      if (lineBookmark1 == null) return -1
      if (lineBookmark2 == null) return 1
      return lineBookmark1.line.compareTo(lineBookmark2.line)
    }
    if (file1.isDirectory && !file2.isDirectory) return -1
    if (!file1.isDirectory && file2.isDirectory) return 1
    return StringUtil.naturalCompare(file1.presentableName, file2.presentableName)
  }

  override fun prepareGroup(nodes: List<AbstractTreeNode<*>>): List<AbstractTreeNode<*>> {
    nodes.forEach { (it as? FileNode)?.ungroup() } // clean all file groups if needed
    val node = nodes.firstNotNullOfOrNull { it as? LineNode } ?: nodes.firstNotNullOfOrNull { it as? UrlNode } ?: return nodes
    if (node.bookmarksView?.groupLineBookmarks?.isSelected != true) return nodes // grouping disabled

    val map = hashMapOf<VirtualFile, FileNode?>()
    nodes.forEach {
      when (it) {
        is LineNode -> map.putIfAbsent(it.virtualFile, null)
        is UrlNode -> it.virtualFile?.let { file -> map.putIfAbsent(file, null) }
        is FileNode -> map[it.virtualFile] = it
      }
    }
    // create fake file nodes to group corresponding line nodes
    map.mapNotNull { if (it.value == null) it.key else null }.forEach {
      map[it] = FileNode(project, FileBookmarkImpl(this, it)).apply {
        bookmarkGroup = node.bookmarkGroup
        parent = node.parent
      }
    }

    return nodes.mapNotNull {
      when (it) {
        is LineNode -> map[it.virtualFile]!!.grouped(it)
        is UrlNode -> {
          val file = it.virtualFile
          if (file != null) {
            map[file]!!.grouped(it)
          } else {
            it
          }
        }
        is FileNode -> it.grouped()
        else -> it
      }
    }
  }

  override fun createBookmark(map: Map<String, String>): Bookmark? {
    val url = map["url"] ?: return null
    val line = StringUtil.parseInt(map["line"], -1)
    val lineText = map["lineText"]
    return createValidBookmark(url, line, lineText) ?: InvalidBookmark(this, url, line, lineText)
  }

  override fun createBookmark(context: Any?): Bookmark? = when (context) {
    is com.intellij.ide.bookmarks.Bookmark -> createBookmark(context.file, context.line)
    is AbstractUrl -> when (context.type) {
      AbstractUrl.TYPE_DIRECTORY -> createBookmark(context.url)
      AbstractUrl.TYPE_PSI_FILE -> createBookmark(context.url)
      else -> null
    }
    is PsiElement -> createBookmark(context)
    is VirtualFile -> createBookmark(context, -1)
    is ProjectFileNode -> createBookmark(context.virtualFile)
    is TreePath -> createBookmark(context)
    else -> null
  }

  fun createBookmark(file: VirtualFile, line: Int = -1): FileBookmark? = createBookmark(file, line, null)

  internal fun createBookmark(file: VirtualFile, line: Int, expectedText: String?): FileBookmark? = when {
    !file.isValid || file is LightVirtualFile -> null
    line >= 0 -> LineBookmarkImpl(this, file, line, expectedText)
    else -> FileBookmarkImpl(this, file)
  }

  fun createBookmark(editor: Editor, line: Int? = null): FileBookmark? {
    if (editor.isOneLineMode) return null
    val file = FileDocumentManager.getInstance().getFile(editor.document) ?: return null
    return createBookmark(file, line ?: editor.caretModel.logicalPosition.line)
  }

  internal fun createValidBookmark(url: String, line: Int = -1, expectedText: String? = null): Bookmark? {
    val file = VirtualFileManager.getInstance().findFileByUrl(url) ?: return null
    if (expectedText != null && line >= 0) {
      val document = FileDocumentManager.getInstance().getDocument(file) ?: return null
      val currentText = Util.readLineText(document, line)
      if (expectedText != currentText) return null
    }
    return createBookmark(file, line, expectedText)
  }

  private fun createBookmark(element: PsiElement): FileBookmark? {
    if (element is PsiFileSystemItem) return element.virtualFile?.let { createBookmark(it) }
    if (element is PsiCompiledElement) return null
    val file = PsiUtilCore.getVirtualFile(element) ?: return null
    if (file is LightVirtualFile) return null
    val document = FileDocumentManager.getInstance().getDocument(file) ?: return null
    return when (val offset = element.textOffset) {
      in 0..document.textLength -> createBookmark(file, document.getLineNumber(offset))
      else -> null
    }
  }

  private fun createBookmark(path: TreePath): FileBookmark? {
    val file = path.asVirtualFile ?: return null
    val parent = path.parentPath?.asVirtualFile ?: return null
    // see com.intellij.ide.projectView.impl.ClassesTreeStructureProvider
    return if (!parent.isDirectory || file.parent != parent) null else createBookmark(file)
  }

  private val TreePath.asVirtualFile
    get() = TreeUtil.getLastUserObject(ProjectViewNode::class.java, this)?.virtualFile

  private fun afterDocumentChange(document: Document, event: DocumentEvent? = null) {
    val file = FileDocumentManager.getInstance().getFile(document) ?: return
    if (file is LightVirtualFile) return
    val manager = BookmarksManager.getInstance(project) ?: return

    if (event != null && (event.isWholeTextReplaced || document.isInBulkUpdate)) {
      if (updateBookmarksUsingDiffMapping(file, document, event, manager)) {
        return
      }
    }

    validateBookmarksUsingRangeMarker(file, manager)
  }

  private fun validateBookmarksUsingRangeMarker(file: VirtualFile, manager: BookmarksManager) {
    val map = sortedMapOf<LineBookmarkImpl, Int>(compareBy { it.line })
    val set = hashSetOf<Int>()
    for (bookmark in manager.bookmarks) {
      if (bookmark is LineBookmarkImpl && bookmark.file == file) {
        val rangeMarker = (manager as? BookmarksManagerImpl)?.findLineHighlighter(bookmark) ?: bookmark.descriptor.rangeMarker
        val line = rangeMarker?.let { if (it.isValid) it.document.getLineNumber(it.startOffset) else null } ?: -1
        when (bookmark.line) {
          line -> set.add(line)
          else -> map[bookmark] = line
        }
      }
    }
    if (map.isEmpty()) return
    val document = FileDocumentManager.getInstance().getDocument(file)
    val bookmarks = mutableMapOf<Bookmark, Bookmark?>()
    map.forEach { (bookmark, line) ->
      bookmarks[bookmark] = when {
        line < 0 || set.contains(line) -> null
        else -> {
          set.add(line)
          val newExpectedText = document?.let { Util.readLineText(it, line) }
          createBookmark(file, line, newExpectedText)
        }
      }
    }
    manager.update(bookmarks)
  }

  private class LineTextIndexBuilder(private val document: Document) {
    private val lineCache: MutableMap<Int, String> = hashMapOf()

    fun findLineByText(text: String, preferredLine: Int): Int {
      getLineText(preferredLine)?.let { if (it == text) return preferredLine }

      for (distance in 1 until document.lineCount) {
        getLineText(preferredLine - distance)?.let { if (it == text) return preferredLine - distance }
        getLineText(preferredLine + distance)?.let { if (it == text) return preferredLine + distance }
      }

      return -1
    }

    private fun getLineText(line: Int): String? {
      if (line !in 0 until document.lineCount) {
        return null
      }

      return lineCache.getOrPut(line) {
        Util.readLineText(document, line) ?: return null
      }
    }
  }

  private fun updateLineBookmark(
    bookmark: LineBookmarkImpl,
    event: DocumentEventImpl,
    document: Document,
    file: VirtualFile,
    indexBuilder: LineTextIndexBuilder,
    processedLines: MutableSet<Int>,
    updates: MutableMap<Bookmark, Bookmark?>
  ) {
    val mappedLine = event.translateLineViaDiffStrict(bookmark.line)
    val expectedText = bookmark.expectedText

    when {
      mappedLine >= 0 && mappedLine < document.lineCount && expectedText == Util.readLineText(document, mappedLine) -> {
        if (processedLines.add(mappedLine)) {
          if (bookmark.line != mappedLine) {
            updates[bookmark] = createBookmark(file, mappedLine, expectedText)
          }
        }
        else {
          updates[bookmark] = InvalidBookmark(this, file.url, bookmark.line, expectedText)
        }
      }
      expectedText != null -> {
        val preferredLine = if (mappedLine >= 0) mappedLine else bookmark.line
        val foundLine = indexBuilder.findLineByText(expectedText, preferredLine)
        updates[bookmark] = if (foundLine >= 0 && processedLines.add(foundLine)) {
          createBookmark(file, foundLine, expectedText)
        }
        else {
          InvalidBookmark(this, file.url, bookmark.line, expectedText)
        }
      }
      else -> {
        updates[bookmark] = InvalidBookmark(this, file.url, bookmark.line, null)
      }
    }
  }

  private fun tryRestoreInvalidBookmark(
    bookmark: InvalidBookmark,
    file: VirtualFile,
    document: Document,
    event: DocumentEventImpl,
    indexBuilder: LineTextIndexBuilder,
    processedLines: MutableSet<Int>,
    updates: MutableMap<Bookmark, Bookmark?>
  ) {
    val expectedText = bookmark.expectedText ?: return

    val insertedLines = try {
      findInsertedLines(document, event)
    }
    catch (_: FilesTooBigForDiffException) {
      null
    }

    if (!insertedLines.isNullOrEmpty()) {
      for (line in insertedLines) {
        if (Util.readLineText(document, line) == expectedText) {
          if (processedLines.add(line)) {
            updates[bookmark] = createBookmark(file, line, expectedText)
            return
          }
          else {
            updates[bookmark] = null
            return
          }
        }
      }
    }
    else {
      val foundLine = indexBuilder.findLineByText(expectedText, bookmark.line)
      if (foundLine >= 0) {
        if (processedLines.add(foundLine)) {
          updates[bookmark] = createBookmark(file, foundLine, expectedText)
        }
        else {
          updates[bookmark] = null
        }
      }
    }
  }

  private fun findInsertedLines(document: Document, event: DocumentEventImpl): Set<Int> {
    val mappedNewLines = hashSetOf<Int>()

    val startLine = document.getLineNumber(event.offset)
    val oldFragmentLineCount = Diff.splitLines(event.oldFragment).size
    val newFragmentLineCount = Diff.splitLines(event.newFragment).size

    for (oldLine in startLine until (startLine + oldFragmentLineCount)) {
      val newLine = event.translateLineViaDiffStrict(oldLine)
      if (newLine >= 0) {
        mappedNewLines.add(newLine)
      }
    }

    val insertedLines = hashSetOf<Int>()
    for (newLine in startLine until (startLine + newFragmentLineCount)) {
      if (newLine !in mappedNewLines) {
        insertedLines.add(newLine)
      }
    }

    return insertedLines
  }

  private fun updateBookmarksUsingDiffMapping(
    file: VirtualFile,
    document: Document,
    event: DocumentEvent,
    manager: BookmarksManager
  ): Boolean {
    if (event !is DocumentEventImpl) {
      return false
    }

    try {
      val updates = hashMapOf<Bookmark, Bookmark?>()
      val processedLines = hashSetOf<Int>()
      val indexBuilder = LineTextIndexBuilder(document)

      val lineBookmarks = manager.bookmarks.filterIsInstance<LineBookmarkImpl>().filter { it.file == file }
      val invalidBookmarks = manager.bookmarks.filterIsInstance<InvalidBookmark>().filter { it.url == file.url }

      for (bookmark in lineBookmarks) {
        updateLineBookmark(bookmark, event, document, file, indexBuilder, processedLines, updates)
      }
      for (bookmark in invalidBookmarks) {
        tryRestoreInvalidBookmark(bookmark, file, document, event, indexBuilder, processedLines, updates)
      }

      if (updates.isNotEmpty()) {
        if (LOG.isDebugEnabled) {
          LOG.debug("[Diff] Updated ${updates.size} bookmarks in ${file.name}: " +
                    updates.entries.joinToString(limit = 10) { (old, new) ->
                      val oldLine = (old as? LineBookmarkImpl)?.line ?: "?"
                      val newLine = when (new) {
                        is LineBookmarkImpl -> new.line.toString()
                        is InvalidBookmark -> "invalid"
                        null -> "removed"
                        else -> "?"
                      }
                      "$oldLine -> $newLine"
                    })
        }
        manager.update(updates)
      }
      return true
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (_: FilesTooBigForDiffException) {
      return false
    }
  }

  private fun prepareChange(events: List<VFileEvent>): AsyncFileListener.ChangeApplier? {
    val update = events.any { it is VFileCreateEvent || it is VFileDeleteEvent || it is VFileContentChangeEvent }
    if (update) requestValidation()
    return null
  }

  private val validateQueue = DebouncedUpdates.forScope<Unit>(coroutineScope, "validate-bookmarks", 100)
    .restartTimerOnAdd(true)
    .runLatest { validateAndUpdate() }

  fun requestValidation() {
    validateQueue.queue(Unit)
  }

  private fun captureExpectedTextBeforeReload(file: VirtualFile, document: Document) {
    val manager = BookmarksManager.getInstance(project) ?: return
    for (bookmark in manager.bookmarks) {
      if (bookmark is LineBookmarkImpl && bookmark.file == file) {
        bookmark.ensureExpectedTextInitialized(document)
      }
    }
  }

  private suspend fun validateAndUpdate() {
    val manager = BookmarksManager.getInstance(project) ?: return
    val bookmarks = readAction {
      val indexBuilderCache = mutableMapOf<String, LineTextIndexBuilder>()
      hashMapOf<Bookmark, Bookmark?>().apply {
        manager.bookmarks.forEach { validate(it, indexBuilderCache)?.run { this@apply[it] = this } }
      }
    }
    if (bookmarks.isNotEmpty()) manager.update(bookmarks)
  }

  private fun validate(bookmark: Bookmark, indexBuilderCache: MutableMap<String, LineTextIndexBuilder>): Bookmark? {
    return when (bookmark) {
      is InvalidBookmark -> {
        val created = createValidBookmark(bookmark.url, bookmark.line, bookmark.expectedText)
        if (created != null) return created

        if (bookmark.expectedText != null && bookmark.line >= 0) {
          val indexBuilder = indexBuilderCache.getOrPut(bookmark.url) {
            val file = VirtualFileManager.getInstance().findFileByUrl(bookmark.url) ?: return null
            val document = FileDocumentManager.getInstance().getDocument(file) ?: return null
            LineTextIndexBuilder(document)
          }
          val foundLine = indexBuilder.findLineByText(bookmark.expectedText, bookmark.line)
          if (foundLine >= 0) return createValidBookmark(bookmark.url, foundLine, bookmark.expectedText)
        }
        null
      }
      is FileBookmarkImpl -> bookmark.file.run { if (isValid) null else InvalidBookmark(this@LineBookmarkProvider, url, -1, null) }
      is LineBookmarkImpl -> bookmark.file.run { if (isValid) null else InvalidBookmark(this@LineBookmarkProvider, url, bookmark.line, bookmark.expectedText) }
      else -> null
    }
  }

  init {
    if (!project.isDefault) {
      val multicaster = EditorFactory.getInstance().eventMulticaster
      multicaster.addDocumentListener(object : DocumentListener {
        override fun beforeDocumentChange(event: DocumentEvent) {
          val file = FileDocumentManager.getInstance().getFile(event.document) ?: return
          if (event.isWholeTextReplaced || event.document.isInBulkUpdate) {
            captureExpectedTextBeforeReload(file, event.document)
          }
        }

        override fun documentChanged(event: DocumentEvent) {
          this@LineBookmarkProvider.afterDocumentChange(event.document, event)
        }
      }, project)
      VirtualFileManager.getInstance().addAsyncFileListenerBackgroundable({ events -> this@LineBookmarkProvider.prepareChange(events) }, project)
    }
  }

  object Util {
    @JvmStatic
    fun find(project: Project): LineBookmarkProvider? = when {
      project.isDisposed -> null
      else -> BookmarkProvider.EP.findExtension(LineBookmarkProvider::class.java, project)
    }

    fun readLineText(bookmark: LineBookmark?): String? = bookmark?.let {
      if (it is LineBookmarkImpl && it.expectedText != null) {
        return it.expectedText
      }

      runReadActionBlocking {
        readLineText(it.file, it.line)
      }
    }

    private fun readLineText(file: VirtualFile, line: Int): String? {
      val document = FileDocumentManager.getInstance().getDocument(file) ?: return null
      return readLineText(document, line)
    }

    @JvmStatic
    @ApiStatus.Internal
    fun readLineText(document: Document, line: Int): String? {
      if (line < 0 || document.lineCount <= line) return null
      val start = document.getLineStartOffset(line)
      if (start < 0) return null
      val end = document.getLineEndOffset(line)
      if (end < start) return null
      return document.getText(TextRange.create(start, end))
    }
  }
}
