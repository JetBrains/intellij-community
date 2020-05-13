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
package com.intellij.java.codeInspection.java18api;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.java18api.Java8MapApiInspection;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

public class Java8MapApiInspectionTest extends LightQuickFixParameterizedTestCase {
  @Override
  protected LocalInspectionTool @NotNull [] configureLocalInspectionTools() {
    Java8MapApiInspection inspection = new Java8MapApiInspection();
    inspection.myTreatGetNullAsContainsKey = true;
    return new LocalInspectionTool[]{inspection};
  }

  public static class ImplTest {
    @Test
    public void testNameCandidate() {
      assertEquals("e", Java8MapApiInspection.getNameCandidate("element"));
      assertEquals("t", Java8MapApiInspection.getNameCandidate("accessToken"));
      assertEquals("s", Java8MapApiInspection.getNameCandidate("SQL"));
      assertEquals("n", Java8MapApiInspection.getNameCandidate("myUserName"));
      assertEquals("v", Java8MapApiInspection.getNameCandidate("___VAR"));
      assertEquals("k", Java8MapApiInspection.getNameCandidate("_1"));
    }
  }

  @Override
  protected String getBasePath() {
    return "/inspection/java8MapApi";
  }
}
