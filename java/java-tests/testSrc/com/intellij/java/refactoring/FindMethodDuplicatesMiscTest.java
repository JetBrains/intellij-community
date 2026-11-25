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
package com.intellij.java.refactoring;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.java.refactoring.FindMethodDuplicatesFixtureKt.findMethodDuplicatesFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;

@TestApplication
public class FindMethodDuplicatesMiscTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor(LanguageLevel.JDK_1_7));
  private final TestFixture<FindMethodDuplicatesFixture> duplicates = findMethodDuplicatesFixture(projectFixture, "/refactoring/methodDuplicatesMisc/");

  @Test
  public void testChangeReturnTypeByParameter() {
    duplicates.get().doTest();
  }

  @Test
  public void testChangeReturnTypeByField() {
    duplicates.get().doTest();
  }

  @Test
  public void testMethodTypeParameters() {
    duplicates.get().doTest();
  }

  @Test
  public void testChangeReturnTypeByReturnExpression() {
    duplicates.get().doTest();
  }

  @Test
  public void testChangeReturnTypeByReturnValue() {
    duplicates.get().doTest();
  }

  @Test
  public void testParametersModification() {
    duplicates.get().doTest();
  }

  @Test
  public void testPassArray2VarargMethodCall() {
    duplicates.get().doTest();
  }

  @Test
  public void testDetectNameConflicts() {
    duplicates.get().doTest();
  }

  @Test
  public void testNoDetectNameConflicts() {
    duplicates.get().doTest();
  }

  @Test
  public void testDetectNameConflictsWithStatic() {
    duplicates.get().doTest();
  }

  @Test
  public void testCorrectThis() {
    duplicates.get().doTest();
  }

  @Test
  public void testSuperInTheSameContext() {
    duplicates.get().doTest(false);
  }

  @Test
  public void testSuperInTheSameContextQualified() {
    duplicates.get().doTest();
  }

  @Test
  public void testInsertSuperQualifierWhenNameConflicts() {
    duplicates.get().doTest();
  }

  @Test
  public void testUnqualifiedStaticAccess() {
    duplicates.get().doTest();
  }

  @Test
  public void testCandidateUnqualifiedStaticAccess() {
    duplicates.get().doTest();
  }

  @Test
  public void testVarargsAccess() {
    duplicates.get().doTest();
  }

  @Test
  public void testIncorrectVarargsAccess() {
    duplicates.get().doTest();
  }

  @Test
  public void testVarVarargsAccess() {
    duplicates.get().doTest();
  }

  @Test
  public void testSkipNotAccessible() {
    duplicates.get().doTest(false);
  }
  
  @Test
  
  public void testQualifiers() {
    duplicates.get().doTest();
  }

  @Test
  public void testSimpleConstant() {
    duplicates.get().doTest();
  }

  @Test
  public void testAnonymousInitializer() {
    duplicates.get().doTest();
  }

  @Test
  public void testMakeStaticOnOverride() {
    duplicates.get().doTest(false);
  }

  @Test
  public void testReplaceDuplicateInsideAnonymous() {
    duplicates.get().doTest();
  }

  @Test
  public void testMakeStaticWhenUsedInInheritor() {
    duplicates.get().doTest();
  }
}