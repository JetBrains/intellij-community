// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.indentGuide

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.openapi.editor.impl.IndentsModelImpl
import com.intellij.openapi.editor.impl.zombie.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


internal class IndentGuideNecromancerAwaker : NecromancerAwaker<IndentGuideZombie> {
  override fun awake(project: Project, coroutineScope: CoroutineScope): Necromancer<IndentGuideZombie> {
    return IndentGuideNecromancer(project, coroutineScope)
  }
}

private class IndentGuideNecromancer(
  project: Project,
  coroutineScope: CoroutineScope,
) : GravingNecromancer<IndentGuideZombie>(
  project,
  coroutineScope,
  "graved-indent-guides",
  IndentGuideZombie.Necromancy,
) {

  override fun turnIntoZombie(recipe: TurningRecipe): IndentGuideZombie? {
    if (isEnabled()) {
      val indents = (recipe.editor.indentsModel as IndentsModelImpl).indents
      if (indents.isNotEmpty()) {
        return IndentGuideZombie(indents)
      }
    }
    return null
  }

  override suspend fun shouldSpawnZombie(recipe: SpawnRecipe): Boolean {
    return isEnabled()
  }

  override suspend fun spawnZombie(recipe: SpawnRecipe, zombie: IndentGuideZombie?) {
    if (zombie != null && zombie.limbs().isNotEmpty()) {
      val indentGuides = IndentGuides(recipe.document, IndentGuideZombieRenderer)
      runReadAction {
        indentGuides.buildIndents(zombie.limbs())
      }
      val editor = recipe.editorSupplier()
      withContext(Dispatchers.EDT) {
        if (recipe.isValid(editor)) {
          indentGuides.applyIndents(editor)
        }
      }
    }
  }

  private fun isEnabled(): Boolean {
    return isNecromancerEnabled() && isIndentGuideEnabled()
  }

  private fun isIndentGuideEnabled(): Boolean {
    return EditorSettingsExternalizable.getInstance().isIndentGuidesShown
  }

  private fun isNecromancerEnabled(): Boolean {
    return Registry.`is`("cache.indent.guide.model.on.disk", true)
  }
}
