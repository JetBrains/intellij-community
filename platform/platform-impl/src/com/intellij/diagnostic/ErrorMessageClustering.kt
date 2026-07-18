// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import com.intellij.diagnostic.IdeErrorsDialog.Companion.hashMessage
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.PluginUtil
import com.intellij.ide.plugins.newui.PluginUiModel
import com.intellij.ide.plugins.newui.UiPluginManager
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.ProblematicPluginInfo
import com.intellij.openapi.diagnostic.ProblematicPluginInfoWithDescriptor
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.registry.Registry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import org.jetbrains.annotations.ApiStatus

@Service(Service.Level.APP)
internal class ErrorMessageClustering(private val coroutineScope: CoroutineScope) {
  companion object {
    fun getInstance(): ErrorMessageClustering = service<ErrorMessageClustering>()
  }

  internal fun clusterMessages(): Deferred<List<ErrorMessageCluster>> {
    return coroutineScope.async {
      val messages = MessagePool.getInstance().getFatalErrors(true, true)
      val deduplicateReports = ErrorMessageClusteringSettings.isDeduplicationEnabled()
      messages
        .groupBy { if (deduplicateReports) hashMessage(it) else it }
        .map { createCluster(it.value) }
    }
  }

  private suspend fun createCluster(messages: List<AbstractMessage>): ErrorMessageCluster {
    val first = messages.first()
    val pluginId = analyzeCause(first)
    val plugin = createPluginInfo(pluginId)
    val submitter = DefaultIdeaErrorLogger.findSubmitterByPluginInfo(first.throwable, plugin)
    return ErrorMessageCluster(messages, pluginId, plugin, submitter)
  }

  internal fun analyzeCause(first: AbstractMessage): PluginId? {
    val t = first.throwable
    if (t.isInstance<Freeze>()) {
      if (t is RemoteSerializedThrowable) {
        // todo freeze is from backend, cannot analyze in frontend, infer it from exception
        return PluginUtil.getInstance().findPluginId(t)
      }

      return IdeaFreezeReporter.analyzeFreeze(first)
    }

    return PluginUtil.getInstance().findPluginId(t)
  }

  internal suspend fun createPluginInfo(pluginId: PluginId?): ProblematicPluginInfo? {
    if (pluginId == null) return null

    val localPlugin = PluginManagerCore.getPlugin(pluginId)
    if (localPlugin != null) return ProblematicPluginInfoBasedOnDescriptor(localPlugin)

    val uiModel = UiPluginManager.getInstance().getPlugin(pluginId) ?: return null
    return ProblematicPluginInfoBasedOnModel(uiModel)
  }
}

@ApiStatus.Internal
object ErrorMessageClusteringSettings {
  const val DEDUPLICATE_REPORTS: String = "ide.errors.deduplicate"

  @Volatile
  private var deduplicationOverride: Boolean? = null

  fun setDeduplicationOverride(enabled: Boolean?) {
    deduplicationOverride = enabled
  }

  fun isDeduplicationEnabled(): Boolean = deduplicationOverride ?: Registry.`is`(DEDUPLICATE_REPORTS, true)
}

internal inline fun <reified T : Throwable> Throwable.isBackendInstance(): Boolean {
  return this is RemoteSerializedThrowable && classFqn == T::class.qualifiedName
}

internal inline fun <reified T : Throwable> Throwable.isInstance() = this is T || isBackendInstance<T>()

private class ProblematicPluginInfoBasedOnModel(val plugin: PluginUiModel) : ProblematicPluginInfo {
  override val pluginId: PluginId
    get() = plugin.pluginId
  override val isBuiltIn: Boolean
    get() = plugin.isBundled || plugin.isBundledUpdate
  override val isImplementationDetail: Boolean
    get() = plugin.isImplementationDetail
  override val isEssential: Boolean
    get() = plugin.isEssential
  override val allowsBundledUpdate: Boolean
    get() = plugin.allowBundledUpdate
  override val name: @NlsSafe String
    get() = plugin.name ?: pluginId.idString
  override val version: @NlsSafe String?
    get() = plugin.version
  override val organization: @NlsSafe String?
    get() = plugin.organization
  override val vendor: @NlsSafe String?
    get() = plugin.vendor
  override val vendorUrl: String?
    get() = plugin.vendorDetails?.url
  override val vendorEmail: String?
    get() = null
}

private class ProblematicPluginInfoBasedOnDescriptor(override val pluginDescriptor: IdeaPluginDescriptor) : ProblematicPluginInfo,
                                                                                                            ProblematicPluginInfoWithDescriptor {
  override val pluginId: PluginId
    get() = pluginDescriptor.pluginId
  override val isBuiltIn: Boolean
    get() = pluginDescriptor.isBundled || PluginManagerCore.isUpdatedBundledPlugin(pluginDescriptor)
  override val isImplementationDetail: Boolean
    get() = pluginDescriptor.isImplementationDetail
  override val isEssential: Boolean
    get() = ApplicationInfo.getInstance().isEssentialPlugin(pluginId)
  override val allowsBundledUpdate: Boolean
    get() = pluginDescriptor.allowBundledUpdate()
  override val name: @NlsSafe String
    get() = pluginDescriptor.name ?: pluginId.idString
  override val version: @NlsSafe String?
    get() = pluginDescriptor.version
  override val organization: @NlsSafe String?
    get() = pluginDescriptor.organization
  override val vendor: @NlsSafe String?
    get() = pluginDescriptor.vendor
  override val vendorUrl: String?
    get() = pluginDescriptor.vendorUrl
  override val vendorEmail: String?
    get() = pluginDescriptor.vendorEmail
}

@ApiStatus.Internal
fun toProblematicPluginInfo(pluginDescriptor: IdeaPluginDescriptor?): ProblematicPluginInfo? =
  pluginDescriptor?.let { ProblematicPluginInfoBasedOnDescriptor(it) }