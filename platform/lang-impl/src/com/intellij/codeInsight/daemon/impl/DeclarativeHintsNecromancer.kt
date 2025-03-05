// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl

import com.intellij.codeInsight.hints.InlayHintsSettings
import com.intellij.codeInsight.hints.declarative.DeclarativeInlayHintsSettings
import com.intellij.codeInsight.hints.declarative.InlayActionPayload
import com.intellij.codeInsight.hints.declarative.PsiPointerInlayActionPayload
import com.intellij.codeInsight.hints.declarative.impl.*
import com.intellij.codeInsight.hints.declarative.impl.DeclarativeInlayHintsPass.PreprocessedInlayData
import com.intellij.codeInsight.hints.declarative.impl.inlayRenderer.DeclarativeIndentedBlockInlayRenderer
import com.intellij.codeInsight.hints.declarative.impl.util.TinyTree
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readActionBlocking
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.editor.impl.zombie.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ide.diagnostic.startUpPerformanceReporter.FUSProjectHotStartUpMeasurer
import com.intellij.platform.ide.diagnostic.startUpPerformanceReporter.FUSProjectHotStartUpMeasurer.MarkupType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private class DeclarativeHintsNecromancerAwaker : NecromancerAwaker<DeclarativeHintsZombie> {
  override fun awake(project: Project, coroutineScope: CoroutineScope): Necromancer<DeclarativeHintsZombie> {
    return DeclarativeHintsNecromancer(project, coroutineScope)
  }
}

private class DeclarativeHintsNecromancer(
  project: Project,
  coroutineScope: CoroutineScope,
) : GravingNecromancer<DeclarativeHintsZombie>(
  project,
  coroutineScope,
  "graved-declarative-hints",
  DeclarativeHintsNecromancy,
) {

  override fun turnIntoZombie(recipe: TurningRecipe): DeclarativeHintsZombie? {
    if (isDeclarativeEnabled() && isCacheEnabled()) {
      val inlineHints = recipe.editor.getInlayModel().getInlineElementsInRange(
        0,
        recipe.editor.getDocument().textLength,
        DeclarativeInlayRenderer::class.java,
      )
      val eolHints = recipe.editor.getInlayModel().getAfterLineEndElementsInRange(
        0,
        recipe.editor.getDocument().textLength,
        DeclarativeInlayRenderer::class.java,
      )
      val blockHints = recipe.editor.getInlayModel().getBlockElementsInRange(
        0,
        recipe.editor.getDocument().textLength,
        DeclarativeIndentedBlockInlayRenderer::class.java,
      )
      val inlayDataList = ArrayList<InlayData>()
      inlineHints.flatMapTo(inlayDataList) { it.renderer.toInlayData() }
      eolHints.flatMapTo(inlayDataList) { it.renderer.toInlayData() }
      blockHints.flatMapTo(inlayDataList) { it.renderer.toInlayData() }
      return DeclarativeHintsZombie(inlayDataList)
    } else {
      return null
    }
  }

  override suspend fun shouldSpawnZombie(recipe: SpawnRecipe): Boolean {
    return isDeclarativeEnabled() && isCacheEnabled()
  }

  override suspend fun spawnZombie(recipe: SpawnRecipe, zombie: DeclarativeHintsZombie?) {
    if (zombie != null && zombie.limbs().isNotEmpty()) {
      val settings = DeclarativeInlayHintsSettings.getInstance()
      for (inlayData in zombie.limbs()) {
        initZombiePointers(recipe.project, recipe.file, inlayData.tree)
      }
      val inlayDataMap = readActionBlocking {
        if (!recipe.isValid()) return@readActionBlocking emptyMap<String, PreprocessedInlayData>()
        zombie.limbs()
          .filter { settings.isProviderEnabled(it.providerId) ?: true }
          .groupBy { it.sourceId }
          .mapValues { (_, inlayDataList) -> DeclarativeInlayHintsPass.preprocessCollectedInlayData(inlayDataList, recipe.document) }
      }
      if (inlayDataMap.isNotEmpty()) {
        val editor = recipe.editorSupplier()
        withContext(Dispatchers.EDT) {
          if (recipe.isValid(editor)) {
            //maybe readaction
            writeIntentReadAction {
              inlayDataMap.forEach { (sourceId, preparedInlayData) ->
                DeclarativeInlayHintsPass.applyInlayData(editor, recipe.project, preparedInlayData, sourceId)
              }
              DeclarativeInlayHintsPassFactory.resetModificationStamp(editor)
              FUSProjectHotStartUpMeasurer.markupRestored(recipe, MarkupType.DECLARATIVE_HINTS)
            }
          }
        }
      }
    }
  }

  private fun initZombiePointers(project: Project, file: VirtualFile, tree: TinyTree<Any?>, index: Byte = 0) {
    val dataPayload = tree.getDataPayload(index)
    if (dataPayload is ActionWithContent) {
      val payload: InlayActionPayload = dataPayload.actionData.payload
      if (payload is PsiPointerInlayActionPayload) {
        val pointer = payload.pointer
        if (pointer is ZombieSmartPointer) {
          pointer.projectSupp = { project }
          pointer.fileSupp = { file }
        }
      }
    }
    tree.processChildren(index) { child ->
      initZombiePointers(project, file, tree, child)
      true
    }
  }

  private fun isCacheEnabled() = Registry.`is`("cache.inlay.hints.on.disk", true)

  private fun isDeclarativeEnabled(): Boolean {
    val enabledGlobally = InlayHintsSettings.instance().hintsEnabledGlobally()
    return enabledGlobally && Registry.`is`("inlays.declarative.hints", true)
  }
}
