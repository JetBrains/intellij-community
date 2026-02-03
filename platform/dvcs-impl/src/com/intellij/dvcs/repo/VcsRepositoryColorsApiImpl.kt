// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dvcs.repo

import com.intellij.dvcs.rpc.VcsRepositoryColor
import com.intellij.dvcs.rpc.VcsRepositoryColorsApi
import com.intellij.dvcs.rpc.VcsRepositoryColorsState
import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.platform.project.ProjectId
import com.intellij.vcs.log.ui.VcsLogColorManagerFactory
import com.intellij.vcs.rpc.ProjectScopeRpcHelper.projectScopedCallbackFlow
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.distinctUntilChanged

internal class VcsRepositoryColorsApiImpl: VcsRepositoryColorsApi {
  @OptIn(FlowPreview::class)
  override suspend fun syncColors(projectId: ProjectId): Flow<VcsRepositoryColorsState> =
    projectScopedCallbackFlow(projectId) { project, messageBusConnection ->
      send(calcColorsState(project))
      messageBusConnection.subscribe(VcsRepositoryManager.VCS_REPOSITORY_MAPPING_UPDATED, VcsRepositoryMappingListener {
        LOG.debug { "VCS mapping changed" }
        trySend(calcColorsState(project))
      })
      messageBusConnection.subscribe(LafManagerListener.TOPIC, LafManagerListener {
        LOG.debug { "LAF changed" }
        trySend(calcColorsState(project))
      })
    }.buffer(onBufferOverflow = BufferOverflow.DROP_OLDEST).distinctUntilChanged()

  private fun calcColorsState(project: Project): VcsRepositoryColorsState {
    val repos = VcsRepositoryManager.getInstance(project).getRepositories()
    val colorManager = VcsLogColorManagerFactory.create(repos.map { it.root }.toSet())

    val colors = repos.associate { repo ->
      val color = colorManager.getRootColor(repo.root)
      repo.repositoryId() to VcsRepositoryColor.of(color)
    }

    return VcsRepositoryColorsState(colors)
  }

  companion object {
    private val LOG = logger<VcsRepositoryColorsApiImpl>()
  }
}