// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.frontend.impl

import com.intellij.codeWithMe.ClientId
import com.intellij.diff.DiffContext
import com.intellij.diff.FrameDiffTool
import com.intellij.diff.contents.DiffContent
import com.intellij.diff.contents.DocumentContent
import com.intellij.diff.contents.EmptyContent
import com.intellij.diff.contents.FileContent
import com.intellij.diff.frontend.FrontendDiffContent
import com.intellij.diff.frontend.FrontendDiffContext
import com.intellij.diff.frontend.FrontendDiffExtension
import com.intellij.diff.frontend.FrontendDiffLineLocation
import com.intellij.diff.frontend.FrontendDiffRequest
import com.intellij.diff.frontend.FrontendDiffUserDataKeyDescriptor
import com.intellij.diff.frontend.FrontendDiffViewer
import com.intellij.diff.requests.ContentDiffRequest
import com.intellij.diff.requests.DiffRequest
import com.intellij.diff.tools.fragmented.UnifiedDiffViewer
import com.intellij.diff.tools.simple.SimpleDiffViewer
import com.intellij.diff.tools.simple.SimpleOnesideDiffViewer
import com.intellij.diff.tools.util.base.DiffViewerBase
import com.intellij.diff.tools.util.base.DiffViewerListener
import com.intellij.diff.util.DiffUserDataKeys
import com.intellij.diff.util.LineCol
import com.intellij.diff.util.Side
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolder
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

/** Only works for local IDE (not rem-dev), for rem-dev RemoteFrontendDiffExtensionBridge.kt is used */
@ApiStatus.Internal
object LocalFrontendDiffExtensionBridge {
  @JvmStatic
  fun notifyViewerCreated(viewer: FrameDiffTool.DiffViewer, context: DiffContext, request: DiffRequest) {
    if (!ClientId.isCurrentlyUnderLocalId) return
    val contentRequest = request as? ContentDiffRequest ?: return
    val contents = contentRequest.contents.map { adaptContent(it) ?: return }
    val disposable = Disposer.newDisposable("Frontend diff extension")
    val frontendViewer = adaptViewer(viewer, disposable)
    if (frontendViewer == null) {
      Disposer.dispose(disposable)
      return
    }

    Disposer.register(viewer, disposable)
    FrontendDiffExtension.install(
      context = LocalFrontendDiffContext(context),
      request = LocalFrontendDiffRequest(contents, request),
      viewer = frontendViewer,
      disposable = disposable,
    )
  }

  private fun adaptViewer(viewer: FrameDiffTool.DiffViewer, disposable: Disposable): FrontendDiffViewer? = when (viewer) {
    is SimpleOnesideDiffViewer -> LocalOneSideDiffViewer(viewer)
    is SimpleDiffViewer -> LocalTwoSideDiffViewer(viewer, disposable)
    is UnifiedDiffViewer -> LocalUnifiedDiffViewer(viewer, disposable)
    else -> null
  }

  private fun adaptContent(content: DiffContent): FrontendDiffContent? {
    return when (content) {
      is EmptyContent -> FrontendDiffContent(file = null, document = null, isCurrent = false)
      is DocumentContent -> FrontendDiffContent(
        file = (content as? FileContent)?.file,
        document = content.document,
        isCurrent = content is FileContent,
      )
      else -> null
    }
  }
}

private class LocalFrontendDiffContext(private val context: DiffContext) : FrontendDiffContext {
  override val project: Project? get() = context.project

  override val place: String? get() = context.getUserData(DiffUserDataKeys.PLACE)

  override fun <T : Any> getUserData(key: Key<T>): T? = context.getFrontendDiffUserData(key)
}

private class LocalFrontendDiffRequest(
  override val contents: List<FrontendDiffContent>,
  private val request: DiffRequest,
) : FrontendDiffRequest {
  override fun <T : Any> getUserData(key: Key<T>): T? = request.getFrontendDiffUserData(key)
}

/**
 * Reads [key] the way the split-mode frontend reads it: a key without a [FrontendDiffUserDataKeyDescriptor] cannot be
 * transferred there, so it reads as absent here too, and an extension cannot come to depend on data it only gets locally.
 */
private fun <T : Any> UserDataHolder.getFrontendDiffUserData(key: Key<T>): T? {
  if (FrontendDiffUserDataKeyDescriptor.find(key) == null) return null
  return getUserData(key)
}

/**
 * Proxies [SimpleOnesideDiffViewer]. It shows a single document as it is, so there is no mapping that may go out of date.
 */
private class LocalOneSideDiffViewer(private val viewer: SimpleOnesideDiffViewer) : FrontendDiffViewer.OneSide {
  override val component: JComponent get() = viewer.component
  override val side: Side get() = viewer.side
  override val editor: Editor get() = viewer.editor
  override val isActual: Boolean get() = true

  override fun addActualStateListener(disposable: Disposable, listener: () -> Unit) = Unit
}

