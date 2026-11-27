// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.openapi.roots.impl;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.HeavyJavaCodeInsightFixture;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.codeInsight.HeavyJavaCodeInsightFixture.heavyJavaCodeInsightFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class LanguageLevelProjectExtensionTest {
  private final TestFixture<HeavyJavaCodeInsightFixture> heavyFixture = heavyJavaCodeInsightFixture(JavaTestUtil.getJavaTestDataPath());

  @Test
  public void testLanguageLevelChangesWhenProjectSdkChanges() {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    LanguageLevelProjectExtension extension = LanguageLevelProjectExtension.getInstance(fixture.getProject());

    Sdk jdk17 = IdeaTestUtil.getMockJdk17();
    WriteAction.runAndWait(() -> ProjectJdkTable.getInstance().addJdk(jdk17, fixture.getTestRootDisposable()));

    // Initialize: set language level to JDK 17 with default=true
    // This enables automatic language level updates when SDK changes
    WriteAction.runAndWait(() -> {
      extension.setLanguageLevel(LanguageLevel.JDK_1_7);
      extension.setDefault(true);
    });

    WriteAction.runAndWait(() -> ProjectRootManager.getInstance(fixture.getProject()).setProjectSdk(jdk17));

    assertTrue(extension.isDefault(), "Language level should be default");
    assertEquals(LanguageLevel.JDK_1_7,
                 extension.getLanguageLevel(), "Language level should match JDK 1.7");

    Sdk jdk18 = IdeaTestUtil.getMockJdk18();
    WriteAction.runAndWait(() -> ProjectJdkTable.getInstance().addJdk(jdk18, fixture.getTestRootDisposable()));

    // Change project SDK to JDK 1.8 - this should trigger language level update
    WriteAction.runAndWait(() -> ProjectRootManager.getInstance(fixture.getProject()).setProjectSdk(jdk18));

    // Verify language level changed to JDK_1_8
    assertTrue(extension.isDefault(), "Language level should still be default");
    assertEquals(LanguageLevel.JDK_1_8,
                 extension.getLanguageLevel(), "Language level should match JDK 1.8");
  }
}
