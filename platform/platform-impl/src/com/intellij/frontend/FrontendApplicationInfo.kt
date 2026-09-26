// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.frontend

import com.intellij.openapi.util.Key
import com.intellij.util.application
import org.jetbrains.annotations.ApiStatus

// TODO: move to intellij.platform.frontend when IJPL-163613 is fixed
// This class should be used only in frontend parts
@ApiStatus.Internal
object FrontendApplicationInfo {
  val frontendTypeDataKey = Key<FrontendType>("platform.frontend.type")

  /**
   * Determines the type of the frontend.
   *
   * Use this function as rare as possible and only for temporary solutions.
   * Ideally, code should not work conditionally based on the [FrontendType].
   */
  fun getFrontendType(): FrontendType {
    // [frontendTypeDataKey] is put by thin client (RemoteDev and Code With Me), otherwise it is Monolith
    return application.getUserData(frontendTypeDataKey) ?: FrontendType.Monolith
  }
}

/**
 * Enum representing different types of IntelliJ Platform Frontends.
 * Namely:
 * - Monolith: Represents a single process IDE.
 * - Remote: Represents a frontend in a remote development and Code With Me client.
 */
@ApiStatus.Internal
sealed interface FrontendType {
  object Monolith : FrontendType

  /**
   * Represents a frontend in a remote mode, whether it's a Code With Me guest or Remote Development controller
   *
   * @property type a type of remote frontend
   */
  data class Remote(val type: RemoteFrontendType) : FrontendType {
    fun isController(): Boolean = type == RemoteFrontendType.Controller
    fun isGuest(): Boolean = type == RemoteFrontendType.Guest
  }


  enum class RemoteFrontendType {
    /**
     * if the frontend is connected as a guest to Code With Me Session
     */
    Guest,

    /**
     * if the frontend is connected as a controller to a Remote Development session
     */
    Controller,
  }
}