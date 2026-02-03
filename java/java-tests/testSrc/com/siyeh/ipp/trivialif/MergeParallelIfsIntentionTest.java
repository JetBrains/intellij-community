// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ipp.trivialif;

import com.siyeh.ipp.IPPTestCase;

/**
 * @author Bas Leijdekkers
 */
public class MergeParallelIfsIntentionTest extends IPPTestCase {

  public void testCommentsAreKept() {
    doTest("""
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
