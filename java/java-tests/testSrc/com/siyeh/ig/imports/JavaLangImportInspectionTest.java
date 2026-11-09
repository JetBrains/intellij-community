/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.siyeh.ig.imports;

import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
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
public class JavaLangImportInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> new JavaLangImportInspection());

  @Test
  public void testSamePackageConflict() {
    inspectionFixture.get().addEnvironmentClass("package a;" +
                        "class String {}");
    inspectionFixture.get().doTest("package a;" +
           "import java.lang.String;" +
           "class X {" +
           "  String s;" +
           "}");
  }

  @Test
  public void testSimple() {
    inspectionFixture.get().doTest("package a;" +
           "/*Unnecessary import from the 'java.lang' package*/import java.lang.String;/**/" +
           "class X {{" +
           "  String s;" +
           "}}");
  }

  @Test
  public void testPackageOnDemandConflict() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    myFixture.addClass("""
                                package b;
                                class String {
                                }""");
    inspectionFixture.get().doTest("""
             package a;
             import java.lang.String;
             import b.*;
             class X {
               String s;
             }""");
  }
}