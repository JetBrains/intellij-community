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

  /**
   * class of command, used to classify the most popular commands
   */
  private val CLASS_NAME_COMMAND = EventFields.StringValidatedByCustomRule("class", ClassNameRuleValidator::class.java)

  /**
   * invocation_type - how the completion is invoked, with '.', '..' or with empty line
   */
  private val INVOCATION_TYPE_COMMAND = EventFields.StringValidatedByCustomRule("invocation_type", ClassNameRuleValidator::class.java)

  /**
   * language of a file where completion lookup is shown
   */
  private val LANGUAGE_FILE = EventFields.LanguageById


  /**
   * case when the command completion is shown to users
   */
  private val COMMAND_SHOWN = COUNTER_GROUP.registerVarargEvent("command.completion.shown",
                                                                CLASS_NAME_COMMAND,
                                                                LANGUAGE_FILE,
                                                                INVOCATION_TYPE_COMMAND)

  /**
   * case when the command completion is called
   */
  private val COMMAND_CALLED = COUNTER_GROUP.registerVarargEvent("command.completion.called",
                                                                 CLASS_NAME_COMMAND,
                                                                 LANGUAGE_FILE,
                                                                 INVOCATION_TYPE_COMMAND)


  /**
   * Logs the command shown event with the specified class name, language, and invocation type.
   *
   * @param className The class object representing the name of the class for the command shown event.
   * @param language The language associated with the command shown event.
   * @param invocationType The class object representing the type of invocation for the command shown event ('.', '..', or empty line)
   */
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

  /**
   * Logs the details of a command being called, such as the class name, language, and invocation type.
   *
   * @param className the class representing the command being called.
   * @param language the language context of the command being executed.
   * @param invocationType the type of invocation for the command being called.
   */
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
