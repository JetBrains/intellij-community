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
  private val languagesTracker: ModificationTracker

  init {
    val language = Language.findInstance(UastMetaLanguage::class.java)
    val psiManager = PsiManager.getInstance(project)
    languagesTracker = psiManager.modificationTracker.forLanguages { language.matchesLanguage(it) }
  }

  override fun getModificationCount(): Long = languagesTracker.modificationCount

  companion object {
    @JvmStatic
    @ApiStatus.Experimental
    fun getInstance(project: Project) : UastModificationTracker {
      return project.getService(UastModificationTracker::class.java)
    }
  }
}