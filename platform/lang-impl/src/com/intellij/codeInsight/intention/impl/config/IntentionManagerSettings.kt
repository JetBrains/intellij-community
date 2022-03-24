// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("unused", "ReplacePutWithAssignment")

package com.intellij.codeInsight.intention.impl.config

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.IntentionActionBean
import com.intellij.ide.ui.TopHitCache
import com.intellij.ide.ui.search.SearchableOptionContributor
import com.intellij.ide.ui.search.SearchableOptionProcessor
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.util.containers.Interner
import org.jdom.Element
import java.io.IOException
import java.util.*
import java.util.regex.Pattern

private const val IGNORE_ACTION_TAG = "ignoreAction"
private const val NAME_ATT = "name"

@State(name = "IntentionManagerSettings", storages = [Storage("intentionSettings.xml")], category = SettingsCategory.CODE)
class IntentionManagerSettings : PersistentStateComponent<Element> {
  companion object {
    @JvmStatic
    fun getInstance() = service<IntentionManagerSettings>()
  }

  private class MetaDataKey(categoryNames: Array<String>, familyName: String) :
    AbstractMap.SimpleImmutableEntry<String?, String?>(categoryNames.joinToString(separator = ":"), interner.intern(familyName)) {
    companion object {
      private val interner = Interner.createWeakInterner<String>()
    }
  }

  @Volatile
  private var ignoredActions = emptySet<String>()
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

  fun isShowLightBulb(action: IntentionAction) = !ignoredActions.contains(action.familyName)

  override fun loadState(element: Element) {
    val children = element.getChildren(IGNORE_ACTION_TAG)
    val ignoredActions = LinkedHashSet<String>(children.size)
    for (e in children) {
      ignoredActions.add(e.getAttributeValue(NAME_ATT)!!)
    }
    this.ignoredActions = ignoredActions
  }

  override fun getState(): Element {
    val element = Element("state")
    for (name in ignoredActions) {
      element.addContent(Element(IGNORE_ACTION_TAG).setAttribute(NAME_ATT, name))
    }
    return element
  }

  @Synchronized
  fun getMetaData(): List<IntentionActionMetaData> = java.util.List.copyOf(metadataMap.values)

  fun isEnabled(metaData: IntentionActionMetaData) = !ignoredActions.contains(getFamilyName(metaData))

  fun setEnabled(metaData: IntentionActionMetaData, enabled: Boolean) {
    ignoredActions = if (enabled) ignoredActions - getFamilyName(metaData) else ignoredActions + getFamilyName(metaData)
  }

  fun isEnabled(action: IntentionAction): Boolean {
    val familyName = try {
      getFamilyName(action)
    }
    catch (ignored: ExtensionNotApplicableException) {
      return false
    }
    return !ignoredActions.contains(familyName)
  }

  fun setEnabled(action: IntentionAction, enabled: Boolean) {
    ignoredActions = if (enabled) ignoredActions - getFamilyName(action) else ignoredActions + getFamilyName(action)
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

  private class IntentionSearchableOptionContributor : SearchableOptionContributor() {
    companion object {
      private val HTML_PATTERN = Pattern.compile("<[^<>]*>")
    }

    override fun processOptions(processor: SearchableOptionProcessor) {
      for (metaData in getInstance().getMetaData()) {
        try {
          val descriptionText = HTML_PATTERN.matcher(metaData.description.text.lowercase(Locale.ENGLISH)).replaceAll(" ")
          val displayName = IntentionSettingsConfigurable.getDisplayNameText()
          val configurableId = IntentionSettingsConfigurable.HELP_ID
          val family = metaData.family
          processor.addOptions(descriptionText, family, family, configurableId, displayName, false)
          processor.addOptions(family, family, family, configurableId, displayName, true)
        }
        catch (e: IOException) {
          logger<IntentionManagerSettings>().error(e)
        }
      }
    }
  }
}

private fun getFamilyName(metaData: IntentionActionMetaData): String {
  val joiner = StringJoiner("/")
  for (category in metaData.myCategory) {
    joiner.add(category)
  }
  joiner.add(metaData.action.familyName)
  return joiner.toString()
}

private fun getFamilyName(action: IntentionAction): String {
  return if (action is IntentionActionWrapper) action.fullFamilyName else action.familyName
}

