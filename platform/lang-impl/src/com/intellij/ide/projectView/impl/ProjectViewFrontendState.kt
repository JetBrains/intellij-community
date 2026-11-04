// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectView.impl

import com.intellij.ide.ui.UISettings
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.productMode.IdeProductMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.jetbrains.annotations.ApiStatus

/**
 * Keeps the Project View options that only the UI uses:
 * Open Files with Single Click, Open Directories with Single Click and Always Select Opened File.
 *
 * The legacy and the split Project View both read these options from here.
 * In remote development, the frontend owns them, and the settings sync sends them only to the backend.
 * The backend copy is for the backend code that reads these options.
 * A change of the backend copy stays on the backend.
 *
 * The [ProjectViewState] fields with the same names keep a copy of these options.
 * This component reads that copy once, when the project has no stored state of this component.
 */
@ApiStatus.Internal
@State(name = "ProjectViewFrontendState", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class ProjectViewFrontendState(private val project: Project) : PersistentStateComponent<ProjectViewFrontendState.State> {
  companion object {
    @JvmStatic
    fun getInstance(project: Project): ProjectViewFrontendState = project.service()
  }

  data class Options(
    val autoscrollToSource: Boolean,
    val openDirectoriesWithSingleClick: Boolean,
    val autoscrollFromSource: Boolean,
  )

  class State {
    var autoscrollToSource: Boolean = UISettings.getInstance().state.defaultAutoScrollToSource
    var openDirectoriesWithSingleClick: Boolean = false
    var autoscrollFromSource: Boolean = false
  }

  private val mutableOptions = MutableStateFlow(State().toOptions())

  val options: StateFlow<Options> = mutableOptions.asStateFlow()

  var autoscrollToSource: Boolean
    get() = mutableOptions.value.autoscrollToSource
    set(value) {
      mutableOptions.update { it.copy(autoscrollToSource = value) }
    }

  var openDirectoriesWithSingleClick: Boolean
    get() = mutableOptions.value.openDirectoriesWithSingleClick
    set(value) {
      mutableOptions.update { it.copy(openDirectoriesWithSingleClick = value) }
    }

  var autoscrollFromSource: Boolean
    get() = mutableOptions.value.autoscrollFromSource
    set(value) {
      mutableOptions.update { it.copy(autoscrollFromSource = value) }
    }

  @Suppress("DestructuringDeclaration") // NO!
  override fun getState(): State {
    val options = mutableOptions.value
    return State().apply {
      autoscrollToSource = options.autoscrollToSource
      openDirectoriesWithSingleClick = options.openDirectoriesWithSingleClick
      autoscrollFromSource = options.autoscrollFromSource
    }
  }

  override fun loadState(state: State) {
    mutableOptions.value = state.toOptions()
  }

  override fun noStateLoaded() {
    // On the backend, the frontend owns these options, so no state means that the frontend has the default values.
    if (IdeProductMode.isBackend) {
      loadState(State())
      return
    }
    val legacyState = ProjectViewState.getInstance(project)
    mutableOptions.value = Options(
      autoscrollToSource = legacyState.autoscrollToSource,
      openDirectoriesWithSingleClick = legacyState.openDirectoriesWithSingleClick,
      autoscrollFromSource = legacyState.autoscrollFromSource,
    )
  }
}

private fun ProjectViewFrontendState.State.toOptions(): ProjectViewFrontendState.Options {
  return ProjectViewFrontendState.Options(
    autoscrollToSource = autoscrollToSource,
    openDirectoriesWithSingleClick = openDirectoriesWithSingleClick,
    autoscrollFromSource = autoscrollFromSource,
  )
}
