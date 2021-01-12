// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uast

import com.intellij.lang.Language
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
import com.intellij.psi.PsiManager
import org.jetbrains.annotations.ApiStatus

@Service
@ApiStatus.Experimental
class UastModificationTracker internal constructor(val project: Project) : ModificationTracker {
  private val languageTrackers: List<ModificationTracker>

  init {
    val languages = Language.findInstance(UastMetaLanguage::class.java).matchingLanguages
    val psiManager = PsiManager.getInstance(project)
    languageTrackers = languages.map { psiManager.modificationTracker.forLanguage(it) }
  }

  override fun getModificationCount(): Long = languageTrackers.sumOf { it.modificationCount }

  companion object {
    @JvmStatic
    @ApiStatus.Experimental
    fun getInstance(project: Project): UastModificationTracker {
      return project.getService(UastModificationTracker::class.java)
    }
  }
}