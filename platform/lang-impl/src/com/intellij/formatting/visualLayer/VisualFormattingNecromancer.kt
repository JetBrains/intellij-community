// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.formatting.visualLayer

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.zombie.CleaverNecromancer
import com.intellij.openapi.editor.impl.zombie.Recipe
import com.intellij.openapi.editor.impl.zombie.SpawnRecipe
import com.intellij.openapi.editor.impl.zombie.TurningRecipe
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.ide.diagnostic.startUpPerformanceReporter.FUSProjectHotStartUpMeasurer
import com.intellij.platform.ide.diagnostic.startUpPerformanceReporter.FUSProjectHotStartUpMeasurer.MarkupType
import kotlinx.coroutines.CoroutineScope


internal class VisualFormattingNecromancer(
  project: Project,
  coroutineScope: CoroutineScope,
) : CleaverNecromancer<VisualFormattingZombie, VisualFormattingLimb>(
  project,
  coroutineScope,
  "graved-visual-formatting",
  VisualFormattingZombie.Necromancy,
) {

  override fun enoughMana(recipe: Recipe): Boolean {
    return Registry.`is`("cache.visual.formatting.on.disk", true)
  }

  override fun cutIntoLimbs(recipe: TurningRecipe): List<VisualFormattingLimb> {
    val editor = recipe.editor
    if (!VisualFormattingLayerService.isEnabledForEditor(editor)) {
      return emptyList()
    }
    return collectCurrentElements(editor)
      .map { VisualFormattingLimb.fromElement(it) }
  }

  private fun collectCurrentElements(editor: Editor): List<VisualFormattingLayerElement> {
    val elements = mutableListOf<VisualFormattingLayerElement>()
    val docLength = editor.document.textLength

    // Collect inline inlays
    editor.inlayModel.getInlineElementsInRange(0, docLength)
      .filter { inlay ->
        val renderer = inlay.renderer
        renderer is InlayPresentation && !renderer.vertical
      }
      .forEach { inlay ->
        val renderer = inlay.renderer as InlayPresentation
        elements.add(VisualFormattingLayerElement.InlineInlay(inlay.offset, renderer.fillerLength))
      }

    // Collect block inlays
    editor.inlayModel.getBlockElementsInRange(0, docLength)
      .filter { inlay ->
        val renderer = inlay.renderer
        renderer is InlayPresentation && renderer.vertical
      }
      .forEach { inlay ->
        val renderer = inlay.renderer as InlayPresentation
        elements.add(VisualFormattingLayerElement.BlockInlay(inlay.offset, renderer.fillerLength))
      }

    // Collect foldings marked with visualFormattingElementKey
    editor.foldingModel.allFoldRegions
      .filter { it.getUserData(visualFormattingElementKey) == true }
      .forEach { foldRegion ->
        elements.add(VisualFormattingLayerElement.Folding(foldRegion.startOffset, foldRegion.endOffset - foldRegion.startOffset))
      }

    return elements
  }

  override suspend fun spawnZombie(
    recipe: SpawnRecipe,
    limbs: List<VisualFormattingLimb>,
  ): (Editor) -> Unit {
    val zombie = VisualFormattingZombie.Necromancy.formZombie(limbs)
    return { editor ->
      zombie.applyState(editor)
      FUSProjectHotStartUpMeasurer.markupRestored(recipe, MarkupType.CODE_FOLDING)
    }
  }
}
