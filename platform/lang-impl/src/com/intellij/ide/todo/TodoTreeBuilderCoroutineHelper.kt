// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.todo

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.*
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.ui.tree.TreeUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.future.asCompletableFuture
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.CompletableFuture

private val ASYNC_BATCH_SIZE = RegistryManager.getInstance().get("ide.tree.ui.async.batch.size")

@ApiStatus.Internal
private class TodoTreeBuilderCoroutineHelper(private val treeBuilder: TodoTreeBuilder) : Disposable {

  private val scope = CoroutineScope(SupervisorJob())

  init {
    Disposer.register(treeBuilder, this)
  }

  override fun dispose() {
    scope.cancel()
  }

  fun scheduleCacheAndTreeUpdate(vararg constraints: ReadConstraint): CompletableFuture<*> {
    return scope.launch(Dispatchers.EDT) {
      treeBuilder.onUpdateStarted()
      constrainedReadAction(*constraints) {
        treeBuilder.collectFiles()
      }
      treeBuilder.onUpdateFinished()
    }.asCompletableFuture()
  }

  fun scheduleCacheValidationAndTreeUpdate() {
    scope.launch(Dispatchers.EDT) {
      val pathsToSelect = TreeUtil.collectSelectedUserObjects(treeBuilder.tree).stream()
      treeBuilder.tree.clearSelection()

      readAction {
        treeBuilder.validateCacheAndUpdateTree()
      }

      TreeUtil.promiseSelect(
        treeBuilder.tree,
        pathsToSelect.map { TodoTreeBuilder.getVisitorFor(it) },
      )
    }
  }

  fun scheduleUpdateTree(): CompletableFuture<*> {
    return scope.launch(Dispatchers.Default) {
      readActionBlocking {
        treeBuilder.updateVisibleTree()
      }
    }.asCompletableFuture()
  }
}

@RequiresBackgroundThread
@RequiresReadLock
private fun TodoTreeBuilder.collectFiles() {
  ProgressManager.checkCanceled()
  clearCache()

  collectFiles {
    myFileTree.add(it.virtualFile)

    if (myFileTree.size() % ASYNC_BATCH_SIZE.asInteger() == 0) {
      validateCacheAndUpdateTree()
    }
  }

  validateCacheAndUpdateTree()
}

@RequiresBackgroundThread
@RequiresReadLock
private fun TodoTreeBuilder.validateCacheAndUpdateTree() {
  ProgressManager.checkCanceled()

  todoTreeStructure.validateCache()
  updateVisibleTree()
}

@RequiresBackgroundThread
@RequiresReadLock
private fun TodoTreeBuilder.updateVisibleTree() {
  if (isUpdatable) {
    if (!myDirtyFileSet.isEmpty()) { // suppress redundant cache validations
      validateCache()
      todoTreeStructure.validateCache()
    }
    model.invalidateAsync()
  }
}
