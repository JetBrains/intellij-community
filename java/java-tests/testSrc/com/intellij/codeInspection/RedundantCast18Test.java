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
package com.intellij.codeInspection;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerTestCase;
import com.intellij.codeInspection.redundantCast.RedundantCastInspection;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class RedundantCast18Test extends LightDaemonAnalyzerTestCase {
  @NonNls static final String BASE_PATH = "/inspection/redundantCast/lambda";

  @NotNull
  @Override
  protected LocalInspectionTool[] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{
      new RedundantCastInspection()
    };
  }

  private void doTest() {
    doTest(BASE_PATH + "/" + getTestName(false) + ".java", true, false);
  }

  public void testLambdaContext() throws Exception { doTest(); }
  public void testMethodRefContext() throws Exception { doTest(); }
  public void testExpectedSupertype() throws Exception { doTest(); }
  public void testForeachValue() throws Exception { doTest(); }
  public void testConditional() throws Exception { doTest(); }
  public void testInferApplicabilityError() throws Exception { doTest(); }
  public void testCastToRawType() throws Exception { doTest(); }
}