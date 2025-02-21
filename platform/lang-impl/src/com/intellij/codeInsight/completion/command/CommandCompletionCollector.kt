// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.command

import com.intellij.internal.statistic.collectors.fus.ClassNameRuleValidator
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.lang.Language

internal object CommandCompletionCollector : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = COUNTER_GROUP

  private val COUNTER_GROUP = EventLogGroup("command.completion", 1)
  private val CLASS_NAME_COMMAND = EventFields.StringValidatedByCustomRule("class", ClassNameRuleValidator::class.java)
  private val INVOCATION_TYPE_COMMAND = EventFields.StringValidatedByCustomRule("invocation_type", ClassNameRuleValidator::class.java)
  private val LANGUAGE_FILE = EventFields.LanguageById


  private val COMMAND_SHOWN = COUNTER_GROUP.registerVarargEvent("command.completion.shown",
                                                                CLASS_NAME_COMMAND,
                                                                LANGUAGE_FILE,
                                                                INVOCATION_TYPE_COMMAND)

  private val COMMAND_CALLED = COUNTER_GROUP.registerVarargEvent("command.completion.called",
                                                                 CLASS_NAME_COMMAND,
                                                                 LANGUAGE_FILE,
                                                                 INVOCATION_TYPE_COMMAND)


  @JvmStatic
  fun shown(
    className: Class<*>,
    language: Language,
    invocationType: Class<*>,
  ) {
    COMMAND_SHOWN.log(
      CLASS_NAME_COMMAND.with(className.name),
      LANGUAGE_FILE.with(language.id),
      INVOCATION_TYPE_COMMAND.with(invocationType.name),
    )
  }

  @JvmStatic
  fun called(
    className: Class<*>,
    language: Language,
    invocationType: Class<*>,
  ) {
    COMMAND_CALLED.log(
      CLASS_NAME_COMMAND.with(className.name),
      LANGUAGE_FILE.with(language.id),
      INVOCATION_TYPE_COMMAND.with(invocationType.name),
    )
  }
}
