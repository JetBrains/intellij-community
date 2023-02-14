// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.client

import org.jetbrains.annotations.ApiStatus

/**
 * An enum describing various client types and their combinations.
 * Unlike [com.intellij.openapi.client.ClientType] that list only concrete users.
 * For example when traversing services, it could be useful to get them for a certain combination thus [ClientKind] is used.
 * Each client however specifies its [com.intellij.openapi.client.ClientType].
 */
@ApiStatus.Experimental
enum class ClientKind {
  /**
   * A local owner of the IDE. Operates with IDE on the same computer
   */
  LOCAL,

  /**
   * A remote owner connected to the IDE. Operates with IDE being connected through the Gateway
   *
   */
  CONTROLLER,

  /**
   * A remote user working inside the Code With Me session in IDE. Joined by invitation from the [OWNER]
   */
  GUEST,

  /**
   * A collective state that combines main users of the IDE. Either [LOCAL] or [CONTROLLER]
   */
  OWNER,

  /**
   * A collective state that remotely connected users of the IDE. Either [CONTROLLER] or [GUEST]
   */
  REMOTE,

  /**
   * A collective state that combines all kinds of users. [LOCAL], [CONTROLLER], [GUEST]
   */
  ALL
}
