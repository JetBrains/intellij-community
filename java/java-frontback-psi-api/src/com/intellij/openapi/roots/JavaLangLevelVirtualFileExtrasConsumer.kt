// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots

import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileExtrasConsumer
import com.intellij.pom.java.LanguageLevel
import kotlinx.serialization.Serializable
import kotlin.reflect.KType
import kotlin.reflect.typeOf

open class JavaLangLevelVirtualFileExtrasConsumer : VirtualFileExtrasConsumer<NullableLanguageLevelHolder> {
  companion object {
    val LOG = logger<JavaLangLevelVirtualFileExtrasConsumer>()
  }

  override val id: String = "projectLangLevel"

  override val dataType: KType
    get() = typeOf<NullableLanguageLevelHolder>()

  override fun consumeValue(project: Project, virtualFile: VirtualFile, value: NullableLanguageLevelHolder) {
    //LOG.trace { "tryConsume with accepted key: file=${virtualFile.name} value=$value" }

    project.service<JavaLevelPerVirtualFileHolder>().setLangLevel(virtualFile, value.languageLevel)
  }
}

@Serializable
class NullableLanguageLevelHolder(val languageLevel: LanguageLevel?)