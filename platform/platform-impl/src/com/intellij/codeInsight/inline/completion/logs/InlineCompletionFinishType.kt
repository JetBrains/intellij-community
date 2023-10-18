// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.logs

enum class InlineCompletionFinishType {
  SELECTED,
  ESCAPE_PRESSED,
  KEY_PRESSED,
  INVALIDATED,
  MOUSE_PRESSED,
  CARET_CHANGED,
  DOCUMENT_CHANGED,
  EDITOR_REMOVED,
  FOCUS_LOST,
  EMPTY,
  ERROR,
  OTHER
}