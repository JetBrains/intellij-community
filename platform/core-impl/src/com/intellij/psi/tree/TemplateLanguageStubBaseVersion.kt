// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.tree

import com.intellij.psi.stubs.StubElementRegistryService
import com.intellij.psi.templateLanguages.TemplateLanguage
import com.intellij.util.concurrency.SynchronizedClearableLazy
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object TemplateLanguageStubBaseVersion {
  private val value = SynchronizedClearableLazy<Int> {
    val stubElementRegistryService = StubElementRegistryService.getInstance()
    val stubDescriptors = IElementType.mapNotNull { elementType ->
      if (elementType is IFileElementType && elementType.language !is TemplateLanguage) {
        stubElementRegistryService.getStubDescriptor(elementType.language)
      }
      else null
    }
    stubDescriptors.sumOf { it.stubDefinition.stubVersion }
  }

  val version: Int
    @JvmStatic
    get() = value.value

  @JvmStatic
  fun dropVersion() {
    value.drop()
  }
}