// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.ex

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.util.lang.UrlClassLoader
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import java.util.concurrent.atomic.AtomicReference

val CWE_TOP25_2023: Set<Int> = setOf(20, 22, 77, 78, 79, 89, 94, 119, 125, 190, 269, 276, 287, 306, 352, 362, 416, 434, 476, 502,
                                     787, 798, 862, 863, 918)

private data class InspectionsMetaInformation @JsonCreator constructor(
  @JsonProperty("inspections") val inspections: List<MetaInformation>
)

data class MetaInformation @JsonCreator constructor(
  @JsonProperty("id") val id: String,
  @JsonProperty("cweIds") val cweIds: List<Int>?,
  @JsonProperty("codeQualityCategory") val codeQualityCategory: String?,
  @JsonProperty("categories") val categories: List<String>?
)

class MetaInformationState(@JvmField val inspections: Map<String, MetaInformation>)

@Service(Service.Level.APP)
class InspectionMetaInformationService(serviceScope: CoroutineScope) {
  private val loadJob = AtomicReference<Deferred<MetaInformationState>?>()

  init {
    val app = ApplicationManager.getApplication()
    app.getMessageBus().connect(serviceScope).subscribe(DynamicPluginListener.TOPIC, object : DynamicPluginListener {
      override fun pluginLoaded(pluginDescriptor: IdeaPluginDescriptor) {
        invalidateState()
      }

      override fun pluginUnloaded(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
        invalidateState()
      }
    })
  }

  fun invalidateState() {
    loadJob.set(null)
  }

  suspend fun getState(): MetaInformationState {
    val job = loadJob.get() ?: loadState()
    return job.await()
  }

  private fun loadState(): Deferred<MetaInformationState> {
    val deferred = CompletableDeferred<MetaInformationState>()
    while (true) {
      if (loadJob.compareAndSet(null, deferred)) {
        break
      }

      loadJob.get()?.let {
        return it
      }
    }

    var error: Throwable? = null
    val visited = mutableSetOf<ClassLoader>()
    val result = mutableMapOf<String, MetaInformation>()
    for (plugin in PluginManagerCore.getPluginSet().getEnabledModules()) {
      val classLoader = plugin.getPluginClassLoader() ?: continue
      if (!visited.add(classLoader) || classLoader !is UrlClassLoader) {
        continue
      }
      try {
        readPluginMetaInformation(classLoader, plugin, result)
      }
      catch (e: Throwable) {
        if (error == null) {
          error = e
        }
        else {
          error.addSuppressed(e)
        }
      }
    }

    if (error != null) {
      loadJob.set(null)
      deferred.completeExceptionally(error)
    }
    else {
      deferred.complete(MetaInformationState(result))
    }
    return deferred
  }

  private fun readPluginMetaInformation(classLoader: UrlClassLoader,
                                        plugin: IdeaPluginDescriptorImpl,
                                        storage: MutableMap<String, MetaInformation>) {
    classLoader.processResources("inspectionDescriptions", { "inspectionDescriptions/metaInformation.json" == it }) { _, inputStream ->
      if (inputStream.available() <= 0) return@processResources
      val objectMapper = ObjectMapper()

      val value = objectMapper.readValue(inputStream, InspectionsMetaInformation::class.java)

      value.inspections.forEach {
        if (it.id.isEmpty()) throw IllegalStateException("Empty id in inspection metaInformation.json. $plugin")
        storage[it.id] = it
      }
    }
  }
}