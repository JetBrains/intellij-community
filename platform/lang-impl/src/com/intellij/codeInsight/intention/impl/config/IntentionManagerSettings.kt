// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("unused", "ReplacePutWithAssignment")

package com.intellij.codeInsight.intention.impl.config

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.ide.ui.search.SearchableOptionContributor
import com.intellij.ide.ui.search.SearchableOptionProcessor
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import kotlinx.coroutines.ensureActive
import org.jdom.Element
import java.io.IOException
import java.util.*
import java.util.regex.Pattern
import kotlin.coroutines.coroutineContext

private const val IGNORE_ACTION_TAG = "ignoreAction"
private const val NAME_ATT = "name"

@State(name = "IntentionManagerSettings", storages = [Storage("intentionSettings.xml")], category = SettingsCategory.CODE)
class IntentionManagerSettings : PersistentStateComponent<Element> {
  companion object {
    @JvmStatic
    fun getInstance(): IntentionManagerSettings = service<IntentionManagerSettings>()
  }

  @Volatile
  private var ignoredActions = emptySet<String>()

  fun isShowLightBulb(action: IntentionAction): Boolean = !ignoredActions.contains(action.familyName)

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

  fun getMetaData(): List<IntentionActionMetaData> {
    return IntentionsMetadataService.getInstance().getUniqueMetadata()
  }

  fun isEnabled(metaData: IntentionActionMetaData): Boolean = !ignoredActions.contains(getFamilyName(metaData))

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

  fun unregisterMetaData(intentionAction: IntentionAction) {
    IntentionsMetadataService.getInstance().unregisterMetaData(intentionAction)
  }
}

private class IntentionSearchableOptionContributor : SearchableOptionContributor() {
  private val HTML_PATTERN = Pattern.compile("<[^<>]*>")

  override suspend fun contribute(processor: SearchableOptionProcessor) {
    for (metaData in serviceAsync<IntentionsMetadataService>().getUniqueMetadata()) {
      coroutineContext.ensureActive()

      try {
        val descriptionText = HTML_PATTERN.matcher(metaData.description.getText().lowercase(Locale.ENGLISH)).replaceAll(" ")
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

