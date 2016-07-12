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
package com.intellij.codeInsight.daemon;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.redundantCast.RedundantCastInspection;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

public class LightAdvHighlightingFixtureTest extends LightCodeInsightFixtureTestCase {

  public void testHidingOnDemandImports() throws Exception {
    myFixture.addClass("package foo; public class Foo {" +
                       "  public static String foo;" +
                       "}");

    myFixture.addClass("package foo; public class Bar {" +
                       "  public static void foo(String s) {}" +
                       "}");

    myFixture.configureByFile(getTestName(false) + ".java");
    myFixture.checkHighlighting(false, false, false);
  }

  public void testPackageNamedAsClassInDefaultPackage() throws Exception {
    myFixture.addClass("package test; public class A {}");
    final PsiClass aClass = myFixture.addClass("public class test {}");

    myFixture.configureByFile(getTestName(false) + ".java");
    myFixture.checkHighlighting();

    assertNull(ReferencesSearch.search(aClass).findFirst());
  }

  public void testBoundsPromotionWithCapturedWildcards() throws Exception {
    myFixture.addClass("package a; public interface Provider<A> {}");
    myFixture.addClass("package b; public interface Provider<B> {}");

    myFixture.configureByFile(getTestName(false) + ".java");
    myFixture.checkHighlighting();
  }

  public void testStaticImportCompoundWithInheritance() throws Exception {
    myFixture.addClass("package a; public interface A { static void foo(Object o){} static void foo(String str) {}}");

    myFixture.configureByFile(getTestName(false) + ".java");
    myFixture.checkHighlighting();
  }

  public void testSuppressedInGenerated() throws Exception {
    myFixture.addClass("package javax.annotation; public @interface Generated {}");
    final RedundantCastInspection inspection = new RedundantCastInspection();
    try {
      myFixture.enableInspections(inspection);
      myFixture.configureByFile(getTestName(false) + ".java");
      myFixture.checkHighlighting();
    }
    finally {
      myFixture.disableInspections(inspection);
    }

  }

  public void testUsageOfProtectedAnnotationOutsideAPackage() throws Exception {
    myFixture.addClass("package a;\n" +
                       "import java.lang.annotation.ElementType;\n" +
                       "import java.lang.annotation.Target;\n" +
                       "\n" +
                       "public class A {\n" +
                       "    @Target( { ElementType.METHOD, ElementType.TYPE } )\n" +
                       "    protected @interface Test{\n" +
                       "    }\n" +
                       "}");

    myFixture.configureByFile(getTestName(false) + ".java");
    myFixture.checkHighlighting();
  }

  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/daemonCodeAnalyzer/advFixture";
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }
}
