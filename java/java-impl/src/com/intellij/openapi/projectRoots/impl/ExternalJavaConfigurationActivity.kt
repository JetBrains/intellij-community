// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

/**
 * Configures the project JDK according to configuration files from external config tools.
 */
class ExternalJavaConfigurationActivity : ProjectActivity {

  private val disposableMap = mutableMapOf<Class<*>, Disposable>()

  init {
    if (ApplicationManager.getApplication().isUnitTestMode) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override suspend fun execute(project: Project) {
    // Delay a little bit not to overload project opening
    delay(5.seconds)

    val configWatcherService = project.service<ExternalJavaConfigurationService>()

    ExternalJavaConfigurationProvider.EP_NAME.addExtensionPointListener(object : ExtensionPointListener<ExternalJavaConfigurationProvider<*>> {
      override fun extensionAdded(extension: ExternalJavaConfigurationProvider<*>, pluginDescriptor: PluginDescriptor) {
        setupExtension(project, extension)
      }

      override fun extensionRemoved(extension: ExternalJavaConfigurationProvider<*>, pluginDescriptor: PluginDescriptor) {
        val clazz = extension::class.java
        disposableMap[clazz]?.let { Disposer.dispose(it) }
        disposableMap.remove(extension::class.java)
      }
    }, configWatcherService)

    for (configProvider in ExternalJavaConfigurationProvider.EP_NAME.extensionList) {
      val file = configProvider.getConfigurationFile(project)
      setupExtension(project, configProvider)
      if (file.exists()) configWatcherService.updateJdkFromConfig(configProvider)
    }
  }

  private fun setupExtension(project: Project, extension: ExternalJavaConfigurationProvider<*>) {
    val key = extension::class.java
    if (disposableMap.containsKey(key)) return
    val configWatcherService = project.service<ExternalJavaConfigurationService>()
    val disposable = Disposer.newDisposable(configWatcherService)
    configWatcherService.registerListener(disposable, extension)
    disposableMap[extension::class.java] = disposable
  }
}