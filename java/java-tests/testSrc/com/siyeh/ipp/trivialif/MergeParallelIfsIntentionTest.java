// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ipp.trivialif;

import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ipp.IPPFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ipp.IPPFixtureKt.ippFixture;

/**
 * @author Bas Leijdekkers
 */
@TestApplication
public class MergeParallelIfsIntentionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<IPPFixture> ipp = ippFixture(projectFixture);

  @Test
  public void testCommentsAreKept() {
    ipp.get().doTest("""
             class C {
                 void m(boolean b) {
                     if/*_Merge 'if's*/ (b) //simple end comment
                             {
                                     System.out.println(2); //1
                 }
                     if (b) {//2
                         System.out.println(/*3*/1);//4
                     }//5
                 }
             }""",

           """
             class C {
                 void m(boolean b) {
                     //simple end comment
                     if (b) {
                         System.out.println(2); //1
                         //2
                         System.out.println(/*3*/1);//4
                     }
                 }
             }""");
  }
}
