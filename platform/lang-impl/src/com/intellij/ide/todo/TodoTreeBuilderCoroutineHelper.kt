// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.todo

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.util.SmartList
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
private class TodoTreeBuilderCoroutineHelper(private val treeBuilder: TodoTreeBuilder) : Disposable {

  private val scope = CoroutineScope(SupervisorJob())
  private val jobs = SmartList<Job>() // requires EDT

  @Volatile
  private var cacheSize = -1

  private val loadingPanel: JBLoadingPanel?
    get() = UIUtil.getParentOfType(JBLoadingPanel::class.java, treeBuilder.tree)

  init {
    Disposer.register(treeBuilder, this)
  }

  override fun dispose() {
    scope.cancel()
  }

  @Synchronized
  fun scheduleUpdateCacheAndTree() {
    jobs.forEach { it.cancel() }
    jobs.clear()

    jobs += scope.launch(Dispatchers.EDT) {
      loadingPanel?.startLoading()

      readAction {
        val files = treeBuilder.collectFiles()

        if (isOutdated(files)) {
          treeBuilder.updateCacheAndTree(files)
        }
      }

      loadingPanel?.stopLoading()
    }
  }

  @Synchronized
  private fun isOutdated(files: Set<VirtualFile>): Boolean {
    val newCacheSize = files.size
    val result = cacheSize != newCacheSize
    if (result) {
      cacheSize = newCacheSize
    }
    return result
  }
}

private fun TodoTreeBuilder.collectFiles(): Set<VirtualFile> {
  val files = mutableSetOf<VirtualFile>()
  collectFiles {
    files += it
    true
  }
  return files
}
