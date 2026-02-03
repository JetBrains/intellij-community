// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.documentation.ide.ui

sealed interface PopupUpdateEvent {

  data object ToolbarSizeChanged : PopupUpdateEvent

  data object FontChanged : PopupUpdateEvent

  data object RestoreSize : PopupUpdateEvent

  data class ContentChanged(val updateKind: ContentUpdateKind) : PopupUpdateEvent

  enum class ContentUpdateKind {
    InfoMessage,
    DocumentationPageOpened,
    DocumentationPageNavigated,
  }
}
