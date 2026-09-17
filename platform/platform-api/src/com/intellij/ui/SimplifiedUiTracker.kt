// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import com.intellij.ide.PowerSaveMode
import com.intellij.ide.RemoteDesktopService
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.openapi.util.registry.RegistryValueListener
import com.intellij.util.messages.Topic
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/** Turns the three sources of [DrawUtil.isSimplifiedUI] into one event. */
@Service(Service.Level.APP)
internal class SimplifiedUiTracker(private val cs: CoroutineScope) : Disposable.Default {
  private val lastValue = AtomicBoolean(DrawUtil.isSimplifiedUI())

  init {
    val parentDisposable: Disposable = this
    // 1) Registry.is("ui.simplified")
    Registry.get("ui.simplified").addListener(
      object : RegistryValueListener {
        override fun afterValueChanged(value: RegistryValue) {
          onSourceChanged()
        }
      },
      parentDisposable,
    )
    // 2) RemoteDesktopService.isRemoteSession()
    RemoteDesktopService.subscribe(parentDisposable) {
      onSourceChanged()
    }
    // 3) PowerSaveMode.isEnabled()
    ApplicationManager.getApplication()
      .messageBus
      .connect(cs)
      .subscribe(
        PowerSaveMode.TOPIC,
        PowerSaveMode.Listener {
          onSourceChanged()
        },
      )
  }

  fun subscribe(parent: Disposable, listener: DrawUtil.SimplifiedUiListener) {
    val subscription = Disposer.newCheckedDisposable(parent)
    ApplicationManager.getApplication()
      .messageBus
      .connect(subscription)
      .subscribe(TOPIC, listener)
    cs.launch(DISPATCHER) {
      if (!subscription.isDisposed) {
        listener.simplifiedUiChanged()
      }
    }
  }

  /** Recomputes on [DISPATCHER], so a source call cannot interleave with the compare. */
  private fun onSourceChanged() {
    cs.launch(DISPATCHER) {
      val value = DrawUtil.isSimplifiedUI()
      if (lastValue.compareAndSet(!value, value)) {
        ApplicationManager.getApplication()
          .messageBus
          .syncPublisher(TOPIC)
          .simplifiedUiChanged()
      }
    }
  }

  companion object {
    @Topic.AppLevel
    private val TOPIC: Topic<DrawUtil.SimplifiedUiListener> = Topic(
      "DrawUtil.SimplifiedUiListener",
      DrawUtil.SimplifiedUiListener::class.java,
      Topic.BroadcastDirection.NONE
    )

    /** One shared dispatcher, so every call to a listener runs in one order. */
    private val DISPATCHER: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(1, "SimplifiedUiTracker")
  }
}
