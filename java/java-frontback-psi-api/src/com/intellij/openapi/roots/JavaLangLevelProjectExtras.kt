// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectExtras
import com.intellij.openapi.roots.LanguageLevelProjectExtension.LanguageLevelChangeListener
import com.intellij.pom.java.LanguageLevel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.Serializable
import kotlin.reflect.KType
import kotlin.reflect.typeOf

class JavaLangLevelProjectExtras : ProjectExtras<LanguageLevelHolder> {

  override val id: String = "projectLangLevel"

  override val dataType: KType
    get() = typeOf<LanguageLevelHolder>()

  override fun getValues(project: Project): Flow<LanguageLevelHolder> {
    return callbackFlow {
      val languageLevel = LanguageLevelProjectExtension.getInstance(project).languageLevel
      val element = LanguageLevelHolder(languageLevel)
      trySend(element)
      project.messageBus.connect(this).subscribe(LanguageLevelProjectExtension.LANGUAGE_LEVEL_CHANGED_TOPIC, LanguageLevelChangeListener {
        trySend(LanguageLevelHolder(LanguageLevelProjectExtension.getInstance(project).languageLevel))
      })
      awaitClose()
    }
  }

  override fun consumeValue(project: Project, value: LanguageLevelHolder) {
    LanguageLevelProjectExtension.getInstance(project).languageLevel = value.languageLevel
  }
}

@Serializable
class LanguageLevelHolder(val languageLevel: LanguageLevel)