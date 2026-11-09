// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInspection;

import com.intellij.codeInspection.RecordCanBeClassInspection;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixture;

@TestApplication
public class SerializableRecordCanBeClassInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> new RecordCanBeClassInspection(), getEnvironmentClasses());

  @Test
  public void testSerializableVersionUIDWithoutSerial() {
    inspectionFixture.get().doTest("""
             import java.io.Serializable;
             record <warning descr="Record can be converted to class"><caret>R</warning>() implements Serializable {
               @MyAnn
               private static final long serialVersionUID = 1;
               static long number = 10;
             }""");
    inspectionFixture.get().checkQuickFix("Convert record to class", """
      import java.io.Serial;
      import java.io.Serializable;

      final class R implements Serializable {
          @Serial
          @MyAnn
          private static final long serialVersionUID = 1;
          static long number = 10;

          R() {
          }

          @Override
          public boolean equals(Object obj) {
              return obj == this || obj != null && obj.getClass() == this.getClass();
          }

          @Override
          public int hashCode() {
              return 1;
          }

          @Override
          public String toString() {
              return "R[]";
          }

      }""");
  }

  @Test
  public void testSerializableVersionUIDWithSerial() {
    inspectionFixture.get().doTest("""
             import java.io.Serial;
             import java.io.Serializable;
             record <warning descr="Record can be converted to class"><caret>R</warning>() implements Serializable {
               @Serial  @MyAnn
               private static final long serialVersionUID = 1;
               static long number = 10;
             }""");
    inspectionFixture.get().checkQuickFix("Convert record to class", """
      import java.io.Serial;
      import java.io.Serializable;

      final class R implements Serializable {
          @Serial
          @MyAnn
          private static final long serialVersionUID = 1;
          static long number = 10;

          R() {
          }

          @Override
          public boolean equals(Object obj) {
              return obj == this || obj != null && obj.getClass() == this.getClass();
          }

          @Override
          public int hashCode() {
              return 1;
          }

          @Override
          public String toString() {
              return "R[]";
          }

      }""");
  }

  @Test
  public void testWithoutSerialVersionUID() {
    inspectionFixture.get().doTest("""
             import java.io.Serializable;
             record <warning descr="Record can be converted to class"><caret>R</warning>() implements Serializable {
               static long number = 10;
             }""");
    inspectionFixture.get().checkQuickFix("Convert record to class", """
      import java.io.Serial;
      import java.io.Serializable;

      final class R implements Serializable {
          static long number = 10;
          @Serial
          private static final long serialVersionUID = 0L;

          R() {
          }

          @Override
          public boolean equals(Object obj) {
              return obj == this || obj != null && obj.getClass() == this.getClass();
          }

          @Override
          public int hashCode() {
              return 1;
          }

          @Override
          public String toString() {
              return "R[]";
          }

      }""");
  }

  @Test
  public void testSerialVersionUIDWithWrongModifier() {
    inspectionFixture.get().doTest("import java.io.Serializable;\n" +
           "record <warning descr=\"Record can be converted to class\"><caret>R</warning>() implements Serializable {\n" +
           "  static long number = 10;\n" +
           "  @MyAnn\n" +
           "  private static long serialVersionUID = 10;\n" + // not final
           "}");
    inspectionFixture.get().checkQuickFix("Convert record to class", """
      import java.io.Serializable;

      final class R implements Serializable {
          static long number = 10;
          @MyAnn
          private static long serialVersionUID = 10;

          R() {
          }

          @Override
          public boolean equals(Object obj) {
              return obj == this || obj != null && obj.getClass() == this.getClass();
          }

          @Override
          public int hashCode() {
              return 1;
          }

          @Override
          public String toString() {
              return "R[]";
          }

      }""");
  }

  private static String[] getEnvironmentClasses() {
    return new String[]{
      """
package java.io;
@Target({ElementType.METHOD, ElementType.FIELD})
@Retention(RetentionPolicy.SOURCE)
public @interface Serial {}""",

      """
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.SOURCE)
public @interface MyAnn {}"""
    };
  }
}
