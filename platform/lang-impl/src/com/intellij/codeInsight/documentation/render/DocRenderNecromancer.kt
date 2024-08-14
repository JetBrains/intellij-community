// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.documentation.render

import com.intellij.codeInsight.documentation.render.DocRenderPassFactory.Item
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.editor.CustomFoldRegion
import com.intellij.openapi.editor.impl.zombie.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.ide.diagnostic.startUpPerformanceReporter.FUSProjectHotStartUpMeasurer
import com.intellij.platform.ide.diagnostic.startUpPerformanceReporter.FUSProjectHotStartUpMeasurer.MarkupType
import com.intellij.psi.PsiManager
import com.intellij.util.io.DataInputOutputUtil.readINT
import com.intellij.util.io.DataInputOutputUtil.writeINT
import com.intellij.util.io.IOUtil.readUTF
import com.intellij.util.io.IOUtil.writeUTF
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataInput
import java.io.DataOutput

private class DocRenderNecromancerAwaker : NecromancerAwaker<DocRenderZombie> {
  override fun awake(project: Project, coroutineScope: CoroutineScope): Necromancer<DocRenderZombie> {
    return DocRenderNecromancer(project, coroutineScope)
  }
}

private class DocRenderNecromancer(
  project: Project,
  coroutineScope: CoroutineScope,
) : GravingNecromancer<DocRenderZombie>(
  project,
  coroutineScope,
  "graved-doc-render",
  DocRenderNecromancy,
) {
  override fun turnIntoZombie(recipe: TurningRecipe): DocRenderZombie? {
    if (isNecromancerEnabled()) {
      val limbs = mutableListOf<DocRenderLimb>()
      for (foldRegion in recipe.editor.foldingModel.allFoldRegions) {
        if (foldRegion.group == null && foldRegion is CustomFoldRegion) {
          val renderer = foldRegion.renderer
          if (renderer is DocRenderer) {
            val text = renderer.item.textToRender
            if (text != null) {
              limbs.add(DocRenderLimb(foldRegion.startOffset, foldRegion.endOffset, text))
            }
          }
        }
      }
      return DocRenderZombie(limbs)
    }
    return null
  }

  override suspend fun spawnZombie(recipe: SpawnRecipe, zombie: DocRenderZombie?) {
    val project = recipe.project
    if (isNecromancerEnabled() && zombie != null && zombie.limbs().isNotEmpty()) {
      val itemList= zombie.limbs().map { limb ->
        Item(TextRange(limb.startOffset, limb.endOffset), limb.text)
      }
      val items = DocRenderPassFactory.Items(itemList, true)
      val editor = recipe.editorSupplier()
      if (DocRenderManager.isDocRenderingEnabled(editor)) {
        withContext(Dispatchers.EDT) {
          if (recipe.isValid()) {
            //maybe readaction
            writeIntentReadAction {
              DocRenderPassFactory.applyItemsToRender(editor, project, items, true)
              DocRenderPassFactory.forceRefreshOnNextPass(editor)
              FUSProjectHotStartUpMeasurer.markupRestored(recipe, MarkupType.DOC_RENDER)
            }
          }
        }
      }
    } else {
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
          val editor = recipe.editorSupplier()
          if (DocRenderManager.isDocRenderingEnabled(editor)) {
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
  }

  private fun isNecromancerEnabled(): Boolean {
    return Registry.`is`("cache.folding.model.on.disk", true)
  }
}

private class DocRenderZombie(limbs: List<DocRenderLimb>) : LimbedZombie<DocRenderLimb>(limbs)

private data class DocRenderLimb(
  val startOffset: Int,
  val endOffset: Int,
  val text: String,
)

private object DocRenderNecromancy : LimbedNecromancy<DocRenderZombie, DocRenderLimb>(spellLevel=0) {
  override fun buryLimb(grave: DataOutput, limb: DocRenderLimb) {
    writeINT(grave, limb.startOffset)
    writeINT(grave, limb.endOffset)
    writeUTF(grave, limb.text)
  }

  override fun exhumeLimb(grave: DataInput): DocRenderLimb {
    val startOffset = readINT(grave)
    val endOffset = readINT(grave)
    val text = readUTF(grave)
    return DocRenderLimb(startOffset, endOffset, text)
  }

  override fun formZombie(limbs: List<DocRenderLimb>): DocRenderZombie {
    return DocRenderZombie(limbs)
  }
}
