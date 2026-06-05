// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.command

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.template.postfix.completion.PostfixTemplateLookupElement
import com.intellij.openapi.util.Key
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object RemDevCommandCompletionHelpers {
  enum class CommandState {
    Postfix,
    Command,
  }

  private val COMMAND_FLAG: Key<CommandState> = Key("COMMAND_FLAG")

  fun LookupElement.isPostfix(): Boolean =
    `as`(PostfixTemplateLookupElement::class.java) != null ||
    getUserData(COMMAND_FLAG) == CommandState.Postfix

  fun LookupElement.isCommand(): Boolean =
    `as`(CommandCompletionLookupElement::class.java) != null ||
    getUserData(COMMAND_FLAG) == CommandState.Command

  fun LookupElement.getCommandState(): CommandState? {
    val flag = getUserData(COMMAND_FLAG)
    return when {
      flag != null -> flag
      isCommand() -> CommandState.Command
      isPostfix() -> CommandState.Postfix
      else -> null
    }
  }

  fun LookupElement.installCommandState(commandState: CommandState?) {
    putUserData(COMMAND_FLAG, commandState)
  }
}