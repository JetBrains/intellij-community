// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.commands.impl

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.completion.command.CommandCompletionProviderContext
import com.intellij.codeInsight.completion.command.CompletionCommand
import com.intellij.codeInsight.completion.command.commands.AbstractGenerateCommandProvider
import com.intellij.idea.ActionsBundle
import com.intellij.java.JavaBundle
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiIdentifier

internal class JavaGenerateCommandCompletion : AbstractGenerateCommandProvider() {
  private val renaming = mapOf<String, Pair<String, String>>(
    Pair(ActionsBundle.message("action.GenerateGetter.text"), Pair("Getters", JavaBundle.message("command.completion.getters.text"))),
    Pair(ActionsBundle.message("action.GenerateSetter.text"), Pair("Setters", JavaBundle.message("command.completion.setters.text"))),
    Pair(ActionsBundle.message("action.GenerateGetterAndSetter.text"), Pair("Getters and Setters", JavaBundle.message("command.completion.getters.and.setters.text")))
  )

  override fun generationIsAvailable(element: PsiElement, offset: Int): Boolean {
    val parent = element.parent
    if (parent !is PsiClass) return false
    if (!isOnlySpaceInLine(element.containingFile.fileDocument, offset) &&
        !(element is PsiIdentifier && parent.nameIdentifier == element)) return false
    return true
  }

  override fun getCommands(context: CommandCompletionProviderContext): List<CompletionCommand> {
    val commands = super.getCommands(context)
    for (command in commands) {
      if (command is GenerateCompletionCommand) {
        val newName = renaming[command.action.templateText]
        if (newName != null) {
          command.customName = "Generate \'${newName.first}\'"
          command.customI18nName = CodeInsightBundle.message("command.completion.generate.text", newName.second)
        }
      }
    }
    return commands
  }
}