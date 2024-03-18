// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.fleet.rpc

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.client.ClientAppSession
import com.intellij.openapi.client.currentSession
import com.intellij.openapi.components.service
import fleet.rpc.core.FleetTransportFactory
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@ApiStatus.Experimental
interface FleetRpc {
  companion object {
    fun getInstance(session: ClientAppSession): FleetRpc = session.service()
    fun getCurrentInstance(): FleetRpc = ApplicationManager.getApplication().currentSession.service()
  }

  fun getTransportFactory(): FleetTransportFactory
}


