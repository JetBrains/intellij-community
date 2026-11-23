// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.generate.template.toString

import com.intellij.lang.Language
import com.intellij.lang.LanguageExtension
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.java.generate.template.TemplatesManager

/**
 * Gives the [TemplatesManager] that keeps the toString templates of a language.
 * Register an implementation in the `com.intellij.generation.toStringTemplatesManager` extension point.
 */
@ApiStatus.Internal
public interface ToStringTemplatesManagerProvider {
  public val templatesManager: TemplatesManager

  public companion object {
    private val EP: LanguageExtension<ToStringTemplatesManagerProvider> =
      LanguageExtension("com.intellij.generation.toStringTemplatesManager")

    /**
     * @return the toString templates manager of [language], or null if no provider is registered for it.
     */
    @JvmStatic
    public fun forLanguage(language: Language): TemplatesManager? = EP.forLanguage(language)?.templatesManager
  }
}

internal class JavaToStringTemplatesManagerProvider : ToStringTemplatesManagerProvider {
  override val templatesManager: TemplatesManager
    get() = ToStringTemplatesManager.getInstance()
}
