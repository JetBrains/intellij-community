// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.ide

import com.intellij.ide.IdeBundle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.JBProtocolCommand
import com.intellij.openapi.options.newEditor.SettingsDialog
import com.intellij.openapi.options.newEditor.SettingsDialogFactory
import com.intellij.openapi.project.ProjectManager
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.QueryStringDecoder
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future

private const val SERVICE_NAME = "settings"

@Suppress("HardCodedStringLiteral")
internal class OpenSettingsService : RestService() {
  override fun getServiceName() = SERVICE_NAME

  override fun execute(urlDecoder: QueryStringDecoder, request: FullHttpRequest, context: ChannelHandlerContext): String? {
    val name = urlDecoder.parameters()["name"]?.firstOrNull()?.trim() ?: return parameterMissedErrorMessage("name")
    if (!doOpenSettings(name)) {
      return "no configurables found"
    }

    sendOk(request, context)
    return null
  }
}

private fun doOpenSettings(name: String): Boolean {
  val project = RestService.getLastFocusedOrOpenedProject() ?: ProjectManager.getInstance().defaultProject
  val configurable = SearchConfigurableByNameHelper(name, project).searchByName() ?: return false
  ApplicationManager.getApplication().invokeLater(
    Runnable { SettingsDialogFactory.getInstance().create(project, SettingsDialog.DIMENSION_KEY, configurable, false, false).show() },
    project.disposed)
  return true
}

internal class OpenSettingsJbProtocolService : JBProtocolCommand(SERVICE_NAME) {
  override fun perform(target: String?, parameters: Map<String, String>, fragment: String?): Future<String?> =
    parameter(parameters, "name").let { name ->
      CompletableFuture.completedFuture(if (doOpenSettings(name)) null else IdeBundle.message("jb.protocol.settings.no.configurable", name))
    }
}
