// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl

import com.intellij.lang.Constants.VIRTUAL_FILE_JAVA_LEVEL_KEY
import com.intellij.lang.VirtualFileExtraDataPusher
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.LanguageLevelProjectExtension
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import com.intellij.util.ModalityUiUtil

class VirtualFileJavaLevelInfoPusher(private val project: Project) : VirtualFileExtraDataPusher {

  companion object {
    val LOG = logger<VirtualFileJavaLevelInfoPusher>()
  }

  private val syncedVirtualFiles2Accessors = hashMapOf<VirtualFile, VirtualFileExtraDataPusher.VirtualFileExtraDataAccessor>()
  private var fileJavaLevelChangedListenerCreated = false

  override fun syncStarted(virtualFile: VirtualFile, accessor: VirtualFileExtraDataPusher.VirtualFileExtraDataAccessor) {
    LOG.trace { "syncStarted called: file=${virtualFile.name}" }

    syncedVirtualFiles2Accessors[virtualFile] = accessor

    val fileJavaLevel = ReadAction.compute(ThrowableComputable {
      val psiFile = PsiManager.getInstance(project).findFile(virtualFile)
      if (psiFile !is PsiJavaFile) return@ThrowableComputable null
      psiFile.languageLevel
    }) ?: return
    sendJavaLevelInfo(fileJavaLevel, accessor)

    if (!fileJavaLevelChangedListenerCreated) {
      LOG.trace("syncStarted: create file java level change listener")
      startListenToFileJavaLevelChange()
      fileJavaLevelChangedListenerCreated = true
    }
  }

  private fun startListenToFileJavaLevelChange() {
    project.messageBus.connect().subscribe(VirtualFileJavaLevelChangeListener.TOPIC, object : VirtualFileJavaLevelChangeListener {
      override fun levelChanged(virtualFile: VirtualFile, newLevel: LanguageLevel?) {
        ModalityUiUtil.invokeLaterIfNeeded(ModalityState.defaultModalityState(), Runnable {
          val accessor = syncedVirtualFiles2Accessors[virtualFile] ?: return@Runnable
          LOG.trace { "VirtualFileJavaLevelChangeListener.levelChanged called for synced file: file=${virtualFile.name} newLevel=$newLevel" }
          sendJavaLevelInfo(newLevel, accessor)
        })
      }
    })
  }

  private fun sendJavaLevelInfo(fileJavaLevel: LanguageLevel?,
                                accessor: VirtualFileExtraDataPusher.VirtualFileExtraDataAccessor) {
    LOG.trace("sendJavaLevelInfo called")

    val projectJavaLevel = LanguageLevelProjectExtension.getInstance(project).languageLevel
    val encodedValue = if (fileJavaLevel == projectJavaLevel) null else fileJavaLevel.toString()
    if (accessor.isDisposed()) return

    LOG.trace { "sendJavaLevelInfo: send new lang level info: encodedValue=$encodedValue" }
    accessor.setExtraData(VIRTUAL_FILE_JAVA_LEVEL_KEY, encodedValue)
  }

  override fun syncFinished(virtualFile: VirtualFile) {
    LOG.trace { "syncFinished called: file=${virtualFile.name}" }
    syncedVirtualFiles2Accessors.remove(virtualFile)
  }
}