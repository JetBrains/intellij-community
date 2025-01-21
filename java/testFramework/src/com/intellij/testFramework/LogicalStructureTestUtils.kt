// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework

import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.structureView.logical.impl.LogicalStructureViewService
import com.intellij.ide.util.treeView.PresentableNodeDescriptor
import com.intellij.psi.PsiFile
import com.intellij.ui.SimpleTextAttributes
import junit.framework.AssertionFailedError
import junit.framework.ComparisonFailure
import javax.swing.Icon

object LogicalStructureTestUtils {

  private const val MAX_DEPTH = 20

  fun assertLogicalStructureForFile(
    psiFile: PsiFile,
    expectedStructureInitializer: LogicalStructureNode.() -> Unit,
  ) {
    val project = psiFile.project
    val builder = LogicalStructureViewService.getInstance(project).getLogicalStructureBuilder(psiFile)
    val structureView = builder!!.createStructureView(null, project)
    val actualRoot = createActualNode(structureView.treeModel.root)
    val expectedRoot = LogicalStructureNode(null, "root", "")
    expectedRoot.expectedStructureInitializer()
    if (!expectedRoot.isEqualTo(actualRoot, false)) {
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
    return node
  }

  class LogicalStructureNode(
    val icon: Icon?,
    val name: String,
    val location: String,
    val coloredTextElements: List<PresentableNodeDescriptor.ColoredFragment> = emptyList(),
  ) {

    private val subNodes = mutableListOf<LogicalStructureNode>()

    fun subNode(subNode: LogicalStructureNode) {
      subNodes.add(subNode)
    }

    fun node(icon: Icon?, name: String, location: String? = "", initializer: (LogicalStructureNode.() -> Unit)? = null) {
      val subNode = LogicalStructureNode(icon, name, location ?: "")
      initializer?.invoke(subNode)
      subNodes.add(subNode)
    }

    fun node(icon: Icon?, vararg coloredText: Pair<String, SimpleTextAttributes>, initializer: (LogicalStructureNode.() -> Unit)? = null) {
      val subNode = LogicalStructureNode(icon, "", "", coloredText.map { PresentableNodeDescriptor.ColoredFragment(it.first, it.second) })
      initializer?.invoke(subNode)
      subNodes.add(subNode)
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
      }
      if (subNodes.size != other.subNodes.size) return false
      for (i in subNodes.indices) {
        if (!subNodes[i].isEqualTo(other.subNodes[i], true, availableDepth - 1)) return false
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
      for (subNode in subNodes) {
        result += "\n" + subNode.print(if (printItself) "$indent  " else indent)
      }
      if (!printItself) result = result.substringAfter("\n")
      return result
    }

  }

}