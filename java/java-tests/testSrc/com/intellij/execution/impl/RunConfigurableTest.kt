// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.impl

import com.intellij.execution.application.ApplicationConfigurationType
import com.intellij.execution.impl.RunConfigurableNodeKind.*
import com.intellij.execution.junit.JUnitConfigurationType
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Trinity
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.ui.RowsDnDSupport
import com.intellij.ui.RowsDnDSupport.RefinedDropSupport.Position.*
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.loadElement
import org.jdom.Element
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import java.util.*
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreePath

private val ORDER = arrayOf(CONFIGURATION_TYPE, //Application
                            FOLDER, //1
                            CONFIGURATION, CONFIGURATION, CONFIGURATION, CONFIGURATION, CONFIGURATION, TEMPORARY_CONFIGURATION, TEMPORARY_CONFIGURATION, FOLDER, //2
                            TEMPORARY_CONFIGURATION, FOLDER, //3
                            CONFIGURATION, TEMPORARY_CONFIGURATION, CONFIGURATION_TYPE, //JUnit
                            FOLDER, //4
                            CONFIGURATION, CONFIGURATION, FOLDER, //5
                            CONFIGURATION, CONFIGURATION, TEMPORARY_CONFIGURATION, UNKNOWN //Defaults
)

@RunsInEdt
class RunConfigurableTest {
  companion object {
    @JvmField
    @ClassRule
    val projectRule: ProjectRule = ProjectRule()

    private fun createRunManager(element: Element): RunManagerImpl {
      val runManager = RunManagerImpl(projectRule.project)
      runManager.initializeConfigurationTypes(arrayOf(ApplicationConfigurationType.getInstance(), JUnitConfigurationType.getInstance()))
      runManager.loadState(element)
      return runManager
    }

    private class MockRunConfigurable(override val runManager: RunManagerImpl) : RunConfigurable(projectRule.project) {
      init {
        createComponent()
      }
    }
  }

  @JvmField
  @Rule
  val edtRule: EdtRule = EdtRule()

  @JvmField
  @Rule
  val disposableRule: DisposableRule = DisposableRule()

  private val configurable: RunConfigurable by lazy {
    val result = MockRunConfigurable(createRunManager(loadElement(RunConfigurableTest::class.java.getResourceAsStream("folders.xml"))))
    Disposer.register(disposableRule.disposable, result)
    result
  }

  private val root: DefaultMutableTreeNode
    get() = configurable.root

  private val tree: Tree
    get() = configurable.tree

  private val model: RunConfigurable.MyTreeModel
    get() = configurable.treeModel

  @Test
  fun testDND() {
    doExpand()
    val never = intArrayOf(-1, 0, 14, 22, 23, 999)
    for (i in -1..16) {
      for (j in never) {
        if ((j == 14 || j == 21) && i == j) {
          continue
        }
        assertCannot(j, i, ABOVE)
        assertCannot(j, i, INTO)
        assertCannot(j, i, BELOW)
      }
    }
    assertCan(3, 3, BELOW)
    assertCan(3, 3, ABOVE)
    assertCannot(3, 2, BELOW)
    assertCan(3, 2, ABOVE)
    assertCannot(3, 1, BELOW)
    assertCannot(3, 0, BELOW)
    assertCan(2, 14, ABOVE)
    assertCan(1, 14, ABOVE)
    assertCan(1, 11, ABOVE)
    assertCannot(1, 10, ABOVE)
    assertCannot(1, 10, BELOW)
    assertCannot(8, 6, ABOVE)
    assertCan(8, 6, BELOW)
    assertCannot(5, 7, BELOW)
    assertCan(5, 7, ABOVE)
    assertCannot(15, 11, INTO)
    assertCannot(18, 21, ABOVE)
    assertCan(15, 21, ABOVE)

    assertThat(model.isDropInto(tree, 2, 9)).isTrue()
    assertThat(model.isDropInto(tree, 2, 1)).isTrue()
    assertThat(model.isDropInto(tree, 12, 9)).isTrue()
    assertThat(model.isDropInto(tree, 12, 1)).isTrue()
    assertThat(model.isDropInto(tree, 999, 9)).isFalse()
    assertThat(model.isDropInto(tree, 999, 1)).isFalse()
    assertThat(model.isDropInto(tree, 2, 999)).isFalse()
    assertThat(model.isDropInto(tree, 2, -1)).isFalse()
  }

  private fun doExpand() {
    val toExpand = ArrayList<DefaultMutableTreeNode>()
    RunConfigurable.collectNodesRecursively(root, toExpand, FOLDER)
    assertThat(toExpand).hasSize(5)
    val toExpand2 = ArrayList<DefaultMutableTreeNode>()
    RunConfigurable.collectNodesRecursively(root, toExpand2, CONFIGURATION_TYPE)
    toExpand.addAll(toExpand2)
    for (node in toExpand) {
      tree.expandPath(TreePath(node.path))
    }

    assertThat(ORDER.mapIndexed { index, _ -> RunConfigurable.getKind(tree.getPathForRow(index).lastPathComponent as DefaultMutableTreeNode) }).containsExactly(*ORDER)
  }

