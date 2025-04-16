// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util

import fleet.util.multiplatform.Actual
import kotlinx.browser.window

// todo: define client's os
@Actual("getName")
internal fun getNameWasmJs(): String = osName

@Actual("getVersion")
internal fun getVersionWasmJs(): String = ""

@Actual("getArch")
internal fun getArchWasmJs(): String = ""

private val osName: String by lazy {
  window.navigator.userAgent
    // System information commonly found in the first parenthesis
    // See https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Headers/User-Agent
    .substringBefore(")")
    .substringAfter("(")
    .let {
      // We could also get arch information and system version from this string if needed
      when {
        it.contains("Mac", ignoreCase = true) -> "Mac OS"
        // Includes Android
        it.contains("Linux", ignoreCase = true) || it.contains("Unix", ignoreCase = true) -> "Linux"
        it.contains("Win", ignoreCase = true) -> "Windows"
        else -> "Unknown"
      }
    }
}