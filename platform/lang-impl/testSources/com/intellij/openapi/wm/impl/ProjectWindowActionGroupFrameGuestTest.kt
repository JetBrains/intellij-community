// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.ide.OpenProjectImplOptions
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.project.ProjectStoreOwner
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.createTestOpenProjectOptions
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.nio.file.Path
import kotlin.io.path.createDirectories

/**
 * The Window menu entries of a frame with guests: a frame guest has no entry, the entry of the owner of its frame stands
 * for it, so Next and Previous Project Window switch frames while a guest is active, see [ProjectWindowActionGroup].
 */
@TestApplication
@Timeout(120)
internal class ProjectWindowActionGroupFrameGuestTest {
  private val directoryFixture = tempPathFixture()

  @AfterEach
  fun closeProjects() {
    for (project in ProjectManager.getInstance().openProjects) {
      PlatformTestUtil.forceCloseProjectWithoutSaving(project)
    }
  }

  @Test
  fun `the entry of the frame owner stands for a frame guest`(): Unit = timeoutRunBlocking {
    val first = openOwner("first")
    val second = openOwner("second")
    val guest = openGuest("guest", first)
    val alone = openOwner("alone")
    val group = ProjectWindowActionGroup()
    group.addProject(first)
    group.addProject(second)

    assertThat(group.canActivateNext(guest)).isTrue()
    assertThat(group.canActivatePrevious(guest)).isTrue()
    assertThat(group.findSwitchTarget(locationOf(first), next = true)?.projectLocation).isEqualTo(locationOf(second))
    // a project without an entry has no switch target, as before
    assertThat(group.canActivateNext(alone)).isFalse()
  }

  @Test
  fun `Next and Previous Project Window are enabled for a frame guest`(): Unit = timeoutRunBlocking {
    val first = openOwner("first")
    val second = openOwner("second")
    val guest = openGuest("guest", first)
    val actionManager = ActionManager.getInstance()
    val group = actionManager.getAction("OpenProjectWindows") as ProjectWindowActionGroup
    group.addProject(first)
    group.addProject(second)
    try {
      for (actionId in listOf("NextProjectWindow", "PreviousProjectWindow")) {
        val action = actionManager.getAction(actionId)
        val event = eventOf(action, guest)
        action.update(event)
        assertThat(event.presentation.isEnabled).describedAs("$actionId for the guest").isTrue()
      }
      val entries = withContext(Dispatchers.EDT) { group.getChildren(eventOf(group, guest)) }.filterIsInstance<ProjectWindowAction>()
      val selected = entries.filter { it.isSelected(eventOf(it, guest)) }.map { it.projectLocation }
      assertThat(selected).describedAs("the entry of the owner is checked while its guest is active").containsExactly(locationOf(first))
    }
    finally {
      group.removeProject(first)
      group.removeProject(second)
    }
  }

  private fun eventOf(action: AnAction, project: Project): AnActionEvent {
    val context = DataContext { dataId -> if (CommonDataKeys.PROJECT.`is`(dataId)) project else null }
    return AnActionEvent.createEvent(action, context, null, ActionPlaces.MAIN_MENU, ActionUiKind.NONE, null)
  }

  private fun locationOf(project: Project): Path = (project as ProjectStoreOwner).componentStore.storeDescriptor.presentableUrl

  private suspend fun openOwner(name: String): Project {
    return open(directory(name), OpenProjectTask(createTestOpenProjectOptions()) { isNewProject = true })
  }

  private suspend fun openGuest(name: String, owner: Project): Project {
    return open(directory(name), OpenProjectTask(createTestOpenProjectOptions()) {
      isNewProject = true
      implOptions = OpenProjectImplOptions(frameOwner = owner)
    })
  }

  private suspend fun open(path: Path, options: OpenProjectTask): Project {
    return checkNotNull(ProjectManagerEx.getInstanceEx().openProjectAsync(path, options)) { "No project opened at $path" }
  }

  private suspend fun directory(name: String): Path = withContext(Dispatchers.IO) {
    directoryFixture.get().resolve(name).createDirectories()
  }
}
