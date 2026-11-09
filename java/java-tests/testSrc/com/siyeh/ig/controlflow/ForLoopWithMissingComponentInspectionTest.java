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
package com.siyeh.ig.controlflow;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixture;

/**
 * @author Bas Leijdekkers
 */
@TestApplication
public class ForLoopWithMissingComponentInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> getInspection());

  @Test
  public void testMissingAllComponents() {
    inspectionFixture.get().doStatementTest("/*'for' statement lacks initializer, condition and update*/for/**/ (;;) {}");
  }

  @Test
  public void testMissingConditionAndUpdate() {
    inspectionFixture.get().doStatementTest("/*'for' statement lacks condition and update*/for/**/ (int i = 0;;);");
  }

  @Test
  public void testMissingInitializationAndUpdate() {
    inspectionFixture.get().doStatementTest("/*'for' statement lacks initializer and update*/for/**/ (;true;);");
  }

  @Test
  public void testMissingCondition() {
    inspectionFixture.get().doStatementTest("/*'for' statement lacks condition*/for/**/ (int i = 0;;i++);");
  }

  @Test
  public void testCorrect() {
    inspectionFixture.get().doStatementTest("for(int i = 0;true;i++);");
  }

  @Test
  public void testIterator() {
    inspectionFixture.get().doMemberTest("void m(java.util.List l) {" +
                 "  for (java.util.ListIterator it = l.listIterator(); it.hasNext();) {" +
                 "    System.out.println(it.next());" +
                 "  }" +
                 "}");
  }

  @Test
  public void testListIterator() {
    inspectionFixture.get().doMemberTest("void m(java.util.List l) {" +
                 "  for (java.util.ListIterator it = l.listIterator(); it.hasPrevious();) {" +
                 "    System.out.println(it.previous());" +
                 "  }" +
                 "}");
  }

  @Test
  public void testEnumeration() {
    inspectionFixture.get().doStatementTest("for (java.util.Enumeration e = java.util.Collections.emptyEnumeration(); e.hasMoreElements(); );");
  }

  @Test
  public void testEnumeration2() {
    inspectionFixture.get().doStatementTest("for (java.util.Enumeration e = java.util.Collections.emptyEnumeration(); e.hasMoreElements() && true && true; );");
  }

  private static LocalInspectionTool getInspection() {
    final ForLoopWithMissingComponentInspection inspection = new ForLoopWithMissingComponentInspection();
    inspection.ignoreCollectionLoops = true;
    return inspection;
  }
}
