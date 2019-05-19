// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon.impl;

import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.ui.EditorNotificationPanel;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Pavel.Dolgov
 */
public class SdkSetupNotificationTest extends SdkSetupNotificationTestBase {
  public void testProjectSdk() {
    final EditorNotificationPanel panel = configureBySdkAndText(IdeaTestUtil.getMockJdk18(), false, "Sample.java", "class Sample {}");
    assertThat(panel).isNull();
  }

  public void testNoProjectSdk() {
    final EditorNotificationPanel panel = configureBySdkAndText(null, false, "Sample.java", "class Sample {}");
    assertSdkSetupPanelShown(panel, "Setup SDK");
  }

  public void testModuleSdk() {
    final EditorNotificationPanel panel = configureBySdkAndText(IdeaTestUtil.getMockJdk18(), true, "Sample.java", "class Sample {}");
    assertThat(panel).isNull();
  }

  public void testNoModuleSdk() {
    final EditorNotificationPanel panel = configureBySdkAndText(null, true, "Sample.java", "class Sample {}");
    assertSdkSetupPanelShown(panel, "Setup SDK");
  }
}
