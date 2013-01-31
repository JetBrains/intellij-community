/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.codeInsight;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.inferNullity.NullityInferrer;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * User: anna
 * Date: Sep 2, 2010
 */
public class NullityInferrerTest extends CodeInsightTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
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
    final VirtualFile aLib = LocalFileSystem.getInstance().findFileByPath(getTestDataPath() + nullityPath + "/lib/annotations.jar");
    if (aLib != null) {
      final VirtualFile file = JarFileSystem.getInstance().getJarRootForLocalFile(aLib);
      if (file != null) {
        ModuleRootModificationUtil.addModuleLibrary(myModule, file.getUrl());
      }
    }

    configureByFile(nullityPath + "/before" + getTestName(false) + ".java");
    final NullityInferrer nullityInferrer = new NullityInferrer(annotateLocalVariables, getProject());
    nullityInferrer.collect(getFile());
    nullityInferrer.apply(getProject());
    checkResultByFile(nullityPath + "/after" + getTestName(false)+ ".java");
  }
}
