// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.autoimport

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import java.util.concurrent.atomic.AtomicInteger

class ProjectAwareWrapper(
  val delegate: ExternalSystemProjectAware,
  parentDisposable: Disposable
) : ExternalSystemProjectAware by delegate {

  val subscribeCounter = AtomicInteger(0)
  val unsubscribeCounter = AtomicInteger(0)
  val reloadCounter = AtomicInteger(0)
  val startReloadCounter = AtomicInteger(0)
  val finishReloadCounter = AtomicInteger(0)
  override val settingsFiles = delegate.settingsFiles

  init {
    delegate.subscribe(object : ExternalSystemProjectListener {
      override fun onProjectReloadStart() {
        startReloadCounter.incrementAndGet()
      }

      override fun onProjectReloadFinish(status: ExternalSystemRefreshStatus) {
        finishReloadCounter.incrementAndGet()
      }
    }, parentDisposable)
  }

  override fun subscribe(listener: ExternalSystemProjectListener, parentDisposable: Disposable) {
    delegate.subscribe(listener, parentDisposable)
    subscribeCounter.incrementAndGet()
    Disposer.register(parentDisposable, Disposable { unsubscribeCounter.incrementAndGet() })
  }

  override fun reloadProject(context: ExternalSystemProjectReloadContext) {
    reloadCounter.incrementAndGet()
    delegate.reloadProject(context)
  }
}