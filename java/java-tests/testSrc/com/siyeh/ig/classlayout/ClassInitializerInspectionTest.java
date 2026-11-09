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
package com.siyeh.ig.classlayout;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Bas Leijdekkers
 */
@TestApplication
public class ClassInitializerInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> new ClassInitializerInspection());

  @Test
  public void testSimple() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_15, () -> {
      inspectionFixture.get().doTest();
      assertThat(myFixture.filterAvailableIntentions("Make 'static'")).isNullOrEmpty();
    });
    
  }

  @Test
  public void testAnonymous() { inspectionFixture.get().doTest(); }

  @Test
  public void testJava16() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_16, () -> {
      inspectionFixture.get().doTest();
      assertEquals(1, myFixture.filterAvailableIntentions("Make 'static'").size());
    });
  }
  
  @Test
  public void testNoConstructor() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    final ClassInitializerInspection inspection = new ClassInitializerInspection();
    inspection.onlyWarnWhenConstructor = true;
    myFixture.enableInspections(inspection);
    inspectionFixture.get().doTest();
  }
}
