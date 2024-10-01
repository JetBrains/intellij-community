// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util

/*
 * Following functions are used to replace jvm specific byte array to string conversion that explicitly specified UTF-8 as charset.
 */

// Replaces this.toByteArray(Charset.UTF_8)
fun String.encodeToByteArrayUtf8() = encodeToByteArray()

// Replaces String(this, ..., Charset.UTF_8)
fun ByteArray.decodeToStringUtf8() = decodeToString()
fun ByteArray.decodeToStringUtf8(
  startIndex: Int = 0,
  endIndex: Int = this.size,
  throwOnInvalidSequence: Boolean = false
): String = decodeToString(startIndex, endIndex, throwOnInvalidSequence)
