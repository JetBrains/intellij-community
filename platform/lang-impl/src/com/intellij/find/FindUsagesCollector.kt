// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.find

import com.intellij.find.impl.FindPopupPanel
import com.intellij.find.impl.FindPopupScopeUI
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventId1
import com.intellij.internal.statistic.eventLog.events.EventId3
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.wm.ToolWindowId

internal object FindUsagesCollector : CounterUsagesCollector() {

  private val GROUP = EventLogGroup("find", 8)

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
  private val SELECTED_SEARCH_SCOPE = EventFields.Enum("selected_scope", SelectedSearchScope::class.java)
  private val SEARCH_SESSION_STARTED = GROUP.registerVarargEvent("search.session.started",
                                                                 TYPE,
                                                                 CASE_SENSITIVE,
                                                                 WHOLE_WORDS_ONLY,
                                                                 REGULAR_EXPRESSIONS,
                                                                 WITH_FILE_FILTER,
                                                                 CONTEXT,
                                                                 SELECTED_SEARCH_SCOPE
  )
  private val SEARCH_SCOPE_CHANGED = GROUP.registerEvent("search.scope.changed", SELECTED_SEARCH_SCOPE)

  private val OPTION_VALUE = EventFields.Boolean("option_value")

  private val NOTHING_FOUND_SHOWN = GROUP.registerEvent("nothing.found.shown", EventFields.Boolean("is_filters_applied"))

  private val TOOL_WINDOW_ACTIVE = EventFields.String("active_tool_window_id", ToolWindowId.TOOL_WINDOW_IDS.plus(UNKNOWN).toList())

  private enum class ContextElement {
    EDITOR, TOOLWINDOW, STAUS_BAR, UNKNOWN;
  }

  private enum class PredefinedText {
    EMPTY, FROM_PREVIOUS_SEARCH, FROM_SELECTION;
  }

  private val CONTEXT_ELEMENT = EventFields.Enum("context_element", ContextElement::class.java)
  private val PREDEFINED_TEXT_FIELD = EventFields.Enum("prefilled_text_field", PredefinedText::class.java)
  private val MODE = EventFields.Enum("mode", FindReplaceMode::class.java)
  private val FIND_POPUP_SHOWN = GROUP.registerVarargEvent("find.popup.shown", CONTEXT_ELEMENT, TOOL_WINDOW_ACTIVE, PREDEFINED_TEXT_FIELD, MODE)

  private val TIME_TO_FIRST_RESULT_MS = GROUP.registerEvent("time.to.first.result.ms", EventFields.Long("time_ms"))
  private val SEARCH_FINISHED = GROUP.registerEvent("search.finished", EventFields.Long("time_ms"), EventFields.Int("results_count"), EventFields.Int("count_limit"))

  private enum class FindReplaceMode {
    FIND, REPLACE
  }

  private val SEARCH_HISTORY_SHOWN = GROUP.registerEvent("search.history.shown", EventFields.Enum("source", FindReplaceMode::class.java))
  private val SEARCH_HISTORY_ITEM_SELECTED = GROUP.registerEvent("search.history.item.selected", EventFields.Enum("source", FindReplaceMode::class.java))

  private val REPLACE_ALL_INVOKED = GROUP.registerEvent("replace.all.invoked")
  private val REPLACE_ONE_INVOKED = GROUP.registerEvent("replace.one.invoked")

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
  fun findPopupShown(dataContext: DataContext, findModel: FindModel, stringToFindChanged: Boolean) {
    var toolWindowId = PlatformDataKeys.TOOL_WINDOW.getData(dataContext)?.id
    val contextElement = when {
      toolWindowId != null -> {
        if (!ToolWindowId.TOOL_WINDOW_IDS.contains(toolWindowId)) toolWindowId = UNKNOWN
        ContextElement.TOOLWINDOW
      }
      CommonDataKeys.EDITOR.getData(dataContext) != null -> ContextElement.EDITOR
      PlatformDataKeys.STATUS_BAR.getData(dataContext) != null -> ContextElement.STAUS_BAR
      else -> ContextElement.UNKNOWN
    }

    val predefinedText = when {
      findModel.stringToFind.isEmpty() -> PredefinedText.EMPTY
      stringToFindChanged -> PredefinedText.FROM_SELECTION
      else -> PredefinedText.FROM_PREVIOUS_SEARCH
    }
    val data = mutableListOf<EventPair<*>>()
    data.add(CONTEXT_ELEMENT.with(contextElement))
    toolWindowId?.let { data.add(TOOL_WINDOW_ACTIVE.with(it)) }
    data.add(PREDEFINED_TEXT_FIELD.with(predefinedText))
    data.add(MODE.with(if (findModel.isReplaceState) FindReplaceMode.REPLACE else FindReplaceMode.FIND))

    FIND_POPUP_SHOWN.log(data)
  }

