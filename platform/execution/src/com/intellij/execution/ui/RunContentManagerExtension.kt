// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.ui

import com.intellij.execution.Executor
import com.intellij.execution.RunContentDescriptorId
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentManager
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.CancellationException
import java.util.function.BiPredicate
import javax.swing.Icon

@ApiStatus.Internal
interface RunContentManagerExtension {
  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<RunContentManagerExtension> =
      ExtensionPointName.create("com.intellij.runContentManagerExtension")

    @JvmStatic
    fun getInstance(project: Project): RunContentManagerExtension? {
      for (extension in EP_NAME.extensionsIfPointIsRegistered) {
        try {
          if (extension.isApplicable(project)) {
            return extension
          }
        }
        catch (e: ProcessCanceledException) {
          throw e
        }
        catch (e: CancellationException) {
          throw e
        }
        catch (e: Throwable) {
          logger<RunContentManagerExtension>().error(e)
        }
      }
      return null
    }

    @JvmStatic
    fun isShownInServicesIfAvailable(project: Project, configuration: RunConfiguration): Boolean {
      return getInstance(project)?.isShownInServices(project, configuration) == true
    }

    @JvmStatic
    fun updateRunContentIfAvailable(project: Project, withStructure: Boolean) {
      getInstance(project)?.updateRunContent(project, withStructure)
    }

    @JvmStatic
    fun getConfiguredRunConfigurationTypesIfAvailable(project: Project): Set<String> {
      return getInstance(project)?.getConfiguredRunConfigurationTypes(project) ?: emptySet()
    }

    @JvmStatic
    fun setConfiguredRunConfigurationTypesIfAvailable(project: Project, types: Set<String>) {
      getInstance(project)?.setConfiguredRunConfigurationTypes(project, types)
    }
  }

  fun isApplicable(project: Project): Boolean = true

  fun isShownInServices(project: Project, configuration: RunConfiguration): Boolean = false

  fun isSupported(project: Project, executor: Executor): Boolean = true

  fun getToolWindowId(project: Project): String? = null

  fun getToolWindowIcon(project: Project): Icon? = null

  fun getContentManager(project: Project): ContentManager? = null

  fun getToolWindowIdIfCreated(project: Project): String? = null

  fun getContentManagerIfCreated(project: Project): ContentManager? = null

  fun getReuseCondition(project: Project): BiPredicate<in Content, RunConfiguration?>? = null

  fun contentReused(project: Project, content: Content, oldDescriptor: RunContentDescriptor) {}

  fun navigateToRunContent(project: Project, descriptorId: RunContentDescriptorId, focus: Boolean?) {}

  fun updateRunContent(project: Project, withStructure: Boolean) {}

  fun findService(project: Project, descriptorId: RunContentDescriptorId): Any? = null

  fun getConfiguredRunConfigurationTypes(project: Project): Set<String>? = null

  fun setConfiguredRunConfigurationTypes(project: Project, types: Set<String>) {}

  fun getToolWindowId(project: Project, configuration: RunConfiguration): String? {
    return if (isShownInServices(project, configuration)) getToolWindowId(project) else null
  }
}
