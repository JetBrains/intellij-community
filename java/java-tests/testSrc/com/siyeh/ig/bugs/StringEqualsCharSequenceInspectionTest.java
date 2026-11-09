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
package com.siyeh.ig.bugs;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
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
@SuppressWarnings("StringEqualsCharSequence")
public class StringEqualsCharSequenceInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_8);
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> new StringEqualsCharSequenceInspection());

  @Test
  public void testSimple() {
    inspectionFixture.get().doMemberTest("boolean m(String s, CharSequence cs) {" +
                 "  return s./*'String.equals()' called with 'CharSequence' argument*/equals/**/(cs);" +
                 "}");
  }

  @Test
  public void testMethodReference() {
    inspectionFixture.get().doStatementTest("java.util.function.Predicate<CharSequence> p = \"123\"::/*'String.equals()' called with 'CharSequence' argument*/equals/**/;");
  }

  @Test
  public void testObjectsEquals() {
    inspectionFixture.get().doMemberTest("boolean m(String s, CharSequence cs) {" +
                 "  return java.util.Objects./*'String.equals()' called with 'CharSequence' argument*/equals/**/(s, cs);" +
                 "}");
  }

  @Test
  public void testPredicate() {
    inspectionFixture.get().doMemberTest("boolean m(String s, CharSequence cs) {" +
                 "  return java.util.function.Predicate./*'String.equals()' called with 'CharSequence' argument*/isEqual/**/(s).test(cs);" +
                 "}");
  }
}