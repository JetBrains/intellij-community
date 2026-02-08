// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.analysis.problemsView.toolWindow

import com.intellij.analysis.problemsView.ProblemsProvider
import com.intellij.analysis.problemsView.toolWindow.ProblemsViewHighlightingChildrenBuilder.prepareChildrenForFileRoot
import com.intellij.codeInsight.multiverse.CodeInsightContext
import com.intellij.ide.projectView.PresentationData
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import com.intellij.testFramework.junit5.fixture.virtualFileFixture
import com.intellij.ui.tree.LeafState
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import javax.swing.Icon

/**
 * Tests for [prepareChildrenForFileRoot] function that verifies
 * the grouping behavior for context groups and tool IDs.
 */
@TestApplication
class ProblemsViewHighlightingFileRootTest {

  companion object {
    private val projectFixture = projectFixture()
    private val moduleFixture = projectFixture.moduleFixture("ProblemsViewHighlightingFileRootTest")
    private val sourceRoot = moduleFixture.sourceRootFixture()
  }

  private val project by projectFixture
  private val testFile by sourceRoot.virtualFileFixture("TestFile.kt", "")

  @Test
  fun `single context without tool grouping returns direct ProblemNodes`() {
    val problems = listOf(
      testProblem(contextGroup = null, group = "Tool A"),
      testProblem(contextGroup = null, group = "Tool B"),
      testProblem(contextGroup = null, group = null)
    )

    val children = prepareChildrenForFileRoot(problems, fileNode(), groupByToolId = false)

    assertSize(3, children.size, "Expected 3 problem nodes")
    assertTrue(children.all { it.isProblemNode() })
  }

  @Test
  fun `single context with tool grouping returns ProblemsViewGroupNodes`() {
    val problems = listOf(
      testProblem(contextGroup = null, group = "Tool A"),
      testProblem(contextGroup = null, group = "Tool A"),
      testProblem(contextGroup = null, group = "Tool B"),
      testProblem(contextGroup = null, group = null)
    )

    val children = prepareChildrenForFileRoot(problems, fileNode(), groupByToolId = true)

    // Should have: 2 ProblemsViewGroupNode (for Tool A and Tool B) + 1 ProblemNode (for null group)
    val groupNodes = children.filter { it.isGroupNode() }
    val problemNodes = children.filter { it.isProblemNode() }

    assertSize(2, groupNodes.size, "Expected 2 group nodes for 'Tool A' and 'Tool B'")
    assertSize(1, problemNodes.size, "Expected 1 problem node for null group")
    assertTrue(groupNodes.any { it.name == "Tool A" })
    assertTrue(groupNodes.any { it.name == "Tool B" })
  }

  @Test
  fun `multiple contexts without tool grouping returns ProblemsContextNodes`() {
    val contextA = TestCodeInsightContext("Context A")
    val contextB = TestCodeInsightContext("Context B")

    val problems = listOf(
      testProblem(contextGroup = contextA, group = "Tool A"),
      testProblem(contextGroup = contextB, group = "Tool B"),
      testProblem(contextGroup = null, group = "Global Tool")
    )

    val children = prepareChildrenForFileRoot(problems, fileNode(), groupByToolId = false)

    // Should have: 2 ProblemsContextNode (for contextA and contextB) + 1 ProblemNode (for global null context)
    val contextNodes = children.filter { it.isContextNode() }
    val problemNodes = children.filter { it.isProblemNode() }

    assertSize(2, contextNodes.size, "Expected 2 context nodes")
    assertSize(1, problemNodes.size, "Expected 1 problem node for global context")
  }

  @Test
  fun `multiple contexts with tool grouping creates context nodes with groupByToolId flag`() {
    val contextA = TestCodeInsightContext("Context A")
    val contextB = TestCodeInsightContext("Context B")

    val problems = listOf(
      testProblem(contextGroup = contextA, group = "Tool A"),
      testProblem(contextGroup = contextB, group = "Tool B")
    )

    val children = prepareChildrenForFileRoot(problems, fileNode(), groupByToolId = true)

    // Should have ProblemsContextNodes
    val contextNodes = children.filter { it.isContextNode() }
    assertSize(2, contextNodes.size, "Expected 2 context nodes")
  }

