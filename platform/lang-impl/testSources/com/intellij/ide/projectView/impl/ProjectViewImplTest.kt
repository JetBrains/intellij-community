// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectView.impl

import com.intellij.icons.AllIcons
import com.intellij.ide.SelectInContext
import com.intellij.ide.SelectInTarget
import com.intellij.ide.dnd.aware.DnDAwareTree
import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.project.Project
import com.intellij.projectView.TestProjectTreeStructure
import com.intellij.testFramework.ProjectViewTestUtil
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.tree.DefaultTreeModel

@TestApplication
class ProjectViewImplTest {
  private val project by projectFixture()

  @Test
  fun reAddedSinglePaneWithSameIdCreatesNewComponentAndTree() {
    runInEdtAndWait {
      ProjectViewTestUtil.setupImpl(project, false)
      val projectView = ProjectView.getInstance(project) as ProjectViewImpl

      val firstPane = RecordingProjectViewPane(project, PANE_ID, "First pane")
      projectView.addProjectPane(firstPane)

      assertThat(firstPane.createComponentCalls).isEqualTo(1)
      assertThat(firstPane.createTreeCalls).isEqualTo(1)

      projectView.removeProjectPane(firstPane)

      val secondPane = RecordingProjectViewPane(project, PANE_ID, "Second pane")
      projectView.addProjectPane(secondPane)

      assertThat(secondPane.createComponentCalls).isEqualTo(1)
      assertThat(secondPane.createTreeCalls).isEqualTo(1)
    }
  }

  private class RecordingProjectViewPane(
    project: Project,
    private val paneId: String,
    private val paneTitle: String,
  ) : AbstractProjectViewPaneWithAsyncSupport(project) {
    var createComponentCalls = 0
      private set
    var createTreeCalls = 0
      private set

    override fun createComponent(): JComponent {
      createComponentCalls++
      return super.createComponent()
    }

    override fun createStructure() = TestProjectTreeStructure(myProject, this)

    override fun createTree(treeModel: DefaultTreeModel): DnDAwareTree {
      createTreeCalls++
      return ProjectViewTree(treeModel)
    }

    override fun getTitle(): String = paneTitle

    override fun getIcon(): Icon = AllIcons.General.ProjectTab

    override fun getId(): String = paneId

    override fun getWeight(): Int = 0

    override fun createSelectInTarget(): SelectInTarget {
      return object : SelectInTarget {
        override fun toString(): String = paneTitle

        override fun canSelect(context: SelectInContext): Boolean = false

        override fun selectIn(context: SelectInContext, requestFocus: Boolean) {
        }

        override fun getMinorViewId(): String = paneId
      }
    }
  }

  companion object {
    private const val PANE_ID = "testPane"
  }
}
