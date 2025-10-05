// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.toolWindow

import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@TestApplication
class ToolWindowManagerTest {
  val project by projectFixture()

  @ParameterizedTest
  @ValueSource(booleans = [true, false])
  fun `default layout`(isNewUi: Boolean) = runBlocking {
    testDefaultLayout(isNewUi = isNewUi, project = project)
  }

  @ParameterizedTest
  @ValueSource(strings = ["left", "bottom"])
  fun `button layout`(anchor: String) {
    testButtonLayout(isNewUi = true, anchor = ToolWindowAnchor.fromText(anchor))
  }

  @ParameterizedTest
  @ValueSource(booleans = [true, false])
  fun `remove button on setting an available property to false`(isNewUi: Boolean) {
    ToolWindowManagerTestHelper.available(isNewUi = isNewUi, project = project)
  }

  @ParameterizedTest
  @ValueSource(booleans = [true, false])
  fun `show tool window if it was visible last session but became available only after initial registration`(isNewUi: Boolean) {
    ToolWindowManagerTestHelper.showOnAvailable(isNewUi = isNewUi, project = project)
  }
}