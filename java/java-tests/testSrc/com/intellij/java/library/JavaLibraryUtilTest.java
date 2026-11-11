// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.library;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.fixtures.MavenDependencyUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@TestApplication
public class JavaLibraryUtilTest {
  private static final LightProjectDescriptor JUNIT_4_PROJECT = new DefaultLightProjectDescriptor() {
    @Override
    public void configureModule(@NotNull Module module, @NotNull ModifiableRootModel model, @NotNull ContentEntry contentEntry) {
      super.configureModule(module, model, contentEntry);
      MavenDependencyUtil.addFromMaven(model, "junit:junit:4.13.2");
    }
  };
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(JUNIT_4_PROJECT);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testJpsLibraryVersionFromJarManifest() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    assertEquals("4.13.2", ReadAction.computeBlocking(() -> JavaLibraryUtil.getLibraryVersion(myFixture.getModule(), "junit:junit")));
  }

  @Test
  public void testJpsLibraryVersionOfMissingLibrary() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    assertNull(ReadAction.computeBlocking(() -> JavaLibraryUtil.getLibraryVersion(myFixture.getModule(), "org.junit.jupiter:junit-jupiter-api")));
  }
}
