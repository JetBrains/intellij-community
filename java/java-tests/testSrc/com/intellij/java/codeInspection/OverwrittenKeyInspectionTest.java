// Copyright 2000-2017 JetBrains s.r.o.
// Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.OverwrittenKeyInspection;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixture;

@TestApplication
public class OverwrittenKeyInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_9);
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> new OverwrittenKeyInspection());

  @BeforeEach
  void setUpTestDataPath() {
    inspectionFixture.get().getFixture().setTestDataPath(JavaTestUtil.getJavaTestDataPath() + "/inspection/overwrittenKey/");
  }

  @Test
  public void testOverwrittenKey() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    myFixture.addClass("""
                         package com.google.common.collect;
                         public abstract class ImmutableSet<E> implements Set<E> {
                           public static <E> ImmutableSet<E> of(E... e) {
                             throw new UnsupportedOperationException();
                           }
                         }""");
    inspectionFixture.get().doTest();
  }
  @Test
  public void testOverwrittenKeyArray() {
    inspectionFixture.get().doTest();
  }
}
