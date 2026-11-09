// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.dataflow;

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
public class LawOfDemeterInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> new LawOfDemeterInspection(), getEnvironmentClasses());

  @Test
  public void testSimpleWarning() {
    inspectionFixture.get().doTest("""
           import a.*;
           class X {
             void x(A a) {
               a.b()./*Call to 'a()' violates Law of Demeter*/a/**/();
             }
           }
           """);
  }

  @Test
  public void testFields() {
    inspectionFixture.get().doTest("""
           import a.*;
           class X {
             void x(C c) {
               c.a./*Call to 'b()' violates Law of Demeter*/b/**/();
             }
           }
           """);
  }

  @Test
  public void testFields2() {
    inspectionFixture.get().doTest("""
           import a.*;
           class X {
             void x(D d) {
               var x = new D().c./*Access of 'a' violates Law of Demeter*/a/**/;
             }
           }
           """);
  }

  @Test
  public void testConstructor() {
    inspectionFixture.get().doTest("""
           import a.*;
           class X {
             void x() {
               new C().a();
             }
           }
           """);
  }

  @Test
  public void testArrayLengthAccess() {
    inspectionFixture.get().doTest("""
           class X {  public static Object cloneArray(final Object source) {
               final Object[] result = ((Object[]) source).clone();
               for (int i = 0; i < result.length; i++) {
               }
               return result;
             }
           }
           """);
  }

  @Test
  public void testFactoryMethod() {
    inspectionFixture.get().doTest("""
           import a.*;
           class X {
             void x() {
               C.create().a();
             }
           }
           """);
  }

  @Test
  public void testReturnsSameClass() {
    inspectionFixture.get().doTest("""
           import a.*;
           class X {
             void x(A a) {
               a.a().a().a();
             }
           }
           """);
  }

  @Test
  public void testInnerClass() {
    inspectionFixture.get().doTest("""
           class X {
             void x() {
               new Inner().foo().foo();
             }
             class Inner {
               Inner2 foo() {
                 return null;
               }
             }
             class Inner2 {
               Inner foo() {
                 return null;
               }
             }
           }
           """);
  }

  private static String[] getEnvironmentClasses() {
    return new String[] {
      """
      package a;
      public interface A {
        A a();
        B b();
      }""",

      """
      package a;
      public interface B {
      A a();
      B b();
      }""",

      """
      package a;
      public class C {
        public A a;
        public B b;
        public C() {}
        public static C create() {
          return new C();
        }
        public A a() {
          return a;
        }
        public B b() {
          return b;
        }
      }""",

      """
      package a;
      public class D {
        public C c;
      }"""
    };
  }
}