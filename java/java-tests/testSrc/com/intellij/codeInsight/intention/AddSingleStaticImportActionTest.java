/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.JavaTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;

public class AddSingleStaticImportActionTest extends JavaCodeInsightFixtureTestCase {

  public void testInaccessible() {
    myFixture.addClass("package foo; class Foo {public static void foo(){}}");
    myFixture.configureByFile(getTestName(false) + ".java");

    final IntentionAction intentionAction = myFixture.findSingleIntention("Add static import for 'impl.FooImpl.foo'");
    assertNotNull(intentionAction);
    myFixture.launchAction(intentionAction);
    myFixture.checkResultByFile(getTestName(false) + "_after.java");
  }

  public void testInsideParameterizedReference() {
    myFixture.addClass("package foo; " +
                       "public class Class1 {" +
                       "  public static class Inner1 {}\n" +
                       "  public static class Inner2<T> {}" +
                       "}");
    myFixture.configureByFile(getTestName(false) + ".java");

    final IntentionAction intentionAction = myFixture.findSingleIntention("Add import for 'foo.Class1.Inner2'");
    assertNotNull(intentionAction);
    myFixture.launchAction(intentionAction);
    myFixture.checkResultByFile(getTestName(false) + "_after.java");
  }

  public void testAllowStaticImportWhenAlreadyImported() {
    myFixture.addClass("package foo; " +
                       "public class Clazz {\n" +
                       "      public  enum Foo{\n" +
                       "        Const_1, Const_2\n" +
                       "    }\n" +
                       "}");
    myFixture.configureByFile(getTestName(false) + ".java");

    final IntentionAction intentionAction = myFixture.findSingleIntention("Add import for 'foo.Clazz.Foo'");
    assertNotNull(intentionAction);
    myFixture.launchAction(intentionAction);
    myFixture.checkResultByFile(getTestName(false) + "_after.java");
  }

  public void testInsideParameterizedReferenceInsideParameterizedReference() {
    myFixture.addClass("package foo; " +
                       "public class Class1 {" +
                       "  public static class Inner1 {}\n" +
                       "  public static class Inner2<T> {}" +
                       "}");
    myFixture.configureByFile(getTestName(false) + ".java");

    final IntentionAction intentionAction = myFixture.findSingleIntention("Add import for 'foo.Class1.Inner1'");
    assertNotNull(intentionAction);
    myFixture.launchAction(intentionAction);
    myFixture.checkResultByFile(getTestName(false) + "_after.java");
  }

 public void testDisabledInsideParameterizedReference() {
    myFixture.addClass("package foo; " +
                       "public class Class1 {" +
                       "  public static <T> T foo(){return null;}\n" +
                       "}");
    myFixture.configureByFile(getTestName(false) + ".java");

    final IntentionAction intentionAction = myFixture.getAvailableIntention("Add static import for 'foo.Class1.foo'");
    assertNull(intentionAction);
  }


  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/codeInsight/daemonCodeAnalyzer/quickFix/addSingleStaticImport";
  }
}
