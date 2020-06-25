// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.java15api.Java15APIUsageInspection;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

public class Java14With15APIUsageInspectionTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection/featurepreview/preview14on15API/";
  }

  private static final DefaultLightProjectDescriptor PROJECT_DESCRIPTOR = new DefaultLightProjectDescriptor() {
    @Override
    public Sdk getSdk() {
      return JAVA_15.getSdk();
    }

    @Override
    public void configureModule(@NotNull Module module,
                                @NotNull ModifiableRootModel model,
                                @NotNull ContentEntry contentEntry) {
      super.configureModule(module, model, contentEntry);
      String dataDir = JavaTestUtil.getJavaTestDataPath() + "/inspection/featurepreview/preview14on15API/data";
      PsiTestUtil.newLibrary("JDK15Mock").classesRoot(dataDir + "/classes").addTo(model);
    }
  };

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return PROJECT_DESCRIPTOR;
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new Java15APIUsageInspection());
  }

  public void testLanguageLevel14() {
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_14, () ->
      myFixture.testHighlighting(getTestName(false) + ".java"));
  }
  public void testLanguageLevel14Preview() {
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_14_PREVIEW, () ->
      myFixture.testHighlighting(getTestName(false) + ".java"));
  }
}