  @Test
  fun `single non-null context plus global context is NOT grouped by context`() {
    val contextA = TestCodeInsightContext("Context A")

    val problems = listOf(
      testProblem(contextGroup = contextA, group = "Tool A"),
      testProblem(contextGroup = null, group = "Global Tool")
    )

    val children = prepareChildrenForFileRoot(problems, fileNode(), groupByToolId = false)

    // hasSeveralContextGroups returns false when size <= 2 with null present
    // So this should return direct ProblemNodes, NOT ProblemsContextNodes
    assertTrue(children.all { it.isProblemNode() }, "All children should be ProblemNodes when only 1 non-null context + global")
    assertSize(2, children.size, "Expected 2 problem nodes")
  }

  @Test
  fun `two non-null contexts without global IS grouped by context`() {
    val contextA = TestCodeInsightContext("Context A")
    val contextB = TestCodeInsightContext("Context B")

    val problems = listOf(
      testProblem(contextGroup = contextA, group = "Tool A"),
      testProblem(contextGroup = contextB, group = "Tool B")
    )

    val children = prepareChildrenForFileRoot(problems, fileNode(), groupByToolId = false)

    // hasSeveralContextGroups returns true when size > 1 without null
    // So this SHOULD return ProblemsContextNodes
    val contextNodes = children.filter { it.isContextNode() }
    assertSize(2, contextNodes.size, "Expected 2 context nodes when 2 non-null contexts present")
  }

  @Test
  fun `two non-null contexts plus global IS grouped by context`() {
    val contextA = TestCodeInsightContext("Context A")
    val contextB = TestCodeInsightContext("Context B")

    val problems = listOf(
      testProblem(contextGroup = contextA, group = "Tool A"),
      testProblem(contextGroup = contextB, group = "Tool B"),
      testProblem(contextGroup = null, group = "Global Tool")
    )

    val children = prepareChildrenForFileRoot(problems, fileNode(), groupByToolId = false)

    // hasSeveralContextGroups returns true when size > 2 with null present
    // So this SHOULD return ProblemsContextNodes for non-null + ProblemNode for global
    val contextNodes = children.filter { it.isContextNode() }
    val problemNodes = children.filter { it.isProblemNode() }

    assertSize(2, contextNodes.size, "Expected 2 context nodes for the 2 non-null contexts")
    assertSize(1, problemNodes.size, "Expected 1 problem node for global context")
  }

  @Test
  fun `only global context returns direct ProblemNodes`() {
    val problems = listOf(
      testProblem(contextGroup = null, group = "Tool A"),
      testProblem(contextGroup = null, group = "Tool B")
    )

    val children = prepareChildrenForFileRoot(problems, fileNode(), groupByToolId = false)

    // Only null context should return direct ProblemNodes
    assertTrue(children.all { it.isProblemNode() })
    assertSize(2, children.size, "Expected 2 problem nodes")
  }

  @Test
  fun `tool grouping with null group values creates direct ProblemNodes for nulls`() {
    val problems = listOf(
      testProblem(contextGroup = null, group = "Tool A"),
      testProblem(contextGroup = null, group = "Tool A"),
      testProblem(contextGroup = null, group = null),
      testProblem(contextGroup = null, group = null)
    )

    val children = prepareChildrenForFileRoot(problems, fileNode(), groupByToolId = true)

    // Should have: 1 ProblemsViewGroupNode (for Tool A) + 2 ProblemNodes (for null groups)
    val groupNodes = children.filter { it.isGroupNode() }
    val problemNodes = children.filter { it.isProblemNode() }

    assertSize(1, groupNodes.size, "Expected 1 group node for 'Tool A'")
    assertSize(2, problemNodes.size, "Expected 2 problem nodes for null groups")
    assertTrue(groupNodes.first().name == "Tool A")
  }

  // Second level children tests

