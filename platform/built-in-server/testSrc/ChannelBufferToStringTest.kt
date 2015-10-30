package org.jetbrains.io

import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

private class ChannelBufferToStringTest {
  @Test
  fun readUtf8() {
    val string = "\ud83d\udd1d"
    val byteBuffer = Unpooled.copiedBuffer(string, Charsets.UTF_8)
    assertThat(ChannelBufferToString.readChars(byteBuffer)).isEqualTo(string)
  }
}