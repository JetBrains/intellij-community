// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uast

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
import com.intellij.psi.PsiManager
import org.jetbrains.uast.UastLanguagePlugin

@Service
class UastModificationTracker internal constructor(val project: Project) : ModificationTracker, Disposable {
  private var languageTrackers: List<ModificationTracker>

  init {
    languageTrackers = getLanguageTrackers(project)

    UastLanguagePlugin.extensionPointName.addChangeListener(Runnable {
      languageTrackers = getLanguageTrackers(project)
    }, this)
  }

  override fun getModificationCount(): Long {
    return languageTrackers.sumOf { it.modificationCount }
  }

  override fun dispose() {
    // do nothing
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): UastModificationTracker {
      return project.getService(UastModificationTracker::class.java)
    }

    @JvmStatic
    private fun getLanguageTrackers(project: Project): List<ModificationTracker> {
      val languages = UastMetaLanguage.Holder.getLanguages()
      val psiManager = PsiManager.getInstance(project)
      return languages.map { psiManager.modificationTracker.forLanguage(it) }
    }
  }
}