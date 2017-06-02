/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide.projectView

import com.intellij.ide.projectView.impl.ModuleGroup
import com.intellij.ide.projectView.impl.ModuleGroupingImplementation
import com.intellij.ide.projectView.impl.ModuleGroupingTreeHelper
import com.intellij.openapi.util.Pair
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.UsefulTestCase
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.tree.TreeUtil
import junit.framework.TestCase
import java.util.*
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

/**
 * @author nik
 */
class ModuleGroupingTreeHelperTest: UsefulTestCase() {
  private lateinit var tree: Tree
  private lateinit var root: MockModuleTreeNode
  private lateinit var model: DefaultTreeModel

  override fun setUp() {
    super.setUp()
    root = MockModuleTreeNode("root")
    model = DefaultTreeModel(root)
    tree = Tree(model)
  }

  fun `test disabled grouping`() {
    createHelper(false).createModuleNodes("a.main")
    assertTreeEqual("""
            -root
             a.main""")
    createHelperFromTree(true).moveAllModuleNodesAndCheckResult("""
            -root
             -a
              a.main""")
  }

  fun `test single module`() {
    createHelper().createModuleNodes("a.main")
    assertTreeEqual("""
            -root
             -a
              a.main""")

    createHelperFromTree(false).moveAllModuleNodesAndCheckResult("""
            -root
             a.main""")
  }

  fun `test two modules`() {
    createHelper().createModuleNodes("a.main", "a.util")
    assertTreeEqual("""
            -root
             -a
              a.main
              a.util
""")

    createHelperFromTree(false).moveAllModuleNodesAndCheckResult("""
            -root
             a.main
             a.util""")
  }

  fun `test two groups`() {
    createHelper().createModuleNodes("a.main", "b.util", "a.util", "b.main")
    assertTreeEqual("""
            -root
             -a
              a.main
              a.util
             -b
              b.main
              b.util
""")

    createHelperFromTree(false).moveAllModuleNodesAndCheckResult("""
            -root
             a.main
             a.util
             b.main
             b.util
""")
  }

  fun `test module as a group`() {
    createHelper().createModuleNodes("a.impl", "a", "a.tests")
    assertTreeEqual("""
            -root
             -a
              a.impl
              a.tests
""")

    createHelperFromTree(false).moveAllModuleNodesAndCheckResult("""
            -root
             a
             a.impl
             a.tests""")
  }

  fun `test move module node to new group`() {
    val nodes = createHelper().createModuleNodes("main", "util")
    assertTreeEqual("""
            -root
             main
             util""")
    val node = nodes.find { it.second.name == "main" }!!
    node.second.name = "a.main"
    moveModuleNodeToProperGroupAndCheckResult(node, """
                  -root
                   -a
                    a.main
                   util""")
  }

  fun `test move module node from parent module`() {
    val nodes = createHelper().createModuleNodes("a", "a.main")
    assertTreeEqual("""
            -root
             -a
              a.main""")
    val node = nodes.find { it.second.name == "a.main" }!!
    node.second.name = "main"
    moveModuleNodeToProperGroupAndCheckResult(node, """
            -root
             a
             main""")
  }

  fun `test move module node to parent module`() {
    val nodes = createHelper().createModuleNodes("a", "main")
    val node = nodes.find { it.second.name == "main" }!!
    node.second.name = "a.main"
    moveModuleNodeToProperGroupAndCheckResult(node, """
            -root
             -a
              a.main""")
  }

  fun `test module node become parent module`() {
    val nodes = createHelper().createModuleNodes("b", "a.main")
    val node = nodes.find { it.second.name == "b" }!!
    node.second.name = "a"
    moveModuleNodeToProperGroupAndCheckResult(node, """
            -root
             -a
              a.main""")
  }

  fun `test parent module become ordinary module`() {
    val nodes = createHelper().createModuleNodes("a", "a.main")
    val node = nodes.find { it.second.name == "a" }!!
    node.second.name = "b"
    moveModuleNodeToProperGroupAndCheckResult(node, """
            -root
             -a
              a.main
             b""")
  }

  fun `test do not move node if its group wasn't changed`() {
    val nodes = createHelper().createModuleNodes("a", "a.main")
    nodes.forEach {
      val newNode = createHelperFromTree().moveModuleNodeToProperGroup(it.first, it.second, root, model, tree)
      assertSame(it.first, newNode)
    }
  }

