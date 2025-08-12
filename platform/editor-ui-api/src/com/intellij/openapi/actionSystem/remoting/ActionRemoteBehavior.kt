// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem.remoting

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.components.service
import com.intellij.openapi.util.Key
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@ApiStatus.Experimental
enum class ActionRemoteBehavior {
  /**
   * The action works only on a thin client
   */
  FrontendOnly,

  /**
   * The action is updated on the frontend and backend, choosing the available presentation,
   * but the action will always be performed on the frontend.
   */
  FrontendThenBackend,

  /**
   * The action updates on both backend and frontend,
   * if a frontend action is available, its presentation will be taken, and the action will be performed on the frontend.
   * Otherwise, backend's presentation will be used, and the action will be performed on the backend.
   *
   * It's possible to disable the action update on the backend side by setting [SKIP_FALLBACK_UPDATE] to `true`
   * in the presentation during the frontend's update.
   */
  FrontendOtherwiseBackend,

  /**
   * The action delegates to a backend
   */
  BackendOnly,

  /**
   * The action delegates to a backend and if it isn't available there it tries to be executed on a frontend
   */
  BackendThenFrontend,

  /**
   * The action should have both frontend and backend representations accessible from a frontend.
   * E.g. Debug Log Settings which has the frontend version and also the backend version with (On Host) suffix
   */
  Duplicated,

  /**
   * Action should be disabled in remote dev mode on both sides
   */
  Disabled;

  companion object {
    /**
     * If set as a presentation property by an action with [FrontendOtherwiseBackend] behaviour during frontend update,
     * disables the subsequent backend update, even if the frontend update reports the action as disabled.
     */
    @JvmField
    val SKIP_FALLBACK_UPDATE: Key<Boolean> = Key.create("SKIP_FALLBACK_UPDATE")
  }
}

/**
 * Marker interface which defines how an action should act in remote development mode
 *
 * See [ActionRemoteBehavior] for the modes
 */
@ApiStatus.Internal
@ApiStatus.Experimental
interface ActionRemoteBehaviorSpecification {
  fun getBehavior(): ActionRemoteBehavior

  interface Frontend : ActionRemoteBehaviorSpecification {
    override fun getBehavior(): ActionRemoteBehavior = ActionRemoteBehavior.FrontendOnly
  }

  interface FrontendThenBackend : ActionRemoteBehaviorSpecification {
    override fun getBehavior(): ActionRemoteBehavior = ActionRemoteBehavior.FrontendThenBackend
  }

  interface FrontendOtherwiseBackend : ActionRemoteBehaviorSpecification {
    override fun getBehavior(): ActionRemoteBehavior = ActionRemoteBehavior.FrontendOtherwiseBackend
  }

  interface BackendOnly : ActionRemoteBehaviorSpecification {
    override fun getBehavior(): ActionRemoteBehavior = ActionRemoteBehavior.BackendOnly
  }

  interface Duplicated : ActionRemoteBehaviorSpecification {
    override fun getBehavior(): ActionRemoteBehavior = ActionRemoteBehavior.Duplicated
  }

  interface Disabled : ActionRemoteBehaviorSpecification {
    override fun getBehavior(): ActionRemoteBehavior = ActionRemoteBehavior.Disabled
  }


  companion object {
    fun AnAction.getActionDeclaredBehavior(): ActionRemoteBehavior? {
      return (this as? ActionRemoteBehaviorSpecification)?.getBehavior()
    }

    fun AnAction.getActionBehavior(): ActionRemoteBehavior? {
      return service<ActionRemoteBehaviorCustomizer>().customizeActionUpdateBehavior(this, getActionDeclaredBehavior())
    }
  }
}


