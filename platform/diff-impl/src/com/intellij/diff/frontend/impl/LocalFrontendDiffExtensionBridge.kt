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
import com.intellij.diff.frontend.FrontendDiffContentKeys
import com.intellij.diff.frontend.FrontendDiffContext
import com.intellij.diff.frontend.FrontendDiffEditor
import com.intellij.diff.frontend.FrontendDiffExtension
import com.intellij.diff.frontend.FrontendDiffExtensionData
import com.intellij.diff.frontend.FrontendDiffLineLocation
import com.intellij.diff.frontend.FrontendDiffLineMapper
import com.intellij.diff.frontend.FrontendDiffRequest
import com.intellij.diff.frontend.FrontendDiffUserDataKeyDescriptor
import com.intellij.diff.frontend.FrontendDiffViewer
import com.intellij.diff.frontend.FrontendUnifiedDiffMapping
import com.intellij.diff.requests.ContentDiffRequest
import com.intellij.diff.requests.DiffRequest
import com.intellij.diff.tools.fragmented.UnifiedDiffViewer
import com.intellij.diff.tools.simple.SimpleDiffViewer
import com.intellij.diff.tools.simple.SimpleOnesideDiffViewer
import com.intellij.diff.tools.util.base.DiffViewerListener
import com.intellij.diff.util.DiffUserDataKeys
import com.intellij.diff.util.LineCol
import com.intellij.diff.util.Side
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.util.Disposer
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

/** Only works for local IDE (not rem-dev), for rem-dev RemoteFrontendDiffExtensionBridge.kt is used */
@ApiStatus.Internal
object LocalFrontendDiffExtensionBridge {
  private val LOG = logger<LocalFrontendDiffExtensionBridge>()

  @JvmStatic
  fun notifyViewerCreated(viewer: FrameDiffTool.DiffViewer, context: DiffContext, request: DiffRequest) {
    if (!ClientId.isCurrentlyUnderLocalId) return
    val contentRequest = request as? ContentDiffRequest ?: return
    val contents = contentRequest.contents.map { adaptContent(it) ?: return }
    val parentDisposable = Disposer.newDisposable("Frontend diff extension")
    val frontendRequest = FrontendDiffRequest(
      contents = contents,
      extensionData = LocalFrontendDiffExtensionData(context, request),
    )
    val frontendViewer = adaptViewer(viewer, parentDisposable)
    if (frontendViewer == null) {
      Disposer.dispose(parentDisposable)
      return
    }

    Disposer.register(viewer, parentDisposable)
    val frontendContext = FrontendDiffContext(
      project = context.project,
      place = context.getUserData(DiffUserDataKeys.PLACE),
      request = frontendRequest,
      parentDisposable = parentDisposable,
    )
    for (extension in FrontendDiffExtension.EP_NAME.extensionList) {
      try {
        extension.onViewerCreated(frontendViewer, frontendContext)
      }
      catch (e: Throwable) {
        LOG.error(e)
      }
    }
  }

  private fun adaptViewer(
    viewer: FrameDiffTool.DiffViewer,
    parentDisposable: Disposable,
  ) = when (viewer) {
    is SimpleOnesideDiffViewer -> adaptOneSideViewer(viewer)
    is SimpleDiffViewer -> adaptTwoSideViewer(viewer, parentDisposable)
    is UnifiedDiffViewer -> adaptUnifiedViewer(viewer, parentDisposable)
    else -> null
  }

  private fun adaptOneSideViewer(
    viewer: SimpleOnesideDiffViewer,
  ): FrontendDiffViewer.FrontendOneSideDiffViewer {
    val side = viewer.side
    return object : FrontendDiffViewer.FrontendOneSideDiffViewer {
      override val component: JComponent = viewer.component
      override val side: Side = side
      override val lineMapper: FrontendDirectDiffLineMapper = FrontendDirectDiffLineMapper(viewer.editor.document, side)
      override val editor: FrontendDiffEditor = FrontendDiffEditor(
        editor = viewer.editor,
      )
    }
  }

