// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.gdpr

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Data structure describing all possible Consent JSON attributes
 */
@Serializable
internal class ConsentAttributes {
  companion object {
    @OptIn(ExperimentalSerializationApi::class)
    private val jsonConfig by lazy {
      Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = false
        prettyPrint = true
        prettyPrintIndent = "  "
      }
    }

    fun readListFromJson(json: String): List<ConsentAttributes> {
      return jsonConfig.decodeFromString<List<ConsentAttributes>>(json)
    }

    fun writeListToJson(list: List<ConsentAttributes>): String {
      return jsonConfig.encodeToString<List<ConsentAttributes>>(list)
    }
  }

  @JvmField
  var consentId: String? = null

  @JvmField
  var version: String? = null

  @JvmField
  var text: String? = null

  @JvmField
  var printableName: String? = null

  @JvmField
  var accepted: Boolean = false

  @JvmField
  var deleted: Boolean = false

  @JvmField
  var acceptanceTime: Long = 0

  @JvmField
  var locale: String = "en"
}
