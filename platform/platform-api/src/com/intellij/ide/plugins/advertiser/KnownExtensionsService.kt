// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.advertiser

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.util.xmlb.annotations.Property
import com.intellij.util.xmlb.annotations.Tag
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

@Service(Service.Level.APP)
@State(
  name = "KnownExtensionsService",
  storages = [Storage(StoragePathMacros.CACHE_FILE, roamingType = RoamingType.DISABLED)],
  allowLoadInTests = true,
)
@ApiStatus.Internal
class KnownExtensionsService : SimplePersistentStateComponent<KnownExtensionsService.State>(State()) {

  @Tag("knownExtensions")
  class State : BaseState() {

    @get:Property(surroundWithTag = false)
    var extensions by property<KnownExtensions?>(null) { it == null }
  }

  companion object {

    private val lock = ReentrantReadWriteLock()

    @JvmStatic
    val instance: KnownExtensionsService
      get() = ApplicationManager.getApplication().getService(KnownExtensionsService::class.java)
  }

  var extensions: KnownExtensions?
    get() = lock.read { state.extensions }
    set(value) = lock.write { state.extensions = value }

  override fun loadState(state: State) = lock.write { super.loadState(state) }
}