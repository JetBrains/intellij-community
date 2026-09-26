// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix

import com.intellij.codeInspection.util.IntentionName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModCommand
import com.intellij.modcommand.ModCommandAction
import com.intellij.modcommand.Presentation
import com.intellij.openapi.module.Module
import org.jetbrains.annotations.NonNls

public abstract class CompilerOptionFix(private val module: Module) : ModCommandAction {
  @NonNls
  override fun getFamilyName(): String = "Fix compiler option" // not visible

  override fun getPresentation(context: ActionContext): Presentation? {
    if (module.isDisposed) return null
    return Presentation.of(getText())
  }

  override fun perform(context: ActionContext): ModCommand {
    return ModCommand.updateOptionList(context.file, "JavaCompilerConfiguration.additionalOptions", ::update)
  }

  protected abstract fun update(options: MutableList<String>)

  @IntentionName
  protected abstract fun getText(): String
}