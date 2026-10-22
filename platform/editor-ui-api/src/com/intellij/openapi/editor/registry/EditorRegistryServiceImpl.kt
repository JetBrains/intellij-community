// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.registry

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.openapi.util.registry.RegistryValueListener
import kotlinx.coroutines.CoroutineScope
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater

internal class EditorRegistryServiceImpl(private val coroutineScope: CoroutineScope) : EditorRegistryService {
  @Volatile private var currentSnapshot: EditorRegistry = EditorRegistry.DEFAULT
  private val subscribedKeys: MutableSet<String> = ConcurrentHashMap.newKeySet()
  private val listener = object : RegistryValueListener {
    override fun afterValueChanged(value: RegistryValue) {
      refresh()
    }
  }

  init {
    refresh()
  }

  override fun snapshot(): EditorRegistry {
    return currentSnapshot
  }

  private fun refresh() {
    while (true) {
      val oldSnapshot = currentSnapshot
      val newSnapshot = EditorRegistry.read(::subscribe)
      if (SNAPSHOT_UPDATER.compareAndSet(this, oldSnapshot, newSnapshot)) {
        publish(oldSnapshot, newSnapshot)
        return
      }
    }
  }

  // the subscription comes before the first read of the key, so a concurrent change is not lost
  private fun subscribe(key: String) {
    if (subscribedKeys.add(key)) {
      RegistryManager.getInstance().get(key).addListener(listener, coroutineScope)
    }
  }

  private fun publish(oldSnapshot: EditorRegistry, newSnapshot: EditorRegistry) {
    val publisher = ApplicationManager.getApplication().messageBus.syncPublisher(EditorRegistryListener.TOPIC)
    publisher.snapshotChanged(oldSnapshot, newSnapshot)
  }

  private companion object {
    @JvmStatic
    private val SNAPSHOT_UPDATER: AtomicReferenceFieldUpdater<EditorRegistryServiceImpl, EditorRegistry> =
      AtomicReferenceFieldUpdater.newUpdater(
        EditorRegistryServiceImpl::class.java,
        EditorRegistry::class.java,
        "currentSnapshot",
      )
  }
}
