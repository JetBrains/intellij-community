// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.ide

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.intellij.openapi.Disposable

internal class ToolboxUpdateNotificationHandler : ToolboxServiceHandler<ToolboxUpdateNotificationHandler.UpdateNotification> {
  data class UpdateNotification(val version: String, val build: String)

  override val requestName: String = "update-notification"

  override fun parseRequest(request: JsonElement): UpdateNotification {
    require(request.isJsonObject) { "JSON Object was expected" }
    val obj = request.asJsonObject

    val build = obj["build"]?.asString
    val version = obj["version"]?.asString

    require(!build.isNullOrBlank()) { "the `build` attribute must not be blank" }
    require(!version.isNullOrBlank()) { "the `version` attribute must not be blank" }
    return UpdateNotification(version = version, build = build)
  }

  override fun handleToolboxRequest(lifetime: Disposable, request: UpdateNotification, onResult: (JsonElement) -> Unit) {
    onResult(JsonObject().apply { addProperty("status", "not implemented") })
  }
}
