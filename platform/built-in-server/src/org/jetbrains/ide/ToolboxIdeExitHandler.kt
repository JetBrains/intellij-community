// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.ide

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.impl.LaterInvocator
import com.intellij.util.application

class ToolboxIdeExitHandler : ToolboxServiceHandler<ToolboxIdeExitHandler.ExitParameters> {
  override val requestName: String
    get() = "exit"

  override fun parseRequest(request: JsonElement): ExitParameters {
    require(request.isJsonObject) { "JSON Object was expected" }
    val obj = request.asJsonObject

    return ExitParameters(
      force = obj["force"]?.asBoolean ?: true,
      confirmed = obj["confirmed"]?.asBoolean ?: true,
      restart = obj["restart"]?.asBoolean ?: false,
    )
  }

  override fun handleToolboxRequest(lifetime: Disposable, request: ExitParameters, onResult: (JsonElement) -> Unit) {
    onResult(JsonObject().apply {
      addProperty("status", "accepted")
    })
    application.invokeLater({ LaterInvocator.forceLeaveAllModals("Toolbox requested ${if (request.restart) "restart" else "exit"}") }, ModalityState.any())
    application.invokeLater({ application.exit(request.force, request.confirmed, request.restart) }, ModalityState.nonModal())
  }

  data class ExitParameters(val force: Boolean = true, val confirmed : Boolean = true, val restart: Boolean = false)
}