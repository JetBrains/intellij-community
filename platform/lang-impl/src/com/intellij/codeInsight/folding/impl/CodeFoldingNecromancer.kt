// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.folding.impl

import com.intellij.codeInsight.folding.CodeFoldingManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.FoldRegion
import com.intellij.openapi.editor.impl.FoldingModelImpl.ZOMBIE_REGION_KEY
import com.intellij.openapi.editor.impl.zombie.*
import com.intellij.openapi.fileEditor.impl.text.catchingExceptions
import com.intellij.openapi.progress.blockingContextToIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.ide.diagnostic.startUpPerformanceReporter.FUSProjectHotStartUpMeasurer
import com.intellij.platform.ide.diagnostic.startUpPerformanceReporter.FUSProjectHotStartUpMeasurer.MarkupType
import com.intellij.psi.PsiCompiledFile
import com.intellij.psi.PsiDocumentManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private class CodeFoldingNecromancerAwaker : NecromancerAwaker<CodeFoldingZombie> {
  override fun awake(project: Project, coroutineScope: CoroutineScope): Necromancer<CodeFoldingZombie> {
    return CodeFoldingNecromancer(project, coroutineScope)
  }
}

private class CodeFoldingNecromancer(
  project: Project,
  coroutineScope: CoroutineScope,
) : GravingNecromancer<CodeFoldingZombie>(
  project,
  coroutineScope,
  "graved-code-folding",
  CodeFoldingNecromancy,
) {

  override fun turnIntoZombie(recipe: TurningRecipe): CodeFoldingZombie? {
    if (isNecromancerEnabled()) {
      return CodeFoldingZombie.create(notZombieRegions(recipe.editor))
    } else {
      return null
    }
  }

  override suspend fun spawnZombie(recipe: SpawnRecipe, zombie: CodeFoldingZombie?) {
    val document = recipe.document
    if (isNecromancerEnabled() &&
        zombie != null &&
        !zombie.isEmpty() &&
        isNotCompiledFile(recipe.project, recipe.document)) {
      val editor = recipe.editorSupplier()
      withContext(Dispatchers.EDT) {
        writeIntentReadAction {
          if (recipe.isValid(editor) &&
              editor.foldingModel.isFoldingEnabled &&
              !CodeFoldingManagerImpl.isFoldingsInitializedInEditor(editor)) {
            zombie.applyState(document, editor.foldingModel)
            FUSProjectHotStartUpMeasurer.markupRestored(recipe, MarkupType.CODE_FOLDING)
          }
        }
      }
    } else {
      val project = recipe.project
      val codeFoldingManager = project.serviceAsync<CodeFoldingManager>()
      val psiDocumentManager = project.serviceAsync<PsiDocumentManager>()
      val foldingState = readAction {
        if (psiDocumentManager.isCommitted(document)) {
          catchingExceptions {
            blockingContextToIndicator {
              codeFoldingManager.buildInitialFoldings(document)
            }
          }
        } else {
          null
        }
      }
      if (foldingState != null) {
        val editor = recipe.editorSupplier()
        withContext(Dispatchers.EDT) {
          runReadAction { // set to editor with RA IJPL-159083
            foldingState.setToEditor(editor)
          }
        }
      }
    }
  }

  private fun notZombieRegions(editor: Editor): List<FoldRegion> {
    return editor.foldingModel.allFoldRegions.filter { it.getUserData(ZOMBIE_REGION_KEY) == null }
  }

  private suspend fun isNotCompiledFile(project: Project, document: Document): Boolean {
    val psiManager = project.serviceAsync<PsiDocumentManager>()
    val psiFile = readAction {
      psiManager.getPsiFile(document)
    }
    // disable folding cache if there is no following folding pass IDEA-341064
    // com.intellij.codeInsight.daemon.impl.TextEditorBackgroundHighlighterKt.IGNORE_FOR_COMPILED
    return psiFile != null && psiFile !is PsiCompiledFile
  }

  private fun isNecromancerEnabled(): Boolean {
    return Registry.`is`("cache.folding.model.on.disk", true)
  }
}
