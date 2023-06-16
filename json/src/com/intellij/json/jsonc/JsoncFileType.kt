// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.jsonc

import com.intellij.json.JsonFileType

class JsoncFileType : JsonFileType() {
  companion object {
    val DEFAULT_EXTENSION = "jsonc"
  }

  override fun getName(): String {
    return "JSONC"
  }

  override fun getDefaultExtension(): String {
    return DEFAULT_EXTENSION
  }
}