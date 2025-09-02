// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework

import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase
import com.intellij.ide.structureView.logical.impl.LogicalStructureViewService
import com.intellij.ide.util.treeView.PresentableNodeDescriptor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.ui.SimpleTextAttributes
import junit.framework.AssertionFailedError
import junit.framework.ComparisonFailure
import junit.framework.TestCase.assertNotNull
import javax.swing.Icon

private const val MAX_DEPTH = 20

fun assertLogicalStructure(
  psiFile: PsiFile,
  nodePath: String? = null,
  expectedStructureInitializer: LogicalStructureNode.() -> Unit,
) {
  val project = psiFile.project
  val builder = LogicalStructureViewService.getInstance(project).getLogicalStructureBuilder(psiFile)
  assertNotNull(builder)
  val structureView = builder!!.createStructureView(null, project)
  var targetStructureElement = structureView.treeModel.root
  nodePath?.split("/")?.forEach { pathPart ->
    val child = targetStructureElement.children.firstOrNull {
      val presentation = it.presentation
      presentation.presentableText == pathPart || (presentation as? PresentationData)?.coloredText?.firstOrNull()?.text == pathPart
    } as? StructureViewTreeElement
    assertNotNull("Can't find a child '$pathPart'", child)
    targetStructureElement = child!!
  }
  var actualRoot = createActualNode(targetStructureElement)
  if (nodePath != null) {
    actualRoot = LogicalStructureNode(null, "", "").also {
      it.subNode(actualRoot)
    }
  }
  val expectedRoot = LogicalStructureNode(null, "", "")
  expectedRoot.expectedStructureInitializer()
  if (!expectedRoot.isEqualTo(actualRoot, false)) {
    expectedRoot.synchronizeImportantElements(actualRoot)
    throw ComparisonFailure("The models are not equals: ",
                            expectedRoot.print("", false),
                            actualRoot.print("", false))
  }
}

private fun createActualNode(element: StructureViewTreeElement): LogicalStructureNode {
  val presentation = element.presentation
  val node = LogicalStructureNode(
    presentation.getIcon(false),
    presentation.presentableText ?: "",
    presentation.locationString ?: "",
    (presentation as? PresentationData)?.coloredText ?: emptyList()
  )
  for (child in element.children) {
    if (child !is StructureViewTreeElement) continue
    node.subNode(createActualNode(child))
  }
  node.navigationElement { (element as? PsiTreeElementBase<*>)?.element }
  return node
}

class LogicalStructureNode(
  val icon: Icon?,
  val name: String,
  val location: String,
  val coloredTextElements: List<PresentableNodeDescriptor.ColoredFragment> = emptyList(),
) {

  private val subNodes = mutableListOf<LogicalStructureNode>()
  private var childrenDontMatter = false
  private var childrenOrderDontMatter = false
  private var navigationElementSupplier: (() -> PsiElement?)? = null

  fun subNode(subNode: LogicalStructureNode) {
    subNodes.add(subNode)
  }

  fun node(icon: Icon?, name: String, location: String? = "", childrenDontMatter: Boolean = false, initializer: (LogicalStructureNode.() -> Unit)? = null) {
    val subNode = LogicalStructureNode(icon, name, location ?: "")
    if (childrenDontMatter) subNode.anyNodes()
    initializer?.invoke(subNode)
    subNodes.add(subNode)
  }

  fun node(icon: Icon?, vararg coloredText: Pair<String, SimpleTextAttributes>, initializer: (LogicalStructureNode.() -> Unit)? = null) {
    val subNode = LogicalStructureNode(icon, "", "", coloredText.map { PresentableNodeDescriptor.ColoredFragment(it.first, it.second) })
    initializer?.invoke(subNode)
    subNodes.add(subNode)
  }

  fun anyNodes() {
    childrenDontMatter = true
  }

  fun arbitraryChildrenOrder() {
    childrenOrderDontMatter = true
  }

  fun navigationElement(element: PsiElement) {
    navigationElementSupplier =  { element }
  }

  fun navigationElement(supplier: () -> PsiElement?) {
    navigationElementSupplier = supplier
  }

  fun synchronizeImportantElements(other: LogicalStructureNode) {
    if (name != other.name) return
    if (navigationElementSupplier == null) {
      other.navigationElementSupplier = null
    }
    if (childrenDontMatter) {
      other.anyNodes()
      return
    }
    if (subNodes.size != other.subNodes.size) return
    for (i in subNodes.indices) {
      subNodes[i].synchronizeImportantElements(other.subNodes[i])
    }
  }

  fun isEqualTo(other: LogicalStructureNode, compareItSelf: Boolean, availableDepth: Int = MAX_DEPTH): Boolean {
    if (availableDepth <= 0) throw AssertionFailedError("The structure is too deep. Most probably there is cyclic nodes here.")
    if (compareItSelf) {
      if (coloredTextElements.isNotEmpty()) {
        if (coloredTextElements.size != other.coloredTextElements.size) return false
        for (i in coloredTextElements.indices) {
          if (coloredTextElements[i] != other.coloredTextElements[i]) return false
        }
      } else {
        if (icon != other.icon || name != other.name || location != other.location) return false
      }
      if (navigationElementSupplier != null && navigationElementSupplier!!.invoke() != other.navigationElementSupplier?.invoke()) return false
    }
    if (!childrenDontMatter) {
      if (subNodes.size != other.subNodes.size) return false
      if (childrenOrderDontMatter) {
        return subNodes.all {
          other.subNodes.any { otherSubNode -> it.isEqualTo(otherSubNode, true, availableDepth - 1) }
        }
      }
      else {
        for (i in subNodes.indices) {
          if (!subNodes[i].isEqualTo(other.subNodes[i], true, availableDepth - 1)) return false
        }
      }
    }
    return true
  }

  fun print(indent: String = "", printItself: Boolean = true): String {
    var result = indent
    icon?.toString()?.let { result += "[$it] " }
    if (coloredTextElements.isNotEmpty()) {
      result += coloredTextElements.joinToString(" | ") { it.text }
    } else {
      result += name
      if (location.isNotEmpty()) result += " | $location"
    }
    if (navigationElementSupplier != null) {
      result += "\n$indent navigation to: ${navigationElementSupplier!!.invoke()}"
    }
    if (childrenDontMatter) {
      result += "\n$indent  some nodes..."
    }
    else {
      for (subNode in subNodes) {
        result += "\n" + subNode.print(if (printItself) "$indent  " else indent)
      }
    }
    if (!printItself) result = result.substringAfter("\n")
    return result
  }

}