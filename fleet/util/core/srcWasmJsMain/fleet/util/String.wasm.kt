// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util

import fleet.util.multiplatform.Actual
import js.uri.decodeURIComponent
import js.uri.encodeURIComponent
import web.url.URL

@Actual
fun String.capitalizeWithCurrentLocaleWasmJs(): String = replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

@Actual
fun String.lowercaseWithCurrentLocaleWasmJs(): String = lowercase()

@Actual
fun String.uppercaseWithCurrentLocaleWasmJs(): String = uppercase()

@Actual
fun String.isValidUriStringWasmJs(): Boolean = try {
  URL(this)
  true
}
catch (e: Throwable) {
  false
}