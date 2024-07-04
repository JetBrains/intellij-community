// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.multiverse

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.Nls
import javax.swing.Icon

internal class ModuleEntityContextPresentationProvider : CodeInsightContextPresentationProvider<ModuleContext> {
  override fun isApplicable(context: CodeInsightContext): Boolean = context is ModuleContext

  override fun getIcon(context: ModuleContext, project: Project): Icon = AllIcons.Nodes.Module

  override fun getPresentableText(context: ModuleContext, project: Project): @Nls String {
    return context.getModule()?.name ?: ""
  }
}