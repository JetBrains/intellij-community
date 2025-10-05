// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.psi.search;

import com.intellij.concurrency.JobScheduler;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.StandardProgressIndicatorBase;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassOwner;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.impl.compiled.ClsClassImpl;
import com.intellij.psi.impl.source.PsiClassImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.search.searches.DirectClassInheritorsSearch;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.util.CommonProcessors;
import com.intellij.util.concurrency.ThreadingAssertions;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class ClassInheritorsTest extends JavaCodeInsightFixtureTestCase {

  public void testClsAndSourcesDoNotMixUp() {
    PsiClass numberClass = myFixture.getJavaFacade().findClass("java.lang.Number", GlobalSearchScope.allScope(getProject()));
    assertTrue(String.valueOf(numberClass), numberClass instanceof ClsClassImpl);
    PsiClass n2 = (PsiClass)numberClass.getNavigationElement();
    assertTrue(String.valueOf(n2), n2 instanceof PsiClassImpl);
    Collection<PsiClass> subClasses = DirectClassInheritorsSearch.search(n2, GlobalSearchScope.allScope(getProject())).findAll();
    List<String> fqn = subClasses.stream().map(PsiClass::getQualifiedName).sorted().toList();
    assertEquals(fqn.toString(), fqn.size(), new HashSet<>(fqn).size()); // no dups mean no Cls/Psi mixed

    Collection<PsiClass> allSubClasses = ClassInheritorsSearch.search(n2, GlobalSearchScope.allScope(getProject()), true).findAll();
    List<String> allFqn = allSubClasses.stream().map(PsiClass::getQualifiedName).sorted().toList();
    assertEquals(allFqn.toString(), allFqn.size(), new HashSet<>(allFqn).size());
  }

  public void testStressInPresenceOfPCEs() {
    ThreadingAssertions.assertEventDispatchThread(); // no write action can go through while we test
    int N = 1000;
    PsiClassOwner file0 = (PsiJavaFile)myFixture.addFileToProject("C0.java", "class C0 { }");
    for (int i=1;i<N ;i++) {
      int extI = i - 1 - (i - 1) % 10; // 10 inheritors
      myFixture.addClass("class C" + i + " extends C" + extI + " { }");
    }
    PsiClass class0 = file0.getClasses()[0];

    int delayToCancel = 100;
    for (int i=0;i<1000;i++) {
      //System.out.println("i = " + i+ "; delayToCancel="+delayToCancel);
      StandardProgressIndicatorBase progress = new StandardProgressIndicatorBase();
      JobScheduler.getScheduler().schedule(progress::cancel, delayToCancel, TimeUnit.MILLISECONDS);
      try {
        Collections.nCopies(Runtime.getRuntime().availableProcessors(), "").stream().parallel().forEach(__ -> {
          Collection<PsiClass> inheritors = Collections.synchronizedSet(new HashSet<>());
          ProgressManager.getInstance().executeProcessUnderProgress(()-> {
            boolean success = ClassInheritorsSearch.search(class0).forEach(new CommonProcessors.CollectProcessor<>(inheritors));
            if (N - 1 != inheritors.size() || !success) {
              assertEquals(N - 1, inheritors.size());
            }
          }, progress);
        });
        myFixture.getPsiManager().dropResolveCaches();
        //System.out.println("Iterated all");
        delayToCancel--;
      }
      catch (ProcessCanceledException e) {
        //System.out.println("e = " + e);
        delayToCancel++;
      }
    }
  }

  public void testPrivateClassCanHaveInheritorsInAnotherFile() {
    myFixture.addClass("""
                         public class Test {
                           public static class A { }
                           private static class B extends A { }
                           public static class C1 extends B { }
                           public static class C2 extends B { }
                         }""");
    myFixture.addClass("""
                         public class Test2 {
                           private static class D1 extends Test.C1 { }
                           private static class D2 extends Test.C2 { }
                         }""");
    assertSize(5, ClassInheritorsSearch.search(myFixture.findClass("Test.A")).findAll());
    assertSize(4, ClassInheritorsSearch.search(myFixture.findClass("Test.B")).findAll());
  }

  public void testPackageLocalClassCanHaveInheritorsInAnotherPackage() {
    myFixture.addClass("""
                         package one; public class Test {
                           public static class A { }
                           static class B extends A { }
                           public static class C1 extends B { }
                           public static class C2 extends B { }
                         }""");
    myFixture.addClass("""
                         package another; public class Test2 {
                           private static class D1 extends one.Test.C1 { }
                           private static class D2 extends one.Test.C2 { }
                         }""");
    assertSize(5, ClassInheritorsSearch.search(myFixture.findClass("one.Test.A")).findAll());
    assertSize(4, ClassInheritorsSearch.search(myFixture.findClass("one.Test.B")).findAll());
  }
  
  public void testClassExposedViaContainingClassSubclass() {
    myFixture.addClass("""
      package one;
      interface OuterSuper {
        interface Inner {}
      }
      """);
    myFixture.addClass("""
      package one;
      public interface Child extends OuterSuper {
      }
      """);
    myFixture.addClass("""
      package two;
      interface InnerChild extends one.Child.Inner {}
      """);
    assertSize(1, ClassInheritorsSearch.search(myFixture.findClass("one.OuterSuper.Inner")).findAll());
  }

  public void testInheritorsInAnotherModuleWithNoDirectDependency() throws IOException {
    myFixture.addFileToProject("A.java", "class A {}");
    myFixture.addFileToProject("mod1/B.java", "class B extends A {}");
    myFixture.addFileToProject("mod2/C.java", "class C extends B {}");

    Module mod1 = PsiTestUtil.addModule(getProject(), JavaModuleType.getModuleType(), "mod1", myFixture.getTempDirFixture().findOrCreateDir("mod1"));
    Module mod2 = PsiTestUtil.addModule(getProject(), JavaModuleType.getModuleType(), "mod2", myFixture.getTempDirFixture().findOrCreateDir("mod2"));

    ModuleRootModificationUtil.addDependency(mod1, getModule(), DependencyScope.COMPILE, true);
    ModuleRootModificationUtil.addDependency(mod2, mod1, DependencyScope.COMPILE, false);

    assertSize(2, ClassInheritorsSearch.search(myFixture.findClass("A")).findAll());
  }

  public void testInheritorsInAnotherModuleWithProductionOnTestDependency() throws IOException {
    myFixture.addFileToProject("tests/B.java", "class B {}");
    myFixture.addFileToProject("mod2/C.java", "class C extends B {}");

    PsiTestUtil.addSourceRoot(getModule(), myFixture.getTempDirFixture().findOrCreateDir("tests"), true);
    Module mod2 = PsiTestUtil.addModule(getProject(), JavaModuleType.getModuleType(), "mod2", myFixture.getTempDirFixture().findOrCreateDir("mod2"));

    ModuleRootModificationUtil.updateModel(mod2, model ->
      model.addModuleOrderEntry(getModule()).setProductionOnTestDependency(true));

    assertSize(1, ClassInheritorsSearch.search(myFixture.findClass("B")).findAll());
  }

  public void testSpaceBeforeSuperTypeGenerics() {
    myFixture.addFileToProject("A.java", "interface A<T> {}");
    myFixture.addFileToProject("B.java", "class B implements A <T> {}");
    assertSize(1, ClassInheritorsSearch.search(myFixture.findClass("A")).findAll());
  }

  public void testQueryingNonAnonymousInheritors() {
    PsiClass foo = myFixture.addClass("class Foo { { new Foo(){}; }; class Bar extends Foo {} }");
    GlobalSearchScope scope = GlobalSearchScope.allScope(getProject());
    assertSize(1, ClassInheritorsSearch.search(foo, scope, true, true, false).findAll());
    assertSize(2, ClassInheritorsSearch.search(foo, scope, true, true, true).findAll());
  }
}
