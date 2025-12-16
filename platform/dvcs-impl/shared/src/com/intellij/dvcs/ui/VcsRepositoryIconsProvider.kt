// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dvcs.ui

import com.intellij.dvcs.rpc.VcsRepositoryColor.Companion.toAwtColor
import com.intellij.dvcs.rpc.VcsRepositoryColorsApi
import com.intellij.dvcs.rpc.VcsRepositoryColorsState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.platform.project.projectId
import com.intellij.platform.vcs.impl.shared.RepositoryId
import com.intellij.util.PlatformIcons
import com.intellij.util.ui.CheckboxIcon
import fleet.rpc.client.durable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import java.awt.Color
import javax.swing.Icon

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class VcsRepositoryIconsProvider(project: Project, cs: CoroutineScope) {
  private var state: VcsRepositoryColorsState = VcsRepositoryColorsState()

  init {
    cs.launch {
      durable {
        VcsRepositoryColorsApi.getInstance().syncColors(project.projectId()).collect { newState ->
          LOG.debug("Received new colors - ${newState.colors.size} repos")
          state = newState
        }
      }
    }
  }

  fun getIcon(repositoryId: RepositoryId): Icon {
    val color = getColor(repositoryId)
    return if (color != null) CheckboxIcon.createAndScale(color) else PlatformIcons.FOLDER_ICON
  }

  private fun getColor(repositoryId: RepositoryId): Color? {
    val repoColor = state.colors[repositoryId]

    if (repoColor == null) {
      LOG.warn("Color for $repositoryId not loaded")
      return null
    }

    return repoColor.toAwtColor()
  }

  companion object {
    private val LOG = Logger.getInstance(VcsRepositoryIconsProvider::class.java)

    fun getInstance(project: Project): VcsRepositoryIconsProvider = project.service()
  }
}