  private fun assertCan(oldIndex: Int, newIndex: Int, position: RowsDnDSupport.RefinedDropSupport.Position) {
    assertDrop(oldIndex, newIndex, position, true)
  }

  private fun assertCannot(oldIndex: Int, newIndex: Int, position: RowsDnDSupport.RefinedDropSupport.Position) {
    assertDrop(oldIndex, newIndex, position, false)
  }

  private fun assertDrop(oldIndex: Int, newIndex: Int, position: RowsDnDSupport.RefinedDropSupport.Position, canDrop: Boolean) {
    val message = StringBuilder()
    message.append("(").append(oldIndex).append(")").append(tree.getPathForRow(oldIndex)).append("->")
    message.append("(").append(newIndex).append(")").append(tree.getPathForRow(newIndex)).append(position)
    if (canDrop) {
      // message.toString()
      assertThat(model.canDrop(oldIndex, newIndex, position)).isTrue()
    }
    else {
      // message.toString()
      assertThat(model.canDrop(oldIndex, newIndex, position)).isFalse()
    }
  }

  @Test
  fun testMoveUpDown() {
    doExpand()
    checkPositionToMove(0, 1, null)
    checkPositionToMove(2, 1, Trinity.create<Int, Int, RowsDnDSupport.RefinedDropSupport.Position>(2, 3, BELOW))
    checkPositionToMove(2, -1, null)
    checkPositionToMove(14, 1, null)
    checkPositionToMove(14, -1, null)
    checkPositionToMove(15, -1, null)
    checkPositionToMove(16, -1, null)
    checkPositionToMove(3, -1, Trinity.create<Int, Int, RowsDnDSupport.RefinedDropSupport.Position>(3, 2, ABOVE))
    checkPositionToMove(6, 1, Trinity.create<Int, Int, RowsDnDSupport.RefinedDropSupport.Position>(6, 9, BELOW))
    checkPositionToMove(7, 1, Trinity.create<Int, Int, RowsDnDSupport.RefinedDropSupport.Position>(7, 8, BELOW))
    checkPositionToMove(10, -1, Trinity.create<Int, Int, RowsDnDSupport.RefinedDropSupport.Position>(10, 8, BELOW))
    checkPositionToMove(8, 1, Trinity.create<Int, Int, RowsDnDSupport.RefinedDropSupport.Position>(8, 9, BELOW))
    checkPositionToMove(21, -1, Trinity.create<Int, Int, RowsDnDSupport.RefinedDropSupport.Position>(21, 20, BELOW))
    checkPositionToMove(21, 1, null)
    checkPositionToMove(20, 1, Trinity.create<Int, Int, RowsDnDSupport.RefinedDropSupport.Position>(20, 21, ABOVE))
    checkPositionToMove(20, -1, Trinity.create<Int, Int, RowsDnDSupport.RefinedDropSupport.Position>(20, 19, ABOVE))
    checkPositionToMove(19, 1, Trinity.create<Int, Int, RowsDnDSupport.RefinedDropSupport.Position>(19, 20, BELOW))
    checkPositionToMove(19, -1, Trinity.create<Int, Int, RowsDnDSupport.RefinedDropSupport.Position>(19, 17, BELOW))
    checkPositionToMove(17, -1, Trinity.create<Int, Int, RowsDnDSupport.RefinedDropSupport.Position>(17, 16, ABOVE))
    checkPositionToMove(17, 1, Trinity.create<Int, Int, RowsDnDSupport.RefinedDropSupport.Position>(17, 18, BELOW))
  }

  private fun checkPositionToMove(selectedRow: Int, direction: Int, expected: Trinity<Int, Int, RowsDnDSupport.RefinedDropSupport.Position>?) {
    tree.setSelectionRow(selectedRow)
    assertThat(configurable.getAvailableDropPosition(direction)).isEqualTo(expected)
  }

  @Test
  fun testSort() {
    doExpand()
    assertThat(configurable.isModified).isFalse()
    model.drop(2, 0, ABOVE)
    assertThat(configurable.isModified).isTrue()
    configurable.apply()
    assertThat(configurable.runManager.allSettings.map { it.name }).isEqualTo(listOf("Renamer",
                                                                                     "UI",
                                                                                     "AuTest",
                                                                                     "Simples",
                                                                                     "OutAndErr",
                                                                                     "C148C_TersePrincess",
                                                                                     "Periods",
                                                                                     "C148E_Porcelain",
                                                                                     "ErrAndOut",
                                                                                     "All in titled",
                                                                                     "All in titled2",
                                                                                     "All in titled3",
                                                                                     "All in titled4",
                                                                                     "All in titled5"))
    assertThat(configurable.isModified).isFalse()
    model.drop(4, 8, BELOW)
    configurable.apply()
    assertThat(configurable.runManager.allSettings.map { it.name }).isEqualTo(listOf("Renamer",
                                                                                     "AuTest",
                                                                                     "Simples",
                                                                                     "UI",
                                                                                     "OutAndErr",
                                                                                     "C148C_TersePrincess",
                                                                                     "Periods",
                                                                                     "C148E_Porcelain",
                                                                                     "ErrAndOut",
                                                                                     "All in titled",
                                                                                     "All in titled2",
                                                                                     "All in titled3",
                                                                                     "All in titled4",
                                                                                     "All in titled5"))
  }
}