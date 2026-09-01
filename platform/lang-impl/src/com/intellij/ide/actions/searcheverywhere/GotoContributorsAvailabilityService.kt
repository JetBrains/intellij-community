// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere

import com.intellij.navigation.ChooseByNameContributor
import com.intellij.navigation.ChooseByNameRegistry
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.project.projectId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus

/**
 * Tells whether any goto-class or goto-symbol contributor is available now.
 * The check combines the local contributors with the backend contributors.
 * In remote development, a contributor can live on the frontend, on the backend, or on both.
 * A contributor counts only when [ChooseByNameContributor.isAvailableNow] returns `true`.
 */
@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class GotoContributorsAvailabilityService(private val project: Project, private val coroutineScope: CoroutineScope) {

  private class RemoteAvailability(@JvmField val hasClassContributors: Boolean, @JvmField val hasSymbolContributors: Boolean)

  @Volatile
  private var remoteAvailability: RemoteAvailability? = null

  init {
    val refresh = Runnable {
      coroutineScope.launch {
        fetchRemoteAvailability()
      }
    }
    ChooseByNameContributor.CLASS_EP_NAME.addChangeListener(coroutineScope, refresh)
    ChooseByNameContributor.SYMBOL_EP_NAME.addChangeListener(coroutineScope, refresh)
    refresh.run()
  }

  /**
   * A fast check for an action update.
   * It uses the last known remote state. The state refreshes when an extension point changes.
   * Before the first fetch completes, the remote state counts as available. This keeps the current behavior.
   */
  fun hasClassContributors(): Boolean {
    return hasLocalClassContributors(project) || (remoteAvailability?.hasClassContributors ?: true)
  }

  /** See [hasClassContributors]. */
  fun hasSymbolContributors(): Boolean {
    return hasLocalSymbolContributors(project) || (remoteAvailability?.hasSymbolContributors ?: true)
  }

  /** A slow check for a suspending caller. It fetches the fresh remote state. */
  suspend fun awaitHasClassContributors(): Boolean {
    return hasLocalClassContributors(project) || fetchRemoteAvailability().hasClassContributors
  }

  /** See [awaitHasClassContributors]. */
  suspend fun awaitHasSymbolContributors(): Boolean {
    return hasLocalSymbolContributors(project) || fetchRemoteAvailability().hasSymbolContributors
  }

  private suspend fun fetchRemoteAvailability(): RemoteAvailability {
    val projectId = project.projectId()
    val api = GotoContributorsAvailabilityApi.getInstance()
    val availability = RemoteAvailability(hasClassContributors = api.hasClassContributors(projectId),
                                          hasSymbolContributors = api.hasSymbolContributors(projectId))
    remoteAvailability = availability
    return availability
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): GotoContributorsAvailabilityService = project.service()

    @JvmStatic
    fun hasLocalClassContributors(project: Project): Boolean =
      ChooseByNameRegistry.getInstance().classModelContributorList.any { it.isAvailableNow(project) }

    @JvmStatic
    fun hasLocalSymbolContributors(project: Project): Boolean =
      ChooseByNameRegistry.getInstance().symbolModelContributors.any { it.isAvailableNow(project) }
  }
}
