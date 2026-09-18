// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.annotations.RequiresReadLockAbsence
import org.jetbrains.annotations.ApiStatus

private val LOG = Logger.getInstance(AiEnabledFlag::class.java)

/**
 * Writes the AI flag of [AiEnabledState] and brings the plugin subsystem to the state that the new flag asks for.
 *
 * The flag controls only the JetBrains internal plugins that depend on [AIR_AI_MARKER_MODULE_ID]. It is not a general
 * switch of every AI feature of the IDE.
 *
 * The owner of the flag calls this instead of [DynamicPlugins.reconfigure].
 */
@ApiStatus.Internal
@ApiStatus.Experimental
object AiEnabledFlag {
  /**
   * Writes the flag and reconfigures the plugin subsystem to match it.
   *
   * @param project owns the modal progress of the reconfiguration only. `null` lets the platform guess the
   *   active window. It does not change the computed plugin set.
   * @return `false` when the plugin subsystem needs a restart of the IDE to match. The stored flag stays written.
   */
  @RequiresReadLockAbsence(generateAssertion = false /* IJPL-115548 */)
  suspend fun setEnabledAndReconfigure(enabled: Boolean, project: Project? = null): Boolean {
    LOG.trace { "a caller asks for AI enabled=$enabled\n${Throwable().stackTraceToString()}" }
    val changed = AiEnabledState.setEnabled(enabled)
    // The flag can already be right while the plugin set is not, as after a failed reconfiguration.
    val mismatch = isMarkerDeclared() && isMarkerEnabled() != enabled
    LOG.info("AI is enabled=$enabled: the flag changed: $changed, the plugin set differs: $mismatch")
    if (!changed && !mismatch) {
      return true
    }
    val reconfigured = DynamicPlugins.reconfigure(
      project = project,
      addNewCustomPlugins = emptyList(),
      forceRemovePlugins = emptyList(),
      extraStateValidator = { null },
    )
    if (!reconfigured) {
      LOG.warn("the plugin subsystem needs a restart of the IDE to apply enabled=$enabled")
    }
    return reconfigured
  }

  /** A marker that no installed plugin declares can never load, so it needs no reconfiguration. */
  private fun isMarkerDeclared(): Boolean =
    PluginManagerCore.getPluginSet().resolvedPluginSet.candidateSet.resolveContentModuleId(AIR_AI_MARKER_MODULE_ID) != null

  private fun isMarkerEnabled(): Boolean = PluginManagerCore.getPluginSet().isModuleEnabled(AIR_AI_MARKER_MODULE_ID)
}
