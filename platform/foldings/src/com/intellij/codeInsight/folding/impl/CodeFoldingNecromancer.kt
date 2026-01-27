// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.folding.impl

import com.intellij.codeInsight.folding.CodeFoldingManager
import com.intellij.formatting.visualLayer.visualFormattingElementKey
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.impl.FoldingKeys.ZOMBIE_REGION_KEY
import com.intellij.openapi.editor.impl.zombie.*
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.blockingContextToIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.ide.diagnostic.startUpPerformanceReporter.FUSProjectHotStartUpMeasurer
import com.intellij.platform.ide.diagnostic.startUpPerformanceReporter.FUSProjectHotStartUpMeasurer.MarkupType
import com.intellij.psi.PsiCompiledFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.util.SlowOperations
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.CancellationException


internal class CodeFoldingNecromancerAwaker : NecromancerAwaker<CodeFoldingZombie> {
  override fun awake(project: Project, coroutineScope: CoroutineScope): Necromancer<CodeFoldingZombie> {
    return CodeFoldingNecromancer(project, coroutineScope)
  }
}

private class CodeFoldingNecromancer(
  project: Project,
  coroutineScope: CoroutineScope,
) : CleaverNecromancer<CodeFoldingZombie, FoldLimb>(
  project,
  coroutineScope,
  "graved-code-folding",
  CodeFoldingNecromancy,
) {

  override fun enoughMana(recipe: Recipe): Boolean {
    return true
  }

  override fun isZombieFriendly(recipe: Recipe): Boolean {
    return Registry.`is`("cache.folding.model.on.disk", true)
  }

  override fun cutIntoLimbs(recipe: TurningRecipe): List<FoldLimb> {
    return recipe.editor.foldingModel.allFoldRegions
      .filter {
        it.getUserData(ZOMBIE_REGION_KEY) == null &&
        it.getUserData(visualFormattingElementKey) != true // `VisualFormattingNecromancer` handles vf
      }
      .map { FoldLimb(it) }
  }

  override suspend fun spawnZombie(
    recipe: SpawnRecipe,
    limbs: List<FoldLimb>,
  ): ((Editor) -> Unit)? {
    if (isNotCompiledFile(recipe.project, recipe.document)) {
      return { editor ->
        editor as EditorEx
        if (editor.foldingModel.isFoldingEnabled && !CodeFoldingManagerImpl.isFoldingsInitializedInEditor(editor)) {
          CodeFoldingZombie(limbs).applyState(editor)
          FUSProjectHotStartUpMeasurer.markupRestored(recipe, MarkupType.CODE_FOLDING)
        }
      }
    } else {
      spawnNoZombie(recipe)
      return null
    }
  }

  override suspend fun spawnNoZombie(recipe: SpawnRecipe) {
    val project = recipe.project
    val document = recipe.document
    val codeFoldingManager = project.serviceAsync<CodeFoldingManager>()
    val psiDocumentManager = project.serviceAsync<PsiDocumentManager>()
    val editor = recipe.editorSupplier()
    var modStamp:Long = 0
    val foldingState = readAction {
      if (psiDocumentManager.isCommitted(document)) {
        modStamp = document.modificationStamp
        catchingExceptions {
          blockingContextToIndicator {
            codeFoldingManager.updateFoldRegionsAsync(editor, true)
          }
        }
      } else {
        null
      }
    }
    if (foldingState != null) {
      withContext(Dispatchers.EDT) {
        if (editor.foldingModel.isFoldingEnabled && modStamp == document.modificationStamp) {
          runReadAction { // set to editor with RA IJPL-159083
            SlowOperations.knownIssue("IJPL-165088").use {
              foldingState.run()
            }
          }
        }
      }
    }
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

  // not `inline` to ensure that this function is not used for a `suspend` task
  @Suppress("IncorrectCancellationExceptionHandling")
  private fun <T : Any> catchingExceptions(computable: () -> T?): T? {
    try {
      return computable()
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: ProcessCanceledException) {
      // will throw if actually canceled
      ProgressManager.checkCanceled()
      // otherwise, this PCE is manual -> treat it like any other exception
      thisLogger().warn("Exception during editor loading", RuntimeException(e))
    }
    catch (e: Throwable) {
      thisLogger().warn("Exception during editor loading", if (e is ControlFlowException) RuntimeException(e) else e)
    }
    return null
  }
}
