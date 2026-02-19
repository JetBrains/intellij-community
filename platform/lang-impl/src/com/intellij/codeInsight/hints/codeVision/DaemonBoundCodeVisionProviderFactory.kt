// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.codeVision

import com.intellij.codeInsight.codeVision.CodeVisionProvider
import com.intellij.codeInsight.codeVision.CodeVisionProviderFactory
import com.intellij.openapi.project.Project

internal class DaemonBoundCodeVisionProviderFactory : CodeVisionProviderFactory {
  override fun createProviders(project: Project): Sequence<CodeVisionProvider<*>> {
    return DaemonBoundCodeVisionProvider.extensionPoint.extensionList.asSequence().map(::CodeVisionProviderAdapter)
  }
}