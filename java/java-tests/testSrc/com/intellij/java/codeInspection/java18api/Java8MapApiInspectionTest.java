// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection.java18api;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.java18api.Java8MapApiInspection;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;

@RunWith(Enclosed.class)
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
