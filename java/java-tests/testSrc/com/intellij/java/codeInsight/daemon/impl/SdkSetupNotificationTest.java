// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.impl.JavaProjectSdkSetupValidator;
import com.intellij.idea.TestFor;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.projectRoots.*;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.ui.EditorNotificationPanel;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Pavel.Dolgov
 */
@TestFor(classes = JavaProjectSdkSetupValidator.class)
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

  @TestFor(issues = "IDEA-237884")
  public void testShouldNotRantOnCustomSDKType() {
    final Sdk broken = ProjectJdkTable.getInstance().createSdk("broken-sdk-123", SimpleJavaSdkType.getInstance());
    WriteAction.run(() -> {
      SdkModificator m = broken.getSdkModificator();
      m.setHomePath("invalid home path");
      m.setVersionString("11");
      m.commitChanges();

      ProjectJdkTable.getInstance().addJdk(broken, getTestRootDisposable());

      ModifiableRootModel mod = ModuleRootManager.getInstance(getModule()).getModifiableModel();
      mod.setSdk(broken);
      mod.commit();

    });
    final EditorNotificationPanel panel = runOnText(myFixture, "Sample.java", "class Sample {}");
    assertThat(panel).isNull();
  }

  private class SdkTestCases {
    final Sdk broken = ProjectJdkTable.getInstance().createSdk("broken-sdk-123", JavaSdk.getInstance());
    final Sdk valid = IdeaTestUtil.getMockJdk18();

    private SdkTestCases() {
      WriteAction.run(() -> {
        SdkModificator m = broken.getSdkModificator();
        m.setHomePath("invalid home path");
        m.setVersionString("11");
        m.commitChanges();

        ProjectJdkTable.getInstance().addJdk(broken, getTestRootDisposable());
        ProjectJdkTable.getInstance().addJdk(valid, getTestRootDisposable());
      });
    }
  }

  public void testBrokenModuleSdk() {
    new SdkTestCases() {
      {
        WriteAction.run(() -> {
          ModifiableRootModel m = ModuleRootManager.getInstance(getModule()).getModifiableModel();
          m.setSdk(broken);
          m.commit();

          ProjectRootManager.getInstance(getProject()).setProjectSdk(valid);
        });

        final EditorNotificationPanel panel = runOnText(myFixture, "Sample.java", "class Sample {}");
        assertSdkSetupPanelShown(panel, "Setup SDK");
        assertThat(panel.getText()).contains(broken.getName());
        assertThat(panel.getText()).containsIgnoringCase("Module JDK");
      }
    };
  }

  public void testValidProjectSdk() {
    new SdkTestCases() {
      {
        WriteAction.run(() -> {
          ModifiableRootModel m = ModuleRootManager.getInstance(getModule()).getModifiableModel();
          m.inheritSdk();
          m.commit();

          ProjectRootManager.getInstance(getProject()).setProjectSdk(valid);
        });

        final EditorNotificationPanel panel = runOnText(myFixture, "Sample.java", "class Sample {}");
        assertNull(panel);
      }
    };
  }

  public void testBrokenProjectValidModuleSdk() {
    new SdkTestCases() {
      {
        WriteAction.run(() -> {
          ModifiableRootModel m = ModuleRootManager.getInstance(getModule()).getModifiableModel();
          m.setSdk(valid);
          m.commit();

          ProjectRootManager.getInstance(getProject()).setProjectSdk(broken);
        });

        final EditorNotificationPanel panel = runOnText(myFixture, "Sample.java", "class Sample {}");
        assertNull(panel);
      }
    };
  }
}
