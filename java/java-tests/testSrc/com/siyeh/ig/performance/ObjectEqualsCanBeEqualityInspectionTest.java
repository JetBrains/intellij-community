// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.performance;

import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class ObjectEqualsCanBeEqualityInspectionTest extends LightJavaInspectionTestCase {

  public void testTopLevel() {
    doTest("class X {" +
           "  void x(Object one, Object two) {" +
           "    one./*_*/equals(two);" +
           "  }" +
           "}");
    assertQuickFixNotAvailable(CommonQuickFixBundle.message("fix.replace.x.with.y", "equals()", "=="));
  }

  public void testClass() {
    doTest("class X {" +
           "  boolean m(Class c1, Class c2) {" +
           "    return c1./*'equals()' can be replaced with '=='*//*_*/equals/**/(c2);" +
           "  }" +
           "}");
    checkQuickFix(CommonQuickFixBundle.message("fix.replace.x.with.y", "equals()", "=="),
                 "class X {" +
                 "  boolean m(Class c1, Class c2) {" +
                 "    return c1 == c2;" +
                 "  }" +
                 "}");
  }

  public void testClassObjects() {
    doTest("class X {" +
           "  boolean m(Class c1, Class c2) {" +
           "    return !java.util.Objects./*'!equals()' can be replaced with '!='*//*_*/equals/**/(c1, c2);" +
           "  }" +
           "}");
    checkQuickFix(CommonQuickFixBundle.message("fix.replace.x.with.y", "!equals()", "!="),
                  "class X {" +
                  "  boolean m(Class c1, Class c2) {" +
                  "    return c1 != c2;" +
                  "  }" +
                  "}");
  }

  public void testObject() {
    doTest("class X {" +
           "  boolean m(Object o1, Object o2) {" +
           "    return o1.equals(o2);" +
           "  }" +
           "}");
  }

  public void testEnum() {
    // should not warn, this is reported by the "'equals()' called on Enum value" inspection
    doTest("class X {" +
           "  boolean m(E e1, E e2) {" +
           "    return e1.equals(e2);" +
           "  }" +
           "}" +
           "enum E {" +
           "  A,B,C" +
           "}");
  }

  public void testString() {
    doTest("class X {" +
           "  boolean isRighteous(String a) {" +
           "    return a.equals(\"righteous\");" +
           "  }" +
           "}");
  }

  public void testSingleton() {
    doTest("""
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

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new ObjectEqualsCanBeEqualityInspection();
  }
}