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

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.java.JavaBundle;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;

public class AddExplicitTypeArgumentsIntentionTest extends JavaCodeInsightFixtureTestCase {

  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder moduleBuilder) throws Exception {
    super.tuneFixture(moduleBuilder);
    moduleBuilder.addJdk(IdeaTestUtil.getMockJdk18Path().getPath());
  }

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

  public void testContextClass() {
    doTest("import java.util.Comparator;" +
           "abstract class UserSession {\n" +
           "    private static final Comparator<UserSession> USER_SESSION_COMPARATOR = Comparator\n" +
           "            .comp<caret>aring(new java.util.function.Function<UserSession, String>() {\n" +
           "                               @Override\n" +
           "                               public String apply(UserSession userSession) {\n" +
           "                                  return userSession.toString();\n" +
           "                               }});\n" +
           "}",
           "import java.util.Comparator;\n" +
           "import java.util.function.Function;\n" +
           "\n" +
           "abstract class UserSession {\n" +
           "    private static final Comparator<UserSession> USER_SESSION_COMPARATOR = Comparator\n" +
           "            .<UserSession, String>comparing(new Function<UserSession, String>() {\n" +
           "                @Override\n" +
           "                public String apply(UserSession userSession) {\n" +
           "                    return userSession.toString();\n" +
           "                }\n" +
           "            });\n" +
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
    final IntentionAction intentionAction = myFixture.getAvailableIntention(JavaBundle.message("intention.add.explicit.type.arguments.family"));
    assertNull(intentionAction);
  }

  public void testNotAvailableWhenWildcardInferred() {
    myFixture.configureByText("a.java", "import java.util.stream.*;\n" +
                                        "\n" +
                                        "public class JDbQueryElement {\n" +
                                        "\n" +
                                        "    void m(final Stream<String> stringStream) {\n" +
                                        "        stringStream.c<caret>ollect(Collectors.joining(\", \"));\n" +
                                        "    }\n" +
                                        "\n" +
                                        "}");
    final IntentionAction intentionAction = myFixture.getAvailableIntention(JavaBundle.message("intention.add.explicit.type.arguments.family"));
    assertNull(intentionAction);
  }

  private void doTest(String beforeText, String afterText) {
    myFixture.configureByText("a.java", beforeText);
    final IntentionAction intentionAction = myFixture.findSingleIntention(JavaBundle.message("intention.add.explicit.type.arguments.family"));
    assertNotNull(intentionAction);
    myFixture.launchAction(intentionAction);
    myFixture.checkResult(afterText);
  }
}
