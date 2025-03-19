// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints

import com.intellij.codeInsight.daemon.impl.HintRenderer
import com.intellij.codeInsight.daemon.impl.ParameterHintsPresentationManager
import com.intellij.codeInsight.hints.ParameterHintsPass.HintData
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readActionBlocking
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.impl.zombie.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ide.diagnostic.startUpPerformanceReporter.FUSProjectHotStartUpMeasurer
import com.intellij.platform.ide.diagnostic.startUpPerformanceReporter.FUSProjectHotStartUpMeasurer.MarkupType
import com.intellij.psi.PsiManager
import com.intellij.util.SmartList
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private class ParameterHintsNecromancerAwaker : NecromancerAwaker<ParameterHintsZombie> {
  override fun awake(project: Project, coroutineScope: CoroutineScope): Necromancer<ParameterHintsZombie> {
    return ParameterHintsNecromancer(project, coroutineScope)
  }
}

private class ParameterHintsNecromancer(
  project: Project,
  coroutineScope: CoroutineScope,
) : GravingNecromancer<ParameterHintsZombie>(
  project,
  coroutineScope,
  "graved-parameter-hints",
  ParameterHintsNecromancy
) {

  override fun turnIntoZombie(recipe: TurningRecipe): ParameterHintsZombie? {
    if (isEnabled()) {
      val editor = recipe.editor
      val hints = ParameterHintsPresentationManager.getInstance()
        .getParameterHintsInRange(editor, 0, editor.document.textLength)
        .mapNotNull { inlay -> inlay.toHintData() }
        .toList()
      return ParameterHintsZombie(hints)
    } else {
      return null
    }
  }

  override suspend fun shouldSpawnZombie(recipe: SpawnRecipe): Boolean {
    return isEnabled() && isEnabledForLang(recipe.project, recipe.file)
  }

  override suspend fun spawnZombie(recipe: SpawnRecipe, zombie: ParameterHintsZombie?) {
    if (zombie != null && zombie.limbs().isNotEmpty()) {
      val zombieHints = Int2ObjectOpenHashMap<MutableList<HintData>>()
      for ((offset, hint) in zombie.limbs()) {
        val list: MutableList<HintData> = zombieHints.getOrPut(offset) { SmartList() }
        val hint1 = if (isDebug()) {
          debugHintData(hint)
        } else {
          hint
        }
        list.add(hint1)
      }
      val editor = recipe.editorSupplier()
      withContext(Dispatchers.EDT) {
        writeIntentReadAction {
          if (recipe.isValid(editor)) {
            ParameterHintsUpdater(
              editor,
              listOf(),
              zombieHints,
              Int2ObjectOpenHashMap(0),
              true
            ).update()
            FUSProjectHotStartUpMeasurer.markupRestored(recipe, MarkupType.PARAMETER_HINTS)
          }
        }
      }
    }
  }

  private fun Inlay<*>.toHintData(): Pair<Int, HintData>? {
    val renderer = this.renderer as? HintRenderer
    val text = renderer?.text
    return if (renderer != null && text != null) {
      Pair(offset, HintData(text, isRelatedToPrecedingText, renderer.widthAdjustment))
    } else {
      null
    }
  }

  private fun debugHintData(hintData: HintData): HintData {
    val text = hintData.presentationText
    val colonIndex = text.lastIndexOf(':')
    val debugText = if (colonIndex == -1) {
      "$text?"
    } else {
      text.substring(0, colonIndex) + "?:"
    }
    return HintData(debugText, hintData.relatesToPrecedingText, hintData.widthAdjustment)
  }

  private suspend fun isEnabledForLang(project: Project, file: VirtualFile): Boolean {
    val psiManager = project.serviceAsync<PsiManager>()
    val language = readActionBlocking { psiManager.findFile(file)?.language }
    return language != null &&
           InlayParameterHintsExtension.forLanguage(language) != null &&
           isParameterHintsEnabledForLanguage(language)
  }

  private fun isEnabled(): Boolean = Registry.`is`("cache.inlay.hints.on.disk", true)
  private fun isDebug(): Boolean = Registry.`is`("cache.markup.debug", false)
}
