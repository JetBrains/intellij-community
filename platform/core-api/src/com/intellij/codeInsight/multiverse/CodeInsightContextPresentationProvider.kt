// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.multiverse;

import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import javax.swing.Icon

// todo move to some ui module? intellij.platform.ide.core?

/**
 * Provides presentation for [CodeInsightContext]
 *
 * Extension point `com.intellij.multiverse.codeInsightContextPresentationProvider`
 */
@ApiStatus.OverrideOnly
interface CodeInsightContextPresentationProvider<Context : CodeInsightContext> {
  fun isApplicable(context: CodeInsightContext): Boolean

  fun getIcon(context: Context, project: Project): Icon
  fun getPresentableText(context: Context, project: Project): @Nls String
}
