// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.inheritance;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.openapi.application.PathManager;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MissingOverrideAnnotationInspectionTest extends LightJavaInspectionTestCase {

  public void testSimple() {
    doTest();
  }

  public void testNotAvailable() {
    doTest();
  }

  public void testHierarchy() {
    doTest();
  }

  public void testHierarchy2() {
    doTest();
  }
  
  public void testSimpleJava5() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_1_5, this::doTest);
  }
  
  public void testNotAvailableMethodInLanguageLevel7() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_1_7, this::doTest);
  }
  
  public void _testRecordAccessorJava14() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.HIGHEST, this::doTest);
  }
  
  public void testRecordAccessorJava15() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.HIGHEST, this::doTest);
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new MissingOverrideAnnotationInspection();
  }

  public static class MissingOverrideAnnotationInspectionFixTest extends LightQuickFixParameterizedTestCase {
    @Override
    protected LocalInspectionTool @NotNull [] configureLocalInspectionTools() {
      return new MissingOverrideAnnotationInspection[]{new MissingOverrideAnnotationInspection()};
    }

    @Override
    protected String getBasePath() {
      return "/com/siyeh/igtest/inheritance/missing_override_annotation";
    }

    @NotNull
    @Override
    protected String getTestDataPath() {
      return PathManager.getCommunityHomePath() + "/java/java-tests/testData/ig";
    }
  }
}
