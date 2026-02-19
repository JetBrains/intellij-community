// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

public final class ModifierFixtureTest extends LightJavaCodeInsightFixtureTestCase {
  
  public void testUnreachableFunctionalTypeFromLambda() {
    myFixture.addClass("""
                         package one;
                         public interface Supplier<T> {
                             T get();
                         }
                         """);
    myFixture.addClass("""
                         package one;
                         class M1 {} // note package-private
                         """);
    myFixture.addClass("""
                         package one;
                         
                         public class M2 {
                             public static void test(Supplier<M1> supplier) {
                                 System.out.println(supplier.get());
                             }
                         
                             public static M1Sub create() {
                                 return new M1Sub();
                             }
                         
                             public static class M1Sub extends M1 {
                         
                             }
                         }
                         """);
    myFixture.configureByText("M3.java", """
      package two;
      
      import one.*;
      
      public class M3 {
          public static void main(String[] args) {
              M2.test(() -> M2.<caret>create());
          }
      }
      """);
    myFixture.findSingleIntention("Make 'M1' public");
  }

}
