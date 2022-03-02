// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints.codeVision

import com.intellij.codeInsight.codeVision.CodeVisionProvider
import com.intellij.codeInsight.codeVision.CodeVisionProviderFactory
import com.intellij.openapi.project.Project
import java.util.stream.Stream

class DaemonBoundCodeVisionProviderFactory : CodeVisionProviderFactory {
  override fun createProviders(project: Project): Stream<CodeVisionProvider<*>> {
    return DaemonBoundCodeVisionProvider.extensionPoint.extensions().map { CodeVisionProviderAdapter(it) }
  }
}