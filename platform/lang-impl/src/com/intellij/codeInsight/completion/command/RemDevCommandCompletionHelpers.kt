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

  fun isPostfix(element: LookupElement): Boolean =
    element.`as`(PostfixTemplateLookupElement::class.java) != null ||
    element.getUserData(COMMAND_FLAG) == CommandState.Postfix

  fun isCommand(element: LookupElement): Boolean =
    element.`as`(CommandCompletionLookupElement::class.java) != null ||
    element.getUserData(COMMAND_FLAG) == CommandState.Command

  fun getCommandState(element: LookupElement): CommandState? {
    val flag = element.getUserData(COMMAND_FLAG)
    return when {
        flag != null -> flag
        isCommand(element) -> CommandState.Command
        isPostfix(element) -> CommandState.Postfix
        else -> null
    }
  }

  fun installCommandState(element: LookupElement, commandState: CommandState?) {
    element.putUserData(COMMAND_FLAG, commandState)
  }
}