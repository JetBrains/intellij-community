// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("unused")

package com.intellij.ui.icons

import java.security.SecureRandom
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.time.Duration.Companion.milliseconds

// like https://github.com/segmentio/ksuid#what-is-a-ksuid but 32-bit random payload.
private class ColorPatcherIdGenerator {
  companion object {
    // https://github.com/segmentio/ksuid/blob/b65a0ff7071caf0c8770b63babb7ae4a3c31034d/ksuid.go#L19
    private const val EPOCH = 160_000_0000
    private const val TIMESTAMP_LENGTH = 4
    private const val PAYLOAD_LENGTH = 16
    private const val MAX_ENCODED_LENGTH = 27

    @JvmStatic
    fun main(args: Array<String>) {
      val timestamp = (ZonedDateTime.now(ZoneOffset.UTC).toInstant().toEpochMilli().milliseconds.inWholeSeconds - EPOCH).toInt()
      val id = (timestamp.toLong() shl 32) or ((SecureRandom().nextInt()).toLong() and 0xffffffffL)
      println(id)
    }
  }
}