  private fun adaptTwoSideViewer(
    viewer: SimpleDiffViewer,
    parentDisposable: Disposable,
  ): FrontendDiffViewer.FrontendTwoSideDiffViewer {
    val mapping = LocalTwoSideDiffMapping(viewer, parentDisposable)
    return object : FrontendDiffViewer.FrontendTwoSideDiffViewer {
      override val component: JComponent = viewer.component
      override val leftLineMapper: FrontendDiffLineMapper = FrontendDirectDiffLineMapper(viewer.editor1.document, Side.LEFT, mapping)
      override val rightLineMapper: FrontendDiffLineMapper = FrontendDirectDiffLineMapper(viewer.editor2.document, Side.RIGHT, mapping)
      override val left: FrontendDiffEditor = FrontendDiffEditor(
        editor = viewer.editor1,
      )
      override val right: FrontendDiffEditor = FrontendDiffEditor(
        editor = viewer.editor2,
      )
    }
  }

  private fun adaptUnifiedViewer(
    viewer: UnifiedDiffViewer,
    parentDisposable: Disposable,
  ): FrontendDiffViewer.FrontendUnifiedDiffViewer {
    val mapping = LocalFrontendUnifiedDiffMapping(viewer, parentDisposable)
    return object : FrontendDiffViewer.FrontendUnifiedDiffViewer {
      override val component: JComponent = viewer.component
      override val mapping: FrontendUnifiedDiffMapping = mapping
      override val lineMapper: FrontendDiffLineMapper =
        FrontendUnifiedDiffLineMapper(viewer.editor.document, mapping, mapping::unifiedLineToLocation)
      override val editor: FrontendDiffEditor = FrontendDiffEditor(
        editor = viewer.editor,
      )
    }
  }

  private fun adaptContent(content: DiffContent): FrontendDiffContent? {
    return when (content) {
      is EmptyContent -> FrontendDiffContent(file = null, document = null, isCurrent = false)
      is DocumentContent -> FrontendDiffContent(
        file = (content as? FileContent)?.file,
        document = content.document,
        isCurrent = content.getUserData(FrontendDiffContentKeys.IS_CURRENT) == true,
      )
      else -> null
    }
  }
}

private class LocalFrontendDiffExtensionData(
  private val context: DiffContext,
  private val request: DiffRequest,
) : FrontendDiffExtensionData {
  override fun <T : Any> getContextData(descriptor: FrontendDiffUserDataKeyDescriptor<T>): T? = context.getUserData(descriptor.key)

  override fun <T : Any> getRequestData(descriptor: FrontendDiffUserDataKeyDescriptor<T>): T? = request.getUserData(descriptor.key)
}

/**
 * The two-side mapping of a local viewer: it is up to date whenever the viewer has a computed diff, which is what
 * [SimpleDiffViewer.transferPosition] needs to map a line onto the opposite side.
 */
private class LocalTwoSideDiffMapping(
  private val viewer: SimpleDiffViewer,
  parentDisposable: Disposable,
) : FrontendTwoSideDiffMapping {
  private var currentRevision = 0L
  private var available = false
  private val listeners = mutableListOf<() -> Unit>()

  init {
    val diffListener = object : DiffViewerListener() {
      override fun onBeforeRediff() {
        invalidate()
      }

      override fun onAfterRediff() {
        currentRevision++
        available = true
        notifyListeners()
      }

      override fun onRediffAborted() {
        invalidate()
      }
    }
    viewer.addListener(diffListener)
    Disposer.register(parentDisposable, Disposable { viewer.removeListener(diffListener) })

    @Suppress("SplitModeApiUsage")
    val documentListener = object : DocumentListener {
      override fun documentChanged(event: DocumentEvent) {
        invalidate()
      }
    }
    viewer.editor1.document.addDocumentListener(documentListener, parentDisposable)
    viewer.editor2.document.addDocumentListener(documentListener, parentDisposable)
  }

  override val isAvailable: Boolean get() = available

  override val revision: Long get() = currentRevision

  override fun addListener(parentDisposable: Disposable, listener: () -> Unit) {
    listeners += listener
    Disposer.register(parentDisposable, Disposable { listeners.remove(listener) })
  }

  override fun mapOtherSide(side: Side, line: Int): Int {
    if (!isAvailable) return -1
    return viewer.transferPosition(side, LineCol(line, 0)).line
  }

  private fun invalidate() {
    if (!available) return
    available = false
    notifyListeners()
  }

  private fun notifyListeners() {
    listeners.toList().forEach { it() }
  }
}

