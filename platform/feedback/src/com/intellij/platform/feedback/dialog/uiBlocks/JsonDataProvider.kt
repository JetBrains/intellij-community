// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.dialog.uiBlocks

import kotlinx.serialization.json.JsonObjectBuilder

interface JsonDataProvider {

  fun collectBlockDataToJson(jsonObjectBuilder: JsonObjectBuilder)
}