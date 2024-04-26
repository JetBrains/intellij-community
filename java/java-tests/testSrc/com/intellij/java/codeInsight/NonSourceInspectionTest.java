// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight;

import com.intellij.codeInspection.GlobalInspectionContext;
import com.intellij.codeInspection.InspectionEngine;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.codeInspection.uncheckedWarnings.UncheckedWarningLocalInspection;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import org.intellij.lang.annotations.Language;

import java.io.IOException;

public class NonSourceInspectionTest extends JavaCodeInsightFixtureTestCase {
  public void testInspectionOutsideSourceRoot() throws IOException {
    PsiTestUtil.removeAllRoots(getModule(), ModuleRootManager.getInstance(getModule()).getSdk());
    PsiTestUtil.addSourceRoot(getModule(), myFixture.getTempDirFixture().findOrCreateDir("src"));

    @Language("JAVA") String generic = """
      package foo;
      public interface GenericQuery<T> {
          public T execute();
      }
      """;
    myFixture.addFileToProject("src/foo/GenericQuery.java", generic);

    @Language("JAVA") String some = """
      import foo.GenericQuery;
      import java.util.Collection;

      class SomeClass {
        Collection<User> foo(GenericQuery<Collection<User>> query) {
          return query.execute();
        }
        class User {}
      }


      """;
    PsiFile file = myFixture.addFileToProject("SomeClass.java", some);

    LocalInspectionToolWrapper wrapper = new LocalInspectionToolWrapper(new UncheckedWarningLocalInspection());
    GlobalInspectionContext context = InspectionManager.getInstance(getProject()).createNewGlobalContext();
    assertEmpty(InspectionEngine.runInspectionOnFile(file, wrapper, context));
  }

  public void testResolveSuperConstructorReference() throws IOException {
    PsiTestUtil.removeAllRoots(getModule(), ModuleRootManager.getInstance(getModule()).getSdk());
    PsiTestUtil.addSourceRoot(getModule(), myFixture.getTempDirFixture().findOrCreateDir("src"));

    @Language("JAVA") String foo = """
      class Foo<T> {
          public Foo(T x) {
          }
      }
      """;
    myFixture.addFileToProject("src/Foo.java", foo);

    @Language("JAVA") String foo2 = """
      class Foo<T> {
          public Foo(T x) {
          }
      }

      class Bar extends Foo<String> {
          public Bar() {
              sup<caret>er("a");
          }
      }
      """;
    myFixture.configureByText("Foo.java", foo2);

    assertTrue(myFixture.getFile().isPhysical());
    assertEquals("Foo", assertInstanceOf(myFixture.getElementAtCaret(), PsiMethod.class).getName());
  }
}
