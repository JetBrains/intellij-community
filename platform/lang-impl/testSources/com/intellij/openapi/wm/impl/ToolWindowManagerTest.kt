// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.testFramework.ProjectExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class ToolWindowManagerTest {
  companion object {
    @JvmField
    @RegisterExtension
    val projectRule = ProjectExtension(runPostStartUpActivities = false, preloadServices = false)
  }

  @Test
  fun testInit() {
    val manager = ToolWindowManagerImpl(projectRule.project)
    manager.setLayoutOnInit(DesktopLayout(IntellijPlatformDefaultToolWindowLayoutProvider().createDefaultToolWindowLayout()))
  }
}