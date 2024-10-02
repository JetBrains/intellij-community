// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.safeDelete.impl

import com.intellij.model.Pointer
import com.intellij.model.search.SearchService
import com.intellij.openapi.application.*
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.psi.impl.search.runSearch
import com.intellij.refactoring.BaseRefactoringProcessor.ConflictsInTestsException
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.rename.api.FileOperation
import com.intellij.refactoring.rename.impl.FileUpdates
import com.intellij.refactoring.rename.ui.withBackgroundIndicator
import com.intellij.refactoring.safeDelete.UnsafeUsagesDialog
import com.intellij.refactoring.safeDelete.api.*
import com.intellij.util.Query
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ReceiveChannel
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly

internal fun safeDelete(
  project: Project,
  targetPointer: Pointer<out SafeDeleteTarget>
): Deferred<Unit> {
  return safeDelete(CoroutineScope(CoroutineName("root safe delete coroutine")), project, targetPointer)
}

private fun safeDelete(
  cs: CoroutineScope,
  project: Project,
  targetPointer: Pointer<out SafeDeleteTarget>
): Deferred<Unit> {
  return cs.async(Dispatchers.Default) {
    val targets = withBackgroundIndicator(project, RefactoringBundle.message("progress.text")) {
      readAction {
        val target = targetPointer.dereference() ?: return@readAction null
        val resultedTargets = mutableSetOf<Pointer<out SafeDeleteTarget>>()
        findDependentTargets(target, resultedTargets, project)
        return@readAction resultedTargets
      }
    } ?: return@async

    doSafeDelete(this, project, targets, targetPointer)
  }
}

private fun findDependentTargets(rootTarget: SafeDeleteTarget, targets: MutableSet<Pointer<out SafeDeleteTarget>>, project: Project) {
  if (targets.add(rootTarget.createPointer())) {
    SearchService.getInstance().searchParameters(
      SafeDeleteAdditionalTargetsSearchParameters(rootTarget, project)
    ).forEach {
      findDependentTargets(it, targets, project)
    }
  }
}

private suspend fun doSafeDelete(
  cs: CoroutineScope,
  project: Project,
  targets: MutableSet<Pointer<out SafeDeleteTarget>>,
  targetPointer: Pointer<out SafeDeleteTarget>
) {

  val rootTarget = readAction { targetPointer.dereference() } ?: return
  val targetPresentation = readAction { rootTarget.targetPresentation() }

  val targetDeclarations = mutableSetOf<PsiSafeDeleteDeclarationUsage>()

  val iterator = targets.iterator()
  for (target in iterator) {
    targetDeclarations.addAll(readAction {
      val currentTarget = target.dereference()
      //ignore additional targets if they are not safe to delete
      val declarations = currentTarget?.declarations()
      if (declarations != null && currentTarget != rootTarget && declarations.any { !it.isSafeToDelete }) {
        iterator.remove()
        return@readAction emptyList()
      }
      declarations ?: emptyList()
    })
  }

  val query: Query<out Pointer<out SafeDeleteUsage>> = readAction {
    val queries = ArrayList<Query<Pointer<out SafeDeleteUsage>>>()
    for (target in targets) {
      val t = target.dereference() ?: continue
      queries.add(buildUsageQuery(project, t).mapping {
        it.createPointer()
      })
    }
    SearchService.getInstance().merge(queries)
  }

  val channel: ReceiveChannel<Pointer<out SafeDeleteUsage>> = runSearch(cs, project, query)
  val deferredUsages: Deferred<Collection<Pointer<out SafeDeleteUsage>>> = withBackgroundIndicator(project, RefactoringBundle.message(
    "progress.text")) {
    cs.async {
      val result = ArrayList<Pointer<out SafeDeleteUsage>>()
      for (pointer in channel) {
        readAction {
          pointer.dereference()?.let { usage ->
            if (targetDeclarations.none { declaration -> usage is PsiSafeDeleteUsage && usage.isInside(declaration) }) {
              result.add(pointer)
            }
          }
        }
      }
      result
    }
  }
  val usagePointers: Collection<Pointer<out SafeDeleteUsage>> = deferredUsages.await()
  val conflicts = mutableListOf<SafeDeleteUsage>()
  val fileUpdates: FileUpdates = withBackgroundIndicator(project, RefactoringBundle.message("progress.title.prepare.to.delete")) {
    readAction {
      val updaters = arrayListOf<FileOperation>()

      fun fillUpdaters(usages: Collection<SafeDeleteUsage>) {
        for (declaration in usages) {
          if (declaration.isSafeToDelete) {
            updaters.addAll(declaration.fileUpdater.prepareFileUpdate())
          }
          else {
            conflicts.add(declaration)
          }
        }
      }


      fillUpdaters(usagePointers.mapNotNull { it.dereference() })
      fillUpdaters(targetDeclarations)

      FileUpdates.createFileUpdates(updaters)
    }
  }

  if (!conflicts.isEmpty()) {
    val messages = mutableListOf<String>()
    var unsafeCount = 0
    for (conflict in conflicts) {
      val conflictMessage = conflict.conflictMessage
      if (conflictMessage != null) {
        messages.add(conflictMessage)
      }
      else {
        unsafeCount++
      }
    }
    if (unsafeCount > 0) {
      messages.add(RefactoringBundle.message("0.has.1.usages.that.are.not.safe.to.delete", targetPresentation.presentableText, unsafeCount))
    }

    if (ApplicationManager.getApplication().isUnitTestMode) {
      if (!ConflictsInTestsException.isTestIgnore()) throw ConflictsInTestsException(messages)
    }
    withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
      val dialog = UnsafeUsagesDialog(messages.toTypedArray(), project)
      dialog.show()
    }

    return
  }

  WriteCommandAction
    .writeCommandAction(project)
    .withName(RefactoringBundle.message("safe.delete.command",
                                        targets.mapNotNull { t -> t.dereference()?.targetPresentation()?.presentableText }.joinToString()))
    .run<Throwable> {
      fileUpdates.doUpdate()
    }
}


internal fun buildUsageQuery(project: Project, target: SafeDeleteTarget): Query<out SafeDeleteUsage> {
  val queries = ArrayList<Query<out SafeDeleteUsage>>()
  queries += SearchService.getInstance().searchParameters(
    SafeDeleteSearchParameters(target, project)
  )
  //todo text options
  return SearchService.getInstance().merge(queries)
}

private fun PsiSafeDeleteUsage.isInside(target: PsiSafeDeleteDeclarationUsage): Boolean {
  if (file != target.file) return false
  return target.range.contains(range)
}

@TestOnly
@ApiStatus.Internal
fun safeDeleteAndWait(
  project: Project,
  targetPointer: Pointer<out SafeDeleteTarget>
) {
  runBlocking {
    withTimeout(timeMillis = 1000 * 60 * 10) {
      safeDelete(project, targetPointer).await()
    }
  }
}