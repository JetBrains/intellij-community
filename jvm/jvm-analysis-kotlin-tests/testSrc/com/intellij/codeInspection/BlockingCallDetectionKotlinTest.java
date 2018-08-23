// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.codeInspection.blockingCallsDetection.BlockingMethodInNonBlockingContextInspection;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;

import java.util.Collections;

public class BlockingCallDetectionKotlinTest extends JavaCodeInsightFixtureTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();

    final String dataPath = PathManagerEx.getTestDataPath() + "/codeInspection/blockingCallsDetection";
    myFixture.setTestDataPath(dataPath);

    BlockingMethodInNonBlockingContextInspection myInspection = new BlockingMethodInNonBlockingContextInspection();
    myInspection.myBlockingAnnotations =
      Collections.singletonList(BlockingMethodInNonBlockingContextInspection.DEFAULT_BLOCKING_ANNOTATION);
    myInspection.myNonBlockingAnnotations =
      Collections.singletonList(BlockingMethodInNonBlockingContextInspection.DEFAULT_NONBLOCKING_ANNOTATION);
    myFixture.enableInspections(myInspection);
  }

  public void testKotlinCodeInspecting() {
    myFixture.addClass("package org.jetbrains.annotations;\n" +
                       "\n" +
                       "import java.lang.annotation.ElementType;\n" +
                       "import java.lang.annotation.Retention;\n" +
                       "import java.lang.annotation.RetentionPolicy;\n" +
                       "import java.lang.annotation.Target;\n" +
                       "\n" +
                       "@Target(value = ElementType.METHOD)\n" +
                       "@Retention(value = RetentionPolicy.CLASS)\n" +
                       "public @interface Blocking {\n" +
                       "}");
    myFixture.addClass("package org.jetbrains.annotations;\n" +
                       "\n" +
                       "import java.lang.annotation.ElementType;\n" +
                       "import java.lang.annotation.Retention;\n" +
                       "import java.lang.annotation.RetentionPolicy;\n" +
                       "import java.lang.annotation.Target;\n" +
                       "\n" +
                       "@Target(value = ElementType.METHOD)\n" +
                       "@Retention(value = RetentionPolicy.CLASS)\n" +
                       "public @interface NonBlocking {\n" +
                       "}");

    myFixture.testHighlighting(true, false, true, "TestKotlinCodeInspection.kt");
  }
}