private class LocalFrontendUnifiedDiffMapping(
  private val viewer: UnifiedDiffViewer,
  parentDisposable: Disposable,
) : FrontendUnifiedDiffMapping {
  private var currentRevision = 0L
  private var available = viewer.getLineNumberMapping(Side.LEFT) != null && viewer.getLineNumberMapping(Side.RIGHT) != null
  private val listeners = mutableListOf<() -> Unit>()

  init {
    val diffListener = object : DiffViewerListener() {
      override fun onBeforeRediff() {
        invalidate()
      }

      override fun onAfterRediff() {
        if (viewer.getLineNumberMapping(Side.LEFT) == null || viewer.getLineNumberMapping(Side.RIGHT) == null) {
          invalidate()
          return
        }
        currentRevision++
        available = true
        notifyListeners()
      }

      override fun onRediffAborted() {
        invalidate()
      }
    }
    viewer.addListener(diffListener)
    Disposer.register(parentDisposable, Disposable { viewer.removeListener(diffListener) })

    @Suppress("SplitModeApiUsage")
    val documentListener = object : DocumentListener {
      override fun documentChanged(event: DocumentEvent) {
        invalidate()
      }
    }
    viewer.editor.document.addDocumentListener(documentListener, parentDisposable)
    viewer.getDocument(Side.LEFT).addDocumentListener(documentListener, parentDisposable)
    viewer.getDocument(Side.RIGHT).addDocumentListener(documentListener, parentDisposable)
  }

  override val isAvailable: Boolean get() = available

  override val revision: Long get() = currentRevision

  override fun addListener(parentDisposable: Disposable, listener: () -> Unit) {
    listeners += listener
    Disposer.register(parentDisposable, Disposable { listeners.remove(listener) })
  }

  override fun sideLineToUnified(side: Side, line: Int): Int? {
    val sideDocument = viewer.getDocument(side)
    if (!isAvailable || line !in 0 until sideDocument.lineCount) return null
    return viewer.transferLineToOnesideStrict(side, line).takeIf { it >= 0 }
  }

  override fun unifiedLineToSide(side: Side, line: Int, strict: Boolean): Int? {
    if (!isAvailable || line !in 0 until viewer.editor.document.lineCount) return null
    val sideLine = if (strict) {
      viewer.transferLineFromOnesideStrict(side, line)
    }
    else {
      viewer.transferLineFromOneside(side, line)
    }
    return sideLine.takeIf { it >= 0 }
  }

  fun unifiedLineToLocation(line: Int): FrontendDiffLineLocation? {
    if (!isAvailable || line !in 0 until viewer.editor.document.lineCount) return null
    val mapping = viewer.transferLineFromOnesideStrict(line) ?: return null
    val lines = mapping.first
    val side = mapping.second
    val sideLine = lines[side.index].takeIf { it >= 0 } ?: return null
    return FrontendDiffLineLocation(side, sideLine)
  }

  override fun unifiedLineToSideLines(line: Int): Pair<Int, Int> {
    if (!isAvailable || line !in 0 until viewer.editor.document.lineCount) return -1 to -1
    val lines = viewer.transferLineFromOneside(line).first
    return lines[0] to lines[1]
  }

  private fun invalidate() {
    if (!available) return
    available = false
    notifyListeners()
  }

  private fun notifyListeners() {
    listeners.toList().forEach { it() }
  }
}
