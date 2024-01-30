// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import com.intellij.DynamicBundle
import com.intellij.openapi.util.text.Strings
import java.io.InputStream
import java.util.*
import kotlin.io.path.Path
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.pathString

class LocalizationUtil {
  companion object {
    val locale: Locale? = DynamicBundle.findLanguageBundle()?.locale?.let { Locale.forLanguageTag(it) }
    val pluginClassLoader: ClassLoader? = DynamicBundle.findLanguageBundle()?.pluginDescriptor?.pluginClassLoader
    private fun convertPathToLocalizationFolderUsage(path: String, locale: Locale, withRegion: Boolean): String {
      val localizationFolderName = "localization"
      val result = StringBuilder("$localizationFolderName/${locale.language}/")
      if (withRegion && locale.country.isNotEmpty()) {
        result.append(locale.country)
        result.append("/")
      }
      val myPath = path.removeSuffix("/").removePrefix("/")
      result.append(myPath)
      return result.toString()
    }

    private fun convertPathToLocaleSuffixUsage(filePath: String, locale: Locale?, withRegion: Boolean): String {
      if (locale == null) return filePath
      val path = Path(filePath)
      val fileName = path.nameWithoutExtension
      val extension = path.extension
      val foldersPath = path.parent?.pathString ?: ""
      val result = StringBuilder(foldersPath).append("/").append(fileName)
      val language = locale.language
      if (!language.isEmpty()) {
        result.append('_').append(language)
        val country = locale.country
        if (country.isNotEmpty() && withRegion) {
          result.append('_').append(country)
        }
      }
      if (extension.isNotEmpty()) {
        result.append(".").append(extension)
      }
      return result.toString()
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

    fun getBasePathAndFileName(filePath: String): Pair<String, String> {
      val path = Path(filePath)
      val basePath = path.parent?.pathString ?: ""
      val fileName = path.fileName?.pathString ?: ""
      return Pair(basePath, fileName)
    }

    fun getLocalizedPaths(basePath: String, fileName: String): List<String> {
      val fixedPath = Strings.trimStart(Strings.trimEnd(basePath, "/"), "/")
      val result = mutableListOf<String>()
      if (locale != null) {
        //localizations/zh/CN/inspectionDescriptions/name.html
        result.add(convertPathToLocalizationFolderUsage("$fixedPath/$fileName", locale, true))

        //inspectionDescriptions/name_zh_CN.html
        result.add(convertPathToLocaleSuffixUsage("$fixedPath/$fileName", locale, true))

        //localizations/zh/inspectionDescriptions/name.html
        result.add(convertPathToLocalizationFolderUsage("$fixedPath/$fileName", locale, false))

        //inspectionDescriptions/name_zh.html
        result.add(convertPathToLocaleSuffixUsage("$fixedPath/$fileName", locale, false))
      }
      //inspectionDescriptions/name.html
      result.add("$fixedPath/$fileName")
      return result
    }


    fun getFolderLocalizedPaths(filePath: String): List<String> {
      val fileNameAndPath = getBasePathAndFileName(filePath)
      return getFolderLocalizedPaths(fileNameAndPath.first, fileNameAndPath.second)
    }

    fun getFolderLocalizedPaths(basePath: String, fileName: String): List<String> {
      val fixedPath = Strings.trimStart(Strings.trimEnd(basePath, "/"), "/")
      val result = mutableListOf<String>()
      if (locale != null) {
        //localizations/zh/CN/inspectionDescriptions/name.html
        result.add(convertPathToLocalizationFolderUsage("$fixedPath/$fileName", locale, true))

        //localizations/zh/inspectionDescriptions/name.html
        result.add(convertPathToLocalizationFolderUsage("$fixedPath/$fileName", locale, false))
      }
      return result
    }
  }
}