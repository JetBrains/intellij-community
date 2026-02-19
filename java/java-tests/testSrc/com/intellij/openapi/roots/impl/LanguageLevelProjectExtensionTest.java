// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.JavaProjectTestCase;

public class LanguageLevelProjectExtensionTest extends JavaProjectTestCase {

  public void testLanguageLevelChangesWhenProjectSdkChanges() {
    LanguageLevelProjectExtension extension = LanguageLevelProjectExtension.getInstance(myProject);

    Sdk jdk17 = IdeaTestUtil.getMockJdk17();
    WriteAction.run(() -> ProjectJdkTable.getInstance().addJdk(jdk17, getTestRootDisposable()));

    // Initialize: set language level to JDK 17 with default=true
    // This enables automatic language level updates when SDK changes
    WriteAction.run(() -> {
      extension.setLanguageLevel(LanguageLevel.JDK_1_7);
      extension.setDefault(true);
    });

    WriteAction.run(() -> ProjectRootManager.getInstance(myProject).setProjectSdk(jdk17));

    assertTrue("Language level should be default", extension.isDefault());
    assertEquals("Language level should match JDK 1.7",
                 LanguageLevel.JDK_1_7, extension.getLanguageLevel());

    Sdk jdk18 = IdeaTestUtil.getMockJdk18();
    WriteAction.run(() -> ProjectJdkTable.getInstance().addJdk(jdk18, getTestRootDisposable()));

    // Change project SDK to JDK 1.8 - this should trigger language level update
    WriteAction.run(() -> ProjectRootManager.getInstance(myProject).setProjectSdk(jdk18));

    // Verify language level changed to JDK_1_8
    assertTrue("Language level should still be default", extension.isDefault());
    assertEquals("Language level should match JDK 1.8",
                 LanguageLevel.JDK_1_8, extension.getLanguageLevel());
  }
}
