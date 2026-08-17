// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.spi

import com.intellij.platform.ijent.tcp.TcpDeployInfo
import kotlinx.coroutines.flow.StateFlow

/**
 * An interface that represents information about a used proxy for Ijent Connection.
 *
 * Current uses are to connect to the remote machine using dynamic port forwarding
 */
sealed interface IjentTcpProxyInformation {
  data class SOCKS5(val hostInfo: TcpDeployInfo.FixedPort) : IjentTcpProxyInformation
  data class SOCKS5Dynamic(val hostInfo: StateFlow<TcpDeployInfo.FixedPort?>) : IjentTcpProxyInformation
}