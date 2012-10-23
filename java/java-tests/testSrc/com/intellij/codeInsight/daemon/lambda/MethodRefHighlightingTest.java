/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.lambda;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerTestCase;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.unusedSymbol.UnusedSymbolLocalInspection;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.JavaVersionService;
import com.intellij.openapi.projectRoots.JavaVersionServiceImpl;
import org.jetbrains.annotations.NonNls;

public class MethodRefHighlightingTest extends LightDaemonAnalyzerTestCase {
  @NonNls static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/lambda/methodRef";

  @Override
  protected LocalInspectionTool[] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{
      new UnusedSymbolLocalInspection(),
    };
  }
  
  public void testValidContext() throws Exception {
    doTest();
  }

  public void testAssignability() throws Exception {
    doTest();
  }
  
  public void testAmbiguity() throws Exception {
    doTest();
  }

  public void testMethodReferenceReceiver() throws Exception {
    doTest();
  }
  
  public void testMethodRefMisc() throws Exception {
    doTest();
  }

  public void testMethodTypeParamsInference() throws Exception {
    doTest();
  }

  public void testMethodRefMisc1() throws Exception {
    doTest();
  }

  public void testQualifierTypeArgs() throws Exception {
    doTest();
  }

  public void testStaticProblems() throws Exception {
    doTest();
  }

  public void testConstructorRefs() throws Exception {
    doTest();
  }

  public void testConstructorRefsInnerClasses() throws Exception {
    doTest();
  }
  
  public void testVarargs() throws Exception {
    doTest();
  }

  public void testVarargs1() throws Exception {
    doTest();
  }

  public void testConstructorRefInnerFromSuper() throws Exception {
    doTest();
  }

  public void testReferenceParameters() throws Exception {
    doTest();
  }

  public void testRawQualifier() throws Exception {
    doTest();
  }

  public void testCyclicInference() throws Exception {
    doTest();
  }

  public void testAccessModifiers() throws Exception {
    doTest();
  }
  
  public void testDefaultConstructor() throws Exception {
    doTest();
  }

  public void testWildcards() throws Exception {
    doTest();
  }

  public void testVarargsInReceiverPosition() throws Exception {
    doTest();
  }

  public void testInferenceFromMethodReference() throws Exception {
    doTest();
  }

  public void testAssignability1() throws Exception {
    doTest();
  }

  public void testTypeArgumentsOnMethodRefs() throws Exception {
    doTest();
  }

  public void testConstructorAssignability() throws Exception {
    doTest();
  }

  public void testInferenceFromReturnType() throws Exception {
    doTest(true);
  }

  public void testReturnTypeSpecific() throws Exception {
    doTest(true);
  }

  private void doTest() throws Exception {
    doTest(false);
  }

  private void doTest(final boolean warnings) throws Exception {
    final JavaVersionServiceImpl versionService = (JavaVersionServiceImpl)JavaVersionService.getInstance();
    try {
      versionService.setTestVersion(JavaSdkVersion.JDK_1_8);
      doTest(BASE_PATH + "/" + getTestName(false) + ".java", warnings, false);
    }
    finally {
      versionService.setTestVersion(null);
    }
  }
}
