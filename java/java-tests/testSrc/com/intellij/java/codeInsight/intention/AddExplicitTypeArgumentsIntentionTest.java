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
    doTest("""
             class Test {
                 static {
                     String s = fo<caret>o("");
                 }
                 static <T> T foo(T t) {
                     return t;
                 }
             }""",
           """
             class Test {
                 static {
                     String s = Test.<String>foo("");
                 }
                 static <T> T foo(T t) {
                     return t;
                 }
             }""");
  }
  
  public void testNoThisQualifier() {
    doTest("""
             class Test {
                 {
                     String s = fo<caret>o("");
                 }
                 <T> T foo(T t) {
                     return t;
                 }
             }""",
           """
             class Test {
                 {
                     String s = this.<String>foo("");
                 }
                 <T> T foo(T t) {
                     return t;
                 }
             }""");
  }

  public void testContextClass() {
    doTest("""
             import java.util.Comparator;abstract class UserSession {
                 private static final Comparator<UserSession> USER_SESSION_COMPARATOR = Comparator
                         .comp<caret>aring(new java.util.function.Function<UserSession, String>() {
                                            @Override
                                            public String apply(UserSession userSession) {
                                               return userSession.toString();
                                            }});
             }""",
           """
             import java.util.Comparator;
             import java.util.function.Function;

             abstract class UserSession {
                 private static final Comparator<UserSession> USER_SESSION_COMPARATOR = Comparator
                         .<UserSession, String>comparing(new Function<UserSession, String>() {
                             @Override
                             public String apply(UserSession userSession) {
                                 return userSession.toString();
                             }
                         });
             }""");
  }

  public void testInferredCapturedWildcard() {
    doTest("""
             class Test {
                 static void m(java.util.List<? extends String> l) {
                     fo<caret>o(l.get(0));
                 }
                 static <T> void foo(T t) {
                 }
             }""",
           """
             class Test {
                 static void m(java.util.List<? extends String> l) {
                     Test.<String>foo(l.get(0));
                 }
                 static <T> void foo(T t) {
                 }
             }""");
  }

  public void testNotAvailableWhenRawTypeInferred() {
    myFixture.configureByText("a.java", """
      import java.util.List;
      class Foo {
          <T> List<T> getList() {return null;}
          {
              List l;
              l = get<caret>List();
          }
      }""");
    final IntentionAction intentionAction = myFixture.getAvailableIntention(JavaBundle.message("intention.add.explicit.type.arguments.family"));
    assertNull(intentionAction);
  }

  public void testNotAvailableWhenWildcardInferred() {
    myFixture.configureByText("a.java", """
      import java.util.stream.*;

      public class JDbQueryElement {

          void m(final Stream<String> stringStream) {
              stringStream.c<caret>ollect(Collectors.joining(", "));
          }

      }""");
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
