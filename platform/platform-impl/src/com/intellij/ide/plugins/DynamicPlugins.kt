// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

@ApiStatus.Internal
object DynamicPlugins {
  /**
   * @return true if the requested enabled state was applied without restart, false if restart is required
   */
  fun loadPlugins(plugins: List<PluginMainDescriptor>, project: Project?): Boolean {
    return DynamicPluginsLegacyImpl.loadPlugins(plugins, project)
  }

  @JvmOverloads
  fun loadPlugin(pluginDescriptor: PluginMainDescriptor, project: Project? = null): Boolean {
    return DynamicPluginsLegacyImpl.loadPlugin(pluginDescriptor, project)
  }

  /**
   * @return true if the requested enabled state was applied without restart, false if restart is required
   */
  fun unloadPlugins(
    plugins: List<PluginMainDescriptor>,
    project: Project? = null,
    parentComponent: JComponent? = null,
    options: UnloadPluginOptions = UnloadPluginOptions(disable = true),
  ): Boolean {
    return DynamicPluginsLegacyImpl.unloadPlugins(plugins, project, parentComponent, options)
  }

  @Deprecated("use overload with PluginMainDescriptor parameter")
  @JvmOverloads
  fun unloadPlugin(pluginDescriptor: IdeaPluginDescriptorImpl,
                   options: UnloadPluginOptions = UnloadPluginOptions(disable = true)): Boolean {
    return DynamicPluginsLegacyImpl.unloadPlugin(pluginDescriptor, options)
  }

  @JvmOverloads
  fun unloadPlugin(pluginDescriptor: PluginMainDescriptor,
                   options: UnloadPluginOptions = UnloadPluginOptions(disable = true)): Boolean {
    return DynamicPluginsLegacyImpl.unloadPlugin(pluginDescriptor, options)
  }

  fun unloadPluginWithProgress(project: Project? = null,
                               parentComponent: JComponent?,
                               pluginDescriptor: PluginMainDescriptor,
                               options: UnloadPluginOptions): Boolean {
    return DynamicPluginsLegacyImpl.unloadPluginWithProgress(project, parentComponent, pluginDescriptor, options)
  }

  @JvmStatic
  @JvmOverloads
  fun allowLoadUnloadWithoutRestart(descriptor: IdeaPluginDescriptorImpl,
                                    baseDescriptor: IdeaPluginDescriptorImpl? = null,
                                    context: List<IdeaPluginDescriptorImpl> = emptyList()): Boolean {
    return DynamicPluginsLegacyImpl.allowLoadUnloadWithoutRestart(descriptor, baseDescriptor, context)
  }

  /**
   * Checks if the plugin can be loaded/unloaded immediately when the corresponding action is invoked in the
   * plugins settings, without pressing the Apply button.
   */
  // TODO migrate to isUIOnlyDynamicPlugin
  @JvmStatic
  fun allowLoadUnloadSynchronously(module: IdeaPluginDescriptorImpl): Boolean {
    return DynamicPluginsLegacyImpl.allowLoadUnloadSynchronously(module)
  }

  fun checkCanUnloadWithoutRestart(module: IdeaPluginDescriptorImpl): String? {
    return DynamicPluginsLegacyImpl.checkCanUnloadWithoutRestart(module)
  }

  fun runAfter(runAlways: Boolean, callback: Runnable) {
    return DynamicPluginsLegacyImpl.runAfter(runAlways, callback)
  }

  internal fun notify(@NlsContexts.NotificationContent text: String, notificationType: NotificationType, vararg actions: AnAction) {
    return DynamicPluginsLegacyImpl.notify(text, notificationType, *actions)
  }

  /**
   * Checks if a given plugin affects only the UI representation of the IDE.
   *
   * Acts as an allowlist condition to enable some features for "UI-only" plugins.
   */
  // TODO should we demand their "statelessness"?
  fun isUIOnlyDynamicPlugin(plugin: PluginMainDescriptor): Boolean {
    return DynamicPluginsLegacyImpl.isUIOnlyDynamicPlugin(plugin)
  }

  // TODO imprecise naming
  internal fun isUIOnlyExtension(extensionFqn: String): Boolean {
    return DynamicPluginsLegacyImpl.isUIOnlyExtension(extensionFqn)
  }

  fun onPluginUnload(parentDisposable: Disposable, callback: Runnable) {
    return DynamicPluginsLegacyImpl.onPluginUnload(parentDisposable, callback)
  }

  data class UnloadPluginOptions(
    var disable: Boolean = true,
    var isUpdate: Boolean = false,
    var save: Boolean = true,
    var requireMemorySnapshot: Boolean = false,
    var waitForClassloaderUnload: Boolean = false,
    var checkImplementationDetailDependencies: Boolean = true,
    var unloadWaitTimeout: Int? = null,
  ) {

    fun withUpdate(isUpdate: Boolean): UnloadPluginOptions = also {
      this.isUpdate = isUpdate
    }

    fun withWaitForClassloaderUnload(waitForClassloaderUnload: Boolean): UnloadPluginOptions = also {
      this.waitForClassloaderUnload = waitForClassloaderUnload
    }

    fun withDisable(disable: Boolean): UnloadPluginOptions = also {
      this.disable = disable
    }

    fun withRequireMemorySnapshot(requireMemorySnapshot: Boolean): UnloadPluginOptions = also {
      this.requireMemorySnapshot = requireMemorySnapshot
    }

    fun withUnloadWaitTimeout(unloadWaitTimeout: Int): UnloadPluginOptions = also {
      this.unloadWaitTimeout = unloadWaitTimeout
    }

    fun withSave(save: Boolean): UnloadPluginOptions = also {
      this.save = save
    }
    fun withCheckImplementationDetailDependencies(checkImplementationDetailDependencies: Boolean): UnloadPluginOptions = also {
      this.checkImplementationDetailDependencies = checkImplementationDetailDependencies
    }
  }
}