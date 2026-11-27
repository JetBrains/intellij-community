// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.psi.search;

import com.intellij.concurrency.JobScheduler;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.extensions.LoadingOrder;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.StandardProgressIndicatorBase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassOwner;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.ResolveScopeProvider;
import com.intellij.psi.impl.compiled.ClsClassImpl;
import com.intellij.psi.impl.search.RelaxedDirectInheritorChecker;
import com.intellij.psi.impl.source.PsiClassImpl;
import com.intellij.psi.search.CodeInsightContextAwareSearchScopes;
import com.intellij.psi.search.CodeInsightContextInfo;
import com.intellij.psi.search.DelegatingGlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.search.searches.DirectClassInheritorsSearch;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.CommonProcessors;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.intellij.testFramework.JavaCodeInsightFixtureKt.javaModuleCodeInsightFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

@TestApplication
public class ClassInheritorsTest {
  private final TestFixture<JavaCodeInsightTestFixture> codeInsightFixture = javaModuleCodeInsightFixture(PathManager.getHomePath());

  @Test
  public void testClsAndSourcesDoNotMixUp() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiClass numberClass = ReadAction.computeBlocking(() -> myFixture.getJavaFacade().findClass("java.lang.Number", GlobalSearchScope.allScope(myFixture.getProject())));
    assertInstanceOf(ClsClassImpl.class, numberClass, String.valueOf(numberClass));
    PsiClass n2 = ReadAction.computeBlocking(() -> (PsiClass)numberClass.getNavigationElement());
    assertInstanceOf(PsiClassImpl.class, n2, String.valueOf(n2));
    Collection<PsiClass> subClasses =
      ReadAction.computeBlocking(() -> DirectClassInheritorsSearch.search(n2, GlobalSearchScope.allScope(myFixture.getProject())).findAll());
    List<String> fqn = ReadAction.computeBlocking(() -> subClasses.stream().map(PsiClass::getQualifiedName).sorted().toList());
    assertEquals(fqn.size(), new HashSet<>(fqn).size(), fqn.toString()); // no dups mean no Cls/Psi mixed

