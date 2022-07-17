// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.externalSystem.testFramework;

import com.intellij.openapi.externalSystem.settings.AbstractExternalSystemSettings;
import com.intellij.openapi.externalSystem.settings.ExternalSystemSettingsListener;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * @author Denis Zhdanov
 */
public class TestExternalSystemSettings
  extends AbstractExternalSystemSettings<TestExternalSystemSettings, TestExternalProjectSettings, TestExternalSystemSettingsListener> {
  public TestExternalSystemSettings(Project project) {
    super(ExternalSystemTestUtil.SETTINGS_TOPIC, project);
  }

  @Override
  public void subscribe(@NotNull ExternalSystemSettingsListener<TestExternalProjectSettings> listener) {
    throw new UnsupportedOperationException();
  }

  @Override
  protected void copyExtraSettingsFrom(@NotNull TestExternalSystemSettings settings) {
    throw new UnsupportedOperationException();
  }

  @Override
  protected void checkSettings(@NotNull TestExternalProjectSettings old, @NotNull TestExternalProjectSettings current) {
  }
}
