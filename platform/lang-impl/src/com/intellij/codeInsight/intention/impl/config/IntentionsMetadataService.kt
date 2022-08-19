// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl.config

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.IntentionActionBean
import com.intellij.ide.ui.TopHitCache
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.util.containers.Interner
import java.util.AbstractMap
import java.util.HashMap
import java.util.LinkedHashMap

@Service(Service.Level.APP)
internal class IntentionsMetadataService {
  private class MetaDataKey(categoryNames: Array<String>, familyName: String) :
    AbstractMap.SimpleImmutableEntry<String?, String?>(categoryNames.joinToString(separator = ":"), interner.intern(familyName)) {
    companion object {
      private val interner = Interner.createWeakInterner<String>()
    }
  }

  // guarded by this
  private val metadataMap: MutableMap<MetaDataKey, IntentionActionMetaData>
  // guarded by this
  private val extensionMapping: MutableMap<IntentionActionBean, MetaDataKey>

  init {
    val size = IntentionManagerImpl.EP_INTENTION_ACTIONS.point.size()
    metadataMap = LinkedHashMap(size)
    extensionMapping = HashMap(size)
    IntentionManagerImpl.EP_INTENTION_ACTIONS.forEachExtensionSafe { registerMetaDataForEp(it) }
    IntentionManagerImpl.EP_INTENTION_ACTIONS.addExtensionPointListener(object : ExtensionPointListener<IntentionActionBean?> {
      override fun extensionAdded(extension: IntentionActionBean, pluginDescriptor: PluginDescriptor) {
        // on each plugin load/unload SearchableOptionsRegistrarImpl drops the cache, so, it will be recomputed later on demand
        registerMetaDataForEp(extension)
        serviceIfCreated<TopHitCache>()?.invalidateCachedOptions(IntentionsOptionsTopHitProvider::class.java)
      }

      override fun extensionRemoved(extension: IntentionActionBean, pluginDescriptor: PluginDescriptor) {
        if (extension.categories == null) {
          return
        }

        unregisterMetaDataForEP(extension)
        serviceIfCreated<TopHitCache>()?.invalidateCachedOptions(IntentionsOptionsTopHitProvider::class.java)
      }
    }, null)
  }

  private fun registerMetaDataForEp(extension: IntentionActionBean) {
    val categories = extension.categories ?: return
    val instance = IntentionActionWrapper(extension)
    val descriptionDirectoryName = extension.getDescriptionDirectoryName() ?: instance.descriptionDirectoryName
    try {
      val metadata = IntentionActionMetaData(instance, extension.loaderForClass, categories, descriptionDirectoryName)
      val key = MetaDataKey(metadata.myCategory, metadata.family)
      synchronized(this) {
        metadataMap.put(key, metadata)
        extensionMapping.put(extension, key)
      }
    }
    catch (ignore: ExtensionNotApplicableException) {
    }
  }

  fun registerIntentionMetaData(intentionAction: IntentionAction,
                                category: Array<String?>,
                                descriptionDirectoryName: String) {
    val classLoader = if (intentionAction is IntentionActionWrapper) {
      intentionAction.implementationClassLoader
    }
    else {
      intentionAction.javaClass.classLoader
    }
    val metadata = IntentionActionMetaData(intentionAction, classLoader, category, descriptionDirectoryName)
    val key = MetaDataKey(metadata.myCategory, metadata.family)
    synchronized(this) {
      // not added as searchable option - this method is deprecated and intentionAction extension point must be used instead
      metadataMap.put(key, metadata)
    }
  }

  @Synchronized
  fun getMetaData(): List<IntentionActionMetaData> {
    return java.util.List.copyOf(metadataMap.values)
  }

  @Synchronized
  fun unregisterMetaData(intentionAction: IntentionAction) {
    for ((key, value) in metadataMap) {
      if (value.action === intentionAction) {
        metadataMap.remove(key)
        break
      }
    }
  }

  @Synchronized
  private fun unregisterMetaDataForEP(extension: IntentionActionBean) {
    extensionMapping.remove(extension)?.let { key ->
      metadataMap.remove(key)
    }
  }

  companion object {
    @JvmStatic
    fun getInstance(): IntentionsMetadataService = service()
  }
}