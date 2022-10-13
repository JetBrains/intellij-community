// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("NavBarIdeUtil")

package com.intellij.ide.navbar.ide

import com.intellij.ide.IdeEventQueue
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow

internal val isNavbarV2Enabled: Boolean = Registry.`is`("ide.navBar.v2", false)

internal val LOG: Logger = Logger.getInstance("#com.intellij.ide.navbar.ide")

internal fun UISettings.isNavbarShown(): Boolean {
  return showNavigationBar && !presentationMode
}

// TODO move to activity tracker?
internal fun activityFlow(): Flow<Unit> {
  return channelFlow {
    val disposable: Disposable = Disposer.newDisposable()
    IdeEventQueue.getInstance().addActivityListener(Runnable {
      this.trySend(Unit)
    }, disposable)
    awaitClose {
      Disposer.dispose(disposable)
    }
  }.buffer(Channel.CONFLATED)
}