  @Test
  fun `context node children without tool grouping are direct ProblemNodes`() {
    val contextA = TestCodeInsightContext("Context A")
    val contextB = TestCodeInsightContext("Context B")

    val problems = listOf(
      testProblem(contextGroup = contextA, group = "Tool A"),
      testProblem(contextGroup = contextA, group = "Tool B"),
      testProblem(contextGroup = contextB, group = "Tool C")
    )

    val children = prepareChildrenForFileRoot(problems, fileNode(), groupByToolId = false)
    val contextNodes = children.filter { it.isContextNode() }

    assertSize(2, contextNodes.size, "Expected 2 context nodes")

    // All context node children should be direct ProblemNodes
    for (contextNode in contextNodes) {
      val contextChildren = contextNode.getChildren()
      assertTrue(contextChildren.all { it.isProblemNode() },
                 "All children of context node should be ProblemNodes when groupByToolId=false")
    }
  }

  @Test
  fun `context node children with tool grouping are ProblemsContextGroupNodes`() {
    val contextA = TestCodeInsightContext("Context A")
    val contextB = TestCodeInsightContext("Context B")

    val problems = listOf(
      testProblem(contextGroup = contextA, group = "Tool A"),
      testProblem(contextGroup = contextA, group = "Tool A"),
      testProblem(contextGroup = contextA, group = "Tool B"),
      testProblem(contextGroup = contextB, group = "Tool C")
    )

    val children = prepareChildrenForFileRoot(problems, fileNode(), groupByToolId = true)
    val contextNodes = children.filter { it.isContextNode() }

    assertSize(2, contextNodes.size, "Expected 2 context nodes")

    // Find context node with 2 children (Context A with Tool A and Tool B)
    val contextWith2Groups = contextNodes.first { it.getChildren().size == 2 }
    val context2Children = contextWith2Groups.getChildren()
    assertTrue(context2Children.all { it.isContextGroupNode() },
               "All children of context node should be ProblemsContextGroupNodes when groupByToolId=true")
    assertTrue(context2Children.any { it.name == "Tool A" })
    assertTrue(context2Children.any { it.name == "Tool B" })

    // Find context node with 1 child (Context B with Tool C)
    val contextWith1Group = contextNodes.first { it.getChildren().size == 1 }
    val context1Children = contextWith1Group.getChildren()
    assertTrue(context1Children.first().isContextGroupNode())
    assertTrue(context1Children.first().name == "Tool C")
  }

  @Test
  fun `context node children with tool grouping and null groups have mixed children`() {
    val contextA = TestCodeInsightContext("Context A")
    val contextB = TestCodeInsightContext("Context B")

    val problems = listOf(
      testProblem(contextGroup = contextA, group = "Tool A"),
      testProblem(contextGroup = contextA, group = null),
      testProblem(contextGroup = contextA, group = null),
      testProblem(contextGroup = contextB, group = "Tool B")
    )

    val children = prepareChildrenForFileRoot(problems, fileNode(), groupByToolId = true)
    val contextNodes = children.filter { it.isContextNode() }

    assertSize(2, contextNodes.size, "Expected 2 context nodes")

    // Find context node with 3 children (Context A: 1 group node + 2 problem nodes)
    val contextWithMixedChildren = contextNodes.first { it.getChildren().size == 3 }
    val mixedChildren = contextWithMixedChildren.getChildren()
    val groupNodes = mixedChildren.filter { it.isContextGroupNode() }
    val problemNodes = mixedChildren.filter { it.isProblemNode() }

    assertSize(1, groupNodes.size, "Context should have 1 group node for 'Tool A'")
    assertSize(2, problemNodes.size, "Context should have 2 problem nodes for null groups")
    assertTrue(groupNodes.first().name == "Tool A")
  }

