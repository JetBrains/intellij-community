// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.codeVision

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project

/**
 * Factory allows creating programmatically [CodeVisionProvider] for the given project.
 */
interface CodeVisionProviderFactory {
  companion object {
    const val EP_NAME: String = "com.intellij.codeInsight.codeVisionProviderFactory"
    val extensionPoint: ExtensionPointName<CodeVisionProviderFactory> = ExtensionPointName<CodeVisionProviderFactory>(EP_NAME)

    fun createAllProviders(project: Project): List<CodeVisionProvider<*>> {
      return extensionPoint.extensionList.flatMap { it.createProviders(project) }
    }
  }

  fun createProviders(project: Project): Sequence<CodeVisionProvider<*>>
}