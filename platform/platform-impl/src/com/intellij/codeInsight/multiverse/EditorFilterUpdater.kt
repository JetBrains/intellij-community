// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.multiverse

import com.intellij.openapi.editor.impl.EditorHighlightingPredicate
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.util.Key
import com.intellij.util.application

internal class EditorFilterUpdater : EditorContextManager.ChangeEventListener {
  override fun editorContextsChanged(event: EditorContextManager.ChangeEvent) {
    val editor = event.editor as? EditorImpl ?: return
    application.invokeLater {
      val predicate = EditorSelectedContextsPredicate(event.newContexts)
      editor.addHighlightingPredicate(editorPredicateKey, predicate)
    }
  }
}

private class EditorSelectedContextsPredicate(val contexts: EditorSelectedContexts) : EditorHighlightingPredicate {
  override fun shouldRender(highlighter: RangeHighlighter): Boolean {
    val highlighterContext = highlighter.codeInsightContext ?: return true
    return highlighterContext in contexts
  }
}

private val editorPredicateKey: Key<EditorSelectedContextsPredicate> = Key.create("editorPredicateEquality")
