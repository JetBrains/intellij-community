// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.usageView.impl

import com.intellij.ide.OpenProjectImplOptions
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.util.Disposer
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

/** The usages of a frame guest go to the Find tool window of the frame owner, the only one its frame shows. */
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
  fun `the usages of a frame guest show in the Find tool window of the frame owner`(): Unit = timeoutRunBlocking {
    val owner = open("owner", frameOwner = null)
    val guest = open("guest", frameOwner = owner)

    withContext(Dispatchers.EDT) {
      showUsages(guest, "guest usages")

      val ownerFind = ToolWindowManager.getInstance(owner).getToolWindow(ToolWindowId.FIND)
      assertThat(ownerFind).describedAs("the Find tool window of the frame owner").isNotNull()
      assertThat(ownerFind!!.contentManager.contents.map { it.displayName }).describedAs("its tabs").containsExactly("guest usages")
      assertThat(UsageViewContentManager.getInstance(guest).toolWindow).describedAs("the Find tool window of the guest").isSameAs(ownerFind)
    }
  }

  @Test
  fun `a frame guest and its frame owner see only their own tab as selected`(): Unit = timeoutRunBlocking {
    val owner = open("owner", frameOwner = null)
    val guest = open("guest", frameOwner = owner)

    withContext(Dispatchers.EDT) {
      showUsages(owner, "owner usages")
      showUsages(guest, "guest usages")

      assertThat(UsageViewContentManager.getInstance(guest).selectedContent?.displayName).isEqualTo("guest usages")
      assertThat(UsageViewContentManager.getInstance(owner).selectedContent).describedAs("the tab of the guest").isNull()
      assertThat(UsageViewManager.getInstance(owner).selectedUsageView).describedAs("the usage view of the guest").isNull()
    }
  }

  @Test
  fun `a frame guest takes its tabs away when it closes`(): Unit = timeoutRunBlocking {
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

  @Test
  fun `a frame guest takes its last tab away before the write action of its close`(): Unit = timeoutRunBlocking {
    val owner = open("owner", frameOwner = null)
    val guest = open("guest", frameOwner = owner)

    withContext(Dispatchers.EDT) {
      showUsages(guest, "guest usages")
      val content = checkNotNull(UsageViewContentManager.getInstance(guest).selectedContent) { "the tab of the guest" }
      var removedInWriteAction: Boolean? = null
      Disposer.register(content) { removedInWriteAction = ApplicationManager.getApplication().isWriteAccessAllowed }
      PlatformTestUtil.forceCloseProjectWithoutSaving(guest)

      assertThat(removedInWriteAction).describedAs("the tab removed in a write action").isFalse()
      val ownerFind = ToolWindowManager.getInstance(owner).getToolWindow(ToolWindowId.FIND)!!
      assertThat(ownerFind.contentManager.contentCount).describedAs("the tabs of the frame owner").isZero()
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
