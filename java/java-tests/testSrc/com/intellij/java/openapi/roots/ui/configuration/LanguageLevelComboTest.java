// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.openapi.roots.ui.configuration;

import com.intellij.JavaTestUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.roots.ui.configuration.LanguageLevelCombo;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.TestDisposable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;

import static com.intellij.testFramework.assertions.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class LanguageLevelComboTest {
  private Project myProject;
  private LanguageLevelCombo myCombo;

  @BeforeEach
  void setUp(@TestDisposable Disposable testRootDisposable) {
    myProject = PlatformTestUtil.loadAndOpenProject(Paths.get(JavaTestUtil.getJavaTestDataPath(), "ide/project"), testRootDisposable);
    myCombo = new LanguageLevelCombo("default") {
      @Override
      protected LanguageLevel getDefaultLevel() {
        return null;
      }
    };
    myCombo.reset(myProject);
  }

  @AfterEach
  void tearDown() {
    if (myProject != null) {
      EdtTestUtil.runInEdtAndWait(() -> ProjectManagerEx.getInstanceEx().forceCloseProject(myProject));
    }
  }

  @Test
  public void testDefaultLanguageLevelForBrokenSdk() {
    assertTrue(myCombo.isDefault());
    assertThat(myCombo.getSelectedLevel()).isNull();
  }

  @Test
  public void testPreserveLanguageLevel() {
    LanguageLevelProjectExtension extension = LanguageLevelProjectExtension.getInstance(myProject);
    WriteAction.runAndWait(() -> {
      extension.setDefault(false);
      extension.setLanguageLevel(LanguageLevel.JDK_1_5);
    });
    myCombo.reset(myProject);

    myCombo.sdkUpdated(IdeaTestUtil.getMockJdk17(), false);
    assertEquals(LanguageLevel.JDK_1_5, myCombo.getSelectedLevel());
  }
}
