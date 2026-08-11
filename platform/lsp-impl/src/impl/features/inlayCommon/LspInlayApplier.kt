package com.intellij.platform.lsp.impl.features.inlayCommon

import com.intellij.codeInsight.hints.InlayContentListener
import com.intellij.codeInsight.hints.presentation.InlayPresentation
import com.intellij.codeInsight.hints.presentation.PresentationFactory
import com.intellij.codeInsight.hints.presentation.PresentationRenderer
import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.impl.LspCoroutineScopeService
import com.intellij.platform.lsp.impl.features.inlayHint.collectInlayHintItems
import com.intellij.platform.lsp.impl.features.inlayHintColor.collectColorInlayItems
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * One inline inlay to display — an inlay hint or a document-color swatch.
 */
internal interface LspInlayItem {
  /** The document offset at which the inline inlay is anchored. */
  val offset: Int

  /**
   * A value-equality key for the rendered content. MUST exclude [offset] (the inlay moves with edits on its own).
   * Identities of different features are type-distinct, so a hint and a swatch at the same offset never cross-match.
   */
  val identity: Any

  /** Builds the presentation; called on the EDT inside [com.intellij.openapi.editor.InlayModel.execute]. */
  fun buildPresentation(editor: Editor, factory: PresentationFactory): InlayPresentation
}

/**
 * Applies LSP inline inlays (inlay hints and document color) directly to the editor
 * [com.intellij.openapi.editor.InlayModel] when a server response arrives, instead of restarting the daemon. This is
 * the inlay-model counterpart of [com.intellij.platform.lsp.impl.features.highlighting.LspHighlightingApplier], which
 * does the analogous thing for the markup model — and like it, a single applier collects ALL features together
 * ([collectInlays]) under one per-file generation counter, rather than one applier per feature.
 *
 * Updates are applied as a **minimal diff** ([applyToEditor]): inlays whose offset and rendered [LspInlayItem.identity]
 * are unchanged are left in place (a typical refresh re-fetches the whole document but invalidates only a handful of
 * items), only the changed/removed ones are disposed and the new ones added. This preserves inlay identity and avoids
 * the flicker/layout-thrash/GC-churn a dispose-all-then-readd would cause.
 */
@Service(Service.Level.PROJECT)
internal class LspInlayApplier(private val project: Project) {

  private val fileToCurrentGeneration = ConcurrentHashMap<VirtualFile, AtomicLong>()
  private val serializedDispatcher = Dispatchers.Default.limitedParallelism(1)

  /**
   * Called when any backing cache (inlay hint, document color) is updated for [file]. Increments the per-file
   * generation and launches a coroutine that re-collects and re-applies. If a newer refresh has already bumped the
   * generation before the collectInlays starts, the stale coroutine bails.
   *
   * The bail check is intentionally only BEFORE [collectInlays], not before the apply: the serialized dispatcher runs each
   * coroutine's collectInlays to its first suspension before the next starts, so applies reach the EDT in generation order
   * and the newest one wins (the diff is against the current cache). A second bail check on the EDT would livelock
   * under a frequent caller — every coroutine superseded before it can paint — so it is omitted (as in
   * [com.intellij.platform.lsp.impl.features.highlighting.LspHighlightingApplier]).
   */
  fun scheduleRefresh(file: VirtualFile) {
    if (file is VirtualFileWindow || !file.isInLocalFileSystem) return

    val generationCounter = fileToCurrentGeneration.computeIfAbsent(file) { AtomicLong() }
    val gen = generationCounter.incrementAndGet()
    LspCoroutineScopeService.getInstance(project).cs.launch(serializedDispatcher) {
      if (generationCounter.get() > gen) return@launch
      val items = readAction { collectInlays(file) }
      withContext(Dispatchers.EDT) {
        // InlayModel is per-editor, so apply to every editor currently showing this file.
        // Inlays are not document content: this runs on the EDT but intentionally takes NO write action.
        for (editor in EditorFactory.getInstance().allEditors) {
          if (editor.virtualFile == file) {
            applyToEditor(editor, items)
          }
        }
      }
    }
  }

  /**
   * Releases per-file state once [file] is fully closed (no editor shows it anymore). The inlays were already disposed
   * with their editors' InlayModels; this just drops the generation counter so the map stays bounded.
   */
  fun onFileClosed(file: VirtualFile) {
    fileToCurrentGeneration.remove(file)
  }

  /**
   * Collects every LSP inlay item for [file] across features. Also triggers a server request per feature when its cache is stale.
   */
  private fun collectInlays(file: VirtualFile): List<LspInlayItem> =
    collectInlayHintItems(project, file) + collectColorInlayItems(project, file)

  private fun applyToEditor(editor: Editor, items: List<LspInlayItem>) {
    val inlayModel = editor.inlayModel
    val existing = inlayModel.getInlineElementsInRange(0, editor.document.textLength, PresentationRenderer::class.java)

    // Bucket our existing inlays by (current offset + rendered identity). The offset is read live, so inlays that the
    // platform already shifted in response to edits are matched at their new position.
    // Ownership: the renderer-type filter alone is not enough (other plugins also use PresentationRenderer), so we
    // additionally require our MANAGED key — the diff/disposal below must never touch an inlay we don't own.
    val existingByKey = HashMap<InlayMatchKey, ArrayDeque<Inlay<*>>>()
    for (inlay in existing) {
      val identity = inlay.getUserData(MANAGED) ?: continue // not ours
      existingByKey.getOrPut(InlayMatchKey(inlay.offset, identity)) { ArrayDeque() }.addLast(inlay)
    }

    // Match each incoming item against an existing inlay; a match is left untouched (no recreate => no flicker).
    val toAdd = ArrayList<LspInlayItem>()
    for (item in items) {
      val key = InlayMatchKey(item.offset, item.identity)
      if (existingByKey[key]?.removeFirstOrNull() == null) {
        toAdd.add(item)
      }
    }

    // Existing inlays that weren't reused are stale (changed payload or gone) and must be disposed.
    val toDispose = existingByKey.values.flatten()
    if (toAdd.isEmpty() && toDispose.isEmpty()) return

    val isBulk = toAdd.size + toDispose.size > BULK_THRESHOLD
    // The diff is computed above, outside execute(): this block must contain only inlay add/dispose ops
    // (no document/folding/caret/soft-wrap changes), per InlayModel.execute's batch-mode purity contract.
    inlayModel.execute(isBulk) {
      toDispose.forEach { Disposer.dispose(it) }

      val factory = PresentationFactory(editor)
      for (item in toAdd) {
        val presentation = item.buildPresentation(editor, factory)
        val inlay = inlayModel.addInlineElement(item.offset, false, PresentationRenderer(presentation)) ?: continue
        presentation.addListener(InlayContentListener(inlay))
        inlay.putUserData(MANAGED, item.identity)
      }
    }
  }

  private data class InlayMatchKey(val offset: Int, val identity: Any)

  companion object {
    private const val BULK_THRESHOLD = 100

    /** Marks an inline inlay as owned by us, and stores its [LspInlayItem.identity] for the next diff. */
    private val MANAGED: Key<Any> = Key.create("lsp.inlay.managed")

    fun getInstance(project: Project): LspInlayApplier = project.service()
  }
}
