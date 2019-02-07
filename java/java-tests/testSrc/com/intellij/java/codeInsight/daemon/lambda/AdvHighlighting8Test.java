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
package com.intellij.java.codeInsight.daemon.lambda;

import com.intellij.JavaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class AdvHighlighting8Test extends LightCodeInsightFixtureTestCase {
  @NonNls private static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/lambda/advHighlighting8";

  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + BASE_PATH;
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }

  public void testProtectedVariable() {
    myFixture.addClass("package p1;\n" +
                       "public class A {\n" +
                       "  protected String myFoo = \"A\";\n" +
                       "}");
    doTest();
  }

  public void testIDEA67842() {
    doTest();
  }

  public void testUnrelatedConcreteInConstructors() {
    myFixture.addClass("package p;\n" +
                       "import java.util.List;\n" +
                       "\n" +
                       "public class A {\n" +
                       "  public A(List l) {\n" +
                       "  }\n" +
                       "}");
    myFixture.addClass("import java.util.List;\n" +
                       "public class A<T> extends p.A {\n" +
                       "  public A(List<T> l) {\n" +
                       "    super(l);\n" +
                       "  }\n" +
                       "}");
    doTest();
  }

  public void testPackageLocalMethod() {
    myFixture.addClass("package foo;\n" +
                       "public abstract class A {\n" +
                       "  abstract void foo();\n" +
                       "}");
    myFixture.addClass("package foo.bar;\n" +
                       "import foo.A;\n" +
                       "abstract class B extends A {}");
    doTest();
  }

  private void doTest() {
    myFixture.testHighlighting(false, false, false, getTestName(false) + ".java");
  }
}
