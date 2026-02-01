// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.xml

import com.intellij.platform.plugins.parser.impl.elements.ModuleLoadingRuleValue
import com.intellij.platform.plugins.parser.impl.elements.xmlValue
import org.jetbrains.intellij.build.productLayout.ContentModule

/**
 * DSL marker for XML builder to prevent nested scope leaking.
 */
@DslMarker
internal annotation class XmlBuilderMarker

/**
 * Builder for content inside <content> element.
 */
@XmlBuilderMarker
internal class ContentBuilder(@PublishedApi internal val sb: StringBuilder, @PublishedApi internal val indentLevel: Int) {
  @PublishedApi internal val indent: String = "  ".repeat(indentLevel)

  /**
   * Appends a <module> element.
   */
  fun module(name: String, loading: ModuleLoadingRuleValue = ModuleLoadingRuleValue.OPTIONAL) {
    sb.append("$indent<module name=\"$name\"")
    // Only output loading attribute for non-default values (OPTIONAL is default, omit it)
    if (loading != ModuleLoadingRuleValue.OPTIONAL) {
      sb.append(" loading=\"${loading.xmlValue}\"")
    }
    sb.append("/>\n")
  }

  /**
   * Appends a module with EMBEDDED loading.
   */
  fun embeddedModule(name: String) {
    module(name, ModuleLoadingRuleValue.EMBEDDED)
  }

  /**
   * Appends XML comment.
   */
  fun comment(text: String) {
    sb.append("$indent<!-- $text -->\n")
  }
}

/**
 * Extension function to append module with ContentModule data class.
 */
internal fun ContentBuilder.module(contentModule: ContentModule) {
  module(contentModule.name.value, contentModule.loading)
}

/**
 * Extension function to append multiple modules.
 */
internal fun ContentBuilder.modules(moduleList: List<ContentModule>) {
  for (module in moduleList) {
    module(module)
  }
}
