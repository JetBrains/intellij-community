// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.ui

import com.github.benmanes.caffeine.cache.Caffeine
import com.intellij.ide.ui.VirtualFileAppearanceListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectCloseListener
import com.intellij.openapi.util.LowMemoryWatcher
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.util.SystemProperties
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asContextElement
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.LongAdder
import javax.swing.Icon

internal class IconDeferrerImpl private constructor(private val coroutineScope: CoroutineScope) : IconDeferrer() {
  companion object {
    private val isEvaluationInProgress = ThreadLocal.withInitial { false }

    suspend inline fun <T> evaluateDeferred(crossinline runnable: suspend () -> T): T {
      try {
        isEvaluationInProgress.set(true)
        return withContext(isEvaluationInProgress.asContextElement()) {
          runnable()
        }
      }
      finally {
        isEvaluationInProgress.set(false)
      }
    }
  }

  private val iconCache = Caffeine.newBuilder()
    .maximumSize(SystemProperties.getLongProperty("ide.icons.deferrerCacheSize", 1000))
    .build<Any, Icon>()

  private var lastClearTimestamp = LongAdder()

  init {
    val connection = ApplicationManager.getApplication().messageBus.connect()
    connection.subscribe(PsiModificationTracker.TOPIC, PsiModificationTracker.Listener(::scheduleClearCache))
    // update "locked" icon
    connection.subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
      override fun after(events: List<VFileEvent>) {
        scheduleClearCache()
      }
    })
    connection.subscribe(ProjectCloseListener.TOPIC, object : ProjectCloseListener {
      override fun projectClosed(project: Project) {
        scheduleClearCache()
      }
    })
    connection.subscribe(VirtualFileAppearanceListener.TOPIC, VirtualFileAppearanceListener { scheduleClearCache() })
    LowMemoryWatcher.register(::scheduleClearCache, connection)
  }

  private fun scheduleClearCache() {
    coroutineScope.launch {
      iconCache.invalidateAll()
    }
  }

  override fun clearCache() {
    iconCache.invalidateAll()
    lastClearTimestamp.increment()
  }

  override fun <T> defer(base: Icon?, param: T, evaluator: (T) -> Icon?): Icon {
    if (isEvaluationInProgress.get()) {
      return evaluator(param) ?: DeferredIconImpl.EMPTY_ICON
    }

    return iconCache.get(param) {
      val started = lastClearTimestamp.sum()
      DeferredIconImpl(baseIcon = base,
                       param = param,
                       needReadAction = true,
                       evaluator = evaluator) { source, icon ->
        // check if our result is not outdated yet
        if (started == lastClearTimestamp.sum()) {
          iconCache.put(source.param!!, icon)
        }
      }
    }
  }

  override fun equalIcons(icon1: Icon?, icon2: Icon?): Boolean = DeferredIconImpl.equalIcons(icon1, icon2)
}
