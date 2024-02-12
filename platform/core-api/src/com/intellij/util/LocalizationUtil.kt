// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import com.intellij.DynamicBundle
import com.intellij.DynamicBundle.getLocale
import org.jetbrains.annotations.ApiStatus
import java.io.InputStream
import java.nio.file.Path
import java.util.*
import kotlin.io.path.Path
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.pathString

@ApiStatus.Internal
class LocalizationUtil {
  companion object {
    private fun getPluginClassLoader(): ClassLoader? = DynamicBundle.findLanguageBundle()?.pluginDescriptor?.pluginClassLoader
    private fun convertPathToLocalizationFolderUsage(path: Path, locale: Locale, withRegion: Boolean): Path {
      val localizationFolderName = "localization"
      var result = Path(localizationFolderName).resolve(locale.language)
      if (withRegion && locale.country.isNotEmpty()) {
        result = result.resolve(locale.country)
      }
      result = result.resolve(path)
      return result
    }

    private fun convertPathToLocaleSuffixUsage(path: Path, locale: Locale?, withRegion: Boolean): Path {
      if (locale == null) return path
      val fileName = StringBuilder(path.nameWithoutExtension)
      val extension = path.extension
      val foldersPath = path.parent ?: Path("")
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
        val pathString = localizedPath.pathString.replace('\\', '/')
        getPluginClassLoader()?.getResourceAsStream(pathString)?.let { return it }
        defaultLoader?.getResourceAsStream(pathString)?.let { return it }
      }
      return null
    }

    @JvmOverloads
    fun getLocalizedPaths(path: Path, specialLocale: Locale? = null): List<Path> {
      val locale = specialLocale ?: getLocale()
      val result = mutableListOf<Path>()
      //localizations/zh/CN/inspectionDescriptions/name.html
      result.add(convertPathToLocalizationFolderUsage(path, locale, true))

      //inspectionDescriptions/name_zh_CN.html
      result.add(convertPathToLocaleSuffixUsage(path, locale, true))

      //localizations/zh/inspectionDescriptions/name.html
      result.add(convertPathToLocalizationFolderUsage(path, locale, false))

      //inspectionDescriptions/name_zh.html
      result.add(convertPathToLocaleSuffixUsage(path, locale, false))
      //inspectionDescriptions/name.html
      result.add(path)
      return result
    }

    @JvmOverloads
    fun getFolderLocalizedPaths(path: Path, specialLocale: Locale? = null): List<Path> {
      val locale = specialLocale ?: getLocale()
      val result = mutableListOf<Path>()
      //localizations/zh/CN/inspectionDescriptions/name.html
      result.add(convertPathToLocalizationFolderUsage(path, locale, true))

      //localizations/zh/inspectionDescriptions/name.html
      result.add(convertPathToLocalizationFolderUsage(path, locale, false))
      return result
    }
  }
}