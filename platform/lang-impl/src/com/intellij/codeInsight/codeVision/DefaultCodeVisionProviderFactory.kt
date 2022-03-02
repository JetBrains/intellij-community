// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.codeVision

import com.intellij.openapi.project.Project
import java.util.stream.Stream

class DefaultCodeVisionProviderFactory : CodeVisionProviderFactory {
  override fun createProviders(project: Project): Stream<CodeVisionProvider<*>> {
    return CodeVisionProvider.providersExtensionPoint.extensions()
  }
}