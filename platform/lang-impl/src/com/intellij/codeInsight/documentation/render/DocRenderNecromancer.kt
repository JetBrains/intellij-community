// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.documentation.render

import com.intellij.codeInsight.documentation.render.DocRenderPassFactory.Item
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.editor.CustomFoldRegion
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.zombie.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.ide.diagnostic.startUpPerformanceReporter.FUSProjectHotStartUpMeasurer
import com.intellij.platform.ide.diagnostic.startUpPerformanceReporter.FUSProjectHotStartUpMeasurer.MarkupType
import com.intellij.psi.PsiManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


internal class DocRenderNecromancerAwaker : NecromancerAwaker<DocRenderZombie> {
  override fun awake(project: Project, coroutineScope: CoroutineScope): Necromancer<DocRenderZombie> {
    return DocRenderNecromancer(project, coroutineScope)
  }
}

private class DocRenderNecromancer(
  project: Project,
  coroutineScope: CoroutineScope,
) : CleaverNecromancer<DocRenderZombie, DocRenderZombie.Limb>(
  project,
  coroutineScope,
  "graved-doc-render",
  DocRenderZombie.Necromancy,
) {

  override fun enoughMana(recipe: Recipe): Boolean {
    return true
  }

  override fun isZombieFriendly(recipe: Recipe): Boolean {
    return Registry.`is`("cache.folding.model.on.disk", true)
  }

  override fun cutIntoLimbs(recipe: TurningRecipe): List<DocRenderZombie.Limb> {
    val limbs = mutableListOf<DocRenderZombie.Limb>()
    for (foldRegion in recipe.editor.foldingModel.allFoldRegions) {
      if (foldRegion.group == null && foldRegion is CustomFoldRegion) {
        val renderer = foldRegion.renderer
        if (renderer is DocRenderer) {
          val text = renderer.item.textToRender
          if (text != null) {
            limbs.add(DocRenderZombie.Limb(foldRegion.startOffset, foldRegion.endOffset, text))
          }
        }
      }
    }
    return limbs
  }

  override suspend fun spawnZombie(
    recipe: SpawnRecipe,
    limbs: List<DocRenderZombie.Limb>,
  ): ((Editor) -> Unit)? {
    val itemList = limbs.map { limb ->
      Item(TextRange(limb.startOffset, limb.endOffset), limb.text)
    }
    val items = DocRenderPassFactory.Items(itemList, true)
    val editor = recipe.editorSupplier()
    if (!DocRenderManager.isDocRenderingEnabled(editor)) {
      return null
    }
    return { editor ->
      DocRenderPassFactory.applyItemsToRender(editor, recipe.project, items, true)
      DocRenderPassFactory.forceRefreshOnNextPass(editor)
      FUSProjectHotStartUpMeasurer.markupRestored(recipe, MarkupType.DOC_RENDER)
    }
  }

  override suspend fun spawnNoZombie(recipe: SpawnRecipe) {
    val project = recipe.project
    val editor = recipe.editorSupplier()
    if (!DocRenderManager.isDocRenderingEnabled(editor)) {
      // abort items calc asap IJPL-160508
      return
    }
    val document = recipe.document
    val psiManager = project.serviceAsync<PsiManager>()
    val stampAndItems = readAction {
      psiManager.findFile(recipe.file)?.let { psiFile ->
        document.modificationStamp to DocRenderPassFactory.calculateItemsToRender(document, psiFile, true)
      }
    }
    if (stampAndItems != null) {
      val (stamp, items) = stampAndItems
      if (!items.isEmpty) {
        withContext(Dispatchers.EDT) {
          if (!project.isDisposed && !editor.isDisposed && document.modificationStamp == stamp) {
            writeIntentReadAction {
              DocRenderPassFactory.applyItemsToRender(editor, project, items, true)
            }
          }
        }
      }
    }
  }
}
