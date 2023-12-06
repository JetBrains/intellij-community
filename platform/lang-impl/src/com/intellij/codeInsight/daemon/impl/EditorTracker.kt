// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet")

package com.intellij.codeInsight.daemon.impl

import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.messages.Topic
import java.util.*

interface EditorTracker {
  companion object {
    @JvmStatic
    fun getInstance(project: Project): EditorTracker = project.service<EditorTracker>()
  }

  @get:RequiresEdt
  @set:RequiresEdt
  @set:Deprecated("do not call, it may corrupt daemon internal data structures")
  var activeEditors: List<Editor>
}

interface EditorTrackerListener : EventListener {
  companion object {
    @Topic.ProjectLevel
    @JvmField
    val TOPIC: Topic<EditorTrackerListener> = Topic(EditorTrackerListener::class.java, Topic.BroadcastDirection.NONE, true)
  }

  fun activeEditorsChanged(activeEditors: List<Editor>)
}
