// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem.remoting

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.util.Key
import com.intellij.util.PlatformUtils
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@ApiStatus.Experimental
enum class ActionRemoteBehavior {
  /**
   * The action works only on a thin client
   */
  FrontendOnly,

  /**
   * The action tries to perform on a thin client first and if it's not available it goes to a backend
   */
  FrontendThenBackend,

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
  Disabled
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
    val REMOTE_UPDATE_KEY = Key.create<Boolean>("REMOTE_UPDATE_KEY")

    fun AnAction.getActionBehavior(useDeclaredBehaviour: Boolean = false): ActionRemoteBehavior? {
      val behavior = (this as? ActionRemoteBehaviorSpecification)?.getBehavior()
      val isRiderOrCLion = PlatformUtils.isRider() || PlatformUtils.isCLion()
      return when {
        useDeclaredBehaviour || !isRiderOrCLion -> behavior
        templatePresentation.getClientProperty(REMOTE_UPDATE_KEY) == true -> ActionRemoteBehavior.FrontendThenBackend
        behavior == ActionRemoteBehavior.BackendOnly -> ActionRemoteBehavior.FrontendOnly
        behavior == ActionRemoteBehavior.Disabled -> ActionRemoteBehavior.FrontendOnly
        else -> behavior
      }
    }
  }
}


