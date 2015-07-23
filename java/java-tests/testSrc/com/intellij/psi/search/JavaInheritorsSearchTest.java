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
package com.intellij.psi.search;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class JavaInheritorsSearchTest extends LightCodeInsightFixtureTestCase {
  public void testScope() {
    myFixture.addClass("package pack1;\npublic class Base { }");
    myFixture.addClass("package pack1;\npublic class Derived1 extends Base { }");
    myFixture.addClass("package pack2;\npublic class Derived2 extends pack1.Base { }");
    myFixture.addClass("package pack1;\npublic class Derived3 extends pack2.Derived2 { }");

    doTest("pack1.Base", "pack1", true, "pack1.Derived1", "pack1.Derived3");
  }

  public void testNoJdkScan() {
    doTest("javax.swing.JPanel", "", false);
  }

  public void testDuplicateClasses() {
    myFixture.addClass(
      "package x;\n" +
      "public class Test<T> {\n" +
      "  public void foo(T t) { }\n" +
      "}\n" +
      "class Goo<T> extends Test<T> {\n" +
      "  public void foo(T t) {}\n" +
      "}\n" +
      "class Goo { }\n" +
      "class Zoo extends Goo {\n" +
      "  public void foo(Object t) { }\n" +
      "}");

    doTest("x.Test", "", true, "x.Goo", "x.Zoo");
  }

  public void testIndirectInheritance() {
    myFixture.addClass(
      "interface I { }\n" +
      "class A implements I { }\n" +
      "class B extends A implements I { }");

    doTest("I", "", true, "A", "B");
  }

  private void doTest(String className, String packageScopeName, boolean deep, String... expected) {
    PsiClass aClass = myFixture.getJavaFacade().findClass(className);
    assertNotNull(aClass);

    SearchScope scope;
    if (packageScopeName != null) {
      PsiPackage aPackage = JavaPsiFacade.getInstance(myFixture.getProject()).findPackage(packageScopeName);
      assertNotNull(aPackage);
      scope = PackageScope.packageScope(aPackage, true).intersectWith(GlobalSearchScope.projectScope(myFixture.getProject()));
    }
    else {
      scope = GlobalSearchScope.projectScope(myFixture.getProject());
    }

    List<String> result = ProgressManager.getInstance().runProcess(
      () -> streamOf(ClassInheritorsSearch.search(aClass, scope, deep)).map(PsiClass::getQualifiedName).collect(Collectors.toList()),
      null
    );

    assertSameElements(result, expected);
  }

  private static <T> Stream<T> streamOf(Iterable<T> iterable) {
    return StreamSupport.stream(iterable.spliterator(), false);
  }
}
