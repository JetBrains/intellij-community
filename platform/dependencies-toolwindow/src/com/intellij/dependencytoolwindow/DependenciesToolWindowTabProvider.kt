// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dependencytoolwindow

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.ui.content.Content
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach

interface DependenciesToolWindowTabProvider {
  companion object {
    private val extensionPointName = ExtensionPointName<DependenciesToolWindowTabProvider>("com.intellij.dependenciesToolWindow.tabProvider")

    internal fun availableTabsFlow(project: Project): Flow<List<DependenciesToolWindowTabProvider>> {
      return extensionPointName.extensionsFlow.flatMapLatest { extensions ->
        channelFlow {
          send(extensions.filter { it.isAvailable(project) })
          extensions.map { extension -> extension.isAvailableFlow(project) }
            .merge()
            .onEach {
              val element = extensions.filter { it.isAvailable(project) }
              send(element)
            }
            .launchIn(this)
        }
      }
    }

    internal fun extensions(project: Project): List<DependenciesToolWindowTabProvider> {
      return extensionPointName.extensionList.filter { it.isAvailable(project) }
    }
  }

  fun provideTab(project: Project): Content

  fun isAvailable(project: Project): Boolean

  fun addIsAvailableChangesListener(project: Project, callback: (Boolean) -> Unit): Subscription

  fun interface Subscription {
    /**
     * Stops the listeners that generated this subscription.
     */
    fun unsubscribe()
  }
}