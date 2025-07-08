// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uast

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
import com.intellij.psi.PsiManager
import org.jetbrains.uast.UastLanguagePlugin

@Service(Service.Level.PROJECT)
public class UastModificationTracker internal constructor(private val project: Project) : ModificationTracker, Disposable {
  private var languageTrackers: List<ModificationTracker>

  init {
    languageTrackers = getLanguageTrackers(project)

    UastLanguagePlugin.EP.addChangeListener(Runnable {
      languageTrackers = getLanguageTrackers(project)
    }, this)
  }

  override fun getModificationCount(): Long {
    return languageTrackers.sumOf { it.modificationCount }
  }

  override fun dispose() {
    // do nothing
  }

  public companion object {
    @JvmStatic
    public fun getInstance(project: Project): UastModificationTracker {
      return project.getService(UastModificationTracker::class.java)
    }

    private fun getLanguageTrackers(project: Project): List<ModificationTracker> {
      val psiManager = PsiManager.getInstance(project)
      return getUastMetaLanguages().map { psiManager.modificationTracker.forLanguage(it) }
    }
  }
}