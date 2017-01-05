/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

package com.intellij.codeInsight.intention;

import com.siyeh.ipp.IPPTestCase;

/**
 * @author Bas Leijdekkers
 */
public class FlipCommaIntentionTest extends IPPTestCase {

  public void test() throws Exception {
    doTest("class C {\n" +
           "    int a,/*_Flip ','*/ b;" +
           "}",
           "class C {\n" +
           "    int b, a;\n" +
           "}");
  }

  public void testUnavailableForDangling() throws Exception {
    myFixture.configureByText("a.java", "class C {\n" +
                                        "    int a[] = new int[]{1,2,<caret>};" +
                                        "}");
    assertEmpty(myFixture.filterAvailableIntentions("Flip"));
  }
}
