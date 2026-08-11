// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.gotoByName

import com.intellij.lang.DependentLanguage
import com.intellij.lang.Language
import com.intellij.lang.LanguageUtil
import com.intellij.navigation.NavigationItem
import com.intellij.navigation.PsiElementNavigationItem
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.psi.PsiElement
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import javax.swing.Icon

private val LOG = Logger.getInstance(LanguageRef::class.java)

class LanguageRef private constructor(
  val id: String,
  @field:Nls val displayName: String,
  iconProvider: () -> Icon?,
) {
  private val lazyIcon: Lazy<Icon?> = lazy(LazyThreadSafetyMode.PUBLICATION, iconProvider)
  val icon: Icon? get() = lazyIcon.value

  constructor(id: String, displayName: @Nls String, icon: Icon?) : this(id, displayName, { icon })

  companion object {
    private val cache = ContainerUtil.createConcurrentWeakMap<Language, LanguageRef>()

    @JvmStatic
    fun forLanguage(lang: Language): LanguageRef {
      val language = nonDependentLanguage(lang) ?: lang
      return cache.computeIfAbsent(language) {
        LanguageRef(it.id, it.displayName) {
          runCatching {
            it.associatedFileType?.icon
          }.getOrLogException(LOG)
        }
      }
    }

    private fun nonDependentLanguage(lang: Language): Language? =
      if (lang is DependentLanguage) lang.baseLanguage else lang

    @JvmStatic
    fun forNavigationitem(item: NavigationItem): LanguageRef? = when (item) {
      is PsiElement -> forLanguage(item.language)
      is PsiElementNavigationItem -> item.targetElement?.language?.let { forLanguage(it) }
      else -> null
    }

    @JvmStatic
    fun forAllLanguages(): List<LanguageRef> {
      return Language.getRegisteredLanguages()
        .filter { it !== Language.ANY && it !is DependentLanguage }
        .sortedWith(LanguageUtil.LANGUAGE_COMPARATOR)
        .mapNotNull {
          runCatching {
            forLanguage(it)
          }.getOrLogException(LOG)
        }
    }
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as LanguageRef

    return id == other.id
  }

  override fun hashCode(): Int {
    return id.hashCode()
  }

  override fun toString(): String = "LanguageRef(id=$id, displayName=$displayName)"
}

class FileTypeRef private constructor(
  val name: @NonNls String,
  val displayName: @Nls String,
  iconProvider: () -> Icon?,
) {
  private val lazyIcon: Lazy<Icon?> = lazy(LazyThreadSafetyMode.PUBLICATION, iconProvider)
  val icon: Icon? get() = lazyIcon.value

  constructor(name: @NonNls String, displayName: @Nls String, icon: Icon?) : this(name, displayName, { icon })

  companion object {
    private val cache = ContainerUtil.createConcurrentWeakMap<FileType, FileTypeRef>()

    @JvmStatic
    fun forFileType(fileType: FileType): FileTypeRef =
      cache.computeIfAbsent(fileType) {
        FileTypeRef(it.name, it.displayName) {
          runCatching {
            it.icon
          }.getOrLogException(LOG)
        }
      }

    @JvmStatic
    fun forAllFileTypes(): List<FileTypeRef> {
      return FileTypeManager.getInstance().registeredFileTypes
        .sortedWith(FileTypeComparator.INSTANCE)
        .mapNotNull{
          runCatching {
            forFileType(it)
          }.getOrLogException(LOG)
        }
    }
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as FileTypeRef

    return name == other.name
  }

  override fun hashCode(): Int {
    return name.hashCode()
  }

  override fun toString(): String = "FileTypeRef(name=$name, displayName=$displayName)"
}
