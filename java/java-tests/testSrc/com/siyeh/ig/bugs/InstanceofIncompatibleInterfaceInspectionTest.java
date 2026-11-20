// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.bugs;

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
public class InstanceofIncompatibleInterfaceInspectionTest {
  private static final TestFixture<? extends JavaTestProject> projectFixture = javaTestProjectFixture();
  private final TestFixture<? extends JavaInspectionFixture> inspectionFixture =
    javaInspectionFixture(projectFixture, () -> new InstanceofIncompatibleInterfaceInspection());

  @Test
  public void testTooExpensiveToCheck() {
    inspectionFixture.get().doTest("""
             import java.util.HashMap;
             import java.util.List;
             class X {
               void m() {
                 if(new HashMap() instanceof List);
               }
             }""");
  }

  @Test
  public void testSimple() {
    inspectionFixture.get().doTest("""
           class Alfa {}
           interface Bravo {}
           class Charlie {
             boolean x(Alfa a) {
               return a instanceof /*'instanceof' of expression with type 'Alfa' with incompatible interface 'Bravo'*/Bravo/**/;
             }
           }
           """);
  }

  @Test
  public void testNoWarnOnAssertion() {
    inspectionFixture.get().doTest("""
      interface Cat {}
      class Dog {
        Dog() {
          if (this instanceof Cat) {
            throw new IllegalStateException("a dog should never be a cat!");
          }
        }
      }
      """);
  }

  @Test
  public void testClasses() {
    inspectionFixture.get().doTest("""
             class Foo { }
             interface Bar { }
             final class Main213 {
             
                 static void x(Foo f, Bar b) {
                     if (f instanceof /*'instanceof' of expression with type 'Foo' with incompatible interface 'Bar'*/Bar/**/) {
                         System.out.println("fail");
                     }
                     if (b instanceof /*'instanceof' of expression with type 'Bar' with incompatible class 'Foo'*/Foo/**/) {
                         System.out.println("fail");
                     }
                 }
             }""");
  }

  @Test
  public void testNoWarningOnUncompilableCode() {
    inspectionFixture.get().doTest("""
             final class Foo { }
             interface Bar { }
             final class Main213 {
             
                 static void x(Foo f, Bar b) {
                     if (/*!Inconvertible types; cannot cast 'Foo' to 'Bar'*/f instanceof Bar/*!*/) {
                         System.out.println("fail");
                     }
                     if (/*!Inconvertible types; cannot cast 'Bar' to 'Foo'*/b instanceof Foo/*!*/) {
                         System.out.println("fail");
                     }
                 }
             }""");
  }
}
