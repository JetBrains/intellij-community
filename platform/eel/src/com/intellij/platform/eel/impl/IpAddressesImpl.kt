// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.impl

import com.intellij.platform.eel.EelTunnelsApi
import com.intellij.platform.eel.EelTunnelsApi.ResolvedSocketAddress
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class ResolvedV4SocketAddressImpl(override val port: UShort, override val bits: UInt) : ResolvedSocketAddress.V4
data class ResolvedV6SocketAddressImpl(override val port: UShort, override val higherBits: ULong, override val lowerBits: ULong) : EelTunnelsApi.ResolvedSocketAddress.V6


val SocketAddress.asResolvedSocketAddress: ResolvedSocketAddress
  get() {
    if (this !is InetSocketAddress) error("Socket family $this not supported")
    val inetAddress = this.address
    assert(port >= 0 && port < UShort.MAX_VALUE.toInt()) { "port $port is wrong" }
    val port = port.toUShort()
    val buffer = ByteBuffer.allocate(inetAddress.address.size)
      .order(ByteOrder.BIG_ENDIAN) // network order
      .put(inetAddress.address)
      .rewind()
    return when (inetAddress) {
      is Inet4Address -> {
        assert(buffer.limit() == 4)
        ResolvedV4SocketAddressImpl(port, buffer.int.toUInt())
      }
      is Inet6Address -> {
        assert(buffer.limit() == 16)
        ResolvedV6SocketAddressImpl(port, buffer.long.toULong(), buffer.long.toULong())
      }
      else -> error("Inet family $address not supported")
    }
  }