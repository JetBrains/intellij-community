// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.externalSystem.testFramework;

import com.intellij.openapi.externalSystem.settings.AbstractExternalSystemLocalSettings;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class TestExternalSystemLocalSettings extends AbstractExternalSystemLocalSettings<AbstractExternalSystemLocalSettings.State> {
  public TestExternalSystemLocalSettings(@NotNull Project project) {
    super(ExternalSystemTestUtil.TEST_EXTERNAL_SYSTEM_ID, project, new State());
  }
}
