// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal

import com.intellij.execution.EnvFilesOptions
import com.intellij.execution.Executor
import com.intellij.execution.configurations.*
import com.intellij.execution.configurations.ConfigurationType.CONFIGURATION_TYPE_EP
import com.intellij.execution.runners.ProgramRunner.PROGRAM_RUNNER_EP
import com.intellij.execution.target.TargetEnvironmentAwareRunProfile
import com.intellij.execution.ui.FragmentedSettingsEditor
import com.intellij.ide.plugins.PluginManager
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.options.SettingsEditorGroup
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.StringUtil
import com.intellij.testFramework.LightVirtualFile
import com.intellij.util.ReflectionUtil
import com.intellij.util.containers.mapSmartSet

private const val UNSPECIFIED = "---"

private class DumpRunConfigurationTypesAction : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = e.project != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project!!

    val configurationTypes = CONFIGURATION_TYPE_EP.extensionList

    val appInfo = ApplicationInfo.getInstance()
    var output: String =
      "${configurationTypes.size} Run Configuration Types\n" +
      "${appInfo.fullApplicationName} (${appInfo.fullVersion}) [${appInfo.build.asString()}]\n\n" +
      "Mapping 'Required Modules' to IDEs: https://plugins.jetbrains.com/docs/intellij/plugin-compatibility.html#modules-specific-to-functionality\n\n"

    for (configurationType in configurationTypes.sortedBy { it.id }) {
      output += dumpConfigurationType(configurationType)

      output += dumpPluginAndDependencies(configurationType)

      for (configurationFactory in configurationType.configurationFactories) {
        output += dumpConfigurationFactory(configurationFactory)

        val runConfiguration = configurationFactory.createTemplateConfiguration(project)

        output += dumpRunConfiguration(runConfiguration)

        output += dumpExecutors(runConfiguration)

        output += dumpConfigurationEditor(runConfiguration)

        output += dumpOptionsClass(configurationFactory, runConfiguration)

        output += "\n"
      }

      output += "\n\n"
      output += StringUtil.repeatSymbol('=', 120)
      output += "\n"
    }

    val vf = LightVirtualFile("Run Configuration Templates", PlainTextFileType.INSTANCE, output)
    FileEditorManager.getInstance(project).openEditor(OpenFileDescriptor(project, vf), true)
  }

  private fun dumpPluginAndDependencies(configurationType: ConfigurationType): String {
    val pluginByClass = PluginManager.getPluginByClass(configurationType::class.java)
    if (pluginByClass == null) {
      return "Plugin:           Built-in (Platform)\n\n"
    }

    var output = "\n"
    output += "Plugin:           ${pluginByClass.dump()}\n"
    output += "Min. IDE Version: ${StringUtil.notNullize(pluginByClass.sinceBuild, UNSPECIFIED)}\n"

    var requiredPlugins = ""
    var requiredModules = ""
    PluginManagerCore.getPlugin(pluginByClass.pluginId)!!
      .dependencies
      .filter { !it.isOptional }
      .mapSmartSet { it.pluginId } // de-duplicate
      .forEach {
        val dependency = PluginManagerCore.getPlugin(it)
        if (dependency == null) return@forEach

        if (dependency.pluginId == PluginManagerCore.CORE_ID) {
          requiredModules += "\n\t${it.idString}"
          return@forEach
        }

        requiredPlugins += "\n\t${dependency.dump()}"
      }
    output += "Required Plugins: ${StringUtil.defaultIfEmpty(requiredPlugins, UNSPECIFIED)}\n"
    output += "Required Modules: ${StringUtil.defaultIfEmpty(requiredModules, UNSPECIFIED)}\n"

    output += "\n"
    return output
  }

  private fun dumpConfigurationType(configurationType: ConfigurationType): String {
    var output = ""
    output += "Name:             ${configurationType.displayName}\n"
    output += "Description:      ${configurationType.configurationTypeDescription}\n"
    output += "ID:               ${configurationType.id}\n"
    output += "Implementation:   ${configurationType.javaClass.name}\n"
    output += "Managed:          ${configurationType.isManaged}\n"
    output += "Help topic ID:    ${configurationType.helpTopic}\n"

    return output
  }

  private fun dumpConfigurationFactory(configurationFactory: ConfigurationFactory): String {
    var output = ""
    output += StringUtil.repeatSymbol('-', 40) + "\n"
    output += "Factory Name:     ${configurationFactory.name}\n"
    output += "Factory ID:       ${configurationFactory.id}\n"
    output += "Implementation:   ${configurationFactory.javaClass.name}\n"
    output += "Singleton Policy: ${configurationFactory.singletonPolicy}\n"
    output += "Dumb Mode Edit:   ${configurationFactory.isEditableInDumbMode}\n"

    output += "\n"
    return output
  }

  private fun dumpRunConfiguration(runConfiguration: RunConfiguration): String {
    var output = ""
    output += "Run Configuration:    ${runConfiguration.javaClass.name}\n"
    output += "Create from Context:  ${runConfiguration is LocatableConfiguration}\n"
    output += "Supports Targets:     ${runConfiguration is TargetEnvironmentAwareRunProfile}\n"
    output += "Environment Files:    ${runConfiguration is EnvFilesOptions}\n"
    output += "SearchScope Provider: ${runConfiguration is SearchScopeProvidingRunProfile}\n"
    output += "Virtual:              ${runConfiguration is VirtualConfigurationType}\n"
    output += "Make Before Launch:   ${runConfiguration is RunProfileWithCompileBeforeLaunchOption}\n"
    output += "No Default Debug:     ${runConfiguration is RunConfigurationWithSuppressedDefaultDebugAction}\n"

    output += "\n"
    return output
  }

  private fun dumpExecutors(runConfiguration: RunConfiguration): String {
    var output = ""
    for (executor in Executor.EXECUTOR_EXTENSION_NAME.extensionList) {
      val programRunners = PROGRAM_RUNNER_EP.extensionList.filter { it.canRun(executor.id, runConfiguration) }
      if (programRunners.isEmpty()) continue

      output += "Runners (${executor.id} - ${executor.javaClass.name}):\n"
      for (runner in programRunners) {
        output += "\t${runner.runnerId} - ${runner.javaClass.name}\n"
      }
    }

    output += "\n"
    return output
  }

  private fun dumpConfigurationEditor(runConfiguration: RunConfiguration): String {
    var output = ""

    val configurationEditor = runConfiguration.configurationEditor
    if (configurationEditor is SettingsEditorGroup) {
      output += "Settings Editor Group:\n"
      for (editor in configurationEditor.editors) {
        output += "\t${editor.first} - ${editor.second.dump()}"
      }
    }
    else {
      output += "Settings Editor:      ${configurationEditor.dump()}"
    }

    Disposer.dispose(configurationEditor)

    output += "\n"
    return output
  }

  private fun dumpOptionsClass(configurationFactory: ConfigurationFactory, runConfiguration: RunConfiguration): String {
    var optionsClass: Class<*>? = configurationFactory.optionsClass
    var optionsClassVia = "ConfigurationFactory"

    if (optionsClass == null && runConfiguration is RunConfigurationBase<*>) {
      val methodDeclaringClass = ReflectionUtil.getMethodDeclaringClass(runConfiguration.javaClass, "getOptionsClass")!!
      val declaredMethod = methodDeclaringClass.getDeclaredMethod("getOptionsClass")
      declaredMethod.isAccessible = true
      @Suppress("UNCHECKED_CAST")
      optionsClass = (declaredMethod.invoke(runConfiguration)) as Class<out RunConfigurationOptions?>?
      optionsClassVia = "RunConfigurationBase"
    }

    if (optionsClass != null) {
      return "Options class:        ${optionsClass.name} [via ${optionsClassVia}]\n"
    }

    return "Options class:        n/a\n"
  }

  private fun PluginDescriptor.dump(): String {
    return "${this.name} (Vendor: ${this.vendor} ID: ${this.pluginId.idString} Bundled: ${this.isBundled}${if (this.isImplementationDetail) " [IMPLEMENTATION DETAIL]" else ""})"
  }

  private fun SettingsEditor<*>.dump(): String {
    val newUI = this is FragmentedSettingsEditor
    val className = this.javaClass.name
    return buildString {
      append(className)
      if (newUI) append(" [New UI]")
      appendLine()
    }
  }
}