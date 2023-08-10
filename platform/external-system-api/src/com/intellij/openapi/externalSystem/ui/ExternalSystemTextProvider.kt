// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.ui

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle
import com.intellij.openapi.util.NlsContexts
import org.jetbrains.annotations.Nls

interface ExternalSystemTextProvider {

  val systemId: ProjectSystemId

  /**
   * Text for unlinked project notification.
   * UPN is abbreviation for UnlinkedProjectNotification.
   * @see com.intellij.openapi.externalSystem.autolink.ExternalSystemUnlinkedProjectAware
   */
  fun getUPNText(projectName: @Nls String): @NlsContexts.NotificationContent String =
    ExternalSystemBundle.message("unlinked.project.notification.title", systemId.readableName, projectName)

  /**
   * Text for link project notification action.
   * UPN is abbreviation for UnlinkedProjectNotification.
   * @see com.intellij.openapi.externalSystem.autolink.ExternalSystemUnlinkedProjectAware
   */
  fun getUPNLinkActionText(): @NlsContexts.NotificationContent String =
    ExternalSystemBundle.message("unlinked.project.notification.load.action", systemId.readableName)

  /**
   * Text for skip linking project notification action.
   * UPN is abbreviation for UnlinkedProjectNotification.
   * @see com.intellij.openapi.externalSystem.autolink.ExternalSystemUnlinkedProjectAware
   */
  fun getUPNSkipActionText(): @NlsContexts.NotificationContent String =
    ExternalSystemBundle.message("unlinked.project.notification.skip.action")

  /**
   * Text for unlinked project notification's help.
   * UPN is abbreviation for UnlinkedProjectNotification.
   * @see com.intellij.openapi.externalSystem.autolink.ExternalSystemUnlinkedProjectAware
   */
  fun getUPNHelpText(): @NlsContexts.NotificationContent String =
    ExternalSystemBundle.message("unlinked.project.notification.help.text", systemId.readableName)

  companion object {

    private val EP_NAME = ExtensionPointName<ExternalSystemTextProvider>("com.intellij.externalTextProvider")

    @JvmStatic
    fun getExtension(systemId: ProjectSystemId): ExternalSystemTextProvider {
      val textProvider = EP_NAME.findFirstSafe { it.systemId == systemId }
      if (textProvider != null) return textProvider
      logger<ExternalSystemTextProvider>().debug("Cannot find ExternalSystemTextProvider for $systemId. Fallback to default provider")
      return object : ExternalSystemTextProvider {
        override val systemId: ProjectSystemId = systemId
      }
    }
  }
}