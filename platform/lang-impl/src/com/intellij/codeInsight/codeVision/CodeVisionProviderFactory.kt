// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.codeVision

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

/**
 * Factory allows creating programmatically [CodeVisionProvider] for the given project.
 */
interface CodeVisionProviderFactory {
  companion object {
    @ApiStatus.Internal
    val EP_NAME: ExtensionPointName<CodeVisionProviderFactory> = ExtensionPointName("com.intellij.codeInsight.codeVisionProviderFactory")

    fun createAllProviders(project: Project): List<CodeVisionProvider<*>> {
      val knownIds = mutableSetOf<String>()
      val extensions = mutableListOf<CodeVisionProvider<*>>()
      EP_NAME.forEachExtensionSafe {
        for (provider in it.createProviders(project)) {
          if (knownIds.add(provider.id)) {
            extensions.add(provider)
          }
          else {
            val oldProvider = extensions.find { it.id == provider.id }
            logger<CodeVisionProviderFactory>().warn("Duplicate provider id: ${provider.id}, provider1: $oldProvider, provider2: $provider")
          }
        }
      }
      return extensions
    }
  }

  fun createProviders(project: Project): Sequence<CodeVisionProvider<*>>
}