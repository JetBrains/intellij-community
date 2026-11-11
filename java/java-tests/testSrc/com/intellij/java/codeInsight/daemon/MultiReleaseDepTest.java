// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.fixtures.MavenDependencyUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestApplication
public final class MultiReleaseDepTest {
  private static final @NotNull LightProjectDescriptor MY_DESCRIPTOR = new LightProjectDescriptor() {
    @Override
    public @NotNull Sdk getSdk() {
      return IdeaTestUtil.getMockJdk21();
    }

    @Override
    protected void configureModule(@NotNull Module module, @NotNull ModifiableRootModel model, @NotNull ContentEntry contentEntry) {
      super.configureModule(module, model, contentEntry);
      MavenDependencyUtil.addFromMaven(model, "org.apache.logging.log4j:log4j-api:2.20.0"); // Multi-release-JAR
    }
  };
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(MY_DESCRIPTOR);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testOnDemandResolveIntoMultiReleaseJar() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    // IDEA-350754
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_21, () -> {
      myFixture.configureByText("Test.java", """
      package com.example;

      import org.apache.logging.log4j.*;

      public class Test {
          private static final Logger log = LogManager.getLogger();
      }""");
      myFixture.checkHighlighting();
    });
  }
}
