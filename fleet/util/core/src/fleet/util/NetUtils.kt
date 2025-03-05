// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util

import java.net.InetAddress
import java.net.ServerSocket
import fleet.multiplatform.shims.ConcurrentHashSet
import kotlin.random.Random
import kotlin.random.nextInt

object NetUtils {
  private val used = ConcurrentHashSet<Int>()

  // https://en.wikipedia.org/wiki/Ephemeral_port
  private val portsRange = (1 shl 15) + (1 shl 14) until (1 shl 16)

  private const val seed = 0xDEADBEEF

  /**
   * Finds an available port with a fixed port enumeration algorithm. Helps to set up ports once per workspace (e.g. for local dev)
   */
  @Deprecated("this is always a race, are you sure you need this? maybe you can bind on 0")
  fun findAvailableSocketPort(randomSeed: Long = seed): Int {
    val localRandom = Random(randomSeed)
    return generateSequence {
      localRandom.nextInt(portsRange)
    }.first { port ->
      used.add(port) && isAvailablePort(port)
    }
  }

  @Deprecated("this is always a race, are you sure you need this? maybe you can bind on 0")
  fun isAvailablePort(port: Int, bindAddr: InetAddress? = InetAddress.getByName(localHost())): Boolean {
    return runCatching {
      ServerSocket(port, 0, bindAddr).use { serverSocket ->
        require(serverSocket.localPort == port)
        // workaround for linux : calling close() immediately after opening socket
        // may result that socket is not closed
        try {
          Thread.sleep(1)
        }
        catch (e: InterruptedException) {
          Thread.interrupted()
        }
      }
    }.isSuccess
  }

  fun localHost(): String {
    return "127.0.0.1"
  }
}