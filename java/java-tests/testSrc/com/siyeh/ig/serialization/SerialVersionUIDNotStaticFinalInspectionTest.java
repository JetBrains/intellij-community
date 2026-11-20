// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.serialization;

import com.intellij.testFramework.fixtures.JavaTestProject;
import com.intellij.testFramework.junit5.AnalysisHostTest;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.JavaInspectionFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.JavaTestFixtures.javaTestProjectFixture;
import static com.siyeh.ig.InspectionTestFixtures.javaInspectionFixture;

@AnalysisHostTest
public class SerialVersionUIDNotStaticFinalInspectionTest {
  private static final TestFixture<? extends JavaTestProject> projectFixture = javaTestProjectFixture();
  private final TestFixture<? extends JavaInspectionFixture> inspectionFixture =
    javaInspectionFixture(projectFixture, () -> new SerialVersionUIDNotStaticFinalInspection());

  @Test
  public void testClassRightReturnType() {
    inspectionFixture.get().doTest("""
             import java.io.*;
             class C implements Serializable {
               static final long <warning descr="'serialVersionUID' field of a Serializable class is not declared 'private static final long'"><caret>serialVersionUID</warning> = 1;
             }""");
    inspectionFixture.get().checkQuickFix("Make serialVersionUID 'private static final'",
                  """
                    import java.io.*;
                    class C implements Serializable {
                      private static final long serialVersionUID = 1;
                    }""");
  }

  @Test
  public void testRecordComponent() {
    inspectionFixture.get().doTest("""
             import java.io.*;
             record R(long <warning descr="'serialVersionUID' field of a Serializable class is not declared 'private static final long'"><caret>serialVersionUID</warning>) implements Serializable {
             }""");
    inspectionFixture.get().assertQuickFixNotAvailable("Make serialVersionUID 'private static final'");
  }

  @Test
  public void testRecordStaticField() {
    inspectionFixture.get().doTest("""
             import java.io.*;
             record R() implements Serializable {
               static long <warning descr="'serialVersionUID' field of a Serializable class is not declared 'private static final long'"><caret>serialVersionUID</warning> = 1;
             }""");
    inspectionFixture.get().checkQuickFix("Make serialVersionUID 'private static final'",
                  """
                    import java.io.*;
                    record R() implements Serializable {
                      private static final long serialVersionUID = 1;
                    }""");
  }

}