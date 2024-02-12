// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import com.intellij.DynamicBundle
import com.intellij.DynamicBundle.getLocale
import com.intellij.openapi.util.text.Strings
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

    val pluginClassLoader: ClassLoader? = DynamicBundle.findLanguageBundle()?.pluginDescriptor?.pluginClassLoader
    private fun convertPathToLocalizationFolderUsage(path: Path, locale: Locale, withRegion: Boolean): String {
      val localizationFolderName = "localization"
      var result = Path(localizationFolderName).resolve(locale.language)
      if (withRegion && locale.country.isNotEmpty()) {
        result = result.resolve(locale.country)
      }
      result = result.resolve(path)
      return result.pathString
    }

    private fun convertPathToLocaleSuffixUsage(path: Path, locale: Locale?, withRegion: Boolean): String {
      if (locale == null) return path.pathString
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
      return result.pathString
    }

    fun getResourceAsStream(defaultLoader: ClassLoader?, basePath: String, fileName: String): InputStream? {
      val localizedPaths = getLocalizedPaths(basePath, fileName)
      for (path in localizedPaths) {
        pluginClassLoader?.getResourceAsStream(path)?.let { return it }
        defaultLoader?.getResourceAsStream(path)?.let { return it }
      }
      return null
    }

    fun getResourceAsStream(defaultLoader: ClassLoader?, filePath: String): InputStream? {
      val fileNameAndPath = getBasePathAndFileName(filePath)
      return getResourceAsStream(defaultLoader, fileNameAndPath.first, fileNameAndPath.second)
    }

    fun getLocalizedPaths(filePath: String): List<String> {
      val fileNameAndPath = getBasePathAndFileName(filePath)
      return getLocalizedPaths(fileNameAndPath.first, fileNameAndPath.second)
    }

    private fun getBasePathAndFileName(filePath: String): Pair<String, String> {
      val path = Path(filePath)
      val basePath = path.parent?.pathString ?: ""
      val fileName = path.fileName?.pathString ?: ""
      return Pair(basePath, fileName)
    }

    private fun getLocalizedPaths(basePath: String, fileName: String): List<String> {
      val path = Path(
        Strings.trimStart(Strings.trimEnd(basePath, "/"), "/")).resolve(fileName)
      val result = mutableListOf<String>()
      //localizations/zh/CN/inspectionDescriptions/name.html
      result.add(convertPathToLocalizationFolderUsage(path, getLocale(), true))

      //inspectionDescriptions/name_zh_CN.html
      result.add(convertPathToLocaleSuffixUsage(path, getLocale(), true))

      //localizations/zh/inspectionDescriptions/name.html
      result.add(convertPathToLocalizationFolderUsage(path, getLocale(), false))

      //inspectionDescriptions/name_zh.html
      result.add(convertPathToLocaleSuffixUsage(path, getLocale(), false))
      //inspectionDescriptions/name.html
      result.add(path.pathString)
      return result
    }


    fun getFolderLocalizedPaths(filePath: String): List<String> {
      val fileNameAndPath = getBasePathAndFileName(filePath)
      return getFolderLocalizedPaths(fileNameAndPath.first, fileNameAndPath.second)
    }

    private fun getFolderLocalizedPaths(basePath: String, fileName: String): List<String> {
      val path = Path(Strings.trimStart (Strings.trimEnd(basePath, "/"), "/")).resolve(fileName)
      val result = mutableListOf<String>()
      //localizations/zh/CN/inspectionDescriptions/name.html
      result.add(convertPathToLocalizationFolderUsage(path, getLocale(), true))

      //localizations/zh/inspectionDescriptions/name.html
      result.add(convertPathToLocalizationFolderUsage(path, getLocale(), false))
      return result
    }
  }
}