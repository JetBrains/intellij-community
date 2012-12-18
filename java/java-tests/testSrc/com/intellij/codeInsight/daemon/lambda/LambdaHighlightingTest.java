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
import org.jetbrains.annotations.NonNls;

public class LambdaHighlightingTest extends LightDaemonAnalyzerTestCase {
  @NonNls static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/lambda/highlighting";

  public void testStaticAccess() throws Exception {
    doTest();
  }

  public void testEffectiveFinal() throws Exception {
    doTest();
  }
  
  public void testReassignUsedVars() throws Exception {
    doTest();
  }
  
  public void testLambdaContext() throws Exception {
    doTest();
  }

  public void testReturnTypeCompatibility() throws Exception {
    doTest();
  }
  
  public void testTypeArgsConsistency() throws Exception {
    doTest();
  }

  public void testTypeArgsConsistencyMisc1() throws Exception {
    doTest();
  }

  public void testTypeArgsConsistencyMisc2() throws Exception {
    doTest();
  }

  public void testTypeArgsConsistencyWithoutParams() throws Exception {
    doTest();
  } 

  public void testIncompatibleReturnTypes() throws Exception {
    doTest();
  }

  public void testWildcardBounds() throws Exception {
    doTest();
  }

  public void testInferenceOnMethodCallSite() throws Exception {
    doTest();
  }

  public void testInferFromTypeArgs() throws Exception {
    doTest();
  }
  
  public void testAmbiguity1() throws Exception {
    doTest();
  }

  public void _testAmbiguity2() throws Exception {
    doTest();
  }
  
  public void testAmbiguityVarargs() throws Exception {
    doTest();
  }

  public void testAmbiguityRawGenerics() throws Exception {
    doTest();
  }
  
  public void testDefaultMethod() throws Exception {
    doTest();
  }

  public void testLambdaOnVarargPlace() throws Exception {
    doTest();
  }

  public void testLambdaRawOrNot() throws Exception {
    doTest();
  }

  public void testReturnTypeCompatibility1() throws Exception {
    doTest();
  }
  
  public void testNoInferenceResult() throws Exception {
    doTest();
  }

  public void testInferenceFromArgs() throws Exception {
    doTest();
  }

  public void testInContexts() throws Exception {
    doTest();
  }

  public void testCastInContexts() throws Exception {
    doTest();
  }

  public void testUnhandledException() throws Exception {
    doTest();
  }

  public void testConditionalExpr() throws Exception {
    doTest();
  }
  
  public void testIncompleteSubst() throws Exception {
    doTest();
  }

  public void testVariableInitialization() throws Exception {
    doTest();
  }

  public void testUnreachableStatement() throws Exception {
    doTest();
  }

  public void testReturnValue() throws Exception {
    doTest();
  }
  
  public void testAlreadyUsedParamName() throws Exception {
    doTest();
  }

  public void testRecursiveAccess() throws Exception {
    doTest();
  }

  public void testIncompatibleFormalParameterTypes() throws Exception {
    doTest();
  }

  public void testNestedLambdas() throws Exception {
    doTest();
  }

  public void testEnumConstants() throws Exception {
    doTest();
  }

  public void testRawWhenNoParams() throws Exception {
    doTest();
  }

  private void doTest() throws Exception {
    doTest(BASE_PATH + "/" + getTestName(false) + ".java", false, false);
  }
}
