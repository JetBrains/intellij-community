// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.codeVision

import com.intellij.openapi.project.Project

internal class DefaultCodeVisionProviderFactory : CodeVisionProviderFactory {
  override fun createProviders(project: Project): Sequence<CodeVisionProvider<*>> {
    return CodeVisionProvider.providersExtensionPoint.extensionList.asSequence()
  }
}