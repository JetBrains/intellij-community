// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.frontend

import com.intellij.diff.util.Side
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

@ApiStatus.Internal
interface FrontendDiffExtension {
  @RequiresEdt
  fun onViewerCreated(viewer: FrontendDiffViewer, context: FrontendDiffContext)

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<FrontendDiffExtension> = ExtensionPointName.create("com.intellij.diff.frontendDiffExtension")
  }
}

@ApiStatus.Internal
data class FrontendDiffContext(
  val project: Project?,
  /** Identifies the UI location that owns the diff, or `null` when the caller did not specify one. */
  val place: String?,
  val request: FrontendDiffRequest,
  /** Extensions may register their resources under this disposable, but must not dispose it directly. */
  val parentDisposable: Disposable,
)

@ApiStatus.Internal
sealed interface FrontendDiffViewer {
  val component: JComponent
  val editors: List<FrontendDiffEditor>

  interface FrontendOneSideDiffViewer : FrontendDiffViewer {
    val editor: FrontendDiffEditor
    val side: Side
    val lineMapper: FrontendDiffLineMapper
    override val editors: List<FrontendDiffEditor> get() = listOf(editor)
  }

  interface FrontendTwoSideDiffViewer : FrontendDiffViewer {
    val left: FrontendDiffEditor
    val right: FrontendDiffEditor
    val leftLineMapper: FrontendDiffLineMapper
    val rightLineMapper: FrontendDiffLineMapper
    override val editors: List<FrontendDiffEditor> get() = listOf(left, right)

    fun lineMapper(side: Side): FrontendDiffLineMapper = side.select(leftLineMapper, rightLineMapper)
  }

  interface FrontendUnifiedDiffViewer : FrontendDiffViewer {
    val editor: FrontendDiffEditor
    val lineMapper: FrontendDiffLineMapper
    val mapping: FrontendUnifiedDiffMapping
    override val editors: List<FrontendDiffEditor> get() = listOf(editor)
  }
}

@ApiStatus.Internal
data class FrontendDiffEditor(
  val editor: EditorEx,
)

@ApiStatus.Internal
data class FrontendDiffLineLocation(val side: Side, val line: Int)

@ApiStatus.Internal
interface FrontendDiffLineMapper {
  /**
   * Whether the mapping the editor is viewed through describes the documents as they are right now.
   *
   * In split mode the mapping is computed on the backend, so it is unavailable until the frontend documents catch up with the
   * versions it was built from, and again as soon as they change. While it is `false`, [lineToUnified] cannot resolve the
   * opposite side of the mapping and reports `-1` for it.
   */
  val isAvailable: Boolean

  /** Notifies when [isAvailable] or the mapping itself changes, so that mapped state can be recomputed. */
  fun addListener(parentDisposable: Disposable, listener: () -> Unit)

  fun locationToLine(location: FrontendDiffLineLocation): Int?
  fun lineToLocation(line: Int): FrontendDiffLineLocation?

  /**
   * Maps editor [line] to lines in LEFT/RIGHT documents
   */
  fun lineToUnified(line: Int): Pair<Int, Int>
}

@ApiStatus.Internal
interface FrontendUnifiedDiffMapping {
  val isAvailable: Boolean
  val revision: Long

  fun addListener(parentDisposable: Disposable, listener: () -> Unit)
  fun sideLineToUnified(side: Side, line: Int): Int?

  /** Maps [line] to [side]. When [strict] is `false`, an approximate side line may be returned. */
  fun unifiedLineToSide(side: Side, line: Int, strict: Boolean = true): Int?
  fun unifiedLineToSideLines(line: Int): Pair<Int, Int>
}

@ApiStatus.Internal
data class FrontendDiffRequest(
  val contents: List<FrontendDiffContent>,
  val extensionData: FrontendDiffExtensionData,
)

@ApiStatus.Internal
data class FrontendDiffContent(
  val file: VirtualFile?,
  val document: Document?,
  val isCurrent: Boolean,
) {
  val isEmpty: Boolean get() = file == null && document == null
}

@ApiStatus.Internal
object FrontendDiffContentKeys {
  @JvmField
  val IS_CURRENT: Key<Boolean> = Key.create("Diff.FrontendContent.IsCurrent")
}

@ApiStatus.Internal
interface FrontendDiffExtensionData {
  fun <T : Any> getContextData(descriptor: FrontendDiffUserDataKeyDescriptor<T>): T?
  fun <T : Any> getRequestData(descriptor: FrontendDiffUserDataKeyDescriptor<T>): T?
}
