package com.intellij.openapi.externalSystem.service.settings

import com.intellij.platform.externalSystem.testFramework.ExternalSystemProjectTestCase

class ExternalSystemConfigurableTest : ExternalSystemProjectTestCase() {
  fun `test es settings controls reset`() {
    applyProjectModel(project {})
    val configurable = TestExternalSystemConfigurable(project)
    configurable.createComponent()
    configurable.reset()
    val control = configurable.projectSettingsControls.first() as TestExternalProjectSettingsControl
    assertNotNull(control.project)
  }
}