/**
 * Proxies [SimpleDiffViewer]. It is actual whenever the viewer has a computed diff, which is what
 * [SimpleDiffViewer.transferPosition] needs to transfer a line onto the opposite side.
 */
private class LocalTwoSideDiffViewer(
  private val viewer: SimpleDiffViewer,
  disposable: Disposable,
) : FrontendDiffViewer.TwoSide {
  private val actualState = FrontendDiffActualStateHolder()

  init {
    viewer.trackRediff(disposable, actualState) { true }
    trackDocuments(disposable, actualState, listOf(viewer.editor1.document, viewer.editor2.document))
  }

  override val component: JComponent get() = viewer.component
  override val leftEditor: Editor get() = viewer.editor1
  override val rightEditor: Editor get() = viewer.editor2
  override val isActual: Boolean get() = actualState.isActual

  override fun addActualStateListener(disposable: Disposable, listener: () -> Unit) {
    actualState.addListener(disposable, listener)
  }

  override fun transferLine(side: Side, line: Int): Int? {
    if (!actualState.isActual || line !in 0 until viewer.getEditor(side).document.lineCount) return null
    return viewer.transferPosition(side, LineCol(line, 0)).line
  }
}

/**
 * Proxies [UnifiedDiffViewer]. It is actual whenever the viewer has line number mappings for both sides.
 */
private class LocalUnifiedDiffViewer(
  private val viewer: UnifiedDiffViewer,
  disposable: Disposable,
) : FrontendDiffViewer.Unified {
  private val actualState = FrontendDiffActualStateHolder(viewer.hasLineNumberMappings())

  init {
    viewer.trackRediff(disposable, actualState) { viewer.hasLineNumberMappings() }
    trackDocuments(disposable, actualState, listOf(
      viewer.editor.document,
      viewer.getDocument(Side.LEFT),
      viewer.getDocument(Side.RIGHT),
    ))
  }

  override val component: JComponent get() = viewer.component
  override val unifiedEditor: Editor get() = viewer.editor
  override val isActual: Boolean get() = actualState.isActual

  override fun addActualStateListener(disposable: Disposable, listener: () -> Unit) {
    actualState.addListener(disposable, listener)
  }

  override fun transferLineToUnifiedStrict(side: Side, line: Int): Int? {
    if (!actualState.isActual || line !in 0 until viewer.getDocument(side).lineCount) return null
    return viewer.transferLineToOnesideStrict(side, line).takeIf { it >= 0 }
  }

  override fun transferLineFromUnifiedStrict(side: Side, line: Int): Int? {
    if (!isUnifiedLine(line)) return null
    return viewer.transferLineFromOnesideStrict(side, line).takeIf { it >= 0 }
  }

  override fun transferLineFromUnifiedStrict(line: Int): FrontendDiffLineLocation? {
    if (!isUnifiedLine(line)) return null
    val mapping = viewer.transferLineFromOnesideStrict(line) ?: return null
    val side = mapping.second
    val sideLine = mapping.first[side.index].takeIf { it >= 0 } ?: return null
    return FrontendDiffLineLocation(side, sideLine)
  }

  override fun transferLineFromUnified(side: Side, line: Int): Int? {
    if (!isUnifiedLine(line)) return null
    return viewer.transferLineFromOneside(side, line).takeIf { it >= 0 }
  }

  private fun isUnifiedLine(line: Int): Boolean = actualState.isActual && line in 0 until viewer.editor.document.lineCount
}

private fun UnifiedDiffViewer.hasLineNumberMappings(): Boolean =
  getLineNumberMapping(Side.LEFT) != null && getLineNumberMapping(Side.RIGHT) != null

/** Drops the state for as long as the viewer recomputes its diff, and reinstalls it from [isActualAfterRediff] afterwards. */
private fun DiffViewerBase.trackRediff(
  disposable: Disposable,
  actualState: FrontendDiffActualStateHolder,
  isActualAfterRediff: () -> Boolean,
) {
  val listener = object : DiffViewerListener() {
    override fun onBeforeRediff() {
      actualState.update(false)
    }

    override fun onAfterRediff() {
      val isActual = isActualAfterRediff()
      actualState.update(isActual, mappingChanged = isActual)
    }

    override fun onRediffAborted() {
      actualState.update(false)
    }
  }
  addListener(listener)
  Disposer.register(disposable, Disposable { removeListener(listener) })
}

/** Drops the state on every document change, since the diff it was computed from no longer describes the documents. */
private fun trackDocuments(
  disposable: Disposable,
  actualState: FrontendDiffActualStateHolder,
  documents: List<Document>,
) {
  @Suppress("SplitModeApiUsage")
  val listener = object : DocumentListener {
    override fun documentChanged(event: DocumentEvent) {
      actualState.update(false)
    }
  }
  for (document in documents.distinct()) {
    document.addDocumentListener(listener, disposable)
  }
}
