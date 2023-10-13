// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectExtras
import com.intellij.openapi.roots.LanguageLevelProjectExtension.LanguageLevelChangeListener
import com.intellij.pom.java.LanguageLevel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModuleBuilder
import kotlin.reflect.KClass

// TODO: register
class JavaLangLevelProjectExtras : ProjectExtras<LanguageLevelHolder> {

  override val id: String = "projectLangLevel"

  override fun getValues(project: Project): Flow<LanguageLevelHolder> {
    return callbackFlow {
      trySend(LanguageLevelHolder(LanguageLevelProjectExtension.getInstance(project).languageLevel))
      project.messageBus.connect(this).subscribe(LanguageLevelProjectExtension.LANGUAGE_LEVEL_CHANGED_TOPIC, LanguageLevelChangeListener {
        trySend(LanguageLevelHolder(LanguageLevelProjectExtension.getInstance(project).languageLevel))
      })
    }
  }

  override fun emitValue(project: Project, value: LanguageLevelHolder) {
    LanguageLevelProjectExtension.getInstance(project).languageLevel = value.languageLevel
  }

  override val dataClass: KClass<LanguageLevelHolder>
    get() = LanguageLevelHolder::class

  override fun registerSerializers(moduleBuilder: SerializersModuleBuilder) {
    moduleBuilder.contextual(LanguageLevelHolder::class, LanguageLevelHolder.serializer())
  }
}

@Serializable
class LanguageLevelHolder(val languageLevel: LanguageLevel)