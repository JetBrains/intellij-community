// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.ui.configuration;

import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightPlatformTestCase;

public class LanguageLevelComboTest extends LightPlatformTestCase {

  private Project myProject;
  private LanguageLevelCombo myCombo;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myProject = ProjectManager.getInstance().loadAndOpenProject(PathManagerEx.getTestDataPath("/ide/project"));
    myCombo = new LanguageLevelCombo("default") {
      @Override
      protected LanguageLevel getDefaultLevel() {
        return null;
      }
    };
    myCombo.reset(myProject);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      ProjectManagerEx.getInstanceEx().closeAndDispose(myProject);
    }
    finally {
      super.tearDown();
    }
  }

  public void testDefaultLanguageLevelForBrokenSdk() {
    assertEquals("default", myCombo.getSelectedItem());
    assertNull(myCombo.getSelectedLevel());
  }

  public void testPreserveLanguageLevel() {
    LanguageLevelProjectExtension extension = LanguageLevelProjectExtension.getInstance(myProject);
    extension.setDefault(false);
    extension.setLanguageLevel(LanguageLevel.JDK_1_5);
    myCombo.reset(myProject);

    myCombo.sdkUpdated(IdeaTestUtil.getMockJdk17(), false);
    assertEquals(LanguageLevel.JDK_1_5, myCombo.getSelectedLevel());
  }
}
