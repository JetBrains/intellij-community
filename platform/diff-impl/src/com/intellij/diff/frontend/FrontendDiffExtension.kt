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
import kotlinx.serialization.KSerializer
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
interface FrontendDiffViewer {
  val kind: FrontendDiffViewerKind
  val component: JComponent
  val editors: List<FrontendDiffEditor>
}

@ApiStatus.Internal
enum class FrontendDiffViewerKind {
  ONE_SIDE,
  TWO_SIDE,
  UNIFIED,
}

@ApiStatus.Internal
interface FrontendOneSideDiffViewer : FrontendDiffViewer {
  override val kind: FrontendDiffViewerKind get() = FrontendDiffViewerKind.ONE_SIDE
  val editor: FrontendDiffEditor
  override val editors: List<FrontendDiffEditor> get() = listOf(editor)
}

@ApiStatus.Internal
interface FrontendTwoSideDiffViewer : FrontendDiffViewer {
  override val kind: FrontendDiffViewerKind get() = FrontendDiffViewerKind.TWO_SIDE
  val left: FrontendDiffEditor
  val right: FrontendDiffEditor
  override val editors: List<FrontendDiffEditor> get() = listOf(left, right)
}

@ApiStatus.Internal
interface FrontendUnifiedDiffViewer : FrontendDiffViewer {
  override val kind: FrontendDiffViewerKind get() = FrontendDiffViewerKind.UNIFIED
  val editor: FrontendDiffEditor
  val mapping: FrontendUnifiedDiffMapping
  override val editors: List<FrontendDiffEditor> get() = listOf(editor)
}

@ApiStatus.Internal
data class FrontendDiffEditor(
  val editor: EditorEx,
  val side: Side?,
  val lineMapper: FrontendDiffLineMapper,
)

@ApiStatus.Internal
data class FrontendDiffLineLocation(val side: Side, val line: Int)

@ApiStatus.Internal
interface FrontendDiffLineMapper {
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
  fun sideOffsetToUnified(side: Side, offset: Int): Int?

  /** Maps [offset] to [side]. When [strict] is `false`, an offset on an approximate side line may be returned. */
  fun unifiedOffsetToSide(side: Side, offset: Int, strict: Boolean = true): Int?
  fun sideDocument(side: Side): Document?
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
interface FrontendDiffUserDataKey<T : Any> {
  val id: String
  val rawKey: Key<T>
  val serializer: KSerializer<T>

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<FrontendDiffUserDataKey<*>> =
      ExtensionPointName.create("com.intellij.diff.frontendUserDataKey")
  }
}

@ApiStatus.Internal
interface FrontendDiffExtensionData {
  fun <T : Any> getContextData(key: FrontendDiffUserDataKey<T>): T?
  fun <T : Any> getRequestData(key: FrontendDiffUserDataKey<T>): T?
}
