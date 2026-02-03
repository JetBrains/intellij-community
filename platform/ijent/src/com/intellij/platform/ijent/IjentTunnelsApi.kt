// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent

import com.intellij.platform.eel.EelTunnelsPosixApi
import com.intellij.platform.eel.EelTunnelsWindowsApi

/**
 * Methods for launching tunnels for TCP sockets, Unix sockets, etc.
 */
sealed interface IjentTunnelsApi

interface IjentTunnelsPosixApi : EelTunnelsPosixApi, IjentTunnelsApi

interface IjentTunnelsWindowsApi : EelTunnelsWindowsApi, IjentTunnelsApi
