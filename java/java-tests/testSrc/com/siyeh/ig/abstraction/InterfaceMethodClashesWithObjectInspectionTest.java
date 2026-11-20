/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.siyeh.ig.abstraction;

import com.intellij.testFramework.fixtures.JavaTestProject;
import com.intellij.testFramework.junit5.AnalysisHostTest;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.JavaInspectionFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.JavaTestFixtures.javaTestProjectFixture;
import static com.siyeh.ig.InspectionTestFixtures.javaInspectionFixture;

/**
 * @author Bas Leijdekkers
 */
@AnalysisHostTest
@SuppressWarnings("InterfaceMethodClashesWithObject")
public class InterfaceMethodClashesWithObjectInspectionTest {
  private static final TestFixture<? extends JavaTestProject> projectFixture = javaTestProjectFixture();
  private final TestFixture<? extends JavaInspectionFixture> inspectionFixture =
    javaInspectionFixture(projectFixture, () -> new InterfaceMethodClashesWithObjectInspection());

  @Test
  public void testBadClone() {
    inspectionFixture.get().doTest("interface BadClone {" +
           "  int /*'clone()' clashes with method in 'java.lang.Object'*/clone/**/();" +
           "}");
  }

  @Test
  public void testBadFinalize() {
    inspectionFixture.get().doTest("interface BadFinalize {" +
           "  Object /*'finalize()' clashes with method in 'java.lang.Object'*/finalize/**/();" +
           "}");
  }

  @Test
  public void testGoodClone() {
    inspectionFixture.get().doTest("interface GoodClone {" +
           "  GoodClone clone() throws CloneNotSupportedException;" +
           "}");
  }

  @Test
  public void testAnnotated() {
    inspectionFixture.get().doTest("interface A {" +
           "    @Deprecated" +
           "    void finalize();" +
           "}" +
           "class B implements A {" +
           "    public @Deprecated void finalize() {}" +
           "}");
  }
}