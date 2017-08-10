/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.java.codeInsight.intention;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;

public class AddExplicitTypeArgumentsIntentionTest extends JavaCodeInsightFixtureTestCase {

  public void testNoStaticQualifier() {
    doTest("class Test {\n" +
           "    static {\n" +
           "        String s = fo<caret>o(\"\");\n" +
           "    }\n" +
           "    static <T> T foo(T t) {\n" +
           "        return t;\n" +
           "    }\n" +
           "}",
           "class Test {\n" +
           "    static {\n" +
           "        String s = Test.<String>foo(\"\");\n" +
           "    }\n" +
           "    static <T> T foo(T t) {\n" +
           "        return t;\n" +
           "    }\n" +
           "}");
  }
  
  public void testNoThisQualifier() {
    doTest("class Test {\n" +
           "    {\n" +
           "        String s = fo<caret>o(\"\");\n" +
           "    }\n" +
           "    <T> T foo(T t) {\n" +
           "        return t;\n" +
           "    }\n" +
           "}",
           "class Test {\n" +
           "    {\n" +
           "        String s = this.<String>foo(\"\");\n" +
           "    }\n" +
           "    <T> T foo(T t) {\n" +
           "        return t;\n" +
           "    }\n" +
           "}");
  }

  public void testInferredCapturedWildcard() {
    doTest("class Test {\n" +
           "    static void m(java.util.List<? extends String> l) {\n" +
           "        fo<caret>o(l.get(0));\n" +
           "    }\n" +
           "    static <T> void foo(T t) {\n" +
           "    }\n" +
           "}",
           "class Test {\n" +
           "    static void m(java.util.List<? extends String> l) {\n" +
           "        Test.<String>foo(l.get(0));\n" +
           "    }\n" +
           "    static <T> void foo(T t) {\n" +
           "    }\n" +
           "}");
  }

  public void testNotAvailableWhenRawTypeInferred() {
    myFixture.configureByText("a.java", "import java.util.List;\n" +
                                        "class Foo {\n" +
                                        "    <T> List<T> getList() {return null;}\n" +
                                        "    {\n" +
                                        "        List l;\n" +
                                        "        l = get<caret>List();\n" +
                                        "    }\n" +
                                        "}");
    final IntentionAction intentionAction = myFixture.getAvailableIntention(CodeInsightBundle.message("intention.add.explicit.type.arguments.family"));
    assertNull(intentionAction);
  }

  private void doTest(String beforeText, String afterText) {
    myFixture.configureByText("a.java", beforeText);
    final IntentionAction intentionAction = myFixture.findSingleIntention(CodeInsightBundle.message("intention.add.explicit.type.arguments.family"));
    assertNotNull(intentionAction);
    myFixture.launchAction(intentionAction);
    myFixture.checkResult(afterText);
  }
}
