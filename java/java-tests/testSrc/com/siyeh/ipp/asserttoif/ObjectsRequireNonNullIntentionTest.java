/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.siyeh.ipp.asserttoif;

import com.intellij.java.codeInspection.DataFlowInspectionTest;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.IPPFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ipp.IPPFixtureKt.ippFixture;

/**
 * @see com.siyeh.ipp.asserttoif.ObjectsRequireNonNullIntention
 * @author Bas Leijdekkers
 */
@TestApplication
public class ObjectsRequireNonNullIntentionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_8);
  private final TestFixture<IPPFixture> ipp =
    ippFixture(projectFixture, "asserttoif/objects_require_non_null", IntentionPowerPackBundle.message("objects.require.non.null.intention.name"));

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = ipp.get().getFixture();
    DataFlowInspectionTest.addJavaxNullabilityAnnotations(myFixture);
    DataFlowInspectionTest.addJavaxDefaultNullabilityAnnotations(myFixture);
  }

  @Test
  public void testOne() { ipp.get().doTest(); }
  @Test
  public void testTwo() { ipp.get().doTest(); }
  @Test
  public void testThree() { ipp.get().doTest(); }
  @Test
  public void testContainer() { ipp.get().doTest(); }
}
