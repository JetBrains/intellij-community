// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl.config

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.IntentionActionBean
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.util.containers.Interner

@Service(Service.Level.APP)
internal class IntentionsMetadataService {
  companion object {
    @JvmStatic
    fun getInstance(): IntentionsMetadataService = service()

    private val interner = Interner.createWeakInterner<String>()
  }

  // guarded by this
  private val extensionMetaMap: MutableMap<IntentionActionBean, IntentionActionMetaData> =
    LinkedHashMap(IntentionManagerImpl.EP_INTENTION_ACTIONS.point.size())

  // guarded by this, used only for legacy programmatically registered intentions
  private val dynamicRegistrationMeta: MutableList<IntentionActionMetaData> = ArrayList()

  init {
    IntentionManagerImpl.EP_INTENTION_ACTIONS.forEachExtensionSafe { registerMetaDataForEp(it) }
    IntentionManagerImpl.EP_INTENTION_ACTIONS.addExtensionPointListener(object : ExtensionPointListener<IntentionActionBean> {
      override fun extensionAdded(extension: IntentionActionBean, pluginDescriptor: PluginDescriptor) {
        // on each plugin load/unload SearchableOptionsRegistrarImpl drops the cache, so, it will be recomputed later on demand
        registerMetaDataForEp(extension)
      }

      override fun extensionRemoved(extension: IntentionActionBean, pluginDescriptor: PluginDescriptor) {
        if (extension.categories == null) {
          return
        }

        unregisterMetaDataForEP(extension)
      }
    }, null)
  }

  private fun registerMetaDataForEp(extension: IntentionActionBean) {
    val categories = extension.categories ?: return
    val instance = IntentionActionWrapper(extension)
    val descriptionDirectoryName = extension.getDescriptionDirectoryName() ?: instance.descriptionDirectoryName
    val metadata = try {
      IntentionActionMetaData(instance, extension.loaderForClass, categories, descriptionDirectoryName, extension.skipBeforeAfter)
    }
    catch (ignore: ExtensionNotApplicableException) {
      return
    }

    synchronized(this) {
      extensionMetaMap.put(extension, metadata)
    }
  }

  @Deprecated("Use intentionAction extension point instead")
  fun registerIntentionMetaData(intentionAction: IntentionAction,
                                category: Array<String?>,
                                descriptionDirectoryName: String) {
    val classLoader = if (intentionAction is IntentionActionWrapper) {
      intentionAction.implementationClassLoader
    }
    else {
      intentionAction.javaClass.classLoader
    }
    val metadata = IntentionActionMetaData(intentionAction, classLoader, category, descriptionDirectoryName, false)
    synchronized(this) {
      // not added as searchable option - this method is deprecated and intentionAction extension point must be used instead
      dynamicRegistrationMeta.add(metadata)
    }
  }

  @Synchronized
  fun getMetaData(): List<IntentionActionMetaData> {
    if (dynamicRegistrationMeta.isEmpty()) {
      return java.util.List.copyOf(extensionMetaMap.values)
    }
    else {
      return java.util.List.copyOf(extensionMetaMap.values + dynamicRegistrationMeta)
    }
  }

  fun getUniqueMetadata(): List<IntentionActionMetaData> {
    val allIntentions = getMetaData()
    val unique = HashSet<Pair<List<String>, String>>(allIntentions.size)
    val result = ArrayList<IntentionActionMetaData>(allIntentions.size)
    for (metadata in allIntentions) {
      val key = try {
        metadata.myCategory.asList() to metadata.family
      }
      catch (_: ExtensionNotApplicableException) {
        continue
      }
      if (unique.add(key)) {
        result.add(metadata)
      }
    }
    return result
  }

  @Synchronized
  fun unregisterMetaData(intentionAction: IntentionAction) {
    dynamicRegistrationMeta.removeIf { meta ->
      meta.action === intentionAction
    }
  }

  @Synchronized
  private fun unregisterMetaDataForEP(extension: IntentionActionBean) {
    extensionMetaMap.remove(extension)
  }
}