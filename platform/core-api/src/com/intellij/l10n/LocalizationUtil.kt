// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.l10n

import com.intellij.DynamicBundle
import com.intellij.diagnostic.LoadingState
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.annotations.ApiStatus.Internal
import java.io.InputStream
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import kotlin.io.path.Path
import kotlin.io.path.extension
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.pathString

object LocalizationUtil {
  private const val LOCALIZATION_FOLDER_NAME: String = "localization"

  @Internal
  const val LOCALIZATION_KEY: String = "i18n.locale"
  @Internal
  val defaultLocale: Locale = Locale.ENGLISH

  @JvmOverloads
  fun getLocale(ignoreRestartRequired: Boolean = false): Locale {
    val localizationStateService = LocalizationStateService.getInstance()
    val languageTag = if (localizationStateService == null) {
      return defaultLocale
    }
    else if (!ignoreRestartRequired && localizationStateService.isRestartRequired) {
      localizationStateService.lastSelectedLocale
    }
    else {
      localizationStateService.selectedLocale
    }
    
    val locale = Locale.forLanguageTag(languageTag)
    if (locale.language != defaultLocale.language && findLanguageBundle(locale) == null) {
      return defaultLocale
    }

    return locale
  }

  fun getLocaleOrNullForDefault(): Locale? {
    val locale = getLocale()
    return if (locale.language == defaultLocale.language) null else locale
  }

  @Internal
  @JvmOverloads
  fun getPluginClassLoader(defaultLoader: ClassLoader? = null, locale: Locale = getLocale()): ClassLoader? {
    if (locale == defaultLocale || locale == Locale.ROOT) {
      return null
    }

    val langBundle = findLanguageBundle(locale) ?: return null
    return langBundle.pluginDescriptor?.classLoader ?: defaultLoader
  }

  private fun convertToLocalizationFolderUsage(paths: Path, locale: Locale, withRegion: Boolean): Path {
    var result = Paths.get(LOCALIZATION_FOLDER_NAME).resolve(locale.language)
    if (withRegion && locale.country.isNotEmpty()) {
      result = result.resolve(locale.country)
    }
    result = result.resolve(paths)
    return result
  }

  private fun convertPathToLocaleSuffixUsage(file: Path, locale: Locale?, withRegion: Boolean): Path {
    if (locale == null) {
      return file
    }

    val fileName = StringBuilder(file.nameWithoutExtension)
    val extension = file.extension
    val foldersPath = file.parent ?: Paths.get("")
    val language = locale.language
    if (!language.isEmpty()) {
      fileName.append('_').append(language)
      val country = locale.country
      if (country.isNotEmpty() && withRegion) {
        fileName.append('_').append(country)
      }
    }
    if (extension.isNotEmpty()) {
      fileName.append('.').append(extension)
    }
    return foldersPath.resolve(fileName.toString())
  }

  @Internal
  @JvmOverloads
  fun getResourceAsStream(defaultLoader: ClassLoader?, path: Path, specialLocale: Locale? = null): InputStream? {
    val locale = specialLocale ?: getLocale()
    for (localizedPath in getLocalizedPaths(path, locale)) {
      defaultLoader?.getResourceAsStream(localizedPath.invariantSeparatorsPathString)?.let { return it }
    }
    val resourcePath = path.pathString
    val pureResourcePath = FileUtil.toSystemIndependentName(resourcePath)
    return getPluginClassLoader()?.getResourceAsStream(resourcePath)
           ?: getPluginClassLoader()?.getResourceAsStream(pureResourcePath)
           ?: defaultLoader?.getResourceAsStream(resourcePath)
           ?: defaultLoader?.getResourceAsStream(pureResourcePath)
  }

  @Internal
  @JvmOverloads
  fun getLocalizedPathsWithDefault(path: Path, specialLocale: Locale? = null): List<Path> {
    val locale = specialLocale ?: getLocale()
    return getLocalizedPaths(path, locale).toMutableList().plusElement(path).distinct()
  }

