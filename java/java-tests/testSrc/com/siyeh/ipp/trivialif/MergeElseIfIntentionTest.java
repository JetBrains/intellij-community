/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.siyeh.ipp.trivialif;

import com.siyeh.ipp.IPPTestCase;

/**
 * @author Bas Leijdekkers
 */
public class MergeElseIfIntentionTest extends IPPTestCase {

  public void testCommentsAreKept() {
    doTest("""
             class X {
                 void test(boolean foo, boolean bar) {
                     if (foo) { //asdf

                     } else/*_Merge 'else if'*/ {
                         // blubb
                         // blabb
                         if (bar) { // asdf3

                         } // other
                         // bla
                     } // asdf1
                     // asdf4
                 }
             }
             """,

           """
             class X {
                 void test(boolean foo, boolean bar) {
                     if (foo) { //asdf

                     } else
                         // blubb
                         // blabb
                         if (bar) { // asdf3

                         } // other
             // bla
             // asdf1
                     // asdf4
                 }
             }
             """);
  }
}
