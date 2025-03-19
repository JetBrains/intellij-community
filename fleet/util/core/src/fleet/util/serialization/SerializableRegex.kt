// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util.serialization

import kotlinx.serialization.Serializable

@Serializable
data class SerializableRegex(val pattern: String, val regexOption: RegexOption) {
  val regex by lazy {
    Regex(pattern, regexOption)
  }
}
