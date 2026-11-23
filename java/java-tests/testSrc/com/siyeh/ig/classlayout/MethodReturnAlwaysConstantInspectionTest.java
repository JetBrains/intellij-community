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
package com.siyeh.ig.classlayout;
import com.intellij.JavaTestUtil;
import com.intellij.testFramework.JavaInspectionFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.JavaInspectionFixtureKt.javaInspectionFixture;
import static com.intellij.testFramework.JavaInspectionFixtureKt.javaInspectionProjectFixture;


@TestApplication
public class MethodReturnAlwaysConstantInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = javaInspectionProjectFixture();
  private final TestFixture<JavaInspectionFixture> javaInspection = javaInspectionFixture(projectFixture, JavaTestUtil.getJavaTestDataPath() + "/ig" + "/com/siyeh/igtest/classlayout/method_return_always_constant");


  @Test
  public void testNoInheritors() {
    doTest();
  }

  @Test
  public void testReturnsConstant() {
    doTest();
  }

  @Test
  public void testNotReturnConstant() {
    doTest();
  }

  private void doTest() {
    javaInspection.get().doTest(javaInspection.get().getTestName(true), new MethodReturnAlwaysConstantInspection());
  }
}
