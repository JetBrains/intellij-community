// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.find.findUsages

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.eventLog.events.ObjectEventData
import com.intellij.internal.statistic.eventLog.events.PrimitiveEventField
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.project.Project
import com.intellij.psi.search.PredefinedSearchScopeProvider
import com.intellij.psi.search.SearchScope

class FindUsagesStatisticsCollector : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP

  companion object {
    @JvmField
    val GROUP = EventLogGroup("find.usages", 1)

    @JvmField
    val SEARCHABLE_SCOPE_EVENT_FIELD = SearchableScopeField()

    @JvmField
    val SEARCH_FOR_TEXT_OCCURRENCES_FIELD = EventFields.Boolean("isSearchForTextOccurrences")

    @JvmField
    val IS_USAGES_FIELD = EventFields.Boolean("isUsages")

    const val OPTIONS_EVENT_ID = "options"

    @JvmField
    val ADDITIONAL = EventFields.createAdditionalDataField(GROUP.id, OPTIONS_EVENT_ID)

    @JvmField
    val FIND_USAGES_OPTIONS = GROUP.registerVarargEvent(OPTIONS_EVENT_ID, SEARCH_FOR_TEXT_OCCURRENCES_FIELD,
                                                        IS_USAGES_FIELD, SEARCHABLE_SCOPE_EVENT_FIELD, ADDITIONAL)

    @JvmStatic
    fun logOptions(project: Project, options: FindUsagesOptions) {
      val data: MutableList<EventPair<*>> = mutableListOf(SEARCH_FOR_TEXT_OCCURRENCES_FIELD.with(options.isSearchForTextOccurrences),
                                                          IS_USAGES_FIELD.with(options.isUsages)
      )
      if (SearchableScopeField.isPredefinedScope(project, options.searchScope)) {
        data.add(SEARCHABLE_SCOPE_EVENT_FIELD.with(options.searchScope))
      }
      if (options is FusAwareFindUsagesOptions) {
        data.add(ADDITIONAL.with(ObjectEventData(options.additionalUsageData)))
      }
      FIND_USAGES_OPTIONS.log(project, *data.toTypedArray())
    }
  }

  class SearchableScopeField : PrimitiveEventField<SearchScope>() {
    override val name: String
      get() = "searchScope"

    override fun addData(fuData: FeatureUsageData, value: SearchScope) {
      fuData.addData(name, value.displayName)
    }

    override val validationRule: List<String>
      get() = listOf("{enum:All_Places|Project_Files|Project_and_Libraries|Project_Production_Files|Project_Test_Files|" +
                     "Scratches_and_Consoles|Recently_Viewed_Files|Recently_Changed_Files|Open_Files|Current_File]}")

    companion object {
      @JvmStatic
      fun isPredefinedScope(project: Project, scope: SearchScope): Boolean {
        val predefinedScopes = PredefinedSearchScopeProvider.getInstance().getPredefinedScopes(project, null, true, true, false, false,
                                                                                               true)
        return predefinedScopes.contains(scope)
      }
    }
  }

}