  private fun moveModuleNodeToProperGroupAndCheckResult(node: Pair<MockModuleTreeNode, MockModule>,
                                                        expected: String) {
    val helper = createHelperFromTree()
    helper.checkConsistency()
    helper.moveModuleNodeToProperGroup(node.first, node.second, root, model, tree)
    assertTreeEqual(expected)
    helper.checkConsistency()
  }

  private fun ModuleGroupingTreeHelper<MockModule, MockModuleTreeNode>.moveAllModuleNodesAndCheckResult(expected: String) {
    checkConsistency()
    moveAllModuleNodesToProperGroups(root, model)
    assertTreeEqual(expected)
    checkConsistency()
  }

  private fun ModuleGroupingTreeHelper<MockModule, MockModuleTreeNode>.createModuleNodes(vararg names: String): List<Pair<MockModuleTreeNode, MockModule>> {
    val modules = createModules(*names)
    val nodes = createModuleNodes(modules, root, model)
    return nodes.map { Pair(it, (it as MockModuleNode).module)}
  }

  private fun assertTreeEqual(expected: String) {
    TreeUtil.expandAll(tree)
    PlatformTestUtil.assertTreeEqual(tree, expected.trimIndent() + "\n")
  }

  private fun createHelper(enableGrouping: Boolean = true): ModuleGroupingTreeHelper<MockModule, MockModuleTreeNode> {
    return ModuleGroupingTreeHelper.forEmptyTree(enableGrouping, mockModuleGrouping, ::MockModuleGroupNode, ::MockModuleNode, nodeComparator)
  }

  private fun createHelperFromTree(enableGrouping: Boolean = true): ModuleGroupingTreeHelper<MockModule, MockModuleTreeNode> {
    return ModuleGroupingTreeHelper.forTree(root, { it.moduleGroup }, { (it as? MockModuleNode)?.module },
                                            enableGrouping, mockModuleGrouping, ::MockModuleGroupNode, ::MockModuleNode, nodeComparator)
  }

  private fun createModules(vararg names: String) = names.map { MockModule(it) }

  private fun ModuleGroupingTreeHelper<MockModule, MockModuleTreeNode>.checkConsistency() {
    val expectedNodeForGroup = HashMap<ModuleGroup, MockModuleTreeNode>(getNodeForGroupMap())
    val expectedGroupByNode = HashMap<MockModuleTreeNode, ModuleGroup>(getGroupByNodeMap())
    val expectedModuleByNode = HashMap<MockModuleTreeNode, MockModule>(getModuleByNodeMap())
    TreeUtil.traverse(root, { o ->
      val node = o as MockModuleTreeNode
      if (node == root) return@traverse true
      TestCase.assertSame(node, expectedNodeForGroup[node.moduleGroup])
      expectedNodeForGroup.remove(node.moduleGroup)
      TestCase.assertEquals(node.moduleGroup, expectedGroupByNode[node])
      expectedGroupByNode.remove(node)
      if (node is MockModuleNode) {
        TestCase.assertEquals(node.module, expectedModuleByNode[node])
        expectedModuleByNode.remove(node)
      }
      true
    })
    assertEmpty("Unexpected nodes in helper", expectedNodeForGroup.entries)
    assertEmpty("Unexpected groups in helper", expectedGroupByNode.entries)
    assertEmpty("Unexpected modules in helper", expectedModuleByNode.entries)
  }
}

private val nodeComparator = Comparator.comparing { node: MockModuleTreeNode -> node.text }

private open class MockModuleTreeNode(userObject: Any, val text: String = userObject.toString()): DefaultMutableTreeNode(text) {
  open val moduleGroup: ModuleGroup? = null
  override fun toString() = text
}

private val mockModuleGrouping = object : ModuleGroupingImplementation<MockModule> {
  override fun getGroupPath(m: MockModule) = m.name.split('.').dropLast(1)
  override fun getModuleAsGroupPath(m: MockModule) = m.name.split('.')
}
private class MockModule(var name: String)

private class MockModuleNode(val module: MockModule): MockModuleTreeNode(module, module.name) {
  override val moduleGroup = ModuleGroup(mockModuleGrouping.getModuleAsGroupPath(module)!!)
}

private class MockModuleGroupNode(override val moduleGroup: ModuleGroup): MockModuleTreeNode(moduleGroup)