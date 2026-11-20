// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.serialization;

import com.intellij.testFramework.fixtures.JavaTestProject;
import com.intellij.testFramework.junit5.AnalysisHostTest;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.JavaInspectionFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.JavaTestFixtures.javaTestProjectFixture;
import static com.siyeh.ig.InspectionTestFixtures.javaInspectionFixture;

/**
 * @author Bas Leijdekkers
 */
@AnalysisHostTest
public class SerializableInnerClassHasSerialVersionUIDFieldInspectionTest {
  private static final TestFixture<? extends JavaTestProject> projectFixture = javaTestProjectFixture();
  private final TestFixture<? extends JavaInspectionFixture> inspectionFixture =
    javaInspectionFixture(projectFixture, () -> new SerializableInnerClassHasSerialVersionUIDFieldInspection(), getEnvironmentClasses());

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
