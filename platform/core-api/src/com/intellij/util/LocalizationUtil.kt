// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import com.intellij.DynamicBundle
import com.intellij.DynamicBundle.getLocale
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
  var isL10nPluginInitialized: Boolean = false
  private const val LOCALIZATION_FOLDER_NAME = "localization"
    fun getPluginClassLoader(): ClassLoader? = DynamicBundle.findLanguageBundle()?.pluginDescriptor?.pluginClassLoader
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
    fun getResourceAsStream(defaultLoader: ClassLoader?, path: Path,  specialLocale: Locale? = null): InputStream? {
      val locale = specialLocale ?: getLocale()
      val localizedPaths = getLocalizedPaths(path, locale)
      for (localizedPath in localizedPaths) {
        val pathString = FileUtil.toSystemIndependentName(localizedPath.pathString)
        getPluginClassLoader()?.getResourceAsStream(pathString)?.let { return it }
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
    val locale = specialLocale ?: getLocaleFromPlugin() ?: return emptyList()
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
      val locale = specialLocale ?: getLocaleFromPlugin() ?: return emptyList()
      return listOf(
      //localizations/zh/CN/inspectionDescriptions/name.html
      path.convertToLocalizationFolderUsage(locale, true),

      //localizations/zh/inspectionDescriptions/name.html
      path.convertToLocalizationFolderUsage(locale, false)).distinct()
    }

  fun getLocaleFromPlugin(): Locale? {
    return DynamicBundle.findLanguageBundle()?.locale?.let { Locale.forLanguageTag(it) }
  }
}