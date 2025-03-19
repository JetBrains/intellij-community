// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.frontback.psi.impl

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectCustomDataSynchronizer
import com.intellij.openapi.roots.LanguageLevelProjectExtension
import com.intellij.pom.java.LanguageLevel
import com.intellij.util.messages.impl.subscribeAsFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlin.reflect.KType
import kotlin.reflect.typeOf

class JavaLangLevelProjectCustomDataSynchronizer : ProjectCustomDataSynchronizer<LanguageLevelHolder> {

  override val id: String = "projectLangLevel"

  override val dataType: KType
    get() = typeOf<LanguageLevelHolder>()

  override fun getValues(project: Project): Flow<LanguageLevelHolder> {
    return project.messageBus.subscribeAsFlow(LanguageLevelProjectExtension.LANGUAGE_LEVEL_CHANGED_TOPIC) {
      trySend(Unit)
      LanguageLevelProjectExtension.LanguageLevelChangeListener {
        trySend(Unit)
      }
    }.map { LanguageLevelHolder(LanguageLevelProjectExtension.getInstance(project).languageLevel) }
  }

  override suspend fun consumeValue(project: Project, value: LanguageLevelHolder) {
    LanguageLevelProjectExtension.getInstance(project).languageLevel = value.languageLevel
  }
}

@Serializable
class LanguageLevelHolder(val languageLevel: LanguageLevel)

@Serializable
class NullableLanguageLevelHolder(val languageLevel: LanguageLevel?)

@Serializable
enum class ClassFileInformationType {
  JAVA_CLASS_FILE, JAVA_CLASS_FILE_OUTSIDE
}
@Serializable
class ClassFileInformation(val classFileInformationType: ClassFileInformationType?)
