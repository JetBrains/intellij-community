// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.navigation.actions

import com.intellij.codeInsight.navigation.actions.GotoDeclarationReporter.DeclarationsFound
import com.intellij.codeInsight.navigation.actions.GotoDeclarationReporter.NavigationType
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.ClassEventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventFields.createDurationField
import com.intellij.internal.statistic.eventLog.events.EventId1
import com.intellij.internal.statistic.eventLog.events.EventId2
import com.intellij.internal.statistic.eventLog.events.EventId3
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import org.jetbrains.annotations.ApiStatus.Internal
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.DurationUnit

@Internal
internal interface GotoDeclarationReporter {
  enum class DeclarationsFound { NONE, SINGLE, MULTIPLE }
  enum class NavigationType { AUTO, FROM_POPUP }

  /**
   * This method is designed to be called after the whole search for declarations and (depending on context) usages is done.
   *
   * It seems reasonable to call it after no more declarations were found, and we skip to usages,
   * but it's not how the current implementation works.
   *
   * See [com.intellij.codeInsight.navigation.impl.gotoDeclarationOrUsages]:
   * first for injected code direct navigation is checked, then referenced and declared data is calculated,
   * then the referenced data is used for declarations, then IDE searches for usages.
   * Then the same algorithm is repeated for the host.
   * Once something is found, it's returned as a result.
   *
   * Related code ([com.intellij.codeInsight.navigation.impl.TargetGTDActionData.result]) uses the whole collected data,
   * so enforcing strict separation and order between declarations related data and usages related data is unreasonable.
   */
  fun reportDeclarationSearchFinished(declarationsFound: DeclarationsFound)
  fun reportLookupElementsShown()
  fun reportNavigatedToDeclaration(navigationType: NavigationType, navigationProvider: Any?)
}

@Internal
internal class GotoDeclarationFUSReporter : GotoDeclarationReporter {
  private val start = System.currentTimeMillis()

  private fun getDuration(): Duration {
    return (System.currentTimeMillis() - start).milliseconds
  }

  override fun reportDeclarationSearchFinished(declarationsFound: DeclarationsFound) {
    DECLARATION_SEARCH_FINISHED.log(getDuration(), declarationsFound)
  }

  override fun reportLookupElementsShown() {
    LOOKUP_ELEMENTS_SHOWN_EVENT.log(getDuration())
  }

  override fun reportNavigatedToDeclaration(navigationType: NavigationType, navigationProvider: Any?) {
    NAVIGATED_TO_DECLARATION_EVENT.log(getDuration(), navigationType, navigationProvider?.javaClass)
  }
}

private val GROUP = EventLogGroup("go.to.declaration", 1)

private val DURATION_FIELD = createDurationField(DurationUnit.MILLISECONDS, "duration_ms")
private val DECLARATIONS_FOUND_FIELD = EventFields.Enum<DeclarationsFound>("declarations_found")
private val DECLARATION_SEARCH_FINISHED: EventId2<Duration, DeclarationsFound> =
  GROUP.registerEvent("declaration.search.finished", DURATION_FIELD, DECLARATIONS_FOUND_FIELD)
private val LOOKUP_ELEMENTS_SHOWN_EVENT: EventId1<Duration> = GROUP.registerEvent("lookup.elements.shown", DURATION_FIELD)
private val NAVIGATION_TYPE_FIELD = EventFields.Enum<NavigationType>("navigation_type")
private val NAVIGATION_PROVIDER_CLASS_FIELD: ClassEventField = EventFields.Class("navigation_provider_class")
private val NAVIGATED_TO_DECLARATION_EVENT: EventId3<Duration, NavigationType, Class<*>?> =
  GROUP.registerEvent("navigated.to.declaration", DURATION_FIELD, NAVIGATION_TYPE_FIELD, NAVIGATION_PROVIDER_CLASS_FIELD)

internal class GoToDeclarationCollector : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup {
    return GROUP
  }
}