// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

val CWE_TOP25_2023 = setOf(20, 22, 77, 78, 79, 89, 94, 119, 125, 190, 269, 276, 287, 306, 352, 362, 416, 434, 476, 502,
                           787, 798, 862, 863, 918)

data class InspectionsMetaInformation @JsonCreator constructor(
  @JsonProperty("inspections") val inspections: List<MetaInformation>
)

data class MetaInformation @JsonCreator constructor(
  @JsonProperty("id") val id: String,
  @JsonProperty("cweIds") val cweIds: List<Int>?,
  @JsonProperty("categories") val categories: List<String>?
)

@Service(Service.Level.APP)
class InspectionMetaInformationService(val serviceScope: CoroutineScope) {

  private val storage = ConcurrentHashMap<String, MetaInformation>()
  private val initJob = AtomicReference<Deferred<Any?>?>()

  init {
    val app = ApplicationManager.getApplication()

    app.getMessageBus().connect(serviceScope).subscribe(DynamicPluginListener.TOPIC, object : DynamicPluginListener {
      override fun pluginLoaded(pluginDescriptor: IdeaPluginDescriptor) {
        dropStorage()
      }

      override fun pluginUnloaded(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
        dropStorage()
      }
    })
  }

  fun dropStorage() {
    initJob.set(null)
    storage.clear()
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  fun isInitialized(): Boolean {
    val job = initJob.get() ?: return false
    if (job.isCompleted) return job.getCompletionExceptionOrNull() == null
    return false
  }


  fun initialize(): Deferred<Any?> {
    val deferred = CompletableDeferred<Any?>()
    while (true) {
      if (initJob.compareAndSet(null, deferred)) break
      val job = initJob.get()
      if (job != null) return job
    }

    var error: Throwable? = null
    val visited = mutableSetOf<ClassLoader>()
    for (plugin in PluginManagerCore.getPluginSet().getEnabledModules()) {
      val classLoader = plugin.getPluginClassLoader() ?: continue
      if (!visited.add(classLoader) || classLoader !is UrlClassLoader) {
        continue
      }
      try {
        readPluginMetaInformation(classLoader, plugin)
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
      initJob.set(null)
      deferred.completeExceptionally(error)
    }
    else {
      deferred.complete(null)
    }
    return deferred
  }

  fun getMetaInformation(inspectionId: String): MetaInformation? {
    return storage[inspectionId]
  }

  private fun readPluginMetaInformation(classLoader: UrlClassLoader, plugin: IdeaPluginDescriptorImpl) {
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