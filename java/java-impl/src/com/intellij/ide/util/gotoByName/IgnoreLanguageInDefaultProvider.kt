// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.gotoByName

import com.intellij.lang.Language
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.RequiredElement
import com.intellij.util.xmlb.annotations.Attribute
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class IgnoreLanguageInDefaultProvider {

  @RequiredElement
  @JvmField
  @Attribute("language")
  var language: String = ""

  fun languageInstance(): Language =
    Language.findLanguageByID(language) ?: error("No language with id $language found")


  companion object {
    @ApiStatus.Internal
    val EP_NAME: ExtensionPointName<IgnoreLanguageInDefaultProvider> =
      ExtensionPointName.create<IgnoreLanguageInDefaultProvider>("com.intellij.gotoByName.defaultProvider.ignoreLanguage")

    @JvmStatic
    @ApiStatus.Internal
    fun getIgnoredLanguages(): Set<Language> =
      EP_NAME.extensionList.mapTo(mutableSetOf()) { it.languageInstance() }
  }
}