// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")
@file:OptIn(FlowPreview::class)

package com.intellij.openapi.projectRoots.impl.jdkDownloader

import com.intellij.ide.actions.SettingsEntryPointAction
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.projectRoots.Sdk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus.Internal
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.time.Duration.Companion.milliseconds

@Service(Service.Level.APP)
@Internal
class JdkUpdaterNotifications(private val coroutineScope: CoroutineScope) {
  private val lock = ReentrantLock()
  private val pendingNotifications = HashMap<Sdk, JdkUpdateNotification>()
  private var pendingActionsCopy = listOf<JdkUpdateNotification.JdkUpdateSuggestionAction>()

  private val updateRequests = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  init {
    coroutineScope.launch {
      updateRequests
        .debounce(500.milliseconds)
        .collectLatest {
          doUpdate()
        }
    }
  }

  private fun doUpdate() {
    pendingActionsCopy = lock.withLock {
      pendingNotifications.values.sortedBy { it.persistentId }.map { it.updateAction }
    }

    coroutineScope.launch(Dispatchers.EDT + ModalityState.nonModal().asContextElement()) {
      SettingsEntryPointAction.updateState()
    }
  }

  private fun scheduleUpdate() {
    if (ApplicationManager.getApplication().isUnitTestMode) {
      doUpdate()
    }
    else {
      check(updateRequests.tryEmit(Unit))
    }
  }

  fun hideNotification(jdk: Sdk) {
    lock.withLock {
      pendingNotifications.get(jdk)?.tryReplaceWithNewerNotification()
    }
    scheduleUpdate()
  }

  fun showNotification(jdk: Sdk, actualItem: JdkItem, newItem: JdkItem, showVendorVersion: Boolean = false): JdkUpdateNotification? {
    val newNotification = lock.withLock {
      val newNotification = JdkUpdateNotification(
        jdk = jdk,
        oldItem = actualItem,
        newItem = newItem,
        whenComplete = {
          lock.withLock {
            pendingNotifications.remove(jdk, it)
          }
          scheduleUpdate()
        },
        showVendorVersion = showVendorVersion,
      )

      val currentNotification = pendingNotifications.get(jdk)
      if (currentNotification != null && !currentNotification.tryReplaceWithNewerNotification(newNotification)) {
        return null
      }

      pendingNotifications.put(jdk, newNotification)
      newNotification
    }

    scheduleUpdate()
    return newNotification
  }

  fun getActions() : List<JdkUpdateNotification.JdkUpdateSuggestionAction> = pendingActionsCopy
}

private class JdkSettingsActionRegistryActionProvider : SettingsEntryPointAction.ActionProvider {
  override fun getUpdateActions(context: DataContext): List<JdkUpdateNotification.JdkUpdateSuggestionAction> {
    return serviceIfCreated<JdkUpdaterNotifications>()?.getActions() ?: emptyList()
  }
}
