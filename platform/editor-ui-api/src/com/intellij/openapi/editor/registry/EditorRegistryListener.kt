// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.registry

import com.intellij.util.messages.Topic
import org.jetbrains.annotations.ApiStatus
import java.util.EventListener

@ApiStatus.Internal
fun interface EditorRegistryListener : EventListener {
  /**
   * Called after a change of a key in [EditorRegistry].
   * Runs on the thread that changed the registry.
   * Two calls can arrive out of order, so read [EditorRegistry.current] when you act.
   */
  fun snapshotChanged(oldSnapshot: EditorRegistry, newSnapshot: EditorRegistry)

  companion object {
    @Topic.AppLevel
    @JvmField
    val TOPIC: Topic<EditorRegistryListener> = Topic(EditorRegistryListener::class.java, Topic.BroadcastDirection.NONE)
  }
}
