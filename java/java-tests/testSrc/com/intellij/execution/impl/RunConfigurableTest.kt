// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.impl

import com.intellij.execution.actions.ChooseRunConfigurationManager
import com.intellij.execution.actions.ExecutorProvider
import com.intellij.execution.application.ApplicationConfigurationType
import com.intellij.execution.impl.RunConfigurableNodeKind.*
import com.intellij.execution.junit.JUnitConfigurationType
import com.intellij.ide.DataManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.Trinity
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.ui.RowsDnDSupport
import com.intellij.ui.RowsDnDSupport.RefinedDropSupport.Position.*
import com.intellij.ui.treeStructure.Tree
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreePath
import kotlin.test.assertFalse

private val ORDER = arrayOf(CONFIGURATION_TYPE, //Application
                            FOLDER, //1
                            CONFIGURATION, CONFIGURATION, CONFIGURATION, CONFIGURATION, CONFIGURATION, TEMPORARY_CONFIGURATION,
                            TEMPORARY_CONFIGURATION, FOLDER, //2
                            TEMPORARY_CONFIGURATION, FOLDER, //3
                            CONFIGURATION, TEMPORARY_CONFIGURATION, CONFIGURATION_TYPE, //JUnit
                            FOLDER, //4
                            CONFIGURATION, CONFIGURATION, FOLDER, //5
                            CONFIGURATION, CONFIGURATION, TEMPORARY_CONFIGURATION
)

@RunsInEdt
internal class RunConfigurableTest {
  companion object {
    @JvmField
    @ClassRule
    val projectRule = ProjectRule(runPostStartUpActivities = false)
  }

  @JvmField
  @Rule
  val edtRule = EdtRule()

  @JvmField
  @Rule
  val disposableRule = DisposableRule()

  private val configurable by lazy {
    val runManager = RunManagerImpl(projectRule.project)
    runManager.initializeConfigurationTypes(listOf(ApplicationConfigurationType.getInstance(), JUnitConfigurationType.getInstance()))
    runManager.loadState(JDOMUtil.load(RunConfigurableTest::class.java.getResourceAsStream("folders.xml")))

    val result = object : ProjectRunConfigurationConfigurable(projectRule.project) {
      override val runManager = runManager
    }
    result.createComponent()
    Disposer.register(disposableRule.disposable, runManager)
    Disposer.register(runManager, result)
    result
  }

  private val root: DefaultMutableTreeNode
    get() = configurable.root

  private val tree: Tree
    get() = configurable.tree

  private val model: RunConfigurable.MyTreeModel
    get() = configurable.treeModel

  @Test
  fun dnd() {
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
    assertCannot(3, 3, BELOW)
    assertCannot(3, 3, ABOVE)
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

    assertCannot(arrayOf(1, 2, 3), 2, BELOW)
    assertCan(arrayOf(2, 12), 9, INTO)

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

    assertThat(ORDER.mapIndexed { index, _ ->
      RunConfigurable.getKind(tree.getPathForRow(index).lastPathComponent as DefaultMutableTreeNode)
    }).containsExactly(*ORDER)
  }

  private fun assertCan(oldIndex: Int, newIndex: Int, position: RowsDnDSupport.RefinedDropSupport.Position) {
    assertDrop(arrayOf(oldIndex), newIndex, position, true)
  }

  private fun assertCannot(oldIndex: Int, newIndex: Int, position: RowsDnDSupport.RefinedDropSupport.Position) {
    assertDrop(arrayOf(oldIndex), newIndex, position, false)
  }

  private fun assertCannot(oldIndices: Array<Int>, newIndex: Int, position: RowsDnDSupport.RefinedDropSupport.Position) {
    assertDrop(oldIndices, newIndex, position, false)
  }

  private fun assertCan(oldIndices: Array<Int>, newIndex: Int, position: RowsDnDSupport.RefinedDropSupport.Position) {
    assertDrop(oldIndices, newIndex, position, true)
  }

  private fun assertDrop(oldIndices: Array<Int>, newIndex: Int, position: RowsDnDSupport.RefinedDropSupport.Position, canDrop: Boolean) {
    assertThat(oldIndices.isNotEmpty())
    tree.selectionPaths = oldIndices.map { tree.getPathForRow(it) }.toTypedArray()
    if (canDrop) {
      assertThat(model.canDrop(oldIndices[0], newIndex, position)).isTrue()
    }
    else {
      assertThat(model.canDrop(oldIndices[0], newIndex, position)).isFalse()
    }
  }

  @Test
  fun testMoveUpDown() {
    doExpand()
    checkPositionToMove(0, 1, null)
    checkPositionToMove(2, 1, Trinity.create(2, 3, BELOW))
    checkPositionToMove(2, -1, null)
    checkPositionToMove(14, 1, null)
    checkPositionToMove(14, -1, null)
    checkPositionToMove(15, -1, null)
    checkPositionToMove(16, -1, null)
    checkPositionToMove(3, -1, Trinity.create(3, 2, ABOVE))
    checkPositionToMove(6, 1, Trinity.create(6, 9, BELOW))
    checkPositionToMove(7, 1, Trinity.create(7, 8, BELOW))
    checkPositionToMove(10, -1, Trinity.create(10, 8, BELOW))
    checkPositionToMove(8, 1, Trinity.create(8, 9, BELOW))
    checkPositionToMove(21, -1, Trinity.create(21, 20, BELOW))
    checkPositionToMove(21, 1, null)
    checkPositionToMove(20, 1, Trinity.create(20, 21, ABOVE))
    checkPositionToMove(20, -1, Trinity.create(20, 19, ABOVE))
    checkPositionToMove(19, 1, Trinity.create(19, 20, BELOW))
    checkPositionToMove(19, -1, Trinity.create(19, 17, BELOW))
    checkPositionToMove(17, -1, Trinity.create(17, 16, ABOVE))
    checkPositionToMove(17, 1, Trinity.create(17, 18, BELOW))
  }

