// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

/**
 * Configures the project JDK according to configuration files from external config tools.
 */
public class ExternalJavaConfigurationActivity : ProjectActivity {

  init {
    if (ApplicationManager.getApplication().isUnitTestMode) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override suspend fun execute(project: Project) {
    // Delay a little bit not to overload project opening
    delay(5.seconds)

    val service = project.service<ExternalJavaConfigurationService>()
    service.addExtensionPointListener()

    for (configProvider in ExternalJavaConfigurationProvider.EP_NAME.extensionList) {
      service.updateFromConfig(configProvider, true)
    }
  }
}