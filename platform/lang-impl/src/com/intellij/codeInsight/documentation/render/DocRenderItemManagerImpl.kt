// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.documentation.render

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.CustomFoldRegion
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.ex.util.EditorScrollingPositionKeeper
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolderEx
import com.intellij.util.ConcurrencyUtil
import com.intellij.util.containers.toArray
import com.intellij.util.messages.Topic
import kotlinx.collections.immutable.toImmutableList
import java.util.*
import java.util.function.BooleanSupplier
import java.util.function.Consumer

class DocRenderItemManagerImpl : DocRenderItemManager {
  override fun getItemAroundOffset(editor: Editor, offset: Int): DocRenderItem? {
    val items = editor.getUserData(OWN_ITEMS)
    if (items.isNullOrEmpty()) return null
    val document = editor.document
    if (offset < 0 || offset > document.textLength) return null
    val line = document.getLineNumber(offset)
    val itemOnAdjacentLine = items.filter {
      if (!it.isValid) return@filter false
      val startLine = document.getLineNumber(it.highlighter.startOffset)
      val endLine = document.getLineNumber(it.highlighter.endOffset)
      line >= startLine - 1 && line <= endLine + 1
    }.minByOrNull { it.highlighter.startOffset }
    if (itemOnAdjacentLine != null) return itemOnAdjacentLine
    var foundItem: DocRenderItemImpl? = null
    var foundStartOffset = 0
    for (item in items) {
      if (!item.isValid) continue
      val documentation = item.getInlineDocumentation() ?: continue
      val ownerTextRange = documentation.documentationOwnerRange
      if (ownerTextRange == null || !ownerTextRange.containsOffset(offset)) continue
      val startOffset = ownerTextRange.startOffset
      if (foundItem != null && foundStartOffset >= startOffset) continue
      foundItem = item
      foundStartOffset = startOffset
    }
    return foundItem
  }

  override fun getItems(editor: Editor): Collection<DocRenderItem>? {
    val items = editor.getUserData(OWN_ITEMS) ?: return null
    return items
  }

  override fun removeAllItems(editor: Editor) {
    setItemsToEditor(editor, DocRenderPassFactory.Items(), false)
  }

  override fun setItemsToEditor(editor: Editor, itemsToSet: DocRenderPassFactory.Items, collapseNewItems: Boolean) {
    if (editor.getUserData(OWN_ITEMS) == null && itemsToSet.isEmpty) return
    val items = ConcurrencyUtil.computeIfAbsent(editor, OWN_ITEMS) { mutableListOf() }
    keepScrollingPositionWhile(editor) {
      val foldingTasks = mutableListOf<Runnable>()
      val itemsToUpdateRenderers: MutableList<DocRenderItemImpl> = ArrayList()
      val itemsToUpdateText: MutableList<String> = ArrayList()
      var updated = false
      val it = items.iterator()
      while (it.hasNext()) {
        val existingItem = it.next()
        val matchingNewItem = if (existingItem.isValid && !existingItem.isZombie) {
          itemsToSet.removeItem(existingItem.highlighter)
        }
        else {
          null
        }
        if (matchingNewItem == null) {
          updated = updated or existingItem.remove(foldingTasks)
          it.remove()
        }
        else if (matchingNewItem.textToRender != null && matchingNewItem.textToRender != existingItem.textToRender) {
          itemsToUpdateRenderers.add(existingItem)
          itemsToUpdateText.add(matchingNewItem.textToRender)
        }
        else {
          existingItem.updateIcon(foldingTasks)
        }
      }
      val newRenderItems: MutableCollection<DocRenderItemImpl> = ArrayList()
      for (item in itemsToSet) {
        val newItem = DocRenderItemImpl(
          editor,
          item.textRange,
          if (collapseNewItems) null else item.textToRender,
          DocRendererProvider.getInstance()::provideDocRenderer,
          InlineDocumentationFinder.getInstance(editor.project),
          itemsToSet.isZombie,
        )
        newRenderItems.add(newItem)
        if (collapseNewItems) {
          updated = updated or newItem.toggle(foldingTasks)
          itemsToUpdateRenderers.add(newItem)
          itemsToUpdateText.add(item.textToRender)
        }
      }
      editor.foldingModel.runBatchFoldingOperation({
                                                     foldingTasks.forEach(Consumer { obj: Runnable -> obj.run() })
                                                   }, true, false)
      for (i in itemsToUpdateRenderers.indices) {
        itemsToUpdateRenderers[i].textToRender = itemsToUpdateText[i]
      }
      DocRenderItemUpdater.updateRenderers(itemsToUpdateRenderers, true)
      ApplicationManager.getApplication().messageBus.syncPublisher(TOPIC).itemsTextChanged(editor, itemsToUpdateRenderers)
      items.addAll(newRenderItems)
      updated
    }
    setupListeners(editor, items.isEmpty())
  }

  override fun setupListeners(editor: Editor, disable: Boolean) {
    if (disable) {
      editor.caretModel.removeCaretListener(MyCaretListener)
    } else if (!areListenersAttached(editor)) {
      editor.caretModel.addCaretListener(MyCaretListener)
    }
    super.setupListeners(editor, disable)
  }

  override fun resetToDefaultState(editor: Editor) {
    val items = editor.getUserData(OWN_ITEMS) ?: return
    val editorSetting = DocRenderManager.isDocRenderingEnabled(editor)
    keepScrollingPositionWhile(editor) {
      val foldingTasks = mutableListOf<Runnable>()
      var updated = false
      for (item in items) {
        if (item.isValid && item.foldRegion == null == editorSetting) {
          updated = updated or item.toggle(foldingTasks)
        }
      }
      editor.foldingModel.runBatchFoldingOperation({
                                                     foldingTasks.forEach(Consumer { obj: Runnable -> obj.run() })
                                                   }, true, false)
      updated
    }
  }

  override fun isRenderedDocHighlighter(highlighter: RangeHighlighter): Boolean {
    return true == highlighter.getUserData(OWNS_HIGHLIGHTER)
  }

  object MyCaretListener : CaretListener {
    override fun caretPositionChanged(event: CaretEvent) {
      onCaretUpdate(event)
    }

    override fun caretAdded(event: CaretEvent) {
      onCaretUpdate(event)
    }

    private fun onCaretUpdate(event: CaretEvent) {
      val caret = event.caret ?: return
      val caretOffset = caret.offset
      val foldRegion = caret.editor.foldingModel.getCollapsedRegionAtOffset(caretOffset)
      if (foldRegion is CustomFoldRegion && caretOffset > foldRegion.getStartOffset()) {
        val renderer = foldRegion.renderer
        if (renderer is DocRenderer) {
          val item = renderer.item
          item.toggle()
        }
      }
    }
  }

  interface Listener {
    /**
     * Called only for existing items whose text was changed.
     */
    fun itemsTextChanged(editor: Editor, items: Collection<DocRenderItem>)
  }

  companion object {
    @Topic.AppLevel
    val TOPIC: Topic<Listener> = Topic(
      Listener::class.java, Topic.BroadcastDirection.NONE, true)
    private val OWN_ITEMS = Key.create<MutableList<DocRenderItemImpl>>("doc.render.items")
    @JvmField
    val OWNS_HIGHLIGHTER: Key<Boolean> = Key.create("doc.render.highlighter")
    private fun keepScrollingPositionWhile(editor: Editor, task: BooleanSupplier) {
      val keeper = EditorScrollingPositionKeeper(editor)
      keeper.savePosition()
      if (task.asBoolean) keeper.restorePosition(false)
    }
  }
}
