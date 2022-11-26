// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.tests.kotlin;

import com.intellij.codeInspection.blockingCallsDetection.BlockingMethodInNonBlockingContextInspection;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;

import static com.intellij.codeInspection.blockingCallsDetection.BlockingMethodInNonBlockingContextInspection.DEFAULT_BLOCKING_ANNOTATIONS;
import static com.intellij.codeInspection.blockingCallsDetection.BlockingMethodInNonBlockingContextInspection.DEFAULT_NONBLOCKING_ANNOTATIONS;

public class KotlinBlockingCallDetectionTest extends JavaCodeInsightFixtureTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    BlockingMethodInNonBlockingContextInspection myInspection = new BlockingMethodInNonBlockingContextInspection();
    myInspection.myBlockingAnnotations = DEFAULT_BLOCKING_ANNOTATIONS;
    myInspection.myNonBlockingAnnotations = DEFAULT_NONBLOCKING_ANNOTATIONS;
    myFixture.enableInspections(myInspection);
  }

  public void testKotlinAnnotationDetection() {
    myFixture.addClass("package org.jetbrains.annotations;\n" +
                       "public @interface Blocking {}");
    myFixture.addClass("package org.jetbrains.annotations;\n" +
                       "public @interface NonBlocking {}");
    myFixture.addFileToProject("/TestKotlinAnnotationDetection.kt",
                               """
                                 import org.jetbrains.annotations.Blocking
                                 import org.jetbrains.annotations.NonBlocking
                                 @NonBlocking
                                 fun nonBlockingFunction() {
                                   <warning descr="Possibly blocking call in non-blocking context could lead to thread starvation">blockingFunction</warning>();
                                 }
                                 @Blocking
                                 fun blockingFunction() {}""");

    myFixture.testHighlighting(true, false, true, "TestKotlinAnnotationDetection.kt");
  }

  public void testKotlinThrowsTypeDetection() {
    myFixture.addClass("package org.jetbrains.annotations;\n" +
                       "public @interface NonBlocking {}");

    myFixture.configureByText("/TestKotlinThrowsTypeDetection.kt",
                              """
                                import org.jetbrains.annotations.NonBlocking
                                import java.net.URL

                                @NonBlocking
                                fun nonBlockingFunction() {
                                  Thread.<warning descr="Possibly blocking call in non-blocking context could lead to thread starvation">sleep</warning>(111);
                                 \s
                                  URL("https://example.com")
                                }""");

    myFixture.checkHighlighting(true, false, true);
  }
}