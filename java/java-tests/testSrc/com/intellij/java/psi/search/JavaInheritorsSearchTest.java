// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi.search;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PackageScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class JavaInheritorsSearchTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testScope() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package pack1;\npublic class Base { }");
    myFixture.addClass("package pack1;\npublic class Derived1 extends Base { }");
    myFixture.addClass("package pack2;\npublic class Derived2 extends pack1.Base { }");
    myFixture.addClass("package pack1;\npublic class Derived3 extends pack2.Derived2 { }");

    doTest("pack1.Base", "pack1", true, "pack1.Derived1", "pack1.Derived3");
  }

  @Test
  public void testNoJdkScan() {
    doTest("javax.swing.JPanel", "", false);
  }

  @Test
  public void testDuplicateClasses() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass(
      """
        package x;
        public class Test<T> {
          public void foo(T t) { }
        }
        class Goo<T> extends Test<T> {
          public void foo(T t) {}
        }
        class Goo { }
        class Zoo extends Goo {
          public void foo(Object t) { }
        }""");

    doTest("x.Test", "", true, "x.Goo", "x.Zoo");
  }

  @Test
  public void testIndirectInheritance() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass(
      """
        interface I { }
        class A implements I { }
        class B extends A implements I { }""");

    doTest("I", "", true, "A", "B");
  }

  private void doTest(String className, @Nullable String packageScopeName, boolean deep, String... expected) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    ReadAction.runBlocking(() -> {
      SearchScope scope;
      if (packageScopeName != null) {
        PsiPackage aPackage = JavaPsiFacade.getInstance(myFixture.getProject()).findPackage(packageScopeName);
        assertNotNull(aPackage);
        scope = PackageScope.packageScope(aPackage, true).intersectWith(GlobalSearchScope.projectScope(myFixture.getProject()));
      }
      else {
        scope = GlobalSearchScope.projectScope(myFixture.getProject());
      }

      assertThat(getInheritorNames(className, deep, scope)).containsOnly(expected);
    });
  }

  private List<String> getInheritorNames(String className, boolean deep, SearchScope scope) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    return ReadAction.computeBlocking(() -> {
      PsiClass aClass = myFixture.getJavaFacade().findClass(className);
      assertNotNull(aClass);

      return ProgressManager.getInstance().runProcess(
        () -> streamOf(ClassInheritorsSearch.search(aClass, scope, deep).asIterable()).map(PsiClass::getQualifiedName).collect(Collectors.toList()),
        null
      );
    });
  }

  private static <T> Stream<T> streamOf(Iterable<T> iterable) {
    return StreamSupport.stream(iterable.spliterator(), false);
  }

  @Test
  public void testEnum() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("enum MyEnum {}");
    List<String> names = getInheritorNames(Enum.class.getName(), true, GlobalSearchScope.allScope(myFixture.getProject()));
    assertTrue(names.contains("MyEnum"), names.toString());
    assertTrue(names.contains(RetentionPolicy.class.getName()), names.toString());
  }

  @Test
  public void testAnnotation() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("@interface MyAnnotation {}");
    List<String> names = getInheritorNames(Annotation.class.getName(), true, GlobalSearchScope.allScope(myFixture.getProject()));
    assertTrue(names.contains("MyAnnotation"), names.toString());
    assertTrue(names.contains(Retention.class.getName()), names.toString());
  }

}
