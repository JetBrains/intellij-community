/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.java.codeInsight;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.inferNullity.NullityInferrer;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.util.Comparing;
import com.intellij.project.IntelliJProjectConfiguration;
import com.intellij.testFramework.LightCodeInsightTestCase;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import org.jetbrains.annotations.NotNull;

public class NullityInferrerTest extends LightCodeInsightTestCase {

  private static final DefaultLightProjectDescriptor DEFAULT_LIGHT_PROJECT_DESCRIPTOR = new DefaultLightProjectDescriptor() {
    @Override
    public void configureModule(@NotNull Module module, @NotNull ModifiableRootModel model, @NotNull ContentEntry contentEntry) {
      super.configureModule(module, model, contentEntry);
      PsiTestUtil.addProjectLibrary(model, "annotations", IntelliJProjectConfiguration.getProjectLibraryClassesRootPaths("jetbrains-annotations-java5"));
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
  public void testParameterPassed2NotNull() throws Exception {
    doTest(false);
  }

  public void testParameterCheckedForNull() throws Exception {
    doTest(false);
  }

  public void testParameterDereferenced() throws Exception {
    doTest(false);
  }

  public void testParameterCheckedForInstanceof() throws Exception {
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

  public void testParameterUsedInForeachIteratedValue() throws Exception {
    doTest(false);
  }

  public void testForEachParameter() throws Exception {
    doTest(true);
  }

  public void testConditionalReturnNotNull() throws Exception {
    doTest(false);
  }

  public void testAssertParamNotNull() throws Exception {
    doTest(true);
  }

  public void testTryEnumSwitch() throws Exception {
    doTest(true);
  }

  //-----------------------fields---------------------------------------------------
  public void testFieldsAssignment() throws Exception {
    doTest(false);
  }

  //-----------------------methods---------------------------------------------------
  public void testMethodReturnValue() throws Exception {
    doTest(false);
  }


  private void doTest(boolean annotateLocalVariables) throws Exception  {
    final String nullityPath = "/codeInsight/nullityinferrer";
    configureByFile(nullityPath + "/before" + getTestName(false) + ".java");
    final NullityInferrer nullityInferrer = new NullityInferrer(annotateLocalVariables, getProject());
    nullityInferrer.collect(getFile());
    nullityInferrer.apply(getProject());
    checkResultByFile(nullityPath + "/after" + getTestName(false)+ ".java");
  }
}
