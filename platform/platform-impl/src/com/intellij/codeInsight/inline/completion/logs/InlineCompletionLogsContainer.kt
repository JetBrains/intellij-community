// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.logs

import com.intellij.codeInsight.inline.completion.session.InlineCompletionSession
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.removeUserData
import com.intellij.platform.ml.feature.Feature
import com.intellij.platform.ml.logs.toEventField

class InlineCompletionLogsContainer {

  // TODO extentions? But actually it's ok to have it in this simple way.
  enum class Step {
    CONTEXT_COLLECTION,
    COMPLETION_MODEL_EXECUTION,
    POSTPROCESSING,
    SHOW,
  }
  private val logs = mutableListOf<Pair<Feature, Step>>() // TODO maybe step -> list<feature>?


  fun addFeature(step: Step, feature: Feature) {
    logs.add(feature to step)
  }

  fun log() {
    InlineCompletionLogs.Session.SESSION_EVENT.log(
      logs.map { (feature, step) ->
        // fixme: use step
        val eventField = createConverter(feature.declaration.toEventField())
        eventField.with(feature.value)
      }
    )
  }

  companion object {
    private val KEY: Key<InlineCompletionLogsContainer> = Key("inline.completion.logs.container")
    fun create(editor: Editor) {
      val session = requireNotNull(InlineCompletionSession.getOrNull(editor)) { "Cannot find session" }
      session.context.putUserData(KEY, InlineCompletionLogsContainer())
    }

    fun get(editor: Editor): InlineCompletionLogsContainer {
      return requireNotNull(editor.getUserData(KEY)) { "Cannot find logs container" }
    }

    fun remove(editor: Editor): InlineCompletionLogsContainer? {
      return editor.removeUserData(KEY)
    }
  }
}