  private fun checkPositionToMove(selectedRow: Int,
                                  direction: Int,
                                  expected: Trinity<Int, Int, RowsDnDSupport.RefinedDropSupport.Position>?) {
    tree.setSelectionRow(selectedRow)
    assertThat(configurable.getAvailableDropPosition(direction)).isEqualTo(expected)
  }

  @Test
  fun sort() {
    doExpand()
    assertFalse(model.canDrop(2, 0, ABOVE))
    assertThat(configurable.isModified).isFalse()
    tree.selectionPath = tree.getPathForRow(2)
    model.drop(2, 14, ABOVE)
    assertThat(configurable.isModified).isTrue()
    configurable.apply()
    val runManager = configurable.runManager
    assertThat(runManager.allSettings.map { it.name }).containsExactly("Renamer",
                                                                       "UI",
                                                                       "AuTest",
                                                                       "Simples",
                                                                       "OutAndErr",
                                                                       "C148C_TersePrincess",
                                                                       "Periods",
                                                                       "C148E_Porcelain",
                                                                       "ErrAndOut",
                                                                       "CodeGenerator",
                                                                       "All in titled",
                                                                       "All in titled2",
                                                                       "All in titled3",
                                                                       "All in titled4",
                                                                       "All in titled5")
    assertThat(configurable.isModified).isFalse()
    tree.selectionPath = tree.getPathForRow(4)
    model.drop(4, 8, BELOW)
    configurable.apply()
    assertThat(runManager.allSettings.joinToString("\n") { "[${it.type.displayName}] [${it.folderName ?: ""}] ${it.name}" }).isEqualTo("""
      [Application] [1] Renamer
      [Application] [1] UI
      [Application] [1] Simples
      [Application] [1] OutAndErr
      [Application] [1] C148C_TersePrincess
      [Application] [2] AuTest
      [Application] [2] Periods
      [Application] [3] C148E_Porcelain
      [Application] [3] ErrAndOut
      [Application] [] CodeGenerator
      [JUnit] [4] All in titled
      [JUnit] [4] All in titled2
      [JUnit] [5] All in titled3
      [JUnit] [5] All in titled4
      [JUnit] [] All in titled5
    """.trimIndent())

    val executorProvider = ExecutorProvider { throw UnsupportedOperationException() }
    val dataContext = DataManager.getInstance().getDataContext(configurable.tree)
    assertThat(ChooseRunConfigurationManager.createSettingsList(runManager, executorProvider, false, false, dataContext).joinToString("\n") {
      val value = it.value
      if (value is String) {
        "[$value]"
      }
      else {
        it.value!!.toString()
      }
    }).isEqualTo("""
      [1]
      [2  (mnemonic is to "AuTest")]
      [3]
      Application: CodeGenerator (level: WORKSPACE)
      [4]
      [5]
      JUnit: All in titled5 (level: TEMPORARY)
    """.trimIndent())
    assertThat(ChooseRunConfigurationManager.createSettingsList(runManager, executorProvider, false, true, dataContext).joinToString("\n") {
      val value = it.value
      if (value is String) {
        "[$value]"
      }
      else {
        it.value!!.toString()
      }
    }).isEqualTo("""
     [1]
     [2  (mnemonic is to "AuTest")]
     [3]
     [4]
     [5]
     Application: CodeGenerator (level: WORKSPACE)
     JUnit: All in titled5 (level: TEMPORARY)
    """.trimIndent())
  }

  @Test
  fun insertMultiple() {
    doExpand()
    assertThat(configurable.isModified).isFalse()
    tree.selectionPaths = arrayOf(tree.getPathForRow(3), tree.getPathForRow(6))
    model.drop(3, 9, INTO)
    assertThat(configurable.isModified).isTrue()
    configurable.apply()
    val runManager = configurable.runManager
    assertThat(runManager.allSettings.joinToString("\n") { "[${it.type.displayName}] [${it.folderName ?: ""}] ${it.name}" }).isEqualTo("""
      [Application] [1] CodeGenerator
      [Application] [1] UI
      [Application] [1] AuTest
      [Application] [1] OutAndErr
      [Application] [1] C148C_TersePrincess
      [Application] [2] Renamer
      [Application] [2] Simples
      [Application] [2] Periods
      [Application] [3] C148E_Porcelain
      [Application] [3] ErrAndOut
      [JUnit] [4] All in titled
      [JUnit] [4] All in titled2
      [JUnit] [5] All in titled3
      [JUnit] [5] All in titled4
      [JUnit] [] All in titled5
    """.trimIndent())
  }
}