    Collection<PsiClass> allSubClasses =
      ReadAction.computeBlocking(() -> ClassInheritorsSearch.search(n2, GlobalSearchScope.allScope(myFixture.getProject()), true).findAll());
    List<String> allFqn = ReadAction.computeBlocking(() -> allSubClasses.stream().map(PsiClass::getQualifiedName).sorted().toList());
    assertEquals(allFqn.size(), new HashSet<>(allFqn).size(), allFqn.toString());
  }

  @Test
  public void testStressInPresenceOfPCEs() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    int N = 1000;
    PsiClassOwner file0 = (PsiJavaFile)myFixture.addFileToProject("C0.java", "class C0 { }");
    for (int i=1;i<N ;i++) {
      int extI = i - 1 - (i - 1) % 10; // 10 inheritors
      myFixture.addClass("class C" + i + " extends C" + extI + " { }");
    }
    // no write action can go through while we test
    ReadAction.runBlocking(() -> {
      PsiClass class0 = file0.getClasses()[0];

      int delayToCancel = 100;
      for (int i=0;i<1000;i++) {
        //System.out.println("i = " + i+ "; delayToCancel="+delayToCancel);
        StandardProgressIndicatorBase progress = new StandardProgressIndicatorBase();
        JobScheduler.getScheduler().schedule(progress::cancel, delayToCancel, TimeUnit.MILLISECONDS);
        try {
          Collections.nCopies(Runtime.getRuntime().availableProcessors(), "").stream().parallel().forEach(_ -> {
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
    });
  }

  @Test
  public void testPrivateClassCanHaveInheritorsInAnotherFile() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
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
    assertEquals(5, ReadAction.computeBlocking(() -> ClassInheritorsSearch.search(myFixture.findClass("Test.A")).findAll()).size());
    assertEquals(4, ReadAction.computeBlocking(() -> ClassInheritorsSearch.search(myFixture.findClass("Test.B")).findAll()).size());
  }

  @Test
  public void testPackageLocalClassCanHaveInheritorsInAnotherPackage() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
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
    assertEquals(5, ReadAction.computeBlocking(() -> ClassInheritorsSearch.search(myFixture.findClass("one.Test.A")).findAll()).size());
    assertEquals(4, ReadAction.computeBlocking(() -> ClassInheritorsSearch.search(myFixture.findClass("one.Test.B")).findAll()).size());
  }
  
  @Test
  public void testClassExposedViaContainingClassSubclass() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
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
    assertEquals(1, ReadAction.computeBlocking(() -> ClassInheritorsSearch.search(myFixture.findClass("one.OuterSuper.Inner")).findAll()).size());
  }

  @Test
  public void testInheritorsInAnotherModuleWithNoDirectDependency() throws IOException {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addFileToProject("A.java", "class A {}");
    myFixture.addFileToProject("mod1/B.java", "class B extends A {}");
    myFixture.addFileToProject("mod2/C.java", "class C extends B {}");

    Module mod1 = PsiTestUtil.addModule(myFixture.getProject(), JavaModuleType.getModuleType(), "mod1", myFixture.getTempDirFixture().findOrCreateDir("mod1"));
    Module mod2 = PsiTestUtil.addModule(myFixture.getProject(), JavaModuleType.getModuleType(), "mod2", myFixture.getTempDirFixture().findOrCreateDir("mod2"));

    ModuleRootModificationUtil.addDependency(mod1, myFixture.getModule(), DependencyScope.COMPILE, true);
    ModuleRootModificationUtil.addDependency(mod2, mod1, DependencyScope.COMPILE, false);

    assertEquals(2, ReadAction.computeBlocking(() -> ClassInheritorsSearch.search(myFixture.findClass("A")).findAll()).size());
  }

  @Test
  public void testInheritorsInAnotherModuleWithProductionOnTestDependency() throws IOException {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addFileToProject("tests/B.java", "class B {}");
    myFixture.addFileToProject("mod2/C.java", "class C extends B {}");

    PsiTestUtil.addSourceRoot(myFixture.getModule(), myFixture.getTempDirFixture().findOrCreateDir("tests"), true);
    Module mod2 = PsiTestUtil.addModule(myFixture.getProject(), JavaModuleType.getModuleType(), "mod2", myFixture.getTempDirFixture().findOrCreateDir("mod2"));

    ModuleRootModificationUtil.updateModel(mod2, model ->
      model.addModuleOrderEntry(myFixture.getModule()).setProductionOnTestDependency(true));

    assertEquals(1, ReadAction.computeBlocking(() -> ClassInheritorsSearch.search(myFixture.findClass("B")).findAll()).size());
  }

  @Test
  public void testRelaxedCheckerChecksCancellationWhileScanningSameNamedClasses() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    for (int i = 0; i < 50; i++) {
      myFixture.addClass("package p" + i + "; public class Base { }");
    }
    PsiClass base = ReadAction.computeBlocking(() -> myFixture.findClass("p0.Base"));
    PsiClass inheritor = myFixture.addClass("package p0; public class Inheritor extends Base { }");

    StandardProgressIndicatorBase progress = new StandardProgressIndicatorBase();
    VirtualFile inheritorFile = inheritor.getContainingFile().getVirtualFile();
    // The resolve scope of the inheritor contains no class and cancels the progress at its first check, like a pending write action
    ResolveScopeProvider.EP_NAME.getPoint().registerExtension(new ResolveScopeProvider() {
      @Override
      public GlobalSearchScope getResolveScope(@NotNull VirtualFile file, @NotNull Project project) {
        if (!file.equals(inheritorFile)) return null;
        return new DelegatingGlobalSearchScope(GlobalSearchScope.EMPTY_SCOPE) {
          @Override
          public @NotNull CodeInsightContextInfo getCodeInsightContextInfo() {
            return CodeInsightContextAwareSearchScopes.NoContextInformation();
          }

          @Override
          public boolean contains(@NotNull VirtualFile f) {
            progress.cancel();
            return false;
          }
        };
      }
    }, LoadingOrder.FIRST, myFixture.getTestRootDisposable());

    ReadAction.runBlocking(() -> {
      RelaxedDirectInheritorChecker checker = new RelaxedDirectInheritorChecker(base);
      ProgressManager.getInstance().executeProcessUnderProgress(
        () -> assertThrows(ProcessCanceledException.class, () -> checker.checkInheritance(inheritor)), progress);
    });
  }

  @Test
  public void testSpaceBeforeSuperTypeGenerics() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addFileToProject("A.java", "interface A<T> {}");
    myFixture.addFileToProject("B.java", "class B implements A <T> {}");
    assertEquals(1, ReadAction.computeBlocking(() -> ClassInheritorsSearch.search(myFixture.findClass("A")).findAll()).size());
  }

  @Test
  public void testQueryingNonAnonymousInheritors() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiClass foo = myFixture.addClass("class Foo { { new Foo(){}; }; class Bar extends Foo {} }");
    GlobalSearchScope scope = GlobalSearchScope.allScope(myFixture.getProject());
    assertEquals(1, ReadAction.computeBlocking(() -> ClassInheritorsSearch.search(foo, scope, true, true, false).findAll()).size());
    assertEquals(2, ReadAction.computeBlocking(() -> ClassInheritorsSearch.search(foo, scope, true, true, true).findAll()).size());
  }
}
