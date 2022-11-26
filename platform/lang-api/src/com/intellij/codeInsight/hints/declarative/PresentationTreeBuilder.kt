// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.declarative

/**
 * Once the tree building started, it must provide at least one text node. Otherwise, an exception will be thrown.
 */
interface PresentationTreeBuilder {
  fun list(builder: PresentationTreeBuilder.() -> Unit)

  fun collapsibleList(state: CollapseState = CollapseState.NoPreference,
                      expandedState: CollapsiblePresentationTreeBuilder.() -> Unit,
                      collapsedState: CollapsiblePresentationTreeBuilder.() -> Unit)

  fun text(text: String, actionData: InlayActionData? = null)

  fun clickHandlerScope(actionData: InlayActionData, builder: PresentationTreeBuilder.() -> Unit)
}

interface CollapsiblePresentationTreeBuilder : PresentationTreeBuilder {
  fun toggleButton(builder: PresentationTreeBuilder.() -> Unit)
}

enum class CollapseState {
  Expanded,
  Collapsed,
  // the actual state will be calculated later
  NoPreference,
}

/**
 * Payload which will be available while handling click on the inlay.
 */
class InlayActionData(val payload: InlayActionPayload, val handlerId: String) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as InlayActionData

    if (payload != other.payload) return false
    if (handlerId != other.handlerId) return false

    return true
  }

  override fun hashCode(): Int {
    var result = payload.hashCode()
    result = 31 * result + handlerId.hashCode()
    return result
  }

  override fun toString(): String {
    return "InlayActionData(payload=$payload, handlerId='$handlerId')"
  }
}

sealed interface InlayActionPayload

class StringInlayActionPayload(val text: String) : InlayActionPayload {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as StringInlayActionPayload

    if (text != other.text) return false

    return true
  }

  override fun hashCode(): Int {
    return text.hashCode()
  }

  override fun toString(): String {
    return "StringInlayActionPayload(text='$text')"
  }
}