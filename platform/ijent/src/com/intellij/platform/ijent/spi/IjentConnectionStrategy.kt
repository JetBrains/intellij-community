// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.spi

/**
 * Strategy for establishing connection with a running IJent process.
 *
 * This class allows configuring the initial message exchange with the remote binary,
 * which may result in better performance.
 */
interface IjentConnectionStrategy {

  object Default : IjentConnectionStrategy {
    override suspend fun canUseVirtualSockets(): Boolean {
      return false
    }
  }

  /**
   * Returns `true` if the IDE is ready to communicate with IJent over virtual sockets.
   *
   * It can happen when the binary is located at the same physical machine as the IDE,
   * so it is possible to use low-level virtualization techniques to get better performance.
   * For example, on Windows, virtual sockets are supported by the Hyper-V technology.
   *
   * Even if this function returns `true`, there is no guarantee that virtual sockets will be used.
   */
  suspend fun canUseVirtualSockets(): Boolean
}