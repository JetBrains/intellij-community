// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.indentGuide

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.IndentGuideDescriptor
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.openapi.editor.impl.IndentsModelImpl
import com.intellij.openapi.editor.impl.zombie.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import kotlinx.coroutines.CoroutineScope


internal class IndentGuideNecromancerAwaker : NecromancerAwaker<IndentGuideZombie> {
  override fun awake(project: Project, coroutineScope: CoroutineScope): Necromancer<IndentGuideZombie> {
    return IndentGuideNecromancer(project, coroutineScope)
  }
}

private class IndentGuideNecromancer(
  project: Project,
  coroutineScope: CoroutineScope,
) : CleaverNecromancer<IndentGuideZombie, IndentGuideDescriptor>(
  project,
  coroutineScope,
  "graved-indent-guides",
  IndentGuideZombie.Necromancy,
) {

  override fun enoughMana(recipe: Recipe): Boolean {
    return Registry.`is`("cache.indent.guide.model.on.disk", true) &&
           EditorSettingsExternalizable.getInstance().isIndentGuidesShown
  }

  override fun cutIntoLimbs(recipe: TurningRecipe): List<IndentGuideDescriptor> {
    return (recipe.editor.indentsModel as IndentsModelImpl).indents
  }

  override suspend fun spawnZombie(
    recipe: SpawnRecipe,
    limbs: List<IndentGuideDescriptor>,
  ): (Editor) -> Unit {
    val indentGuides = IndentGuides(recipe.document, IndentGuideZombieRenderer)
    runReadAction {
      indentGuides.buildIndents(limbs)
    }
    return { editor ->
      indentGuides.applyIndents(editor)
    }
  }
}
