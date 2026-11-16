/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.refactoring;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.refactoring.typeMigration.rules.LongAdderConversionRule;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.refactoring.LightMultiFileFixtureKt.lightMultiFileFixture;
import static com.intellij.refactoring.TypeMigrationTestUtil.doTestFieldType;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

/**
 * @author Dmitry Batkovich
 */
@TestApplication
public class TypeMigrationByLongAdderTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_1_6);
  private final TestFixture<LightMultiFileFixture> multiFile =
    lightMultiFileFixture(projectFixture, TypeMigrationTestUtil.getTestDataPath() + "/refactoring/typeMigrationByLongAdder/");

  @Test
  public void testDirectCallsWithoutAssignment() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    doTestFieldType(multiFile.get(), "l",
                    ReadAction.computeBlocking(() -> JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeFromText(LongAdderConversionRule.JAVA_UTIL_CONCURRENT_ATOMIC_LONG_ADDER, null)));
  }

  @Test
  public void testIncrementDecrement() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    doTestFieldType(multiFile.get(), "l",
                    ReadAction.computeBlocking(() -> JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeFromText(LongAdderConversionRule.JAVA_UTIL_CONCURRENT_ATOMIC_LONG_ADDER, null)));
  }

  @Test
  public void testToPrimitivesAndString() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    doTestFieldType(multiFile.get(), "i",
                    ReadAction.computeBlocking(() -> JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeFromText(LongAdderConversionRule.JAVA_UTIL_CONCURRENT_ATOMIC_LONG_ADDER, null)));
  }

  @Test
  public void testUnconvertable() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    doTestFieldType(multiFile.get(), "i",
                    ReadAction.computeBlocking(() -> JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeFromText(LongAdderConversionRule.JAVA_UTIL_CONCURRENT_ATOMIC_LONG_ADDER, null)));
  }
}
