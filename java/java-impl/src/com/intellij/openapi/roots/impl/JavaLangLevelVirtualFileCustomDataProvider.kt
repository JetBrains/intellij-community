// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl

import com.intellij.java.frontback.psi.impl.NullableLanguageLevelHolder
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileCustomDataProvider
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import com.intellij.util.messages.impl.subscribeAsFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.reflect.KType
import kotlin.reflect.typeOf

class JavaLangLevelVirtualFileCustomDataProvider : VirtualFileCustomDataProvider<NullableLanguageLevelHolder> {
  companion object {
    val LOG = logger<JavaLangLevelVirtualFileCustomDataProvider>()
  }

  override val id: String = "virtualFileJavaLangLevel"

  override val dataType: KType
    get() = typeOf<NullableLanguageLevelHolder>()

  override fun getValues(project: Project, virtualFile: VirtualFile): Flow<NullableLanguageLevelHolder> {
    return project.messageBus.subscribeAsFlow(VirtualFileJavaLanguageLevelListener.TOPIC) {
      trySend(getFileJavaLanguageLevel(virtualFile, project))

      val virtualFileRef = virtualFile
      object : VirtualFileJavaLanguageLevelListener {
        override fun levelChanged(virtualFile: VirtualFile, newLevel: LanguageLevel?) {
          if (virtualFile != virtualFileRef) return
          trySend(newLevel)
        }
      }
    }.map { NullableLanguageLevelHolder(it) }
  }

  private suspend fun getFileJavaLanguageLevel(virtualFile: VirtualFile, project: Project) = readAction {
    val psiFile = PsiManager.getInstance(project).findFile(virtualFile)
    if (psiFile !is PsiJavaFile) return@readAction null
    return@readAction psiFile.languageLevel
  }
}