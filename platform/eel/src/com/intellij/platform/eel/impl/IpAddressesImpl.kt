// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.impl

import com.intellij.platform.eel.EelTunnelsApi

data class ResolvedV4SocketAddressImpl(override val port: UShort, override val bits: UInt) : EelTunnelsApi.ResolvedSocketAddress.V4
data class ResolvedV6SocketAddressImpl(override val port: UShort, override val higherBits: ULong, override val lowerBits: ULong) : EelTunnelsApi.ResolvedSocketAddress.V6