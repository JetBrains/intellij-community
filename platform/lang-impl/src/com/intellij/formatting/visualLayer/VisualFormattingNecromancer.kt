// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.formatting.visualLayer

import com.intellij.codeInsight.actions.ReaderModeSettings
import com.intellij.formatting.visualLayer.VisualFormattingZombie.Limb
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.FoldRegion
import com.intellij.openapi.editor.impl.zombie.CleaverNecromancer
import com.intellij.openapi.editor.impl.zombie.Recipe
import com.intellij.openapi.editor.impl.zombie.SpawnRecipe
import com.intellij.openapi.editor.impl.zombie.TurningRecipe
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.application
import kotlinx.coroutines.CoroutineScope


internal class VisualFormattingNecromancer(
  project: Project,
  coroutineScope: CoroutineScope,
) : CleaverNecromancer<VisualFormattingZombie, Limb>(
  project,
  coroutineScope,
  "graved-visual-formatting",
  VisualFormattingZombie.Necromancy,
) {

  override fun enoughMana(recipe: Recipe): Boolean {
    return Registry.`is`("cache.visual.formatting.on.disk", true)
  }

  override fun cutIntoLimbs(recipe: TurningRecipe): List<Limb> {
    val editor = recipe.editor
    return if (isVisualFormattingEnabled(editor)) {
      limbs(editor)
    } else {
      emptyList()
    }
  }

  override suspend fun spawnZombie(
    recipe: SpawnRecipe,
    limbs: List<Limb>,
  ): ((Editor) -> Unit)? {
    if (!isReaderModeEnabled(recipe)) {
      // `isReaderModeEnabled` and `isVisualFormattingEnabled` are slightly different,
      //  so can't use `enoughMana` api
      return null
    }
    val elements = limbs.map { it.toElement() }
    val service = application.serviceAsync<VisualFormattingLayerService>()
    return { editor ->
      ReaderModeSettings.applyReaderMode(recipe.project, editor, recipe.file)
      service.applyVisualFormattingLayerElementsToEditor(editor, elements)
    }
  }

  private fun limbs(editor: Editor): List<Limb> {
    val docLength = editor.document.textLength
    val inlayModel = editor.inlayModel
    val inline = inlayModel.getInlineElementsInRange(0, docLength, InlayPresentation::class.java)
    val block = inlayModel.getBlockElementsInRange(0, docLength, InlayPresentation::class.java)
    val folding = editor.foldingModel.allFoldRegions.filter(this::isVisualFormatting)
    val size = inline.size + block.size + folding.size
    if (size == 0) {
      return emptyList()
    }
    val limbs = ArrayList<Limb>(size)
    for (inlay in inline) {
      limbs.add(Limb.Inline(inlay.offset, inlay.renderer.fillerLength))
    }
    for (inlay in block) {
      limbs.add(Limb.Block(inlay.offset, inlay.renderer.fillerLength))
    }
    for (region in folding) {
      limbs.add(Limb.Folding(region.startOffset, region.endOffset - region.startOffset))
    }
    return limbs
  }

  private fun isVisualFormatting(region: FoldRegion): Boolean {
    return region.getUserData(visualFormattingElementKey) == true
  }

  private fun isVisualFormattingEnabled(editor: Editor): Boolean {
    return VisualFormattingLayerService.isEnabledForEditor(editor)
  }

  private fun isReaderModeEnabled(recipe: SpawnRecipe): Boolean {
    return ReaderModeSettings.getInstance(recipe.project).enabled
  }
}
