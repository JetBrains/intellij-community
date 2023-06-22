// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.declarative.impl

import com.intellij.codeInsight.hints.declarative.InlayActionData
import com.intellij.codeInsight.hints.declarative.impl.util.TinyTree

class PresentationEntryBuilder(val state: TinyTree<Any?>) {
  private val entries = ArrayList<InlayPresentationEntry>()
  private var currentClickArea: InlayMouseArea? = null
  private var parentIndexToSwitch: Byte = -1
  private var indexOfClosestParentList: Byte = -1

  // the max possible depth is 100, so it is not scary to do it using stack recursion
  fun buildPresentationEntries(): Array<InlayPresentationEntry> {
    buildSubtreeForIdOnly(0)
    if (entries.isEmpty()) {
      throw NoPresentableEntriesException()
    }
    return entries.toTypedArray()
  }

  private fun buildSubtreeForIdOnly(index: Byte) {
    state.processChildren(index) { childIndex ->
      buildNode(childIndex)
      true
    }
  }

  private fun buildNode(childIndex: Byte) {
    when (val tag = state.getBytePayload(childIndex)) {
      InlayTags.LIST_TAG -> buildSubtreeForIdOnly(childIndex)
      InlayTags.COLLAPSIBLE_LIST_IMPLICITLY_EXPANDED_TAG -> selectFromList(childIndex, collapsed = false)
      InlayTags.COLLAPSIBLE_LIST_IMPLICITLY_COLLAPSED_TAG -> selectFromList(childIndex, collapsed = true)
      InlayTags.COLLAPSIBLE_LIST_EXPLICITLY_EXPANDED_TAG -> selectFromList(childIndex, collapsed = false)
      InlayTags.COLLAPSIBLE_LIST_EXPLICITLY_COLLAPSED_TAG -> selectFromList(childIndex, collapsed = true)
      InlayTags.COLLAPSE_BUTTON_TAG -> {
        val savedIndexToSwitch = parentIndexToSwitch
        try {
          parentIndexToSwitch = indexOfClosestParentList
          buildSubtreeForIdOnly(childIndex)
        } finally {
          parentIndexToSwitch = savedIndexToSwitch
        }
      }
      InlayTags.TEXT_TAG -> {
        when (val dataPayload = state.getDataPayload(childIndex)) {
          is String -> {
            val area = this.currentClickArea
            val entry = TextInlayPresentationEntry(dataPayload, clickArea = area, parentIndexToSwitch = parentIndexToSwitch)
            addEntry(entry)
            area?.entries?.add(entry)
          }
          is ActionWithContent -> {
            val area = this.currentClickArea ?: InlayMouseArea(dataPayload.actionData)
            val entry = TextInlayPresentationEntry(dataPayload.content as String, clickArea = area, parentIndexToSwitch = parentIndexToSwitch)
            addEntry(entry)
            area.entries.add(entry)
          }
          else -> throw IllegalStateException("Illegal payload for text tag: $dataPayload")
        }
      }
      InlayTags.CLICK_HANDLER_SCOPE_TAG -> {
        val actionData = state.getDataPayload(childIndex) as InlayActionData
        val clickArea = InlayMouseArea(actionData)
        val saved = this.currentClickArea
        this.currentClickArea = clickArea
        state.processChildren(childIndex) { ch ->
          buildNode(ch)
          true
        }
        this.currentClickArea = saved
      }
      //InlayTags.ICON_TAG -> when (val dataPayload = state.getDataPayload(childIndex)) {
      //  is Icon -> {
      //    addEntry(IconInlayPresentationEntry(dataPayload))
      //  }
      //  is ActionWithContent -> {
      //    addEntry(IconInlayPresentationEntry(dataPayload.content as Icon), dataPayload.actionData)
      //  }
      //  else -> throw IllegalStateException("Illegal payload for text tag: $dataPayload")
      //}
      else -> throw IllegalStateException("Unknown tag: $tag")
    }
  }

  private fun selectFromList(index: Byte, collapsed: Boolean) {
    val savedIndexOfClosestParentList = indexOfClosestParentList
    indexOfClosestParentList = index
    try {
      val branchTag = if (collapsed) {
        InlayTags.COLLAPSIBLE_LIST_COLLAPSED_BRANCH_TAG
      }
      else {
        InlayTags.COLLAPSIBLE_LIST_EXPANDED_BRANCH_TAG
      }
      state.processChildren(index) { childIndex ->
        if (state.getBytePayload(childIndex) == branchTag) {
          buildSubtreeForIdOnly(childIndex)
        }
        true
      }
    }
    finally {
      indexOfClosestParentList = savedIndexOfClosestParentList
    }
  }

  private fun addEntry(presentation: InlayPresentationEntry) {
    entries.add(presentation)
  }
}

