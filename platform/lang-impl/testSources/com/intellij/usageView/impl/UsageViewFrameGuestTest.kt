// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.usageView.impl

import com.intellij.ide.OpenProjectImplOptions
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.impl.IdeFrameGuests
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.createTestOpenProjectOptions
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.usageView.UsageViewContentManager
import com.intellij.usages.Usage
import com.intellij.usages.UsageTarget
import com.intellij.usages.UsageViewManager
import com.intellij.usages.UsageViewPresentation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import kotlin.io.path.createDirectories

/**
 * A frame guest keeps the Find tool window of its own, which the frame owner presents. The headless tool window manager
 * presents nothing, so these tests check only that the guest and the owner keep their tabs apart.
 */
@TestApplication
@Timeout(120)
internal class UsageViewFrameGuestTest {
  private val directoryFixture = tempPathFixture()

  @AfterEach
  fun closeProjects() {
    // the guests first, as the frame owner closes them itself
    for (project in ProjectManager.getInstance().openProjects.sortedByDescending(IdeFrameGuests::isFrameGuest)) {
      if (!project.isDisposed) PlatformTestUtil.forceCloseProjectWithoutSaving(project)
    }
  }

  @Test
  fun `the usages of a frame guest show in the Find tool window of the guest`(): Unit = timeoutRunBlocking {
    val owner = open("owner", frameOwner = null)
    val guest = open("guest", frameOwner = owner)

    withContext(Dispatchers.EDT) {
      showUsages(owner, "owner usages")
      showUsages(guest, "guest usages")

      val guestFind = ToolWindowManager.getInstance(guest).getToolWindow(ToolWindowId.FIND)
      assertThat(guestFind).describedAs("the Find tool window of the guest").isNotNull()
      assertThat(guestFind!!.contentManager.contents.map { it.displayName }).describedAs("its tabs").containsExactly("guest usages")
      assertThat(UsageViewContentManager.getInstance(guest).toolWindow).describedAs("the Find tool window of the guest manager").isSameAs(guestFind)
      val ownerFind = ToolWindowManager.getInstance(owner).getToolWindow(ToolWindowId.FIND)!!
      assertThat(ownerFind.contentManager.contents.map { it.displayName }).describedAs("the tabs of the frame owner").containsExactly("owner usages")
    }
  }

  @Test
  fun `a frame guest and its frame owner see their own tab as selected`(): Unit = timeoutRunBlocking {
    val owner = open("owner", frameOwner = null)
    val guest = open("guest", frameOwner = owner)

    withContext(Dispatchers.EDT) {
      showUsages(owner, "owner usages")
      showUsages(guest, "guest usages")

      assertThat(UsageViewContentManager.getInstance(guest).selectedContent?.displayName).isEqualTo("guest usages")
      assertThat(UsageViewContentManager.getInstance(owner).selectedContent?.displayName).isEqualTo("owner usages")
    }
  }

  @Test
  fun `the close of a frame guest keeps the tabs of the frame owner`(): Unit = timeoutRunBlocking {
    val owner = open("owner", frameOwner = null)
    val guest = open("guest", frameOwner = owner)

    withContext(Dispatchers.EDT) {
      showUsages(owner, "owner usages")
      showUsages(guest, "guest usages")
      PlatformTestUtil.forceCloseProjectWithoutSaving(guest)

      val ownerFind = ToolWindowManager.getInstance(owner).getToolWindow(ToolWindowId.FIND)!!
      assertThat(ownerFind.contentManager.contents.map { it.displayName }).describedAs("the tabs of the frame owner").containsExactly("owner usages")
    }
  }

  private fun showUsages(project: Project, tabText: String) {
    UsageViewManager.getInstance(project).showUsages(UsageTarget.EMPTY_ARRAY, Usage.EMPTY_ARRAY, UsageViewPresentation().apply {
      this.tabText = tabText
      isOpenInNewTab = true
    })
  }

  private suspend fun open(name: String, frameOwner: Project?): Project {
    val path = withContext(Dispatchers.IO) { directoryFixture.get().resolve(name).createDirectories() }
    val options = OpenProjectTask(createTestOpenProjectOptions()) {
      isNewProject = true
      if (frameOwner != null) implOptions = OpenProjectImplOptions(frameOwner = frameOwner)
    }
    return checkNotNull(ProjectManagerEx.getInstanceEx().openProjectAsync(path, options)) { "No project opened at $path" }
  }
}
