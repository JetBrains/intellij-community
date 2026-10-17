// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes

import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder
import com.intellij.ide.util.treeView.smartTree.SortableTreeElement
import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.lang.LanguageStructureViewBuilder
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vcs.FileStatus
import com.intellij.psi.ElementDescriptionUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.PsiRecursiveElementWalkingVisitor
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiFilter
import com.intellij.util.IconUtil
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.annotations.ApiStatus
import java.util.Collections
import java.util.IdentityHashMap
import javax.swing.Icon

/** A structure element with no retained PSI. The offset refers to the current file. */
@ApiStatus.Internal
data class StructureChange(
  val id: String,
  val text: @NlsSafe String,
  val icon: Icon?,
  val status: FileStatus,
  val offset: Int?,
  val children: List<StructureChange>,
)

@ApiStatus.Internal
object StructureChangeCollector {
  /** Compares the file structure and retains the parents of changed elements. A null baseline marks every element as added. */
  @JvmStatic
  @RequiresReadLock
  fun collect(before: PsiFile?, after: PsiFile): List<StructureChange> {
    val oldNodes = before?.let(::readStructure).orEmpty()
    val nodes = readStructure(after)
    assignIds(oldNodes, nodes, "")

    val oldElements = LinkedHashMap<PsiElement, Node>()
    val elements = LinkedHashMap<PsiElement, Node>()
    indexNodes(oldNodes, oldElements)
    indexNodes(nodes, elements)
    val filter = object : PsiFilter<PsiElement>(PsiElement::class.java) {
      override fun areEquivalent(first: PsiElement, second: PsiElement): Boolean {
        ProgressManager.checkCanceled()
        return elements[first]?.id == oldElements[second]?.id
      }
    }
    val statuses = PsiChangeTracker.getElementsChanged(elements.keys.toList(), oldElements.keys.toList(), filter)
    val changes = merge(oldNodes, nodes, statuses)
    return flattenFileContainer(after, nodes, changes)
  }

  private fun flattenFileContainer(
    after: PsiFile,
    nodes: List<Node>,
    changes: List<StructureChange>,
  ): List<StructureChange> {
    val node = nodes.singleOrNull() ?: return changes
    val element = node.element as? PsiNamedElement ?: return changes
    if (element.parent !== after || element.name != after.name.substringBeforeLast('.')) return changes
    val change = changes.singleOrNull() ?: return changes
    if (change.id != node.id) return changes
    return change.children
  }

  private fun readStructure(file: PsiFile): List<Node> {
    val builder = LanguageStructureViewBuilder.getInstance().getStructureViewBuilder(file) as? TreeBasedStructureViewBuilder
                  ?: return emptyList()
    val model = builder.createStructureViewModel(null)
    try {
      val visited = Collections.newSetFromMap(IdentityHashMap<PsiElement, Boolean>())
      val root = model.root
      return readNodes(if (root.value is PsiFile) root.children.asList() else listOf(root), file, visited)
    }
    finally {
      Disposer.dispose(model)
    }
  }

  private fun readNodes(treeElements: List<TreeElement>, file: PsiFile, visited: MutableSet<PsiElement>): List<Node> {
    val result = ArrayList<Node>()
    for (treeElement in treeElements) {
      ProgressManager.checkCanceled()
      val element = (treeElement as? StructureViewTreeElement)?.value as? PsiElement
      if (element == null || element is PsiFile) {
        result.addAll(readNodes(treeElement.children.asList(), file, visited))
        continue
      }
      if (!element.isValid || element.containingFile != file || !visited.add(element)) continue
      val presentation = treeElement.presentation
      val text = presentation.presentableText ?: (element as? PsiNamedElement)?.name ?: continue
      val displayText = ElementDescriptionUtil.getElementDescription(element, StructureChangeDescriptionLocation(text))
      val name = (treeElement as? SortableTreeElement)?.alphaSortKey ?: (element as? PsiNamedElement)?.name ?: text
      val key = element.javaClass.name + ":" + name
      val children = readNodes(treeElement.children.asList(), file, visited)
      result.add(Node(element, key, text, displayText, presentation.getIcon(false), children))
    }
    return result
  }

  private fun assignIds(before: List<Node>, after: List<Node>, parentId: String) {
    val oldGroups = before.groupBy { it.key }
    val groups = after.groupBy { it.key }
    for (key in oldGroups.keys + groups.keys) {
      val oldGroup = oldGroups[key].orEmpty()
      val group = groups[key].orEmpty()
      val usePresentation = oldGroup.size > 1 || group.size > 1
      for (siblings in listOf(oldGroup, group)) {
        val occurrences = HashMap<String, Int>()
        for (node in siblings) {
          val identity = if (usePresentation) key + ":" + node.text else key
          val occurrence = occurrences.getOrDefault(identity, 0)
          occurrences[identity] = occurrence + 1
          node.id = "$parentId/${identity.length}:$identity:$occurrence"
        }
      }
      val previous = oldGroup.associateBy { it.id }
      for (node in group) {
        assignIds(previous[node.id]?.children.orEmpty(), node.children, node.id)
      }
      val currentIds = group.mapTo(HashSet()) { it.id }
      for (node in oldGroup) {
        if (node.id !in currentIds) assignIds(node.children, emptyList(), node.id)
      }
    }
  }

  private fun indexNodes(nodes: List<Node>, result: MutableMap<PsiElement, Node>) {
    for (node in nodes) {
      result[node.element] = node
      indexNodes(node.children, result)
    }
  }

  private fun merge(before: List<Node>, after: List<Node>, statuses: Map<PsiElement, FileStatus>): List<StructureChange> {
    val previous = before.associateByTo(LinkedHashMap()) { it.id }
    val result = ArrayList<StructureChange>()
    for (node in after) {
      ProgressManager.checkCanceled()
      val old = previous.remove(node.id)
      val children = merge(old?.children.orEmpty(), node.children, statuses)
      var status = statuses[node.element] ?: FileStatus.NOT_CHANGED
      if (status == FileStatus.MODIFIED && old != null && children.isNotEmpty() && ownText(node) == ownText(old)) {
        status = FileStatus.NOT_CHANGED
      }
      if (status != FileStatus.NOT_CHANGED || children.isNotEmpty()) {
        result.add(StructureChange(node.id, node.displayText, snapshotIcon(node.icon), status, node.element.textOffset, children))
      }
    }
    for (node in previous.values) {
      result.add(StructureChange(node.id, node.displayText, snapshotIcon(node.icon), FileStatus.DELETED, null,
                                 merge(node.children, emptyList(), statuses)))
    }
    return java.util.List.copyOf(result)
  }

  private fun snapshotIcon(icon: Icon?): Icon? = icon?.let { IconLoader.toImage(it)?.let(IconUtil::createImageIcon) }

  private fun ownText(node: Node): String {
    val children = Collections.newSetFromMap(IdentityHashMap<PsiElement, Boolean>())
    node.children.mapTo(children) { it.element }
    val text = StringBuilder()
    node.element.accept(object : PsiRecursiveElementWalkingVisitor() {
      override fun visitElement(element: PsiElement) {
        ProgressManager.checkCanceled()
        if (element in children || element is PsiWhiteSpace) return
        if (element.firstChild == null) text.append(element.text)
        else super.visitElement(element)
      }
    })
    return text.toString()
  }

  /** Holds the PSI only during collection under the read lock. */
  @Suppress("StatefulEp")
  private class Node(
    val element: PsiElement,
    val key: String,
    val text: @NlsSafe String,
    val displayText: @NlsSafe String,
    val icon: Icon?,
    val children: List<Node>,
    var id: String = "",
  )
}
