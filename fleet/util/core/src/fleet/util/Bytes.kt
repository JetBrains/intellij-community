// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

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

// Replaces java.util.Base64.getDecoder(), which did not require padding on decoding
@OptIn(ExperimentalEncodingApi::class)
val Base64WithOptionalPadding = Base64.withPadding(Base64.PaddingOption.PRESENT_OPTIONAL)

// Replaces java.util.Base64.getUrlDecoder(), which did not require padding on decoding
@OptIn(ExperimentalEncodingApi::class)
val UrlSafeBase64WithOptionalPadding = Base64.UrlSafe.withPadding(Base64.PaddingOption.PRESENT_OPTIONAL)
