// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl.jdkDownloader

import com.intellij.openapi.components.*
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.util.xmlb.annotations.OptionTag
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class JdkUpdaterStateData : BaseState() {
  @get:OptionTag
  val dndVersions by stringSet()
}

@State(name = "jdk-update-state", storages = [Storage(StoragePathMacros.CACHE_FILE)], allowLoadInTests = true)
@Service
class JdkUpdaterState : SimplePersistentStateComponent<JdkUpdaterStateData>(JdkUpdaterStateData()) {
  private val lock = ReentrantLock()

  override fun loadState(state: JdkUpdaterStateData) = lock.withLock {
    super.loadState(state)
  }

  private fun key(forJdk: Sdk, feedItem: JdkItem) = "for(${forJdk.name})-${feedItem.fullPresentationText}"

  fun isAllowed(forJdk: Sdk, feedItem: JdkItem) = lock.withLock {
    key(forJdk, feedItem) !in state.dndVersions
  }

  fun blockVersion(forJdk: Sdk, feedItem: JdkItem) = lock.withLock {
    state.dndVersions += key(forJdk, feedItem)
    state.intIncrementModificationCount()
  }
}