  @Test
  fun `single context group node children with tool grouping are ProblemNodes`() {
    val problems = listOf(
      testProblem(contextGroup = null, group = "Tool A"),
      testProblem(contextGroup = null, group = "Tool A"),
      testProblem(contextGroup = null, group = "Tool B")
    )

    val children = prepareChildrenForFileRoot(problems, fileNode(), groupByToolId = true)
    val groupNodes = children.filter { it.isGroupNode() }

    assertSize(2, groupNodes.size, "Expected 2 group nodes")

    // All group node children should be ProblemNodes
    for (groupNode in groupNodes) {
      val groupChildren = groupNode.getChildren()
      assertTrue(groupChildren.all { it.isProblemNode() },
                 "All children of group node should be ProblemNodes")
    }

    // Tool A group should have 2 problems
    val toolANode = groupNodes.first { it.name == "Tool A" }
    assertSize(2, toolANode.getChildren().size, "Tool A group should have 2 problem nodes")

    // Tool B group should have 1 problem
    val toolBNode = groupNodes.first { it.name == "Tool B" }
    assertSize(1, toolBNode.getChildren().size, "Tool B group should have 1 problem node")
  }

  @Test
  fun `context group node third level children are ProblemNodes`() {
    val contextA = TestCodeInsightContext("Context A")
    val contextB = TestCodeInsightContext("Context B")

    val problems = listOf(
      testProblem(contextGroup = contextA, group = "Tool A"),
      testProblem(contextGroup = contextA, group = "Tool A"),
      testProblem(contextGroup = contextB, group = "Tool B")
    )

    val children = prepareChildrenForFileRoot(problems, fileNode(), groupByToolId = true)
    val contextNodes = children.filter { it.isContextNode() }

    assertSize(2, contextNodes.size, "Expected 2 context nodes")

    // Find context node with Tool A group that has 2 problems
    val contextWithToolA = contextNodes.first { node ->
      node.getChildren().any { it.name == "Tool A" }
    }
    val toolAGroupNode = contextWithToolA.getChildren().first { it.name == "Tool A" }
    val toolAProblems = toolAGroupNode.getChildren()

    assertSize(2, toolAProblems.size, "Tool A group should have 2 problem nodes")
    assertTrue(toolAProblems.all { it.isProblemNode() },
               "All children of context group node should be ProblemNodes")
  }

  private fun fileNode(): FileNode {
    val parentNode = TestParentNode(project)
    return FileNode(parentNode, testFile)
  }

  private fun testProblem(contextGroup: CodeInsightContext?, group: String?): HighlightingProblem {
    val mockProvider = object : ProblemsProvider {
      override val project: Project = this@ProblemsViewHighlightingFileRootTest.project
    }
    val mockHighlighter = mock(RangeHighlighter::class.java)
    return TestHighlightingProblem(
      provider = mockProvider,
      file = testFile,
      highlighter = mockHighlighter,
      testContextGroup = contextGroup,
      testGroup = group
    )
  }
}

private fun assertSize(expected: Int, actual: Int, message: String) {
  assertTrue(expected == actual, "$message: expected $expected but was $actual")
}

private class TestParentNode(project: Project) : Node(project) {
  override fun getLeafState(): LeafState = LeafState.NEVER
  override fun getName(): String = "TestParent"
  override fun update(project: Project, presentation: PresentationData) {}
}

private class TestCodeInsightContext(private val name: String) : CodeInsightContext {
  override fun toString(): String = name

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is TestCodeInsightContext) return false
    return name == other.name
  }

  override fun hashCode(): Int = name.hashCode()
}

private class TestHighlightingProblem(
  provider: ProblemsProvider,
  file: VirtualFile,
  highlighter: RangeHighlighter,
  private val testContextGroup: CodeInsightContext?,
  private val testGroup: String?,
) : HighlightingProblem(provider, file, highlighter) {

  override val contextGroup: CodeInsightContext?
    get() = testContextGroup

  override val group: String?
    get() = testGroup

  override val text: String
    get() = "Test problem"

  override val icon: Icon
    get() = mock()
}

private fun Node.isProblemNode(): Boolean = this::class.simpleName == "ProblemNode"

private fun Node.isGroupNode(): Boolean = this::class.simpleName == "ProblemsViewGroupNode"

private fun Node.isContextNode(): Boolean = this::class.simpleName == "ProblemsContextNode"

private fun Node.isContextGroupNode(): Boolean = this::class.simpleName == "ProblemsContextGroupNode"

