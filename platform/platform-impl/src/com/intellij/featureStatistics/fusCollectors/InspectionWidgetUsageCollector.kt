package com.intellij.featureStatistics.fusCollectors

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventFields.Enum
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.lang.Language
import com.intellij.openapi.editor.markup.InspectionsLevel
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object InspectionWidgetUsageCollector : CounterUsagesCollector() {
  private val GROUP = EventLogGroup("inspection.widget", 4)
  private val HIGHLIGHT_LEVEL_CHANGED = GROUP.registerEvent("highlight.level.changed", EventFields.Language,
                                                            Enum("level", InspectionsLevel::class.java))
  private val HIGHLIGHT_LEVEL_CHANGED_POPUP = GROUP.registerEvent("highlight.level.changed.fromPopup", EventFields.Language,
                                                                  Enum("level", InspectionsLevel::class.java))
  private val INSPECTOR_WIDGET_POPUP_SHOWN = GROUP.registerEvent("popup.shown")

  @JvmStatic
  fun logHighlightLevelChanged(project: Project, language: Language, inspectionsLevel: InspectionsLevel) {
    HIGHLIGHT_LEVEL_CHANGED.log(project, language, inspectionsLevel)
  }

  @JvmStatic
  fun logHighlightLevelChangedFromPopup(project: Project?, language: Language?, inspectionsLevel: InspectionsLevel) {
    HIGHLIGHT_LEVEL_CHANGED_POPUP.log(project, language, inspectionsLevel)
  }

  @JvmStatic
  fun logPopupShown(project: Project?) {
    INSPECTOR_WIDGET_POPUP_SHOWN.log(project)
  }

  override fun getGroup(): EventLogGroup {
    return GROUP
  }
}
