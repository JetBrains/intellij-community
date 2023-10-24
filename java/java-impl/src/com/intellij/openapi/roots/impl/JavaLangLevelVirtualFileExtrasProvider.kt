// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl

import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.LanguageLevelProjectExtension
import com.intellij.openapi.roots.NullableLanguageLevelHolder
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileExtrasProvider
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.reflect.KType
import kotlin.reflect.typeOf

class JavaLangLevelVirtualFileExtrasProvider : VirtualFileExtrasProvider<NullableLanguageLevelHolder> {
  companion object {
    val LOG = logger<JavaLangLevelVirtualFileExtrasProvider>()
  }

  override val id: String = "projectLangLevel"

  override val dataType: KType
    get() = typeOf<NullableLanguageLevelHolder>()

  override fun getValues(project: Project, virtualFile: VirtualFile): Flow<NullableLanguageLevelHolder> {
    return project.service<JavaLangLevelVirtualFileProviderProjectService>().getValues(virtualFile)
  }
}

@Service(Service.Level.PROJECT)
class JavaLangLevelVirtualFileProviderProjectService(val project: Project) {
  private val syncedVirtualFiles2sendChannels = hashMapOf<VirtualFile, SendChannel<NullableLanguageLevelHolder>>()
  private var fileJavaLevelChangedListenerCreated = false

  fun getValues(virtualFile: VirtualFile): Flow<NullableLanguageLevelHolder> {
    if (!fileJavaLevelChangedListenerCreated) {
      //LOG.trace("syncStarted: create file java level change listener")
      startListenToFileJavaLevelChange()
      fileJavaLevelChangedListenerCreated = true
    }

    return callbackFlow {
      val fileJavaLevel = readAction {
        val psiFile = PsiManager.getInstance(project).findFile(virtualFile)
        if (psiFile !is PsiJavaFile) return@readAction null
        psiFile.languageLevel
      }

      if (fileJavaLevel != null) {
        val projectJavaLevel = LanguageLevelProjectExtension.getInstance(project).languageLevel
        val langLevelToSend = if (fileJavaLevel == projectJavaLevel) null else fileJavaLevel
        val encoded = NullableLanguageLevelHolder(langLevelToSend)
        this.trySend(encoded)
      }

      synchronized(syncedVirtualFiles2sendChannels) {
        syncedVirtualFiles2sendChannels[virtualFile] = this
      }

      awaitClose {
        synchronized(syncedVirtualFiles2sendChannels) {
          syncedVirtualFiles2sendChannels.remove(virtualFile)
        }
      }
    }
  }

  private fun startListenToFileJavaLevelChange() {
    project.messageBus.connect().subscribe(VirtualFileJavaLevelChangeListener.TOPIC, object : VirtualFileJavaLevelChangeListener {
      override fun levelChanged(virtualFile: VirtualFile, newLevel: LanguageLevel?) {
        val channel = synchronized(syncedVirtualFiles2sendChannels) {
          syncedVirtualFiles2sendChannels[virtualFile]
        } ?: return
        //LOG.trace { "VirtualFileJavaLevelChangeListener.levelChanged called for synced file: file=${virtualFile.name} newLevel=$newLevel" }
        val projectJavaLevel = LanguageLevelProjectExtension.getInstance(project).languageLevel
        val langLevelToSend = if (newLevel == projectJavaLevel) null else newLevel
        val encoded = NullableLanguageLevelHolder(langLevelToSend)
        channel.trySend(encoded)
      }
    })
  }
}