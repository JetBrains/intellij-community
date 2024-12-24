// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.externalSystem.testFramework

import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.util.messages.Topic

object ExternalSystemTestUtil {
  @JvmField
  val TEST_EXTERNAL_SYSTEM_ID: ProjectSystemId = ProjectSystemId("TEST_EXTERNAL_SYSTEM_ID")

  @JvmField
  val SETTINGS_TOPIC: Topic<TestExternalSystemSettingsListener?> = Topic.create<TestExternalSystemSettingsListener?>(
    "TEST_EXTERNAL_SYSTEM_SETTINGS", TestExternalSystemSettingsListener::class.java
  )
}
