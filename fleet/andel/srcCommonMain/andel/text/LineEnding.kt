// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package andel.text

import kotlinx.serialization.Serializable

@Serializable
enum class LineEnding(val separator: String) {
  CR("\r"),
  LF("\n"),
  CRLF("\r\n"),

  // choose line ending depending on the target os on first save
  OS("")
}
