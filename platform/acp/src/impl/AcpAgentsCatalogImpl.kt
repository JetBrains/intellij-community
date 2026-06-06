// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.acp.impl

import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.platform.acp.AcpAgentId
import com.intellij.platform.acp.AcpAgentsCatalog
import com.intellij.platform.acp.AcpAgentsCatalogProvider
import com.intellij.platform.acp.AcpCatalogEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.collections.forEach

/**
 * Platform-owned [com.intellij.platform.acp.AcpAgentsCatalog] service. Merges contributions from all currently registered
 * [com.intellij.platform.acp.AcpAgentsCatalogProvider]s and falls back to an empty catalog when no provider is loaded.
 */
internal class AcpAgentsCatalogImpl(private val coroutineScope: CoroutineScope) : AcpAgentsCatalog {
  private val lock = Any()
  private val _agentsFlow = MutableStateFlow<List<AcpCatalogEntry>>(emptyList())
  private val providerEntries = LinkedHashMap<AcpAgentsCatalogProvider, List<AcpCatalogEntry>>()
  private val providerJobs = HashMap<AcpAgentsCatalogProvider, Job>()

  override val agentsFlow: StateFlow<List<AcpCatalogEntry>> = _agentsFlow.asStateFlow()

  init {
    AcpAgentsCatalogProvider.EP_NAME.addExtensionPointListener(coroutineScope, object : ExtensionPointListener<AcpAgentsCatalogProvider> {
      override fun extensionAdded(extension: AcpAgentsCatalogProvider, pluginDescriptor: PluginDescriptor) = registerProvider(extension)
      override fun extensionRemoved(extension: AcpAgentsCatalogProvider, pluginDescriptor: PluginDescriptor) = unregisterProvider(extension)
    })
    AcpAgentsCatalogProvider.EP_NAME.extensionList.forEach(::registerProvider)
  }

  override fun get(id: AcpAgentId): AcpCatalogEntry? = agentsFlow.value.firstOrNull { it.id == id }

  private fun registerProvider(provider: AcpAgentsCatalogProvider) {
    synchronized(lock) {
      if (provider in providerEntries) return
      providerEntries[provider] = emptyList()
    }

    val job = coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
      provider.agentsFlow.collect { agents ->
        synchronized(lock) {
          if (provider !in providerEntries) return@collect
          providerEntries[provider] = agents
          _agentsFlow.value = buildMergedEntriesLocked()
        }
      }
    }

    synchronized(lock) {
      if (provider in providerEntries) {
        providerJobs[provider] = job
      }
      else {
        job.cancel()
      }
    }
  }

  private fun unregisterProvider(provider: AcpAgentsCatalogProvider) {
    val job = synchronized(lock) {
      providerEntries.remove(provider)
      _agentsFlow.value = buildMergedEntriesLocked()
      providerJobs.remove(provider)
    }
    job?.cancel()
  }

  private fun buildMergedEntriesLocked(): List<AcpCatalogEntry> {
    val mergedEntries = LinkedHashMap<AcpAgentId, AcpCatalogEntry>()
    for (provider in AcpAgentsCatalogProvider.EP_NAME.extensionList) {
      for (entry in providerEntries[provider].orEmpty()) {
        mergedEntries.putIfAbsent(entry.id, entry)
      }
    }
    return mergedEntries.values.toList()
  }
}
