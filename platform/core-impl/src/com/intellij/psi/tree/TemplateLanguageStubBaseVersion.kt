// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.tree

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.psi.stubs.LanguageStubDescriptor
import com.intellij.psi.stubs.StubElementRegistryService
import com.intellij.psi.templateLanguages.TemplateLanguage
import com.intellij.util.concurrency.SynchronizedClearableLazy
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object TemplateLanguageStubBaseVersion {
  private val value = SynchronizedClearableLazy {
    val stubElementRegistryService = StubElementRegistryService.getInstance()

    val versions = IElementType.mapNotNull { elementType ->
      stubElementRegistryService.getStubVersion(elementType)
    }.distinctBy(StubVersion::source)

    val sum = versions.sumOf { it.value }

    val message = "Template stub version: $sum\n" + versions.joinToString("\n  ", prefix = "  ")
    thisLogger().info(message)
    sum
  }

  private fun StubElementRegistryService.getStubVersion(elementType: IElementType): StubVersion? {
    if (elementType !is IFileElementType || elementType.language is TemplateLanguage) {
      return null
    }

    val stubDescriptor = getStubDescriptor(elementType.language) ?: return null

    if (stubDescriptor.fileElementType == elementType) {
      return StubVersion(stubDescriptor.stubDefinition.stubVersion, stubDescriptor)
    }

    if (elementType is IStubFileElementType<*>) {
      // additional file element type for a language
      return StubVersion(elementType.stubVersion, elementType)
    }

    // This elementType is neither the main file element type, nor is it a legacy IStubFileElementType => ignoring it.
    // If you want to have several file element types for your language, use a single common stub version in your LanguageStubDefinition.
    return null
  }

  val version: Int
    @JvmStatic
    get() = value.value

  @JvmStatic
  fun dropVersion() {
    value.drop()
  }
}

private class StubVersion(val value: Int, val source: Any) {
  override fun toString(): String {
    if (source is LanguageStubDescriptor) {
      return "StubVersion(${source.language}, value: $value)"
    }
    else {
      return "StubVersion($source, value: $value)"
    }
  }
}