// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.rpc.server

import fleet.util.UID

interface ConnectionListener {
  fun onConnect(kind: EndpointKind, route: UID, socketId: UID, presentableName: String?) {}
  fun onDisconnect(kind: EndpointKind, route: UID, socketId: UID) {}
}
