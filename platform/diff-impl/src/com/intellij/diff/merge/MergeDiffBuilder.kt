// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.merge

import com.intellij.diff.comparison.ComparisonManager
import com.intellij.diff.comparison.DiffTooBigException
import com.intellij.diff.fragments.MergeLineFragment
import com.intellij.diff.tools.util.base.IgnorePolicy
import com.intellij.diff.tools.util.text.LineOffsets
import com.intellij.diff.tools.util.text.LineOffsetsUtil
import com.intellij.diff.util.MergeConflictResolutionStrategy
import com.intellij.diff.util.MergeConflictType
import com.intellij.diff.util.MergeRange
import com.intellij.diff.util.MergeRangeUtil
import com.intellij.diff.util.ThreeSide
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.progress.checkCanceled
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.project.DumbService.Companion.isDumb
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiPolyVariantReference
import com.intellij.psi.SyntaxTraverser
import com.intellij.util.containers.TreeTraversal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Builds merge diff data (line fragments, offsets, conflict types, etc.) decoupled from MergeThreesideViewer,
 * so it can be reused in other contexts.
 */
internal class MergeDiffBuilder(
  private val project: Project?,
  private val mergeRequest: TextMergeRequest,
  private val conflictResolver: LangSpecificMergeConflictResolverWrapper,
) {

  @Throws(DiffTooBigException::class)
  suspend fun rediff(
    ignorePolicy: IgnorePolicy,
    isAutoResolveImportConflicts: Boolean,
  ): MergeDiffData {

    checkCanceled()

    val sequences = ArrayList<CharSequence>(3)
    var psiFiles = emptyList<PsiFile>()
    var resolveImportsPossible = false

    val importRange: MergeRange? = readAction {
      sequences.addAll(mergeRequest.contents.map { it.document.immutableCharSequence })
      if (conflictResolver.isAvailable() || isAutoResolveImportConflicts) {
        psiFiles = initPsiFiles(project, mergeRequest)
      }
      if (isAutoResolveImportConflicts) {
        resolveImportsPossible = canImportsBeProcessedAutomatically(project, psiFiles)
        if (resolveImportsPossible) {
          return@readAction MergeImportUtil.getImportMergeRange(project, psiFiles)
        }
      }
      null
    }

    val fragmentsWithMetadata = getLineFragments(sequences, importRange, ignorePolicy)
    val lineOffsets = sequences.map(LineOffsetsUtil::create)

    val conflictTypes = fragmentsWithMetadata.fragments.map { fragment: MergeLineFragment ->
      MergeRangeUtil.getLineMergeType(fragment, sequences, lineOffsets, ignorePolicy.comparisonPolicy)
    }

    // initialize resolver and patch conflict types if semantic resolution is available
    withContext(Dispatchers.Default) {
      conflictResolver.init(lineOffsets, fragmentsWithMetadata.fragments, psiFiles)
      patchConflictTypes(conflictTypes, conflictResolver)
    }

    return MergeDiffData(fragmentsWithMetadata = fragmentsWithMetadata,
                         conflictTypes = conflictTypes,
                         lineOffsets = lineOffsets,
                         psiFiles = psiFiles,
                         ignorePolicy = ignorePolicy,
                         isAutoResolveImportConflicts = isAutoResolveImportConflicts,
                         isImportResolutionPossible = resolveImportsPossible)
  }

  private suspend fun getLineFragments(
    sequences: List<CharSequence>,
    importRange: MergeRange?,
    ignorePolicy: IgnorePolicy,
  ): MergeLineFragmentsWithImportMetadata = coroutineToIndicator { indicator ->
    if (importRange != null) {
      MergeImportUtil.getDividedFromImportsFragments(sequences, ignorePolicy.comparisonPolicy, importRange, indicator)
    }
    else {
      val fragments: List<MergeLineFragment> = ComparisonManager.getInstance().mergeLines(
        sequences[0],
        sequences[1],
        sequences[2],
        ignorePolicy.comparisonPolicy,
        indicator
      )
      MergeLineFragmentsWithImportMetadata(fragments)
    }
  }

  private fun initPsiFiles(project: Project?, mergeRequest: TextMergeRequest): List<PsiFile> {
    if (project == null) return emptyList()
    return ThreeSide.entries.mapNotNull { MergeImportUtil.getPsiFile(it, project, mergeRequest) }
  }

  private fun canImportsBeProcessedAutomatically(project: Project?, psiFiles: List<PsiFile>): Boolean {
    if (project == null || isDumb(project) || psiFiles.isEmpty()) return false
    return LOG.runAndLogException { canSideBeProcessed(ThreeSide.LEFT, psiFiles) && canSideBeProcessed(ThreeSide.RIGHT, psiFiles) } ?: false
  }

  @OptIn(ExperimentalAtomicApi::class)
  private fun canSideBeProcessed(side: ThreeSide, psiFiles: List<PsiFile>): Boolean {
    val psiFile = side.select(psiFiles)
    val atLeastOneReferenceFound = AtomicBoolean(false)
    return SyntaxTraverser.psiTraverser(psiFile)
             .traverse(TreeTraversal.PLAIN_BFS)
             .processEach { element ->
               val reference = element.reference
               if (reference == null) return@processEach true
               atLeastOneReferenceFound.store(true)
               if (reference.isSoft) return@processEach true
               if (reference is PsiPolyVariantReference) {
                 return@processEach reference.multiResolve(false).isNotEmpty()
               }
               val resolved = reference.resolve()
               resolved != null
             } && atLeastOneReferenceFound.load()
  }

  private fun patchConflictTypes(conflictTypes: List<MergeConflictType>, conflictResolver: LangSpecificMergeConflictResolverWrapper) {
    conflictTypes.forEachIndexed { index, conflictType ->
      if (!conflictType.canBeResolved() && conflictResolver.canResolveConflictSemantically(index)) {
        conflictType.resolutionStrategy = MergeConflictResolutionStrategy.SEMANTIC
      }
    }
  }

  companion object {
    private val LOG = logger<MergeDiffBuilder>()
  }
}

@ApiStatus.Internal
class MergeDiffData(
  val fragmentsWithMetadata: MergeLineFragmentsWithImportMetadata,
  val conflictTypes: List<MergeConflictType>,
  val lineOffsets: List<LineOffsets>,
  val psiFiles: List<PsiFile>,
  val ignorePolicy: IgnorePolicy,
  val isAutoResolveImportConflicts: Boolean,
  val isImportResolutionPossible: Boolean,
)
