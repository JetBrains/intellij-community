// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.ide

import com.intellij.ide.ui.search.SearchableOptionsRegistrar
import com.intellij.ide.ui.search.SearchableOptionsRegistrarImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.JBProtocolCommand
import com.intellij.openapi.options.ex.ConfigurableExtensionPointUtil
import com.intellij.openapi.options.newEditor.SettingsDialogFactory
import com.intellij.openapi.project.ProjectManager
import com.intellij.util.text.nullize
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.QueryStringDecoder

private const val SERVICE_NAME = "settings"

internal class OpenSettingsService : RestService() {
  override fun getServiceName() = SERVICE_NAME

  override fun execute(urlDecoder: QueryStringDecoder, request: FullHttpRequest, context: ChannelHandlerContext): String? {
    val name = urlDecoder.parameters().get("name")?.firstOrNull()?.trim() ?: return parameterMissedErrorMessage("name")
    if (!doOpenSettings(name)) {
      return "no configurables found"
    }

    sendOk(request, context)
    return null
  }
}

// name must be trimmed
private fun doOpenSettings(name: String): Boolean {
  val effectiveProject = RestService.getLastFocusedOrOpenedProject() ?: ProjectManager.getInstance().defaultProject
  val groups = listOf(ConfigurableExtensionPointUtil.getConfigurableGroup(effectiveProject, true))
  val configurables = (SearchableOptionsRegistrar.getInstance() as SearchableOptionsRegistrarImpl).findByNameOnly(name, groups)
  if (configurables.nameFullHits.isEmpty()) {
    return false
  }

  val nameTopHit = configurables.nameFullHits.firstOrNull()
  ApplicationManager.getApplication().invokeLater(Runnable {
    SettingsDialogFactory.getInstance().create(effectiveProject, groups, nameTopHit, null).show()
  }, effectiveProject.disposed)
  return true
}

internal class OpenSettingsJbProtocolService : JBProtocolCommand(SERVICE_NAME) {
  override fun perform(target: String?, parameters: Map<String, String>) {
    val name = parameters.get("name")?.trim()?.nullize()
    if (name == null) {
      RestService.LOG.warn(RestService.parameterMissedErrorMessage("name") + " for action \"$SERVICE_NAME\"")
      return
    }

    doOpenSettings(name)
  }
}