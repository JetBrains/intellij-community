// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight;

import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInspection.inferNullity.NullityInferrer;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.project.IntelliJProjectConfiguration;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.usageView.UsageInfo;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class NullityInferrerTest {
  public static final String TEST_DATA_PATH = "/codeInsight/nullityinferrer";

  private static final DefaultLightProjectDescriptor DEFAULT_LIGHT_PROJECT_DESCRIPTOR = new DefaultLightProjectDescriptor() {
    @Override
    public void configureModule(@NotNull Module module, @NotNull ModifiableRootModel model, @NotNull ContentEntry contentEntry) {
      super.configureModule(module, model, contentEntry);
      PsiTestUtil.addProjectLibrary(model, "annotations", IntelliJProjectConfiguration.getModuleLibrary("intellij.libraries.jetbrains.annotations", "jetbrains-annotations").getClassesPaths());
    }
  };
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(DEFAULT_LIGHT_PROJECT_DESCRIPTOR);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture =
    lightJavaCodeInsightFixture(projectFixture, "/java/java-tests/testData");

  //-----------------------params and return values---------------------------------
  @Test
  public void testParameterPassed2NotNull() {
    doTest(false);
  }

  @Test
  public void testParameterCheckedForNull() {
    doTest(false);
  }

  @Test
  public void testParameterDereferenced() {
    doTest(false);
  }

  @Test
  public void testParameterCheckedForInstanceof() {
    var inferredNullabilities = configureByFileAndGetInferredNullabilities(false);
    assertTrue(inferredNullabilities.isEmpty(), "Should infer nothing");
  }

  @Test
  public void testParameterUsedInForeachIteratedValue() {
    doTest(false);
  }

  @Test
  public void testForEachParameter() {
    doTest(true);
  }

  @Test
  public void testConditionalReturnNotNull() {
    doTest(false);
  }

  @Test
  public void testAssertParamNotNull() {
    doTest(true);
  }

  @Test
  public void testTryEnumSwitch() {
    doTest(true);
  }
  
  @Test
  public void testCatchParams() {
    doTest(true);
  }

  //-----------------------fields---------------------------------------------------
  @Test
  public void testFieldsAssignment() {
    doTest(false);
  }

  //-----------------------methods---------------------------------------------------
  @Test
  public void testMethodReturnValue() {
    doTest(false);
  }

  @Test
  public void testNullFail() {
    doTest(false);
  }
  
  @Test
  public void testArrayInitializer() {
    doTest(false);
  }

  private void doTest(boolean annotateLocalVariables) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    var inferredNullabilities = configureByFileAndGetInferredNullabilities(annotateLocalVariables);
    myFixture.runInCommand(() -> {
      inferredNullabilities.forEach(u -> NullityInferrer.apply(NullableNotNullManager.getInstance(myFixture.getProject()), u));
      myFixture.checkResultByFile(TEST_DATA_PATH + "/after" + myFixture.getTestName(false) + ".java");
    });
  }

  private @NotNull List<UsageInfo> configureByFileAndGetInferredNullabilities(boolean annotateLocalVariables) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(TEST_DATA_PATH + "/before" + myFixture.getTestName(false) + ".java");
    return ReadAction.computeBlocking(() -> {
      NullityInferrer nullityInferrer = new NullityInferrer(annotateLocalVariables, myFixture.getProject());
      nullityInferrer.collect(myFixture.getFile());
      List<UsageInfo> usages = new ArrayList<>();
      nullityInferrer.collect(usages);
      return usages;
    });
  }
}
