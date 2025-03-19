// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.rpc.core

import fleet.rpc.RemoteApi
import fleet.rpc.Rpc

@Rpc
interface RemoteObject : RemoteApi<Unit> {
  /**
   * This method is called by the client on a proxy provided so the server could free any related resources
   */
  /* TODO
   suspend method seems to be a bad idea for a thing you are usually calling in finally,
   when your coroutine is already dying
  */
  suspend fun clientDispose()
}
