// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util

import fleet.util.multiplatform.Actual
import java.net.URI
import java.net.URISyntaxException
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

@Actual
fun String.capitalizeWithCurrentLocaleJvm(): String = replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }

@Actual
fun String.lowercaseWithCurrentLocaleJvm(): String = lowercase(Locale.getDefault())

@Actual
fun String.uppercaseWithCurrentLocaleJvm(): String = uppercase(Locale.getDefault())

@Actual
fun String.isValidUriStringJvm(): Boolean = try {
  URI(this)
  true
}
catch (_: URISyntaxException) {
  false
}

