// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.ui.popup.AbstractPopup
import com.intellij.util.messages.Topic
import org.jetbrains.annotations.ApiStatus.Internal


@Internal
interface EditorMouseHoverPopupListener {
  fun popupShown(editor: Editor, popup: AbstractPopup, info: HighlightInfo?)
  fun popupUpdated(editor: Editor, popup: AbstractPopup, info: HighlightInfo?)

  companion object {
    @Topic.ProjectLevel
    @JvmField
    val TOPIC: Topic<EditorMouseHoverPopupListener> = Topic(EditorMouseHoverPopupListener::class.java)
  }
}
