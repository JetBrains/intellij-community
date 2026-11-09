// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.performance;

import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
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
public class ObjectEqualsCanBeEqualityInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_8);
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> new ObjectEqualsCanBeEqualityInspection());

  @Test
  public void testTopLevel() {
    inspectionFixture.get().doTest("class X {" +
           "  void x(Object one, Object two) {" +
           "    one./*_*/equals(two);" +
           "  }" +
           "}");
    inspectionFixture.get().assertQuickFixNotAvailable(CommonQuickFixBundle.message("fix.replace.x.with.y", "equals()", "=="));
  }

  @Test
  public void testClass() {
    inspectionFixture.get().doTest("class X {" +
           "  boolean m(Class c1, Class c2) {" +
           "    return c1./*'equals()' can be replaced with '=='*//*_*/equals/**/(c2);" +
           "  }" +
           "}");
    inspectionFixture.get().checkQuickFix(CommonQuickFixBundle.message("fix.replace.x.with.y", "equals()", "=="),
                 "class X {" +
                 "  boolean m(Class c1, Class c2) {" +
                 "    return c1 == c2;" +
                 "  }" +
                 "}");
  }

  @Test
  public void testClassObjects() {
    inspectionFixture.get().doTest("class X {" +
           "  boolean m(Class c1, Class c2) {" +
           "    return !java.util.Objects./*'!equals()' can be replaced with '!='*//*_*/equals/**/(c1, c2);" +
           "  }" +
           "}");
    inspectionFixture.get().checkQuickFix(CommonQuickFixBundle.message("fix.replace.x.with.y", "!equals()", "!="),
                  "class X {" +
                  "  boolean m(Class c1, Class c2) {" +
                  "    return c1 != c2;" +
                  "  }" +
                  "}");
  }

  @Test
  public void testObject() {
    inspectionFixture.get().doTest("class X {" +
           "  boolean m(Object o1, Object o2) {" +
           "    return o1.equals(o2);" +
           "  }" +
           "}");
  }

  @Test
  public void testEnum() {
    // should not warn, this is reported by the "'equals()' called on Enum value" inspection
    inspectionFixture.get().doTest("class X {" +
           "  boolean m(E e1, E e2) {" +
           "    return e1.equals(e2);" +
           "  }" +
           "}" +
           "enum E {" +
           "  A,B,C" +
           "}");
  }

  @Test
  public void testString() {
    inspectionFixture.get().doTest("class X {" +
           "  boolean isRighteous(String a) {" +
           "    return a.equals(\"righteous\");" +
           "  }" +
           "}");
  }

  @Test
  public void testSingleton() {
    inspectionFixture.get().doTest("""
             final class Singleton {
               static String BUNDLE = null;
               static class DynamicBundle { DynamicBundle(Class<?> cls, String bundle) {} }
               private static final DynamicBundle INSTANCE = new DynamicBundle(Singleton.class, BUNDLE);
               private Singleton() {}
             }
             class U {
               boolean f(Singleton s1, Singleton s2) {
                 return s1./*'equals()' can be replaced with '=='*/equals/**/(s2);
               }
             }""");
  }
}