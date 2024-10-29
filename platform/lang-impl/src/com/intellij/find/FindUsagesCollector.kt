// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.find

import com.intellij.find.impl.FindPopupPanel
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventId1
import com.intellij.internal.statistic.eventLog.events.EventId3
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil

internal object FindUsagesCollector : CounterUsagesCollector() {

  private val GROUP = EventLogGroup("find", 6)

  const val FIND_IN_FILE: String = "FindInFile"
  const val FIND_IN_PATH: String = "FindInPath"
  const val UNKNOWN: String = "Unknown"

  private val REGEXP_HELP_CLICKED = GROUP.registerEvent("regexp.help.clicked",
                                                        EventFields.String("type", listOf(FIND_IN_FILE, UNKNOWN)))
  private val CASE_SENSITIVE = EventFields.Boolean("case_sensitive")
  private val WHOLE_WORDS_ONLY = EventFields.Boolean("whole_words_only")
  private val REGULAR_EXPRESSIONS = EventFields.Boolean("regular_expressions")
  private val WITH_FILE_FILTER = EventFields.Boolean("with_file_filter")
  private val CONTEXT = EventFields.Enum("context", FindModel.SearchContext::class.java)
  private val TYPE = EventFields.String("type", listOf(FIND_IN_FILE, FIND_IN_PATH))
  private val SEARCH_SESSION_STARTED = GROUP.registerVarargEvent("search.session.started",
                                                                 TYPE,
                                                                 CASE_SENSITIVE,
                                                                 WHOLE_WORDS_ONLY,
                                                                 REGULAR_EXPRESSIONS,
                                                                 WITH_FILE_FILTER,
                                                                 CONTEXT
  )

  private val OPTION_VALUE = EventFields.Boolean("option_value")

  @JvmField
  val CHECK_BOX_TOGGLED: EventId3<String?, FindPopupPanel.ToggleOptionName, Boolean> = GROUP.registerEvent("check.box.toggled",
                                                                                                           EventFields.String("type",
                                                                                                                              listOf(
                                                                                                                                FIND_IN_PATH)),
                                                                                                           EventFields.Enum("option_name",
                                                                                                                            FindPopupPanel.ToggleOptionName::class.java),
                                                                                                           OPTION_VALUE
  )

  @JvmField
  val PIN_TOGGLED: EventId1<Boolean> = GROUP.registerEvent("pin.toggled", OPTION_VALUE)

  @JvmStatic
  fun triggerUsedOptionsStats(project: Project?,
                              type: String,
                              model: FindModel) {
    SEARCH_SESSION_STARTED.log(project,
                               TYPE.with(type),
                               CASE_SENSITIVE.with(model.isCaseSensitive),
                               WHOLE_WORDS_ONLY.with(model.isWholeWordsOnly),
                               REGULAR_EXPRESSIONS.with(model.isRegularExpressions),
                               WITH_FILE_FILTER.with(model.fileFilter != null),
                               CONTEXT.with(model.searchContext)
    )
  }

  @JvmStatic
  fun triggerRegexHelpClicked(type: String?) {
    REGEXP_HELP_CLICKED.log(StringUtil.notNullize(type, UNKNOWN))
  }

  override fun getGroup(): EventLogGroup = GROUP
}