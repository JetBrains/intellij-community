// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.l10n

import com.intellij.DynamicBundle
import com.intellij.diagnostic.LoadingState
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.annotations.ApiStatus
import java.io.InputStream
import java.nio.file.Path
import java.util.*
import kotlin.io.path.Path
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.pathString

@ApiStatus.Internal
object LocalizationUtil {
  @Volatile
  private var isL10nInitialized: Boolean = false
  private const val LOCALIZATION_FOLDER_NAME: String = "localization"
  const val LOCALIZATION_KEY: String = "i18n.locale"
  val l10nPluginIdToLanguageTag: Map<String, String> = mapOf("com.intellij.ja" to "ja", "com.intellij.ko" to "ko", "com.intellij.zh" to "zh-CN")

  @JvmOverloads
  fun getPluginClassLoader(defaultLoader: ClassLoader? = null): ClassLoader? {
    val langBundle = findLanguageBundle()
    if (langBundle == null) {
      return null
    }
    val pluginDescriptor = langBundle.pluginDescriptor
    return pluginDescriptor?.classLoader ?: defaultLoader
  }

  private fun Path.convertToLocalizationFolderUsage(locale: Locale, withRegion: Boolean): Path {
    var result = Path(LOCALIZATION_FOLDER_NAME).resolve(locale.language)
    if (withRegion && locale.country.isNotEmpty()) {
      result = result.resolve(locale.country)
    }
    result = result.resolve(this)
    return result
  }

  private fun Path.convertPathToLocaleSuffixUsage(locale: Locale?, withRegion: Boolean): Path {
    if (locale == null) return this
    val fileName = StringBuilder(this.nameWithoutExtension)
    val extension = this.extension
    val foldersPath = this.parent ?: Path("")
    val language = locale.language
    if (!language.isEmpty()) {
      fileName.append('_').append(language)
      val country = locale.country
      if (country.isNotEmpty() && withRegion) {
        fileName.append('_').append(country)
      }
    }
    if (extension.isNotEmpty()) {
      fileName.append(".").append(extension)
    }
    val result = foldersPath.resolve(fileName.toString())
    return result
  }

  @JvmOverloads
  fun getResourceAsStream(defaultLoader: ClassLoader?, path: Path, specialLocale: Locale? = null): InputStream? {
    val locale = specialLocale ?: getLocale()
    val localizedPaths = getLocalizedPaths(path, locale)
    getPluginClassLoader()?.getResourceAsStream(FileUtil.toSystemIndependentName(path.pathString))?.let { return it }
    for (localizedPath in localizedPaths) {
      val pathString = FileUtil.toSystemIndependentName(localizedPath.pathString)
      defaultLoader?.getResourceAsStream(pathString)?.let { return it }
    }
    return null
  }

  @JvmOverloads
  fun getLocalizedPaths(path: Path, specialLocale: Locale? = null): List<Path> {
    val locale = specialLocale ?: getLocale()
    return listOf(
      //localizations/zh/CN/inspectionDescriptions/name.html
      path.convertToLocalizationFolderUsage(locale, true),

      //inspectionDescriptions/name_zh_CN.html
      path.convertPathToLocaleSuffixUsage(locale, true),

      //localizations/zh/inspectionDescriptions/name.html
      path.convertToLocalizationFolderUsage(locale, false),

      //inspectionDescriptions/name_zh.html
      path.convertPathToLocaleSuffixUsage(locale, false),

      //inspectionDescriptions/name.html
      path
    ).distinct()
  }

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

  @JvmOverloads
  fun getFolderLocalizedPaths(path: Path, specialLocale: Locale? = null): List<Path> {
    val locale = specialLocale ?: getLocaleOrNullForDefault() ?: return emptyList()
    return listOf(
      //localizations/zh/CN/inspectionDescriptions/name.html
      path.convertToLocalizationFolderUsage(locale, true),

      //localizations/zh/inspectionDescriptions/name.html
      path.convertToLocalizationFolderUsage(locale, false)).distinct()
  }

  @JvmOverloads
  fun getSuffixLocalizedPaths(path: Path, specialLocale: Locale? = null): List<String> {
    val locale = specialLocale ?: getLocale()
    return setOf(
      //inspectionDescriptions/name_zh_CN.html
      path.convertPathToLocaleSuffixUsage(locale, true),

      //inspectionDescriptions/name_zh.html
      path.convertPathToLocaleSuffixUsage(locale, false))
      .map { FileUtil.toSystemIndependentName(it.toString()) }
  }

  fun getLocaleOrNullForDefault(): Locale? {
    val locale = getLocale()
    if (Locale.ENGLISH.language == locale.language) {
      return null
    }
    return locale
  }

  fun setLocalizationInitialized() {
    isL10nInitialized = true
  }

  fun isLocalizationInitialized(): Boolean {
    return isL10nInitialized
  }

  fun getForcedLocale(): String? {
    val languageTag = System.getProperty(LOCALIZATION_KEY)
    if (languageTag.isNullOrEmpty()) {
      return null
    }
    return languageTag
  }

  fun getLocale(): Locale {
    val forcedLocale = getForcedLocale()
    val languageTag = forcedLocale ?: LocalizationStateService.getInstance()?.getSelectedLocale() ?: return Locale.ENGLISH
    val locale = Locale.forLanguageTag(languageTag)

    if (forcedLocale == null) {
      val englishTag = Locale.ENGLISH.toLanguageTag()
      if (languageTag != englishTag && findLanguageBundle(locale) == null) {
        return Locale.ENGLISH
      }
    }

    return locale
  }

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

  fun getAllAvailableLocales(): Pair<List<Locale>, Map<Locale, String>> {
    val list = ArrayList<Locale>()
    val map = HashMap<Locale, String>()

    list.add(Locale.ENGLISH)

    for (bundleEP in getAllLanguageBundleExtensions()) {
      val locale = Locale.forLanguageTag(bundleEP.locale)
      list.add(locale)

      val displayName = bundleEP.displayName
      if (!displayName.isNullOrEmpty()) {
        map[locale] = displayName
      }
    }

    return list to map
  }
}