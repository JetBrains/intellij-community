// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl.jdkDownloader

import com.intellij.ide.actions.SettingsEntryPointAction
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.components.*
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.util.Alarm
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock


@Service(Service.Level.APP)
class JdkUpdaterNotifications : Disposable {
  private val lock = ReentrantLock()
  private val pendingNotifications = HashMap<Sdk, JdkUpdateNotification>()
  private var pendingActionsCopy = listOf<JdkUpdateNotification.JdkUpdateSuggestionAction>()

  private val alarm = MergingUpdateQueue("jdk-update-actions", 500, true, null, this, null, Alarm.ThreadToUse.POOLED_THREAD).usePassThroughInUnitTestMode()
  override fun dispose(): Unit = Unit

  private fun scheduleUpdate() {
    alarm.queue(object: Update(this) {
      override fun run() {
        pendingActionsCopy = lock.withLock {
          pendingNotifications.values.sortedBy { it.persistentId }.map { it.updateAction }
        }

        invokeLater {
          SettingsEntryPointAction.updateState()
        }
      }
    })
  }

  fun hideNotification(jdk: Sdk) {
    lock.withLock {
      pendingNotifications[jdk]?.tryReplaceWithNewerNotification()
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

      val currentNotification = pendingNotifications[jdk]
      if (currentNotification != null && !currentNotification.tryReplaceWithNewerNotification(newNotification)) return null
      pendingNotifications[jdk] = newNotification
      newNotification
    }

    scheduleUpdate()
    return newNotification
  }

  fun getActions() : List<JdkUpdateNotification.JdkUpdateSuggestionAction> = pendingActionsCopy
}

class JdkSettingsActionRegistryActionProvider : SettingsEntryPointAction.ActionProvider {
  override fun getUpdateActions(context: DataContext): List<JdkUpdateNotification.JdkUpdateSuggestionAction> = service<JdkUpdaterNotifications>().getActions()
}
