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
package com.siyeh.ig.dataflow;

import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
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
public class NegativelyNamedBooleanVariableInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> new NegativelyNamedBooleanVariableInspection());

  @Test
  public void testTree() {
    inspectionFixture.get().doTest("class X {" +
           "  boolean /*Boolean variable 'hidden' is negatively named*/hidden/**/ = false;" +
           "  boolean /*Boolean variable 'disabled' is negatively named*/disabled/**/ = false;" +
           "  boolean /*Boolean variable 'isNotChanged' is negatively named*/isNotChanged/**/ = false;" +
           "}");
  }

  @Test
  public void testPrefix() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    JavaCodeStyleSettings.getInstance(myFixture.getProject()).FIELD_NAME_PREFIX = "m_";
    inspectionFixture.get().doTest("class Y {" +
           "  private boolean /*Boolean variable 'm_isNonValid' is negatively named*/m_isNonValid/**/ = false;" +
           "}");
  }

  @Test
  public void testNewNames() {
    inspectionFixture.get().doTest("class Z {" +
           "  private boolean /*Boolean variable 'invalidState' is negatively named*/invalidState/**/ = true;" +
           "  private boolean /*Boolean variable 'isInvalidSource' is negatively named*/isInvalidSource/**/ = false;" +
           "  private boolean /*Boolean variable 'doesNotCompute' is negatively named*/doesNotCompute/**/ = true;" +
           "}");
  }

  @Test
  public void testForeachParameter() {
    inspectionFixture.get().doTest("import java.util.List;" +
           "class Test {" +
           "  void foo(List<Boolean> cants) {" +
           "    for (boolean cant : cants) {" +
           "      System.out.println(cant);" +
           "    }" +
           "  }" +
           "}");
  }
}
