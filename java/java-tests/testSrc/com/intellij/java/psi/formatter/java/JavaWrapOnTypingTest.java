// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.psi.formatter.java;

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

  public void testWrapAtLineWithParameterHints() {
    myFixture.configureByText(JavaFileType.INSTANCE,
                              "public class C {\n" +
                              "    void m(int a, int b) {}\n" +
                              "    void other() { m(1, 2<caret>); }\n" +
                              "}");
    myFixture.doHighlighting();
    myFixture.checkResultWithInlays("public class C {\n" +
                                    "    void m(int a, int b) {}\n" +
                                    "    void other() { m(<hint text=\"a:\"/>1, <hint text=\"b:\"/>2<caret>); }\n" +
                                    "}");

    myFixture.getEditor().getSettings().setWrapWhenTypingReachesRightMargin(true);
    myFixture.getEditor().getSettings().setRightMargin(30);

    myFixture.type(" ");
    myFixture.checkResultWithInlays("public class C {\n" +
                                    "    void m(int a, int b) {}\n" +
                                    "    void other() { m(<hint text=\"a:\"/>1, <hint text=\"b:\"/>2 <caret>); }\n" +
                                    "}");
    myFixture.type(" ");
    myFixture.checkResultWithInlays("public class C {\n" +
                                    "    void m(int a, int b) {}\n" +
                                    "    void other() { m(<hint text=\"a:\"/>1, <hint text=\"b:\"/>2  <caret>\n" +
                                    "    ); }\n" +
                                    "}");
  }
}
