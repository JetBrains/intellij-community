// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util

import fleet.multiplatform.shims.MultiplatformConcurrentHashSet
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.random.Random
import kotlin.random.nextInt

private val used = MultiplatformConcurrentHashSet<Int>()

// https://en.wikipedia.org/wiki/Ephemeral_port
private val portsRange = (1 shl 15) + (1 shl 14) until (1 shl 16)

private const val seed = 0xDEADBEEF

/**
 * Finds an available port with a fixed port enumeration algorithm. Helps to set up ports once per workspace (e.g. for local dev)
 */
@Deprecated("this is always a race, are you sure you need this? maybe you can bind on 0")
fun NetUtils.findAvailableSocketPort(randomSeed: Long = seed): Int {
  val localRandom = Random(randomSeed)
  return generateSequence {
    localRandom.nextInt(portsRange)
  }.first { port ->
    used.add(port) && isAvailablePort(port)
  }
}

@Deprecated("this is always a race, are you sure you need this? maybe you can bind on 0")
fun NetUtils.isAvailablePort(port: Int): Boolean {
  return runCatching {
    ServerSocket(port, 0, InetAddress.getByName(localHost())).use { v4Socket ->
      // Try to bind v6 as well if available
      val v6Socket = runCatching {
        ServerSocket(port, 0, InetAddress.getByName(localHostIpV6()))
      }.getOrNull()

      v6Socket.use { v6Socket ->
        // workaround for linux: calling close() immediately after opening socket
        // may result that socket is not closed. Wait before checking/closing sockets.
        try {
          Thread.sleep(1)
        }
        catch (_: InterruptedException) {
          Thread.interrupted()
        }

        require(v4Socket.localPort == port)
        v6Socket?.let { require(it.localPort == port) }
      }
    }
  }.isSuccess
}
