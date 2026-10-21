// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Internal

package com.intellij.ide.actions

import com.intellij.internal.statistic.collectors.fus.actions.persistence.ActionsEventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.service.fus.collectors.FeatureUsageCollectorExtension
import com.intellij.lang.Language
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileTypes.PlainTextLanguage
import org.jetbrains.annotations.ApiStatus
import java.util.Collections
import java.util.WeakHashMap

private val CREATED_LANG = EventFields.Language("created_lang")

/**
 * Adds the language of the created Home file to `actions/action.finished`.
 * The `lang` field of the event reports the language of the open editor, so it cannot show the type of the new file.
 */
internal class WelcomeNewFileFusEventFields : FeatureUsageCollectorExtension {
  override fun getGroupId(): String = ActionsEventLogGroup.GROUP.id

  override fun getEventId(): String = ActionsEventLogGroup.ACTION_FINISHED_EVENT_ID

  override fun getExtensionFields(): List<EventField<*>> = listOf(CREATED_LANG)
}

private val createdLanguages: MutableMap<AnActionEvent, Language> = Collections.synchronizedMap(WeakHashMap())

fun rememberWelcomeNewFile(event: AnActionEvent, language: Language?) {
  createdLanguages[event] = language ?: PlainTextLanguage.INSTANCE
}

/**
 * Returns the `actions/action.finished` data of [event]. The list is empty when [event] created no file.
 */
fun welcomeNewFileUsageData(event: AnActionEvent): List<EventPair<*>> {
  val language = createdLanguages.remove(event) ?: return emptyList()
  return listOf(CREATED_LANG.with(language))
}
