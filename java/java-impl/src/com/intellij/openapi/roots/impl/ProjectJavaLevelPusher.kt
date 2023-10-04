// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl

import com.intellij.lang.Constants.PROJECT_JAVA_LEVEL_KEY
import com.intellij.lang.ProjectExtraDataPusher
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.LanguageLevelProjectExtension
import com.intellij.util.ModalityUiUtil

class ProjectJavaLevelPusher(private val project: Project) : ProjectExtraDataPusher {

  companion object {
    val LOG = logger<ProjectJavaLevelPusher>()
  }

  override fun syncStarted(accessor: ProjectExtraDataPusher.ProjectExtraDataAccessor, parentDisposable: Disposable) {
    getAndSendLangLevelInfo(accessor)

    project.messageBus.connect(parentDisposable)
      .subscribe(LanguageLevelProjectExtension.LANGUAGE_LEVEL_CHANGED_TOPIC,
                 LanguageLevelProjectExtension.LanguageLevelChangeListener { getAndSendLangLevelInfo(accessor) })
  }

  override fun syncFinished() {
    // nothing
  }

  private fun getAndSendLangLevelInfo(accessor: ProjectExtraDataPusher.ProjectExtraDataAccessor) {
    val level = LanguageLevelProjectExtension.getInstance(project).getLanguageLevel().toString()

    ModalityUiUtil.invokeLaterIfNeeded(ModalityState.defaultModalityState(),
                                       Runnable {
                                         if (accessor.isDisposed()) return@Runnable
                                         LOG.trace("[!] getAndSendLangLevelInfo: ($PROJECT_JAVA_LEVEL_KEY,$level)")
                                         accessor.setExtraData(PROJECT_JAVA_LEVEL_KEY, level)
                                       })
  }

}