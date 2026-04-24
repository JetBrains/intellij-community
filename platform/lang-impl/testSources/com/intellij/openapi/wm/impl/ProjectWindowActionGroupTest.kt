// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.TestActionEvent
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import java.nio.file.Path

class ProjectWindowActionGroupTest {
  @get:Rule
  val applicationRule = ApplicationRule()

  @Test
  fun nextAndPreviousSkipExcludedProjectWindowTargets() {
    val group = ProjectWindowActionGroup()
    val projectA = addWindow(group, "Project A", "project-a")
    val dedicated = addWindow(group, "Project Dedicated", "project-dedicated", previous = projectA, excluded = true)
    val projectB = addWindow(group, "Project B", "project-b", previous = dedicated)

    assertThat(group.findSwitchTarget(projectA.projectLocation, next = true)?.projectLocation).isEqualTo(projectB.projectLocation)
    assertThat(group.findSwitchTarget(projectB.projectLocation, next = false)?.projectLocation).isEqualTo(projectA.projectLocation)
  }

  @Test
  fun dedicatedFrameRemainsValidSwitchAnchor() {
    val group = ProjectWindowActionGroup()
    val projectA = addWindow(group, "Project A", "project-a")
    val dedicated = addWindow(group, "Project Dedicated", "project-dedicated", previous = projectA, excluded = true)
    val projectB = addWindow(group, "Project B", "project-b", previous = dedicated)

    assertThat(group.findSwitchTarget(dedicated.projectLocation, next = true)?.projectLocation).isEqualTo(projectB.projectLocation)
    assertThat(group.findSwitchTarget(dedicated.projectLocation, next = false)?.projectLocation).isEqualTo(projectA.projectLocation)
  }

  @Test
  fun openProjectWindowsHidesExcludedActionsButTraversalKeepsThem() {
    val group = ProjectWindowActionGroup()
    val projectA = addWindow(group, "Project A", "project-a")
    val dedicated = addWindow(group, "Project Dedicated", "project-dedicated", previous = projectA, excluded = true)
    addWindow(group, "Project B", "project-b", previous = dedicated)

    val visibleChildren = group.getChildren(TestActionEvent.createTestEvent()).filterIsInstance<ProjectWindowAction>()
    assertThat(visibleChildren.map { it.projectLocation })
      .containsExactlyInAnyOrder(Path.of("project-a"), Path.of("project-b"))

    val internalChildren = group.getChildren(ActionManager.getInstance()).filterIsInstance<ProjectWindowAction>()
    assertThat(internalChildren.map { it.projectLocation })
      .containsExactlyInAnyOrder(Path.of("project-a"), Path.of("project-b"), Path.of("project-dedicated"))
  }
}

private fun addWindow(
  group: ProjectWindowActionGroup,
  name: String,
  location: String,
  previous: ProjectWindowAction? = null,
  excluded: Boolean = false,
): ProjectWindowAction {
  val action = ProjectWindowAction(
    projectName = name,
    projectLocation = Path.of(location),
    previous = previous,
    excludedFromProjectWindowSwitchOrder = excluded,
  )
  group.add(action)
  return action
}
