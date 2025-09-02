// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.parameters

import com.intellij.codeInsight.hints.*
import com.intellij.codeInsight.hints.filtering.Matcher
import com.intellij.codeInsight.hints.filtering.MatcherConstructor
import com.intellij.codeInsight.hints.settings.ParameterNameHintsSettings
import com.intellij.diagnostic.PluginException
import com.intellij.lang.Language
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.NlsContexts
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting

/**
 * This service exists to enable code reuse and interoperability of exclude lists between different types of inlay hint providers.
 *
 * Originally, we've had only [InlayParameterHintsProvider] supporting these exclude lists.
 * To support parameter name hints implemented using other types of inlay hint providers
 * (e.g. [com.intellij.codeInsight.hints.declarative.InlayHintsProvider]),
 * there is also [ParameterHintsExcludeListConfigProvider].
 *
 * This service provides a single point of access to exclude lists from either, which is needed mainly due to
 * [dependency languages][InlayParameterHintsProvider.blackListDependencyLanguage],
 * i.e., the ability of the exclude list for one language to include the patterns from another language,
 * each of which may originate from a different type of inlay hint provider.
 *
 * @see ExcludeListDialog
 * @see ParameterNameHintsSettings
 * @see InlayParameterHintsProvider
 * @see ParameterHintsExcludeListConfigProvider
 */
@Service(Service.Level.APP)
@ApiStatus.Internal
class ParameterHintsExcludeListService(cs: CoroutineScope) {
  companion object {
    @JvmStatic
    fun getInstance(): ParameterHintsExcludeListService = service()
  }

  init {
    val connection = ApplicationManager.getApplication().messageBus.simpleConnect()
    connection.subscribe(ParameterNameHintsSettings.ExcludeListListener.TOPIC, object : ParameterNameHintsSettings.ExcludeListListener {
      override fun excludeListChanged(language: Language?) {
        clearMatchersCache()
      }
    })
    PARAMETER_NAME_HINTS_EP.addChangeListener(cs, ::clearCaches)
    ParameterHintsExcludeListConfigProvider.EP_NAME.addChangeListener(cs, ::clearCaches)
  }

  @Volatile
  private var configs: Map<Language, ParameterHintsExcludeListConfig> = emptyMap()
  @Volatile
  private var matchers: Map<Language, List<Matcher>> = emptyMap()
  private val lock = Any()

  /**
   * Retrieves a [ParameterHintsExcludeListConfig] for the given [language], or `null` if there is none.
   *
   *  If `InlayParameterHintsProviderExtension.forLanguage(language) != null`
   *  or `ParameterHintsExcludeListConfigProvider.EP.forLanguage(language) != null`,
   *  then `getConfig(language) != null`.
   *
   *  The returned [ParameterHintsExcludeListConfig.language] may be the [language] itself
   *  or one of its [base languages][Language.getBaseLanguage], depending on the registered extensions.
   *
   *  @see com.intellij.lang.LanguageExtension.forLanguage
   */
  fun getConfig(language: Language): ParameterHintsExcludeListConfig? {
    configs[language]?.let { return it }
    synchronized(lock) {
      configs[language]?.let { return it }
      var newConfigs = ensureRegisteredConfigsLoaded(configs)
      val configForLanguage = language.baseLanguagesSequence()
        .firstNotNullOfOrNull { newConfigs[it] }
      if (configForLanguage != null) {
        newConfigs = newConfigs + (language to configForLanguage)
      }
      configs = newConfigs
      return configForLanguage
    }
  }

  fun isExcluded(fullyQualifiedName: String, parameterNames: List<String>, language: Language): Boolean =
    getMatchers(language).any { it.isMatching(fullyQualifiedName, parameterNames) }

  fun getMatchers(language: Language): List<Matcher> {
    matchers[language]?.let { return it }
    synchronized(lock) {
      matchers[language]?.let { return it }
      val excludeList = getFullExcludeList(language)
      val newMatchers = excludeList.mapNotNull { MatcherConstructor.createMatcher(it) }
      matchers = matchers + (language to newMatchers)
      return newMatchers
    }
  }

