// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.codeInspection.blockingCallsDetection.BlockingMethodInNonBlockingContextInspection;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;

import java.util.Collections;

public class BlockingCallDetectionKotlinTest extends JavaCodeInsightFixtureTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    BlockingMethodInNonBlockingContextInspection myInspection = new BlockingMethodInNonBlockingContextInspection();
    myInspection.myBlockingAnnotations =
      Collections.singletonList(BlockingMethodInNonBlockingContextInspection.DEFAULT_BLOCKING_ANNOTATION);
    myInspection.myNonBlockingAnnotations =
      Collections.singletonList(BlockingMethodInNonBlockingContextInspection.DEFAULT_NONBLOCKING_ANNOTATION);
    myFixture.enableInspections(myInspection);
  }

  public void testKotlinCodeInspecting() {
    myFixture.addClass("package org.jetbrains.annotations;\n" +
                       "public @interface Blocking {}");
    myFixture.addClass("package org.jetbrains.annotations;\n" +
                       "public @interface NonBlocking {}");
    myFixture.addFileToProject("/TestKotlinCodeInspection.kt",
                               "import org.jetbrains.annotations.Blocking\n" +
                               "import org.jetbrains.annotations.NonBlocking\n" +
                               "@NonBlocking\n" +
                               "fun nonBlockingFunction() {\n" +
                               "  <warning descr=\"Inappropriate blocking method call\">blockingFunction</warning>();\n" +
                               "}\n" +
                               "@Blocking\n" +
                               "fun blockingFunction() {}");

    myFixture.testHighlighting(true, false, true, "TestKotlinCodeInspection.kt");
  }
}

