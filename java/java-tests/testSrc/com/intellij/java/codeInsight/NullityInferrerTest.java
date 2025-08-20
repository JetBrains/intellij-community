// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.inferNullity.NullityInferrer;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.util.Comparing;
import com.intellij.project.IntelliJProjectConfiguration;
import com.intellij.testFramework.LightJavaCodeInsightTestCase;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import org.jetbrains.annotations.NotNull;

public class NullityInferrerTest extends LightJavaCodeInsightTestCase {

  private static final DefaultLightProjectDescriptor DEFAULT_LIGHT_PROJECT_DESCRIPTOR = new DefaultLightProjectDescriptor() {
    @Override
    public void configureModule(@NotNull Module module, @NotNull ModifiableRootModel model, @NotNull ContentEntry contentEntry) {
      super.configureModule(module, model, contentEntry);
      PsiTestUtil.addProjectLibrary(model, "annotations", IntelliJProjectConfiguration.getProjectLibraryClassesRootPaths("jetbrains-annotations"));
    }
  };

  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return DEFAULT_LIGHT_PROJECT_DESCRIPTOR;
  }

  //-----------------------params and return values---------------------------------
  public void testParameterPassed2NotNull() {
    doTest(false);
  }

  public void testParameterCheckedForNull() {
    doTest(false);
  }

  public void testParameterDereferenced() {
    doTest(false);
  }

  public void testParameterCheckedForInstanceof() {
    try {
      doTest(false);
      fail("Should infer nothing");
    }
    catch (RuntimeException e) {
      if (!Comparing.strEqual(e.getMessage(), NullityInferrer.NOTHING_FOUND_TO_INFER)) {
        fail();
      }
    }
  }

  public void testParameterUsedInForeachIteratedValue() {
    doTest(false);
  }

  public void testForEachParameter() {
    doTest(true);
  }

  public void testConditionalReturnNotNull() {
    doTest(false);
  }

  public void testAssertParamNotNull() {
    doTest(true);
  }

  public void testTryEnumSwitch() {
    doTest(true);
  }
  
  public void testCatchParams() {
    doTest(true);
  }

  //-----------------------fields---------------------------------------------------
  public void testFieldsAssignment() {
    doTest(false);
  }

  //-----------------------methods---------------------------------------------------
  public void testMethodReturnValue() {
    doTest(false);
  }

  public void testNullFail() {
    doTest(false);
  }
  
  public void testArrayInitializer() {
    doTest(false);
  }

  private void doTest(boolean annotateLocalVariables) {
    final String nullityPath = "/codeInsight/nullityinferrer";
    configureByFile(nullityPath + "/before" + getTestName(false) + ".java");
    final NullityInferrer nullityInferrer = new NullityInferrer(annotateLocalVariables, getProject());
    nullityInferrer.collect(getFile());
    nullityInferrer.apply(getProject());
    checkResultByFile(nullityPath + "/after" + getTestName(false)+ ".java");
  }
}
