// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package net.schmizz.sshj.connection.channel.direct

import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.Buffer
import net.schmizz.sshj.common.DisconnectReason
import net.schmizz.sshj.common.SSHPacket
import net.schmizz.sshj.connection.ConnectionException
import net.schmizz.sshj.connection.channel.ChannelInputStream

/**
 * Subclass of SSHJ [SessionChannel] that handles some strange features.
 * Maybe at some day these changes will be pull-requested to upstream.
 *
 * Moved to the same package as the base class because otherwise it would flood logger of the derived class.
 */
internal class PatchedExecChannel(sshClient: SSHClient) : SessionChannel(sshClient.connection, sshClient.remoteCharset) {

  override fun gotExtendedData(buf: SSHPacket) {
    val destination = (errorStream) as ChannelInputStream
    try {
      val dataTypeCode = buf.readUInt32AsInt()
      if (dataTypeCode == 1) {
        receiveInto(destination, buf)
      }
      else {
        throw ConnectionException(DisconnectReason.PROTOCOL_ERROR, "Bad extended data type = $dataTypeCode")
      }
    }
    catch (bufferException: Buffer.BufferException) {
      throw ConnectionException(bufferException)
    }
  }

  /**
   * Just muting exception that caused by data race, when channel closes earlier than all channel
   * packets was delivered.
   */
  override fun receiveInto(stream: ChannelInputStream?, buf: SSHPacket?) {
    try {
      super.receiveInto(stream, buf)
    }
    catch (e: ConnectionException) {
      // It is safe to just ignore new packets. Stream was closed and nothing could be read from it.
      // Of course this error message can be changed at some day but there is no way to
      // differentiate this error from another important ConnectionExceptions.
      @Suppress("SpellCheckingInspection")
      if (e.message != "Getting data on EOF'ed stream") {
        throw e
      }
    }
  }
}