// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl.text

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.impl.zombie.NecromancerAwaker
import com.intellij.openapi.editor.impl.zombie.SpawnRecipe
import com.intellij.openapi.editor.impl.zombie.WeakNecromancer
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private class FocusZoneNecromancerAwaker : NecromancerAwaker<Nothing> {
  override fun awake(project: Project, coroutineScope: CoroutineScope): WeakNecromancer {
    return FocusZoneNecromancer()
  }
}

private class FocusZoneNecromancer : WeakNecromancer("focus-zone") {
  override suspend fun spawn(recipe: SpawnRecipe) {
    if (!FocusModePassFactory.isEnabled()) {
      return
    }

    val psiManager = recipe.project.serviceAsync<PsiManager>()
    val focusZones = readAction {
      val psiFile = psiManager.findFile(recipe.file)
      FocusModePassFactory.calcFocusZones(psiFile)
    } ?: return

    val editor = recipe.editorSupplier()
    withContext(Dispatchers.EDT) {
      FocusModePassFactory.setToEditor(focusZones, editor)
      if (editor is EditorImpl) {
        editor.applyFocusMode()
      }
    }
  }
}
