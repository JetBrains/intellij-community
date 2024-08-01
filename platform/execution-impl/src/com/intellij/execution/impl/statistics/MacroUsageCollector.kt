// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.impl.statistics

import com.intellij.ide.macro.Macro
import com.intellij.ide.macro.MacroManager
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.validator.rules.impl.ExtensionIdValidationRule
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector

internal object MacroUsageCollector : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP

  private val GROUP = EventLogGroup("execution.macro", 1)

  private val name = EventFields.StringValidatedByCustomRule("name", MacroNameValidationRule::class.java)

  private val macroExpanded = GROUP.registerEvent("macro.expanded", name, EventFields.Boolean("success"))

  @JvmStatic
  fun logMacroExpanded(macro: Macro, success: Boolean) {
    macroExpanded.log(macro.name, success)
  }
}

internal class MacroNameValidationRule : ExtensionIdValidationRule<Macro>(Macro.EP_NAME, { macro -> macro.name }) {
  override val extensions: Iterable<Macro>
    get() = MacroManager.getInstance().macros
}