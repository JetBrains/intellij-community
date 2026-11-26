// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.fixtures.MavenDependencyUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@NotNullByDefault
@TestApplication
public class OverrideImplementNullabilityTest {
  private static final String BASE_DIR = "/codeInsight/overrideImplement/nullability/";
  private static final LightProjectDescriptor DESCRIPTOR = new LightJavaCodeInsightFixtureTestCase.ProjectDescriptor(LanguageLevel.HIGHEST) {
    @Override
    public Sdk getSdk() {
      return IdeaTestUtil.getMockJdk18();
    }

    @Override
    public void configureModule(Module module, ModifiableRootModel model, ContentEntry contentEntry) {
      super.configureModule(module, model, contentEntry);
      MavenDependencyUtil.addFromMaven(model, "org.jspecify:jspecify:1.0.0");
    }
  };
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(DESCRIPTOR);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture =
    lightJavaCodeInsightFixture(projectFixture, "/java/java-tests/testData");

  @Test
  public void testUnspecifiedSuperNullMarkedSubclassNormal() {
    doTest();
  }

  @Test
  public void testUnspecifiedSuperNullMarkedSubclassArray() {
    doTest();
  }

  @Test
  public void testUnspecifiedSuperNullMarkedSubclassVarargs() {
    doTest();
  }

  @Test
  public void testUnspecifiedSuperNullMarkedSubclassGeneric() {
    doTest();
  }

  @Test
  public void testNestedTypeParameter() {
    doTest();
  }

  private void doTest() { OverrideImplementTestUtil.doTest(codeInsightFixture.get(), BASE_DIR, false, true); }
}