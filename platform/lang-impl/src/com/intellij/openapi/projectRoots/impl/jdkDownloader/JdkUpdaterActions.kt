// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.openapi.projectRoots.impl.jdkDownloader

import com.intellij.ide.actions.SettingsEntryPointAction
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.util.Alarm
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

@Service(Service.Level.APP)
class JdkUpdaterNotifications(private val coroutineScope: CoroutineScope) : Disposable {
  private val lock = ReentrantLock()
  private val pendingNotifications = HashMap<Sdk, JdkUpdateNotification>()
  private var pendingActionsCopy = listOf<JdkUpdateNotification.JdkUpdateSuggestionAction>()

  private val alarm = MergingUpdateQueue("jdk-update-actions", 500, true, null, this, null, Alarm.ThreadToUse.POOLED_THREAD).usePassThroughInUnitTestMode()

  override fun dispose() {
  }

  private fun scheduleUpdate() {
    alarm.queue(object: Update(this) {
      override fun run() {
        pendingActionsCopy = lock.withLock {
          pendingNotifications.values.sortedBy { it.persistentId }.map { it.updateAction }
        }

        coroutineScope.launch(Dispatchers.EDT + ModalityState.nonModal().asContextElement()) {
          SettingsEntryPointAction.updateState()
        }
      }
    })
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