  @JvmStatic
  fun recordFirstResultTime(time: Long) {
    TIME_TO_FIRST_RESULT_MS.log(time)
  }

  @JvmStatic
  fun recordSearchFinished(time: Long, resultsCount: Int, countLimit: Int) {
    SEARCH_FINISHED.log(time, resultsCount, countLimit)
  }

  @JvmStatic
  @JvmOverloads
  fun triggerUsedOptionsStats(project: Project?,
                              type: String,
                              model: FindModel,
                              scopeType: FindPopupScopeUI.ScopeType? = null) {
    SEARCH_SESSION_STARTED.log(project,
                               TYPE.with(type),
                               CASE_SENSITIVE.with(model.isCaseSensitive),
                               WHOLE_WORDS_ONLY.with(model.isWholeWordsOnly),
                               REGULAR_EXPRESSIONS.with(model.isRegularExpressions),
                               WITH_FILE_FILTER.with(model.fileFilter != null),
                               CONTEXT.with(model.searchContext),
                               SELECTED_SEARCH_SCOPE.with(SelectedSearchScope.getByScopeType(scopeType))
    )
  }

  @JvmStatic
  fun triggerRegexHelpClicked(type: String?) {
    REGEXP_HELP_CLICKED.log(StringUtil.notNullize(type, UNKNOWN))
  }

  @JvmStatic
  fun triggerScopeSelected(scopeType: FindPopupScopeUI.ScopeType?) {
    SEARCH_SCOPE_CHANGED.log(SelectedSearchScope.getByScopeType(scopeType))
  }

  @JvmStatic
  fun recordNothingFoundShown(isFiltersApplied: Boolean) {
    NOTHING_FOUND_SHOWN.log(isFiltersApplied)
  }

  @JvmStatic
  fun searchHistoryShown(isFind: Boolean) {
    SEARCH_HISTORY_SHOWN.log(if (isFind) FindReplaceMode.FIND else FindReplaceMode.REPLACE)
  }

  @JvmStatic
  fun searchHistoryItemSelected(isFind: Boolean) {
    SEARCH_HISTORY_ITEM_SELECTED.log(if (isFind) FindReplaceMode.FIND else FindReplaceMode.REPLACE)
  }

  @JvmStatic
  fun replaceAllInvoked() {
    REPLACE_ALL_INVOKED.log()
  }

  @JvmStatic
  fun replaceOneInvoked() {
    REPLACE_ONE_INVOKED.log()
  }

  override fun getGroup(): EventLogGroup = GROUP
}

internal enum class SelectedSearchScope { Project, Module, Directory, Scopes, Other, Undefined;

companion object {
  fun getByScopeType(scopeType: FindPopupScopeUI.ScopeType?): SelectedSearchScope = when (scopeType?.name) {
    FindPopupScopeUI.PROJECT_SCOPE_NAME -> Project
    FindPopupScopeUI.MODULE_SCOPE_NAME -> Module
    FindPopupScopeUI.DIRECTORY_SCOPE_NAME -> Directory
    FindPopupScopeUI.CUSTOM_SCOPE_SCOPE_NAME -> Scopes
    null -> Undefined
    else -> Other
  }
}
}