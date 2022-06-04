// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.codeVision

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import java.util.stream.Stream
import kotlin.streams.toList


/**
 * Factory allows creating programmatically [CodeVisionProvider] for the given project.
 */
interface CodeVisionProviderFactory {
  companion object {
    const val EP_NAME = "com.intellij.codeInsight.codeVisionProviderFactory"
    val extensionPoint = ExtensionPointName.create<CodeVisionProviderFactory>(EP_NAME)

    fun createAllProviders(project: Project): List<CodeVisionProvider<*>> {
      return extensionPoint.extensions()
        .flatMap { it.createProviders(project) }
        .toList()
    }
  }

  fun createProviders(project: Project): Stream<CodeVisionProvider<*>>
}