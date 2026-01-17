// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.codeVision

import com.intellij.codeInsight.codeVision.CodeVisionEntry
import com.intellij.codeInsight.codeVision.CodeVisionHost
import com.intellij.codeInsight.codeVision.lensContext
import com.intellij.codeInsight.codeVision.settings.CodeVisionSettings
import com.intellij.codeInsight.codeVision.ui.model.RichTextCodeVisionEntry
import com.intellij.codeInsight.codeVision.ui.model.ZombieCodeVisionEntry
import com.intellij.codeInsight.daemon.impl.grave.CodeVisionLimb
import com.intellij.codeInsight.daemon.impl.grave.CodeVisionZombie
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readActionBlocking
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.components.serviceAsync
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
import kotlin.collections.map


internal class CodeVisionNecromancerAwaker : NecromancerAwaker<CodeVisionZombie> {
  override fun awake(project: Project, coroutineScope: CoroutineScope): Necromancer<CodeVisionZombie> {
    return CodeVisionNecromancer(project, coroutineScope)
  }
}

private class CodeVisionNecromancer(
  project: Project,
  coroutineScope: CoroutineScope,
) : CleaverNecromancer<CodeVisionZombie, CodeVisionLimb>(
  project,
  coroutineScope,
  "graved-code-vision",
  CodeVisionZombie.Necromancy,
) {

  override fun enoughMana(recipe: Recipe): Boolean {
    return Registry.`is`("editor.codeVision.new", true) &&
           CodeVisionSettings.getInstance().codeVisionEnabled &&
           CodeVisionProjectSettings.getInstance(recipe.project).isEnabledForProject()
  }

  override fun isZombieFriendly(recipe: Recipe): Boolean {
    return Registry.`is`("cache.inlay.hints.on.disk", true)
  }

  override fun cutIntoLimbs(recipe: TurningRecipe): List<CodeVisionLimb> {
    val context = recipe.editor.lensContext
    if (context != null) {
      return context.getValidPairResult()
        .filter { (_, cvEntry) -> !ignoreEntry(cvEntry) }
        .map { CodeVisionLimb(it) }
        .toList()
    }
    return emptyList()
  }

  override suspend fun spawnZombie(
    recipe: SpawnRecipe,
    limbs: List<CodeVisionLimb>,
  ): (suspend (Editor) -> Unit)? {
    val providerIdToGroupId = providerToGroupMap(recipe.project)
    val settings = CodeVisionSettings.getInstance()
    val entries = limbs
      .map { entry -> Pair(TextRange(entry.startOffset, entry.endOffset), entry.asEntry()) }
      .sortedBy { (_, cvEntry) -> cvEntry.providerId }
      .filter { (_, zombieEntry) ->
        val zombieGroup = providerIdToGroupId[zombieEntry.providerId]
        zombieGroup != null && settings.isProviderEnabled(zombieGroup)
      }
    if (entries.isEmpty()) {
      return null
    }
    return { editor ->
      writeIntentReadAction {
        FUSProjectHotStartUpMeasurer.markupRestored(recipe, MarkupType.CODE_VISION)
        editor.lensContext?.setZombieResults(entries)
      }
    }
  }

  override suspend fun spawnNoZombie(recipe: SpawnRecipe) {
    val psiManager = recipe.project.serviceAsync<PsiManager>()
    val psiFile = readActionBlocking { psiManager.findFile(recipe.file) }
    val editor = recipe.editorSupplier.invoke()
    val placeholders = recipe.project.serviceAsync<CodeVisionHost>().collectPlaceholders(editor, psiFile)
    if (placeholders.isNotEmpty()) {
      withContext(Dispatchers.EDT) {
        if (!editor.isDisposed) {
          writeIntentReadAction {
            editor.lensContext?.setResults(placeholders)
          }
        }
      }
    }
  }

  private suspend fun providerToGroupMap(project: Project): Map<String, String> {
    // Reminder what is what
    // groupId:    "rename", "vcs.code.vision", "references", "llm", "component.usage", "inheritors", "problems"
    // providerId: "Rename refactoring", "vcs.code.vision", "java.references", "LLMDocumentationCodeVisionProvider",
    //             "JSComponentUsageCodeVisionProvider", "go.references", "go.inheritors", "java.inheritors",
    //             "java.RelatedProblems", "python.references", "js.references", "js.inheritors",
    //             "cypress.commands.references", "php.references", "php.inheritors"
    val cvHost = project.serviceAsync<CodeVisionHost>()
    return cvHost.providers.associate { provider -> provider.id to provider.groupId }
  }

  private fun ignoreEntry(cvEntry: CodeVisionEntry): Boolean {
    // TODO: rich text in not supported yet
    return cvEntry is ZombieCodeVisionEntry || cvEntry is RichTextCodeVisionEntry
  }
}
