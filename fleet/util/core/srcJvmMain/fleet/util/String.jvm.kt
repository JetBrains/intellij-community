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
fun String.encodeUriComponentJvm(): String = URLEncoder.encode(this, StandardCharsets.UTF_8.name())
    .replace("+", "%20")
    .replace("%21", "!")
    .replace("%27", "'")
    .replace("%28", "(")
    .replace("%29", ")")
    .replace("%7E", "~")

@Actual
fun String.decodeUriComponentJvm(): String = this
  .replace("%20", "+")
  .replace("!", "%21")
  .replace("'", "%27")
  .replace("(", "%28")
  .replace(")", "%29")
  .replace("~", "%7E")
  .let { URLDecoder.decode(it, StandardCharsets.UTF_8.name()) }

@Actual
fun String.isValidUriStringJvm(): Boolean = try {
  URI(this)
  true
}
catch (_: URISyntaxException) {
  false
}

