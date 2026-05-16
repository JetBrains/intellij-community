// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.toolWindow

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
class ProjectFrameToolWindowLayoutProfileServiceTest {
  private val project by projectFixture()

  @TestDisposable
  private lateinit var disposable: Disposable

  @Test
  fun descriptorLayoutProfileCustomizesDefaultLayout() {
    ExtensionTestUtil.maskExtensions(
      ProjectFrameToolWindowLayoutService.EP_NAME,
      listOf(
        ProjectFrameToolWindowLayoutBean().apply {
          id = "dedicated"
          frameType = "DEDICATED"
          applyMode = ToolWindowLayoutApplyMode.FORCE_ONCE
          migrationVersion = 4
          toolWindows = listOf(
            ProjectFrameToolWindowBean().apply {
              id = ToolWindowId.PROJECT_VIEW
              register = false
            },
            ProjectFrameToolWindowBean().apply {
              id = "Custom"
              anchor = "right"
              visible = true
              weight = 0.25f
            },
          )
        }
      ),
      disposable,
      fireEvents = false,
    )

    val profile = service<ToolWindowLayoutProfileService>().getProfile(project = project, profileId = "dedicated", isNewUi = true)

    assertThat(profile).isNotNull()
    assertThat(profile!!.applyMode).isEqualTo(ToolWindowLayoutApplyMode.FORCE_ONCE)
    assertThat(profile.migrationVersion).isEqualTo(4)
    assertThat(profile.layout.getInfo(ToolWindowId.PROJECT_VIEW)).isNull()

    val customInfo = profile.layout.getInfo("Custom")
    assertThat(customInfo).isNotNull()
    assertThat(customInfo!!.anchor).isEqualTo(ToolWindowAnchor.RIGHT)
    assertThat(customInfo.isVisible).isTrue()
    assertThat(customInfo.weight).isEqualTo(0.25f)
  }
}
