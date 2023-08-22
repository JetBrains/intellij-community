// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.extractMethod

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.project.Project

internal object JavaExtractMethodCollector : CounterUsagesCollector() {

  override fun getGroup(): EventLogGroup = GROUP

  val GROUP = EventLogGroup("java.extract.method", 3)

  @JvmField
  val PARAMETERS_COUNT_FIELD = EventFields.Int("parameters_count")

  @JvmField
  val PARAMETERS_TYPE_CHANGED_FIELD = EventFields.Boolean("parameters_type_changed")

  @JvmField
  val PARAMETERS_RENAMED_FIELD = EventFields.Boolean("parameters_renamed")

  @JvmField
  val PARAMETERS_REMOVED_FIELD = EventFields.Boolean("parameters_removed")

  @JvmField
  val PARAMETERS_REORDERED_FIELD = EventFields.Boolean("parameters_reordered")

  @JvmField
  val VISIBILITY_CHANGED_FIELD = EventFields.Boolean("visibility_changed")

  @JvmField
  val RETURN_CHANGED_FIELD = EventFields.Boolean("return_changed")

  @JvmField
  val STATIC_FIELD = EventFields.Boolean("static")

  @JvmField
  val STATIC_PASS_AVAILABLE_FIELD = EventFields.Boolean("static_pass_fields_available")

  @JvmField
  val MAKE_VARARGS_FIELD = EventFields.Boolean("make_varargs")

  @JvmField
  val FOLDED_FIELD = EventFields.Boolean("folded")

  @JvmField
  val CONSTRUCTOR_FIELD = EventFields.Boolean("constructor")

  @JvmField
  val ANNOTATED_FIELD = EventFields.Boolean("annotated")

  @JvmField
  val PREVIEW_USED_FIELD = EventFields.Boolean("preview_used")

  @JvmField
  val FINISHED_FIELD = EventFields.Boolean("finished")

  @JvmField
  val DIALOG_CLOSED_EVENT = GROUP.registerVarargEvent("dialog.closed",
                                                      PARAMETERS_COUNT_FIELD,
                                                      PARAMETERS_TYPE_CHANGED_FIELD,
                                                      PARAMETERS_RENAMED_FIELD,
                                                      PARAMETERS_REMOVED_FIELD,
                                                      PARAMETERS_REORDERED_FIELD,
                                                      VISIBILITY_CHANGED_FIELD,
                                                      RETURN_CHANGED_FIELD,
                                                      STATIC_FIELD,
                                                      STATIC_PASS_AVAILABLE_FIELD,
                                                      MAKE_VARARGS_FIELD,
                                                      FOLDED_FIELD,
                                                      CONSTRUCTOR_FIELD,
                                                      ANNOTATED_FIELD,
                                                      PREVIEW_USED_FIELD,
                                                      FINISHED_FIELD)

  @JvmStatic
  fun logDialogClosed(project: Project?, data: List<EventPair<*>>) = DIALOG_CLOSED_EVENT.log(project, data)

}