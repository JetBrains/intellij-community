// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl

import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.impl.EditorMarkupModelImpl
import com.intellij.openapi.editor.impl.zombie.*
import com.intellij.openapi.editor.markup.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.ui.GridBag
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Container


internal class TrafficLightNecromancerAwaker : NecromancerAwaker<TrafficLightZombie> {
  override fun awake(project: Project, coroutineScope: CoroutineScope): Necromancer<TrafficLightZombie> {
    return TrafficLightNecromancer(project, coroutineScope)
  }
}

private class TrafficLightNecromancer(
  project: Project,
  coroutineScope: CoroutineScope,
) : GravingNecromancer<TrafficLightZombie>(
  project,
  coroutineScope,
  "graved-traffic-light",
  TrafficLightZombie.Necromancy(project),
) {

  override fun enoughMana(recipe: Recipe): Boolean {
    return Registry.`is`("cache.traffic.light.on.disk", true)
  }

  override fun turnIntoZombie(recipe: TurningRecipe): TrafficLightZombie? {
    val markupModel = recipe.editor.markupModel
    if (markupModel is EditorMarkupModelImpl) {
      val analyzerStatus = markupModel.getCurrentStatus()
      return TrafficLightZombie(
        analyzerStatus.icon,
        analyzerStatus.title,
        analyzerStatus.details,
        analyzerStatus.showNavigation,
        analyzerStatus.isTextStatus(),
        analyzerStatus.controller.isToolbarEnabled,
        analyzerStatus.expandedStatus,
      )
    }
    return null
  }

  override suspend fun spawnZombie(recipe: SpawnRecipe, zombie: TrafficLightZombie?) {
    if (zombie != null) {
      @Suppress("HardCodedStringLiteral")
      val restoredStatus = AnalyzerStatus(
        zombie.icon,
        zombie.title,
        zombie.details,
        ZombieController(zombie.isToolbarEnabled),
      )
      restoredStatus
        .withAnalyzingType(AnalyzingType.PARTIAL)
        .withNavigation(zombie.showNavigation)
        .withExpandedStatus(zombie.expandedStatus)
      val editor = recipe.editorSupplier.invoke()
      withContext(Dispatchers.EDT) {
        if (recipe.isValid(editor)) {
          val markupModel = editor.markupModel
          if (markupModel is EditorMarkupModelImpl) {
            markupModel.changeStatus(restoredStatus)
          }
        }
      }
    }
  }
}

private class ZombieController(private val isToolbarEnabled: Boolean) : UIController {
  override fun isToolbarEnabled(): Boolean = isToolbarEnabled
  override fun getAvailableLevels(): List<InspectionsLevel?> = emptyList()
  override fun getHighlightLevels(): List<LanguageHighlightLevel?> = emptyList()
  override fun setHighLightLevel(newLevel: LanguageHighlightLevel) {}
  override fun fillHectorPanels(container: Container, gc: GridBag) {}
  override fun canClosePopup(): Boolean = false
  override fun onClosePopup() {}
  override fun toggleProblemsView() {}
}