  @Internal
  @JvmOverloads
  fun getLocalizedPaths(path: Path, specialLocale: Locale? = null): Collection<Path> {
    val locale = specialLocale ?: getLocale()
    if (locale == Locale.ROOT) {
      return emptyList()
    }

    return linkedSetOf(
      //localizations/zh/CN/inspectionDescriptions/name.html
      convertToLocalizationFolderUsage(paths = path, locale = locale, withRegion = true),

      //inspectionDescriptions/name_zh_CN.html
      convertPathToLocaleSuffixUsage(file = path, locale = locale, withRegion = true),

      //localizations/zh/inspectionDescriptions/name.html
      convertToLocalizationFolderUsage(paths = path, locale = locale, withRegion = false),

      //inspectionDescriptions/name_zh.html
      convertPathToLocaleSuffixUsage(file = path, locale = locale, withRegion = false),
    )
  }
  
  @Internal
  @JvmOverloads
  fun getLocalizedPathStrings(path: String, specialLocale: Locale? = null): List<String> {
    return getLocalizedPaths(Path(path), specialLocale).map { FileUtil.toSystemIndependentName(it.pathString) }
  }

  @Internal
  fun getLocalizationSuffixes(specialLocale: Locale? = null): List<String> {
    val locale = specialLocale ?: getLocaleOrNullForDefault() ?: return emptyList()
    val result = mutableListOf<String>()
    if (locale.language.isNotEmpty()) {
      if (locale.country.isNotEmpty()) {
        result.add("_${locale.language}_${locale.country}")
      }
      result.add("_${locale.language}")
    }
    return result
  }

  @Internal
  @JvmOverloads
  fun getFolderLocalizedPaths(path: Path, specialLocale: Locale? = null): List<Path> {
    val locale = specialLocale ?: getLocaleOrNullForDefault() ?: return emptyList()
    return listOf(
      //localizations/zh/CN/inspectionDescriptions/name.html
      convertToLocalizationFolderUsage(path, locale, true),

      //localizations/zh/inspectionDescriptions/name.html
      convertToLocalizationFolderUsage(path, locale, false)).distinct()
  }

  @Internal
  @JvmOverloads
  fun getSuffixLocalizedPaths(path: Path, specialLocale: Locale? = null): List<String> {
    val locale = specialLocale ?: getLocale()
    return setOf(
      //inspectionDescriptions/name_zh_CN.html
      convertPathToLocaleSuffixUsage(path, locale, true),

      //inspectionDescriptions/name_zh.html
      convertPathToLocaleSuffixUsage(path, locale, false))
      .map { FileUtil.toSystemIndependentName(it.toString()) }
  }

  @Internal
  @JvmOverloads
  fun findLanguageBundle(locale: Locale = getLocale()): DynamicBundle.LanguageBundleEP? {
    return getAllLanguageBundleExtensions().find {
      val extensionLocale = Locale.forLanguageTag(it.locale)
      extensionLocale == locale
      //extensionLocale.language == locale.language && (extensionLocale.country == locale.country || locale.country == null))
    }
  }

  private fun getAllLanguageBundleExtensions(): List<DynamicBundle.LanguageBundleEP> {
    try {
      if (!LoadingState.COMPONENTS_REGISTERED.isOccurred) {
        return emptyList()
      }

      val app = ApplicationManager.getApplication()
      if (app == null || !app.extensionArea.hasExtensionPoint(DynamicBundle.LanguageBundleEP.EP_NAME)) {
        return emptyList()
      }
      return DynamicBundle.LanguageBundleEP.EP_NAME.extensionList
    }
    catch (e: ProcessCanceledException) {
      throw e
    }
    catch (e: Exception) {
      logger<LocalizationUtil>().error(e)
      return emptyList()
    }
  }

  @Internal
  fun getAllAvailableLocales(): Pair<List<Locale>, Map<Locale, String>> {
    val list = HashSet<Locale>()
    val map = HashMap<Locale, String>()

    for (bundleEP in getAllLanguageBundleExtensions()) {
      val locale = Locale.forLanguageTag(bundleEP.locale)
      list.add(locale)

      val displayName = bundleEP.displayName
      if (!displayName.isNullOrEmpty()) {
        map[locale] = displayName
      }
    }

    return buildList {
      add(defaultLocale)
      addAll(list.sortedBy { map[it] ?: it.getDisplayLanguage(defaultLocale) })
    } to map
  }
}