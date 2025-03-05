// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.org

import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.util.text.VersionComparatorUtil
import com.intellij.util.xmlb.annotations.Tag
import com.intellij.util.xmlb.annotations.XCollection

/**
 * This is the common service to deal with organizational
 * restrictions in the UI for the plugin management.
 */
@Service(Service.Level.APP)
@Deprecated("please use PluginManagementPolicy directly instead",
            ReplaceWith("getPluginManagementPolicy()", "com.intellij.ide.plugins.getPluginManagementPolicy"))
class PluginManagerFilters {
  companion object {
    @JvmStatic
    fun getInstance(): PluginManagerFilters = service()
  }

  private val state
    get() = service<PluginManagerConfigurableForOrgConfig>().state

  fun allowInstallingPlugin(descriptor: IdeaPluginDescriptor): Boolean {
    return state.isAllowed(descriptor)
  }

  fun isPluginAllowed(isLocalPlugin: Boolean, descriptor: IdeaPluginDescriptor): Boolean = allowInstallingPlugin(descriptor)

  fun allowInstallFromDisk(): Boolean = state.allowInstallFromDisk


}

@Service(Service.Level.APP)
@State(name = "plugin-filter", storages = [Storage(value = "orgPluginsFilter.xml")])
private class PluginManagerConfigurableForOrgConfig : SimplePersistentStateComponent<PluginManagerConfigurableForOrgState>(PluginManagerConfigurableForOrgState())

private class PluginManagerConfigurableForOrgState : BaseState() {
  var allowInstallFromDisk by property(true)

  /**
   * - A plugin is accepted if there are no rules
   * - A plugin is rejected if none rules matches it.
   * - It works independently with the [denyRules].
   */
  @get:XCollection
  var allowRules by list<PluginManagerConfigurableForOrgStateRule>()

  /**
   * - A plugin is accepted if there are no rules
   * - A plugin is rejected if at least one deny rule matches it.
   * - It works independently with the [allowRules].
   */
  @get:XCollection
  var denyRules by list<PluginManagerConfigurableForOrgStateRule>()
}

@Tag("rule")
private class PluginManagerConfigurableForOrgStateRule : BaseState() {
  var pluginIdRegex by string()
  var vendorRegex by string()
  var versionRegex by string()

  var versionFromInclusive by string()
  var versionToInclusive by string()
}

private abstract class PluginManagerFiltersConfigureDebugActionBase : DumbAwareAction() {
  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = ApplicationManager.getApplication().isInternal
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  protected abstract fun updateState(state: PluginManagerConfigurableForOrgState)

  override fun actionPerformed(e: AnActionEvent) {
    val state = service<PluginManagerConfigurableForOrgConfig>().state
    updateState(state)
    state.intIncrementModificationCount()
  }
}

private class PluginManagerFiltersConfigureTrustOnlyJetBrainsDebugAction : PluginManagerFiltersConfigureDebugActionBase() {
  override fun updateState(state: PluginManagerConfigurableForOrgState) {
    state.denyRules.clear()
    state.allowRules.clear()
    state.allowRules.add(PluginManagerConfigurableForOrgStateRule().apply {
      vendorRegex = ".*JetBrains.*"
    })
  }
}

private class PluginManagerFiltersConfigureResetTrustDebugAction : PluginManagerFiltersConfigureDebugActionBase() {
  override fun updateState(state: PluginManagerConfigurableForOrgState) {
    state.allowRules.clear()
    state.denyRules.clear()
    state.allowInstallFromDisk = true
  }
}

private fun PluginManagerConfigurableForOrgState.isAllowed(descriptor: IdeaPluginDescriptor): Boolean {
  for (denyRule in denyRules) {
    if (denyRule.matches(descriptor)) return false
  }

  if (allowRules.isEmpty()) return true

  for (allowRule in allowRules) {
    if (allowRule.matches(descriptor)) return true
  }

  return false
}

private fun PluginManagerConfigurableForOrgStateRule.matches(descriptor: IdeaPluginDescriptor): Boolean {
  pluginIdRegex?.let {
    if (!safeMatch(descriptor.pluginId.idString, it)) return false
  }

  vendorRegex?.let {
    if (!safeMatch(descriptor.vendor ?: descriptor.organization, it)) return false
  }

  versionRegex?.let {
    if (!safeMatch(descriptor.version, it)) return false
  }

  versionFromInclusive?.let {
    if (descriptor.version == null) return false
    if (VersionComparatorUtil.compare(it, descriptor.version) > 0) return false
  }

  versionToInclusive?.let {
    if (descriptor.version == null) return false
    if (VersionComparatorUtil.compare(descriptor.version, it) > 0) return false
  }

  return true
}


private fun safeMatch(value: String?, regex: String): Boolean {
  if (value == null) return false

  runCatching {
    return regex.toRegex(RegexOption.IGNORE_CASE).matchEntire(value) != null
  }

  return false
}
