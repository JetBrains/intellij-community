// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor

import com.intellij.codeInsight.multiverse.CodeInsightContext
import com.intellij.codeInsight.multiverse.EditorContextManager.Companion.getInstance
import com.intellij.codeInsight.multiverse.SingleEditorContext
import com.intellij.codeInsight.multiverse.anyContext
import com.intellij.codeInsight.multiverse.isSharedSourceSupportEnabled
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.AsyncNavigatable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import kotlin.math.max
import kotlin.math.min

/**
 * Allows opening file in editor, optionally at specific line/column position.
 */
open class OpenFileDescriptor private constructor(
  val project: Project,
  private val file: VirtualFile,
  @get:ApiStatus.Internal val context: CodeInsightContext,
  val line: Int,
  val column: Int,
  private val _offset: Int,
  persistent: Boolean,
) : FileEditorNavigatable, AsyncNavigatable, Comparable<OpenFileDescriptor> {
  val rangeMarker: RangeMarker?

  private var myUseCurrentWindow = false
  private var myUsePreviewTab = false
  private var myScrollType = ScrollType.CENTER

  @ApiStatus.Experimental
  constructor(project: Project, file: VirtualFile, context: CodeInsightContext, offset: Int) : this(project, file, context, -1, -1, offset,
                                                                                                    false)

  constructor(project: Project, file: VirtualFile, offset: Int) : this(project, file, anyContext(), -1, -1, offset, false)

  constructor(project: Project, file: VirtualFile, logicalLine: Int, logicalColumn: Int) : this(project, file, anyContext(), logicalLine,
                                                                                                logicalColumn, -1, false)

  constructor(project: Project, file: VirtualFile, logicalLine: Int, logicalColumn: Int, persistent: Boolean) : this(project, file,
                                                                                                                     anyContext(),
                                                                                                                     logicalLine,
                                                                                                                     logicalColumn, -1,
                                                                                                                     persistent)

  constructor(project: Project, file: VirtualFile) : this(project, file, anyContext(), -1, -1, -1, false)

  init {
    if (_offset >= 0) {
      this.rangeMarker = LazyRangeMarkerFactory.getInstance(project).createRangeMarker(file, _offset)
    }
    else if (line >= 0) {
      this.rangeMarker = LazyRangeMarkerFactory.getInstance(project).createRangeMarker(file, line, max(0, column), persistent)
    }
    else {
      this.rangeMarker = null
    }
  }

  override fun getFile(): VirtualFile {
    return file
  }

  open val offset: Int
    get() = if (this.rangeMarker != null && rangeMarker.isValid()) rangeMarker.getStartOffset() else _offset

  override fun navigate(requestFocus: Boolean) {
    FileNavigator.getInstance().navigate(this, requestFocus)
  }

  @ApiStatus.Experimental
  override suspend fun navigateAsync(requestFocus: Boolean) {
    if (Registry.`is`("ide.open.file.descriptor.async", false)) {
      FileNavigator.getInstance().navigateAsync(this, requestFocus)
    }
    else {
      withContext(Dispatchers.EDT) {
        FileNavigator.getInstance().navigate(this@OpenFileDescriptor, requestFocus)
      }
    }
  }

  open fun navigateInEditor(project: Project, requestFocus: Boolean): Boolean {
    return FileNavigator.getInstance().navigateInEditor(this, requestFocus)
  }


  open fun navigateIn(e: Editor) {
    navigateInEditor(this, e)
  }

  private fun scrollToCaret(e: Editor) {
    e.getScrollingModel().scrollToCaret(myScrollType)
  }

  override fun canNavigate(): Boolean {
    return FileNavigator.getInstance().canNavigate(this)
  }

  override fun canNavigateToSource(): Boolean {
    return FileNavigator.getInstance().canNavigateToSource(this)
  }

  fun setUseCurrentWindow(search: Boolean): OpenFileDescriptor {
    myUseCurrentWindow = search
    return this
  }

  override fun isUseCurrentWindow(): Boolean {
    return myUseCurrentWindow
  }

  fun setUsePreviewTab(usePreviewTab: Boolean): OpenFileDescriptor {
    myUsePreviewTab = usePreviewTab
    return this
  }

  override fun isUsePreviewTab(): Boolean {
    return myUsePreviewTab
  }

  fun setScrollType(scrollType: ScrollType) {
    myScrollType = scrollType
  }

  fun dispose() {
    if (this.rangeMarker != null) {
      rangeMarker.dispose()
    }
  }

  override fun compareTo(o: OpenFileDescriptor): Int {
    var i = project.getName().compareTo(o.project.getName())
    if (i != 0) return i
    i = file.getName().compareTo(o.file.getName())
    if (i != 0) return i
    if (this.rangeMarker != null) {
      if (o.rangeMarker == null) return 1
      i = rangeMarker.getStartOffset() - o.rangeMarker.getStartOffset()
      if (i != 0) return i
      return rangeMarker.getEndOffset() - o.rangeMarker.getEndOffset()
    }
    return if (o.rangeMarker == null) 0 else -1
  }

  companion object {
    /**
     * Tells descriptor to navigate in specific editor rather than file editor in the main IDE window.
     * For example, if you want to navigate in an editor embedded into modal dialog, you should provide this data.
     */
    @JvmField
    val NAVIGATE_IN_EDITOR: DataKey<Editor> = DataKey.create<Editor>("NAVIGATE_IN_EDITOR")

    @ApiStatus.Internal
    @JvmStatic
    fun navigateInEditor(descriptor: OpenFileDescriptor, e: Editor) {
      val offset = descriptor.offset
      val caretModel = e.getCaretModel()
      var caretMoved = false
      if (descriptor.line >= 0) {
        val pos = LogicalPosition(descriptor.line, max(
          descriptor.column, 0))
        if (offset < 0 || offset == e.logicalPositionToOffset(pos)) {
          caretModel.removeSecondaryCarets()
          caretModel.moveToLogicalPosition(pos)
          caretMoved = true
        }
      }
      if (!caretMoved && offset >= 0) {
        caretModel.removeSecondaryCarets()
        caretModel.moveToOffset(min(offset, e.getDocument().getTextLength()))
        caretMoved = true
      }

      if (caretMoved) {
        e.getSelectionModel().removeSelection()
        FileEditorManager.getInstance(descriptor.project).runWhenLoaded(e, Runnable {
          descriptor.scrollToCaret(e)
          unfoldCurrentLine(e)
        })
      }

      if (isSharedSourceSupportEnabled(descriptor.project)) {
        val context = descriptor.context
        if (context !== anyContext()) {
          getInstance(descriptor.project).setEditorContext(e, SingleEditorContext(context))
        }
      }
    }

    @ApiStatus.Internal
    @JvmStatic
    fun unfoldCurrentLine(editor: Editor) {
      val allRegions = editor.getFoldingModel().getAllFoldRegions()
      val range: TextRange = getRangeToUnfoldOnNavigation(editor)
      editor.getFoldingModel().runBatchFoldingOperation(Runnable {
        for (region in allRegions) {
          if (!region.isExpanded() && range.intersects(region)) {
            region.setExpanded(true)
          }
        }
      })
    }

    @JvmStatic
    fun getRangeToUnfoldOnNavigation(editor: Editor): TextRange {
      val offset = editor.getCaretModel().getOffset()
      val line = editor.getDocument().getLineNumber(offset)
      val start = editor.getDocument().getLineStartOffset(line)
      val end = editor.getDocument().getLineEndOffset(line)
      return TextRange(start, end)
    }
  }
}
