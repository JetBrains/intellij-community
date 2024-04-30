// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.MavenDependencyUtil;
import org.jetbrains.annotations.NotNull;

public final class MultiReleaseDepTest extends LightJavaCodeInsightFixtureTestCase {
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

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return MY_DESCRIPTOR;
  }

  public void testOnDemandResolveIntoMultiReleaseJar() {
    // IDEA-350754
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_21, () -> {
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
