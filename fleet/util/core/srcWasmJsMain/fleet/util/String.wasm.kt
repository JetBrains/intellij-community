// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util

import fleet.util.multiplatform.Actual

@Actual("capitalizeWithCurrentLocale")
fun String.capitalizeWithCurrentLocaleWasmJs(): String = replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

@Actual("lowercaseWithCurrentLocale")
fun String.lowercaseWithCurrentLocaleWasmJs(): String = lowercase()

@Actual("uppercaseWithCurrentLocale")
fun String.uppercaseWithCurrentLocaleWasmJs(): String = uppercase()

@Actual("encodeUriComponent")
fun String.encodeUriComponentWasmJs(): String = encodeUriComponentImpl(this)

private fun encodeUriComponentImpl(value: String): String =
  js("encodeURIComponent(value)")

@Actual("decodeUriComponent")
fun String.decodeUriComponentWasmJs(): String = decodeUriComponentImpl(this)

private fun decodeUriComponentImpl(value: String): String =
  js("decodeURIComponent(value)")

@Actual("isValidUriString")
fun String.isValidUriStringWasmJs(): Boolean = try {
  tryParseUrl(this)
  true
}
catch (e: Throwable) {
  false
}

private fun tryParseUrl(value: String): Unit =
  js("new URL(value)")
