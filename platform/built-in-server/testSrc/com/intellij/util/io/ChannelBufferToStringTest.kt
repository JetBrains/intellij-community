package com.intellij.util.io

import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

internal class ChannelBufferToStringTest {
  @Test
  fun readUtf8() {
    val string = "\ud83d\udd1d"
    val byteBuffer = Unpooled.copiedBuffer(string, Charsets.UTF_8)
    assertThat(byteBuffer.readUtf8()).isEqualTo(string)
  }
}