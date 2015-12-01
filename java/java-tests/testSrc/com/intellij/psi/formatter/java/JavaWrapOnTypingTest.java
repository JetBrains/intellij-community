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
package com.intellij.psi.formatter.java;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;

public class JavaWrapOnTypingTest extends LightCodeInsightFixtureTestCase {


  public void testWrapInsideTags() {
    myFixture.configureByText(JavaFileType.INSTANCE,
      "public class Hw {\n"                                                                                                      +
      "\n"                                                                                                                       +
      "    /**\n"                                                                                                                +
      "     * @return index for the given relative path. Never {@code null}, but returned index may not exist (in which case,\n" +
      "     *\n"                                                                                                                 +
      "     * {@link Index#exists()} returns {@code false}). If the index do not exist, it may or may not be read-only (see <caret>)\n" +
      "     */\n"                                                                                                                +
      "    public static void test() {\n"                                                                                        +
      "    }\n"                                                                                                                  +
      "}\n");

    myFixture.getEditor().getSettings().setWrapWhenTypingReachesRightMargin(true);
    
    myFixture.type('{');
    myFixture.type('@');
    
    myFixture.checkResult("public class Hw {\n"                                                                                  +
      "\n"                                                                                                                       +
      "    /**\n"                                                                                                                +
      "     * @return index for the given relative path. Never {@code null}, but returned index may not exist (in which case,\n" +
      "     *\n"                                                                                                                 +
      "     * {@link Index#exists()} returns {@code false}). If the index do not exist, it may or may not be read-only (see \n" +
      "     * {@<caret>})\n" +
      "     */\n"                                                                                                                +
      "    public static void test() {\n"                                                                                        +
      "    }\n"                                                                                                                  +
      "}\n");
  }
}
