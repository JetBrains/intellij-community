// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectView.impl

import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.ide.util.treeView.AbstractTreeStructure
import com.intellij.ide.util.treeView.NodeDescriptor
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ActionCallback
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.ui.tree.RestoreSelectionListener
import com.intellij.ui.tree.TreeCollector
import com.intellij.ui.tree.TreeStructureDomainModelAdapter
import com.intellij.ui.tree.TreeVisitor
import com.intellij.ui.tree.project.ProjectFileNode.findArea
import com.intellij.ui.tree.project.ProjectFileNodeUpdater
import com.intellij.ui.treeStructure.TreeDomainModel
import com.intellij.ui.treeStructure.TreeNodeViewModel
import com.intellij.ui.treeStructure.TreeSwingModel
import com.intellij.ui.treeStructure.TreeViewModel
import com.intellij.util.SmartList
import kotlinx.coroutines.*
import org.jetbrains.concurrency.await
import javax.swing.JTree
import javax.swing.SwingUtilities
import javax.swing.tree.TreePath

internal class CoroutineProjectViewSupport(
  pane: AbstractProjectViewPaneWithAsyncSupport,
  private val project: Project,
  private val coroutineScope: CoroutineScope,
  treeStructure: AbstractTreeStructure,
  comparator: Comparator<NodeDescriptor<*>>,
) : ProjectViewPaneSupport() {

  private val domainModel = TreeStructureDomainModelAdapter(treeStructure, true, 1)
  private val viewModel = TreeViewModel(coroutineScope, domainModel)
  private val swingModel = TreeSwingModel(coroutineScope, viewModel)

  init {
    Disposer.register(pane, Disposable {
      coroutineScope.cancel()
    })
    myNodeUpdater = Updater(project, coroutineScope)
    setupListeners(pane, project, treeStructure)
    setComparator(comparator)
  }

  override fun setModelTo(tree: JTree) {
    val restoreSelectionListener = RestoreSelectionListener()
    tree.addTreeSelectionListener(restoreSelectionListener)
    tree.model = swingModel
    coroutineScope.coroutineContext.job.invokeOnCompletion {
      SwingUtilities.invokeLater {
        tree.removeTreeSelectionListener(restoreSelectionListener)
      }
    }
  }

  override fun setComparator(comparator: Comparator<in NodeDescriptor<*>>?) {
    domainModel.comparator = comparator
  }

  override fun updateAll(afterUpdate: Runnable?) {
    updateImpl(null, true, afterUpdate)
  }

  override fun update(path: TreePath, updateStructure: Boolean) {
    updateImpl(path, updateStructure)
  }

  private fun updateImpl(element: TreePath?, updateStructure: Boolean, onDone: Runnable? = null) {
    val job = coroutineScope.launch(CoroutineName("Updating $element, structure=$updateStructure")) {
      viewModel.invalidate(element?.lastPathComponent as TreeNodeViewModel?, updateStructure)
      viewModel.awaitUpdates()
    }
    job.invokeOnCompletion {
      onDone?.let { SwingUtilities.invokeLater(it) }
    }
  }

  override fun acceptAndUpdate(visitor: TreeVisitor, presentations: List<TreePath?>?, structures: List<TreePath?>?) {
    coroutineScope.launch(CoroutineName("Updating ${presentations?.size} presentations and ${structures?.size} structures after accepting $visitor")) {
      swingModel.accept(visitor, false).await()
      if (presentations != null) update(presentations, false)
      if (structures != null) update(structures, true)
    }
  }

  override fun select(tree: JTree, toSelect: Any?, file: VirtualFile?): ActionCallback {
    selectLogger.debug { "CoroutineProjectViewSupport.select: object=$toSelect, file=$file" }
    val value = if (toSelect is AbstractTreeNode<*>) {
      toSelect.value?.also { retrieved ->
        selectLogger.debug { "Retrieved the value from the node: $retrieved" }
      }
    }
    else {
      toSelect
    }
    val element = toSelect as? PsiElement
    selectLogger.debug { "select object: $value in file: $file" }
    val callback = ActionCallback()
    selectLogger.debug("Updating nodes before selecting")
    myNodeUpdater.updateImmediately {
      selectLogger.debug("Updated nodes")
      val job = coroutineScope.launch(CoroutineName("Selecting $value in $file") + Dispatchers.EDT) {
        selectLogger.debug("First attempt: trying to select the element or file")
        if (trySelect(tree, element, file)) {
          selectLogger.debug("Selected paths at first attempt. Done")
          return@launch
        }
        if (!canTrySelectAgain(element, file)) return@launch
        // This silly second attempt is necessary because a file, when visited, may tell us it doesn't contain the element we're looking for.
        // Reportedly, it's the case with top-level Kotlin functions and Kotlin files.
        selectLogger.debug("Second attempt: trying to select the file now")
        if (trySelect(tree, null, file)) {
          selectLogger.debug("Selected successfully at the second attempt. Done")
        }
        else {
          selectLogger.debug("Couldn't select at the second attempt. Done")
        }
      }
      job.invokeOnCompletion {
        callback.setDone()
      }
    }
    return callback
  }

  private suspend fun trySelect(tree: JTree, element: PsiElement?, file: VirtualFile?): Boolean {
    val pathsToSelect = SmartList<TreePath>()
    val visitor = AbstractProjectViewPane.createVisitor(element, file, pathsToSelect)
    if (visitor == null) {
      selectLogger.debug("We don't have neither a valid element nor a file. Done")
      return false
    }
    selectLogger.debug("Collecting paths to select")
    swingModel.accept(visitor, true).await()
    selectLogger.debug { "Collected paths to select: $pathsToSelect" }
    return selectPaths(tree, pathsToSelect, visitor)
  }

  private fun canTrySelectAgain(element: PsiElement?, file: VirtualFile?): Boolean {
    if (element == null) {
      selectLogger.debug(
        "Couldn't select paths at first attempt, " +
        "but a second attempt isn't possible because the given element is null " +
        "and therefore we have already tried looking for the file during the first attempt. Done"
      )
      return false
    }
    if (file == null) {
      selectLogger.debug(
        "Couldn't select paths at first attempt, " +
        "but a second attempt isn't possible because the given file is null. Done"
      )
      return false
    }
    if (Registry.`is`("async.project.view.support.extra.select.disabled", false)) {
      selectLogger.debug(
        "Couldn't select paths at first attempt, " +
        "but a second attempt isn't possible because it's disabled in the Registry. Done"
      )
      return false
    }
    return true
  }

  private inner class Updater(project: Project, coroutineScope: CoroutineScope) : ProjectFileNodeUpdater(project, coroutineScope) {
    override fun updateStructure(fromRoot: Boolean, updatedFiles: Set<VirtualFile>) {
      if (fromRoot) {
        updateAll(null)
        return
      }
      coroutineScope.launch(CoroutineName("Updating ${updatedFiles.size} files")) {
        val roots = readAction {
          val collector = TreeCollector.VirtualFileRoots.create()
          for (file in updatedFiles) {
            val dir = if (file.isDirectory) file else file.parent
            if (dir != null && findArea(dir, project) != null) collector.add(file)
          }
          collector.get()
        }
        for (root in roots) {
          updateByFile(root, true)
        }
      }
    }
  }
}

private val CoroutineProjectViewSupport.selectLogger: Logger
  get() = logger<SelectInProjectViewImpl>()
