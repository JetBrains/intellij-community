// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.generate.inspection;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.java.generate.GenerateToStringContext;

/**
 * @author Bas Leijdekkers
 */
public class FieldNotUsedInToStringInspectionTest extends LightJavaCodeInsightFixtureTestCase {

  public void testBasic() {
    doTest("""
             class X {
               private int <warning descr="Field 'i' is not used in 'toString()' method">i</warning> = 0;
               public String toString() {
                 return null;
               }
             }""");
  }

  public void testGetterUsed() {
    doTest("""
              class ToStringTest3 {

                 int number;

                 public int getNumber() {
                     return number;
                 }

                 @Override
                 public String toString() {
                     final StringBuilder sb = new StringBuilder();
                     sb.append("ToStringTest3");
                     sb.append("{number=").append(getNumber());
                     sb.append('}');
                     return sb.toString();
                 }
             }""");
  }

  public void testReflectionUsed() {
    myFixture.addClass("""
                         package java.util;
                         public class Objects {
                           public static String toString(Object object) {
                             return null;
                           }
                         }""");
    doTest("""
             import java.util.Objects;
             class X {
               private int i = 0;
             
               public String toString() {
                 return Objects.toString(this);
               }
             }""");
  }

  public void testMethods() {
    GenerateToStringContext.getConfig().setEnableMethods(true);
    doTest("""
             class Temp {
                 private int field = 0;
                
                 /** Returns whether the work completed */
                 public boolean doSomeWork() { field = 1; return true; }
             
                 @Override
                 public String toString() {
                     return "Temp{" +
                            "field=" + field +
                            '}';
                 }
             }
             """);
  }

  private void doTest(@Language("JAVA") @NonNls String text) {
    myFixture.configureByText("X.java", text);
    myFixture.enableInspections(new FieldNotUsedInToStringInspection());
    myFixture.testHighlighting(true, false, false);
  }
}
