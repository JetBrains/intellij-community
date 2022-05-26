// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.suggested

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector


internal class SuggestedRefactoringFeatureUsage : CounterUsagesCollector() {

  @Suppress("HardCodedStringLiteral")
  companion object {
    private val GROUP = EventLogGroup("suggested.refactorings", 2)

    private var lastFeatureUsageIdLogged: Int? = null

    private const val RENAME = "rename."
    private const val CHANGE_SIGNATURE = "changeSignature."
    private const val SUGGESTED = "suggested"
    const val POPUP_SHOWN = "popup.shown"
    const val POPUP_CANCELED = "popup.canceled"
    const val PERFORMED = "performed"

    private val PLACE = EventFields.ActionPlace
    private val ID = EventFields.Int("id")
    private val LANGUAGE = EventFields.Language
    private val CLASS = EventFields.Class("declaration_type")

    private val RENAME_SUGGESTED = GROUP.registerVarargEvent(RENAME + SUGGESTED, PLACE, ID, LANGUAGE, CLASS)
    private val RENAME_POPUP_SHOWN = GROUP.registerVarargEvent(RENAME + POPUP_SHOWN, PLACE, ID, LANGUAGE, CLASS)
    private val RENAME_POPUP_CANCELED = GROUP.registerVarargEvent(RENAME + POPUP_CANCELED, PLACE, ID, LANGUAGE, CLASS)
    private val RENAME_PERFORMED = GROUP.registerVarargEvent(RENAME + PERFORMED, PLACE, ID, LANGUAGE, CLASS)

    private val CHANGE_SIGNATURE_SUGGESTED = GROUP.registerVarargEvent(CHANGE_SIGNATURE + SUGGESTED, PLACE, ID, LANGUAGE, CLASS)
    private val CHANGE_SIGNATURE_POPUP_SHOWN = GROUP.registerVarargEvent(CHANGE_SIGNATURE + POPUP_SHOWN, PLACE, ID, LANGUAGE, CLASS)
    private val CHANGE_SIGNATURE_POPUP_CANCELED = GROUP.registerVarargEvent(CHANGE_SIGNATURE + POPUP_CANCELED, PLACE, ID, LANGUAGE, CLASS)
    private val CHANGE_SIGNATURE_PERFORMED = GROUP.registerVarargEvent(CHANGE_SIGNATURE + PERFORMED, PLACE, ID, LANGUAGE, CLASS)


    private val eventsMap by lazy {
      listOf(RENAME_SUGGESTED, RENAME_POPUP_SHOWN,
             RENAME_POPUP_CANCELED, RENAME_PERFORMED,
             CHANGE_SIGNATURE_SUGGESTED, CHANGE_SIGNATURE_POPUP_SHOWN,
             CHANGE_SIGNATURE_POPUP_CANCELED, CHANGE_SIGNATURE_PERFORMED).associateBy { it.eventId }
    }

    fun logEvent(
      eventIdSuffix: String,
      refactoringData: SuggestedRefactoringData,
      state: SuggestedRefactoringState,
      actionPlace: String?
    ) {
      val eventIdPrefix = when (refactoringData) {
        is SuggestedRenameData -> RENAME
        is SuggestedChangeSignatureData -> CHANGE_SIGNATURE
      }
      val event = eventsMap[eventIdPrefix + eventIdSuffix]
      event?.log(refactoringData.declaration.project,
                 PLACE.with(actionPlace),
                 ID.with(state.featureUsageId),
                 LANGUAGE.with(refactoringData.declaration.language),
                 CLASS.with(refactoringData.declaration.javaClass))
    }

    fun refactoringSuggested(refactoringData: SuggestedRefactoringData, state: SuggestedRefactoringState) {
      if (state.featureUsageId != lastFeatureUsageIdLogged) {
        lastFeatureUsageIdLogged = state.featureUsageId
        logEvent(SUGGESTED, refactoringData, state, null)
      }
    }
  }

  override fun getGroup(): EventLogGroup = GROUP
}