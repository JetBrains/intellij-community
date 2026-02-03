package com.jetbrains.rhizomedb

import kotlinx.serialization.json.Json

val DbJson: Json by lazy {
  Json {
    allowStructuredMapKeys = true
    ignoreUnknownKeys = true
    encodeDefaults = true
  }
}