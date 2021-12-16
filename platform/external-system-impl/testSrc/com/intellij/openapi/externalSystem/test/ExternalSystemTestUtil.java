// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.test;

import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.util.messages.Topic;

/**
 * @author Denis Zhdanov
 */
public final class ExternalSystemTestUtil {

  public static final ProjectSystemId TEST_EXTERNAL_SYSTEM_ID = new ProjectSystemId("TEST_EXTERNAL_SYSTEM_ID");

  public static final Topic<TestExternalSystemSettingsListener> SETTINGS_TOPIC = Topic.create(
    "TEST_EXTERNAL_SYSTEM_SETTINGS", TestExternalSystemSettingsListener.class
  );

  private ExternalSystemTestUtil() {
  }
}
