// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.tests.java.sourceToSink;

import com.intellij.codeInspection.sourceToSink.SourceToSinkFlowInspection;
import com.intellij.jvm.analysis.JavaJvmAnalysisTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

public class SourceToSinkFlowInspectionTest extends LightJavaCodeInsightFixtureTestCase {

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.addClass("""
                               package org.checkerframework.checker.tainting.qual;
                               import java.lang.annotation.ElementType;
                               import java.lang.annotation.Target;
                               @Target({ElementType.LOCAL_VARIABLE, ElementType.FIELD, ElementType.METHOD})
                               public @interface Tainted {
                               }\
                         """);
    myFixture.addClass("""
                               package org.checkerframework.checker.tainting.qual;
                               import java.lang.annotation.ElementType;
                               import java.lang.annotation.Target;
                               @Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})
                               public @interface Untainted {
                               }\
                         """);
    myFixture.enableInspections(new SourceToSinkFlowInspection());
  }

  @Override
  protected String getBasePath() {
    return JavaJvmAnalysisTestUtil.TEST_DATA_PROJECT_RELATIVE_BASE_PATH + "/codeInspection/sourceToSinkFlow";
  }

  public void testSimple() {
    myFixture.testHighlighting("Simple.java");
  }

  public void testLocalInference() {
    myFixture.testHighlighting("LocalInference.java");
  }
}
