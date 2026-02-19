// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.dialog

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

interface SystemDataJsonSerializable {

  fun serializeToJson(json: Json): JsonElement
}