// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
public class SerializableInnerClassHasSerialVersionUIDFieldInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> new SerializableInnerClassHasSerialVersionUIDFieldInspection(), getEnvironmentClasses());

  @Test
  public void testAnonymousClass() {
    inspectionFixture.get().doTest("class NonSerializable {" +
           "    public void method() {" +
           "        java.io.Serializable s = new /*Inner class 'java.io.Serializable' does not define a 'serialVersionUID' field*/java.io.Serializable/**/() {};" +
           "    }" +
           "}");
  }

  @Test
  public void testInnerClass() {
    inspectionFixture.get().doTest("class NonSerializable {" +
           "    public class /*Inner class 'MySerializable' does not define a 'serialVersionUID' field*/MySerializable/**/ implements java.io.Serializable {" +
           "    }" +
           "}");
  }

  @Test
  public void testLocalClass() {
    inspectionFixture.get().doTest("class A {" +
           "  void m() {" +
           "    class /*Inner class 'Y' does not define a 'serialVersionUID' field*/Y/**/ implements java.io.Serializable {}" +
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
  public void testNoWarn() {
    inspectionFixture.get().doTest("class A {" +
           "  class B implements java.io.Serializable {" +
           "    private static final long serialVersionUID = -8289890549062901754L;" +
           "  }" +
           "}");
  }

  @Test
  public void testRecord() {
    inspectionFixture.get().doTest("class A {" +
           "  record R() implements java.io.Serializable {" +
           "  }" +
           "}");
  }

  @Test
  public void testTypeParameter() {
    inspectionFixture.get().doTest("class A<TypeParameter extends java.awt.Component> {}");
  }

  private static String[] getEnvironmentClasses() {
    return new String[] {
      "package java.awt;" +
      "public abstract class Component {}"
    };
  }
}