  private fun clearCaches() {
    synchronized(lock) {
      configs = emptyMap()
      matchers = emptyMap()
    }
  }

  private fun clearMatchersCache() {
    synchronized(lock) {
      matchers = emptyMap()
    }
  }

  private fun ensureRegisteredConfigsLoaded(recentConfigs: Map<Language, ParameterHintsExcludeListConfig>): Map<Language, ParameterHintsExcludeListConfig> {
    if (recentConfigs.isNotEmpty()) {
      return recentConfigs
    }
    else {
      return doGetRegisteredConfigs()
    }
  }

  private fun doGetRegisteredConfigs(): Map<Language, ParameterHintsExcludeListConfig> = buildMap {
    for (hintProviderBean in PARAMETER_NAME_HINTS_EP.extensionList) {
      val lang = Language.findLanguageByID(hintProviderBean.language) ?: continue
      val provider = InlayParameterHintsExtension.forLanguage(lang)
      put(lang, collectExcludeListConfig(lang, provider))
    }
    for (configProviderBean in ParameterHintsExcludeListConfigProvider.EP_NAME.extensionList) {
      val lang = Language.findLanguageByID(configProviderBean.language) ?: continue
      val provider = ParameterHintsExcludeListConfigProvider.EP.forLanguage(lang)
      if (lang in this) {
        PluginException.logPluginError(
          logger<ParameterHintsExcludeListService>(),
          "Only either one of InlayParameterHintsProvider or ParameterHintsExcludeListConfigProvider extensions" +
          " is allowed to be registered for a language ({${lang.id}), never both.",
          null,
          provider.javaClass
        )
      }
      put(lang, collectExcludeListConfig(lang, provider))
    }
  }

  /** Includes the exclude list of the [dependency language][ParameterHintsExcludeListConfig.excludeListDependencyLanguage] */
  @VisibleForTesting
  fun getFullExcludeList(language: Language): Set<String> {
    val config = getConfig(language) ?: return emptySet()
    val settings = ParameterNameHintsSettings.getInstance()
    val excludeList = getExcludeList(settings, config)
    return config.excludeListDependencyLanguage
             ?.let { getConfig(it) }
             ?.let { excludeList union getExcludeList(settings, it) }
           ?: excludeList
  }
}

/**
 * Encapsulates the configuration of a parameter hints exclude-list.
 *
 * @param language Provider language, i.e., the language for which the exclude list extension is registered.
 * (And not a language of which the provider language is a base language.)
 */
@ApiStatus.Internal
class ParameterHintsExcludeListConfig internal constructor(
  val language: Language,
  val isExcludeListSupported: Boolean,
  val defaultExcludeList: Set<String>,
  val excludeListExplanationHtml: @NlsContexts.DetailedDescription String?,
  val excludeListDependencyLanguage: Language?,
)

/** returned sequence includes [this] */
private fun Language.baseLanguagesSequence(): Sequence<Language> = generateSequence(this) { it.baseLanguage }

internal fun collectExcludeListConfig(
  providerLanguage: Language,
  provider: InlayParameterHintsProvider,
): ParameterHintsExcludeListConfig = ParameterHintsExcludeListConfig(
    providerLanguage,
    provider.isBlackListSupported,
    provider.defaultBlackList,
    provider.blacklistExplanationHTML,
    provider.blackListDependencyLanguage
  )

internal fun collectExcludeListConfig(
  providerLanguage: Language,
  provider: ParameterHintsExcludeListConfigProvider,
): ParameterHintsExcludeListConfig = ParameterHintsExcludeListConfig(
  providerLanguage,
  provider.isExcludeListSupported(),
  provider.getDefaultExcludeList(),
  provider.getExcludeListExplanationHtml(),
  provider.getExcludeListDependencyLanguage()
)