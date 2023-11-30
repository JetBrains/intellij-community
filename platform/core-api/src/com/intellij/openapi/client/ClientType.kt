// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.client

import org.jetbrains.annotations.ApiStatus

/**
 * An enum specifying the type of particular client working in IDE instance.
 * Currently, there can be three different types of clients.
 * Unlike [ClientKind] which also lists popular combination of client types.
 * For example when traversing services, it could be useful to get them for a certain combination thus [ClientKind] is used.
 * Each client however specifies its [ClientType].
 */
@ApiStatus.Experimental
@ApiStatus.Internal
enum class ClientType {
  LOCAL,

  @ApiStatus.Internal
  @Deprecated("This api will be removed")
  // This api will be removed as soon as Rider is able to run separate projects in different processes. Ask Rider Team
  FRONTEND,

  CONTROLLER,
  GUEST;

  val isLocal: Boolean get() = this == LOCAL

  val isController: Boolean get() = this == CONTROLLER

  val isGuest: Boolean get() = this == GUEST

  val isOwner: Boolean get() = isLocal || isController

  val isRemote: Boolean get() = isController || isGuest

  val isFrontend: Boolean get() = this == FRONTEND

  fun matches(kind: ClientKind): Boolean {
    return kind == ClientKind.ALL ||
           kind == ClientKind.LOCAL && isLocal ||
           kind == ClientKind.CONTROLLER && isController ||
           kind == ClientKind.GUEST && isGuest ||
           kind == ClientKind.OWNER && isOwner ||
           kind == ClientKind.REMOTE && isRemote ||
           kind == ClientKind.FRONTEND && isFrontend
  }
}