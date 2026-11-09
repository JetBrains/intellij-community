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
package com.siyeh.ig.serialization;

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
public class SerializableInnerClassWithNonSerializableOuterClassInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> new SerializableInnerClassWithNonSerializableOuterClassInspection());

  @Test
  public void testAnonymousClass() {
    inspectionFixture.get().doTest("class NonSerializable {" +
           "    public void method() {" +
           "        java.io.Serializable s = new /*Inner class 'java.io.Serializable' is serializable while its outer class is not*/java.io.Serializable/**/() {};" +
           "    }" +
           "}");
  }

  @Test
  public void testInnerClass() {
    inspectionFixture.get().doTest("class NonSerializable {" +
           "    public class /*Inner class 'MySerializable' is serializable while its outer class is not*/MySerializable/**/ implements java.io.Serializable {" +
           "    }" +
           "}");
  }

  @Test
  public void testLocalClass() {
    inspectionFixture.get().doTest("class A {" +
           "  void m() {" +
           "    class /*Inner class 'Y' is serializable while its outer class is not*/Y/**/ implements java.io.Serializable {}" +
           "  }" +
           "}");
  }

  @Test
  public void testStaticAnonymousClass() {
    inspectionFixture.get().doTest("class A {" +
           "  static void m() {" +
           "    new java.io.Serializable() {};" +
           "  }" +
           "}");
  }

  @Test
  public void testTypeParameter() {
    inspectionFixture.get().doTest("class A<TypeParameter extends java.awt.Component> {}");
  }
}
