// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.ui

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
import com.intellij.util.containers.FixedHashMap
import java.util.function.Function
import javax.swing.Icon

class IconDeferrerImpl internal constructor() : IconDeferrer() {
  companion object {
    private val evaluationIsInProgress = ThreadLocal.withInitial { false }

    fun evaluateDeferred(runnable: Runnable) {
      try {
        evaluationIsInProgress.set(true)
        runnable.run()
      }
      finally {
        evaluationIsInProgress.set(false)
      }
    }
  }

  private val LOCK = Any()

  // guarded by LOCK
  private val iconCache = FixedHashMap<Any, Icon>(SystemProperties.getIntProperty("ide.icons.deferrerCacheSize", 1000))

  // guarded by LOCK
  private var lastClearTimestamp: Long = 0

  init {
    val connection = ApplicationManager.getApplication().messageBus.connect()
    connection.subscribe(PsiModificationTracker.TOPIC, PsiModificationTracker.Listener { clearCache() })
    // update "locked" icon
    connection.subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
      override fun after(events: List<VFileEvent>) {
        clearCache()
      }
    })
    connection.subscribe(ProjectCloseListener.TOPIC, object : ProjectCloseListener {
      override fun projectClosed(project: Project) {
        clearCache()
      }
    })
    connection.subscribe(VirtualFileAppearanceListener.TOPIC, VirtualFileAppearanceListener { clearCache() })
    LowMemoryWatcher.register({ clearCache() }, connection)
  }

  override fun clearCache() {
    synchronized(LOCK) {
      iconCache.clear()
      lastClearTimestamp++
    }
  }

  override fun <T> defer(base: Icon?, param: T, evaluator: Function<in T, out Icon>): Icon {
    if (evaluationIsInProgress.get()) {
      return evaluator.apply(param)
    }

    synchronized(LOCK) {
      val cached = iconCache.get(param)
      if (cached != null) {
        return cached
      }

      val started = lastClearTimestamp
      val result = DeferredIconImpl(baseIcon = base,
                                    param = param,
                                    needReadAction = true,
                                    evaluator = evaluator) { source, r ->
        synchronized(LOCK) {
          // check if our result is not outdated yet
          if (started == lastClearTimestamp) {
            iconCache.put((source as DeferredIconImpl<*>?)!!.param!!, r)
          }
        }
      }
      iconCache.put(param, result)
      return result
    }
  }

  override fun equalIcons(icon1: Icon, icon2: Icon): Boolean = DeferredIconImpl.equalIcons(icon1, icon2)
}
