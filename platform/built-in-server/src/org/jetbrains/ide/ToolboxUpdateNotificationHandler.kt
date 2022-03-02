// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.ide

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.intellij.ide.IdeBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.components.service
import com.intellij.openapi.util.Disposer
import com.intellij.util.Consumer
import com.intellij.util.concurrency.AppExecutorUtil
import java.util.concurrent.TimeUnit

internal data class UpdateNotification(val version: String, val build: String)

private fun parseUpdateNotificationRequest(request: JsonElement): UpdateNotification {
  require(request.isJsonObject) { "JSON Object was expected" }
  val obj = request.asJsonObject

  val build = obj["build"]?.asString
  val version = obj["version"]?.asString

  require(!build.isNullOrBlank()) { "the `build` attribute must not be blank" }
  require(!version.isNullOrBlank()) { "the `version` attribute must not be blank" }
  return UpdateNotification(version = version, build = build)
}

internal class ToolboxUpdateNotificationHandler : ToolboxServiceHandler<UpdateNotification> {
  override val requestName: String = "update-notification"
  override fun parseRequest(request: JsonElement) = parseUpdateNotificationRequest(request)

  override fun handleToolboxRequest(lifetime: Disposable, request: UpdateNotification, onResult: (JsonElement) -> Unit) {
    val actionHandler = Consumer<AnActionEvent> {
      onResult(JsonObject().apply { addProperty("status", "accepted") })
    }

    val fullProductName = ApplicationNamesInfo.getInstance().fullProductName
    val title = IdeBundle.message("toolbox.updates.download.update.action.text", request.build, request.version, fullProductName)
    val description = IdeBundle.message("toolbox.updates.download.update.action.description", request.build, request.version, fullProductName)
    val action = ToolboxUpdateAction("toolbox-02-update-${request.build}", lifetime, title, description, actionHandler, false)
    service<ToolboxSettingsActionRegistry>().registerUpdateAction(action)
  }
}

internal class ToolboxRestartNotificationHandler : ToolboxServiceHandler<UpdateNotification> {
  override val requestName: String = "restart-notification"
  override fun parseRequest(request: JsonElement) = parseUpdateNotificationRequest(request)

  override fun handleToolboxRequest(lifetime: Disposable, request: UpdateNotification, onResult: (JsonElement) -> Unit) {
    val actionHandler = Consumer<AnActionEvent> {
      //at the normal scenario, the lifetime is disposed after the connection is closed
      //so Toolbox should get everything needed to handle the restart
      //otherwise an exception is thrown here, so it's OK
      Disposer.register(lifetime) {
        AppExecutorUtil.getAppScheduledExecutorService().schedule(Runnable {
          invokeLater {
            val app = ApplicationManager.getApplication()
            if (app?.isUnitTestMode == false) {
              app.exit(false, true, false)
            } else {
              System.setProperty("toolbox-service-test-restart", "1")
            }
          }
        }, 300, TimeUnit.MICROSECONDS)
      }

      onResult(JsonObject().apply {
        addProperty("status", "accepted")
        addProperty("pid", ProcessHandle.current().pid())
      })
    }

    val fullProductName = ApplicationNamesInfo.getInstance().fullProductName
    val title = IdeBundle.message("toolbox.updates.download.ready.action.text", request.build, request.version, fullProductName)
    val description = IdeBundle.message("toolbox.updates.download.ready.action.description", request.build, request.version, fullProductName)
    val action = ToolboxUpdateAction("toolbox-01-restart-${request.build}", lifetime, title, description, actionHandler, true)
    service<ToolboxSettingsActionRegistry>().registerUpdateAction(action)
  }
}
