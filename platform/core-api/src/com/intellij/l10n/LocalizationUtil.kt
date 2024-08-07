// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.l10n

import com.intellij.DynamicBundle
import com.intellij.diagnostic.LoadingState
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.util.PathUtilRt
import org.jetbrains.annotations.ApiStatus.Internal
import java.io.IOException
import java.io.InputStream
import java.util.*

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

  fun getLocaleOrNullForDefault(): Locale? = getLocale().takeIf { it.language != defaultLocale.language }

  @Internal
  @JvmOverloads
  fun getPluginClassLoader(defaultLoader: ClassLoader? = null, locale: Locale = getLocale()): ClassLoader? {
    if (locale == defaultLocale || locale == Locale.ROOT) {
      return null
    }

    val langBundle = findLanguageBundle(locale) ?: return null
    return langBundle.pluginDescriptor?.classLoader ?: defaultLoader
  }

  private fun convertToLocalizationFolderUsage(p: String, locale: Locale, withRegion: Boolean): String {
    val result = StringBuilder().append(LOCALIZATION_FOLDER_NAME).append('/').append(locale.language)
    if (withRegion && locale.country.isNotEmpty()) {
      result.append('/').append(locale.country)
    }
    result.append('/').append(p)
    return result.toString()
  }

  private fun convertPathToLocaleSuffixUsage(file: String, locale: Locale?, withRegion: Boolean): String {
    if (locale == null) {
      return file
    }

    val pathFileName = PathUtilRt.getFileName(file)
    val fileName = StringBuilder().append(PathUtilRt.getParentPath(file)).append('/').append(FileUtilRt.getNameWithoutExtension(pathFileName))
    val extension = FileUtilRt.getExtension(pathFileName)
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
    return fileName.toString()
  }

  @Internal
  fun getResourceAsStream(classLoader: ClassLoader?, path: String, specialLocale: Locale? = null): InputStream? {
    val locale = specialLocale ?: getLocaleOrNullForDefault()
    if (classLoader != null && locale != null) {
      try {
        for (localizedPath in getLocalizedPaths(path, locale)) {
          classLoader.getResourceAsStream(localizedPath)?.let { return it }
        }
      }
      catch (e: IOException) {
        thisLogger().error("Cannot find localized resource: $path", e)
      }
    }
    return locale?.let { getPluginClassLoader(defaultLoader = null, locale = it)?.getResourceAsStream(path) }
           ?: classLoader?.getResourceAsStream(path)
  }

  @Internal
  @JvmOverloads
  fun getLocalizedPathsWithDefault(path: String, specialLocale: Locale? = null): List<String> {
    return getLocalizedPaths(path, specialLocale).toMutableList().plusElement(path).distinct()
  }

  @Internal
  fun getLocalizedPaths(path: String, specialLocale: Locale? = null): Collection<String> {
    val locale = specialLocale ?: getLocaleOrNullForDefault()
    if (locale == null || locale == Locale.ROOT) {
      return emptyList()
    }

    return linkedSetOf(
      //localizations/zh/CN/inspectionDescriptions/name.html
      convertToLocalizationFolderUsage(p = path, locale = locale, withRegion = true),

      //inspectionDescriptions/name_zh_CN.html
      convertPathToLocaleSuffixUsage(file = path, locale = locale, withRegion = true),

      //localizations/zh/inspectionDescriptions/name.html
      convertToLocalizationFolderUsage(p = path, locale = locale, withRegion = false),

      //inspectionDescriptions/name_zh.html
      convertPathToLocaleSuffixUsage(file = path, locale = locale, withRegion = false),
    )
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
  fun getFolderLocalizedPaths(path: String, specialLocale: Locale? = null): List<String> {
    val locale = specialLocale ?: getLocaleOrNullForDefault() ?: return emptyList()
    return listOf(
      //localizations/zh/CN/inspectionDescriptions/name.html
      convertToLocalizationFolderUsage(path, locale, true),

      //localizations/zh/inspectionDescriptions/name.html
      convertToLocalizationFolderUsage(path, locale, false)).distinct()
  }

  @Internal
  @JvmOverloads
  fun getSuffixLocalizedPaths(path: String, specialLocale: Locale? = null): List<String> {
    val locale = specialLocale ?: getLocaleOrNullForDefault()
    return setOf(
      //inspectionDescriptions/name_zh_CN.html
      convertPathToLocaleSuffixUsage(file = path, locale, true),

      //inspectionDescriptions/name_zh.html
      convertPathToLocaleSuffixUsage(file = path, locale, false),
    )
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