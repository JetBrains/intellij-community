// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.frontend

import com.intellij.diff.util.Side
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

/**
 * Frontend-only version of [com.intellij.diff.DiffExtension]
 */
@ApiStatus.Internal
interface FrontendDiffExtension {
  /**
   * Called whenever the diff viewer is created
   *
   * @param disposable disposable that can be used for extensions of this view
   */
  @RequiresEdt
  fun install(
    context: FrontendDiffContext,
    request: FrontendDiffRequest,
    viewer: FrontendDiffViewer,
    disposable: Disposable,
  )

  companion object {
    private val EP_NAME = ExtensionPointName.create<FrontendDiffExtension>("com.intellij.diff.frontendDiffExtension")

    /**
     * Install the registered extensions on the viewer
     */
    fun install(
      context: FrontendDiffContext,
      request: FrontendDiffRequest,
      viewer: FrontendDiffViewer,
      disposable: Disposable,
    ) {
      EP_NAME.forEachExtensionSafe {
        it.install(context, request, viewer, disposable)
      }
    }
  }
}

/**
 * Semi-constant context of the diff view, may be shared between multiple viewers
 */
@ApiStatus.Internal
interface FrontendDiffContext {
  val project: Project?

  /**
   * Identifies the UI location that owns the diff, or `null` when the caller did not specify one.
   */
  val place: String?

  /**
   * Lookup user data by the [key]
   *
   * NB: The key must be registered with [FrontendDiffUserDataKeyDescriptor] to be available here
   */
  fun <T : Any> getUserData(key: Key<T>): T?
}

@ApiStatus.Internal
sealed interface FrontendDiffViewer {
  val component: JComponent

  /**
   * Whether the state of the viewer matches the actual documents state.
   *
   * In split mode the mapping is computed on the backend, so it is unavailable until the frontend documents catch up with the
   * versions it was built from, and again as soon as they change. While it is `false`, every line transfer returns `null`.
   *
   * A [OneSide] viewer has no mapping to go out of date, so it is always actual.
   */
  val isActual: Boolean

  fun addActualStateListener(disposable: Disposable, listener: () -> Unit)

  /**
   * Frontend-only version of [com.intellij.diff.tools.simple.SimpleOnesideDiffViewer], which shows a single content of the diff.
   */
  interface OneSide : FrontendDiffViewer {
    /**
     * The side of the change this diff represents
     */
    val side: Side
    val editor: Editor
  }

  /**
   * Frontend-only version of [com.intellij.diff.tools.simple.SimpleDiffViewer], which shows both contents side by side.
   */
  interface TwoSide : FrontendDiffViewer {
    val leftEditor: Editor
    val rightEditor: Editor

    /**
     * Transfers the [line] on the diff [side] to the opposite side, or `null` when the viewer is not [isActual].
     *
     * The result is approximate - if the line cannot be mapped to the opposite side,
     * the closest mappable line will be returned.
     *
     * @see com.intellij.diff.tools.util.side.TwosideTextDiffViewer.transferPosition
     */
    fun transferLine(side: Side, line: Int): Int?
  }

  /**
   * Frontend-only version of [com.intellij.diff.tools.fragmented.UnifiedDiffViewer], which shows both contents in one editor.
   */
  interface Unified : FrontendDiffViewer {
    val unifiedEditor: Editor

    /**
     * Transfers the [line] on the diff [side] to the unified editor, or `null` when the unified editor does not have the line.
     *
     * @see com.intellij.diff.tools.fragmented.UnifiedDiffViewer.transferLineToOnesideStrict
     */
    fun transferLineToUnifiedStrict(side: Side, line: Int): Int?

    /**
     * Transfers the unified editor [line] to the diff [side], or `null` when that side does not have the line.
     *
     * @see com.intellij.diff.tools.fragmented.UnifiedDiffViewer.transferLineFromOnesideStrict
     */
    fun transferLineFromUnifiedStrict(side: Side, line: Int): Int?

    /**
     * Transfers the unified editor [line] to the side it originates from, or `null` when neither side has the line.
     *
     * @see com.intellij.diff.tools.fragmented.UnifiedDiffViewer.transferLineFromOnesideStrict
     */
    fun transferLineFromUnifiedStrict(line: Int): FrontendDiffLineLocation?

    /**
     * Transfers the unified editor [line] to the closest line on the diff [side], or `null` when the viewer is not [isActual].
     *
     * @see com.intellij.diff.tools.fragmented.UnifiedDiffViewer.transferLineFromOneside
     */
    fun transferLineFromUnified(side: Side, line: Int): Int?
  }
}

@ApiStatus.Internal
data class FrontendDiffLineLocation(val side: Side, val line: Int)

@ApiStatus.Internal
interface FrontendDiffRequest {
  val contents: List<FrontendDiffContent>

  /**
   * Lookup user data by the [key]
   *
   * NB: The key must be registered with [FrontendDiffUserDataKeyDescriptor] to be available here
   */
  fun <T : Any> getUserData(key: Key<T>): T?
}

@ApiStatus.Internal
data class FrontendDiffContent(
  val file: VirtualFile?,
  val document: Document?,
  val isCurrent: Boolean,
) {
  val isEmpty: Boolean get() = file == null && document == null
}
