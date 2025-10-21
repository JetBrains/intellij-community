// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.roots.ModuleSourceOrderEntry;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class MultipleModuleHighlightingTest extends JavaCodeInsightFixtureTestCase {
  public void testUseOriginalPlaceClasspathForReferenceTypeResolving() throws IOException {
    addTwoModules();

    myFixture.addFileToProject("mod1/Class2.java", """
      public class Class2 {
        public void m1() {}
        public void m2() {}
      }
      """);
    myFixture.addFileToProject("mod2/Class2.java", """
      public class Class2 {
        public void m1() {}
      }
      """);
    myFixture.addFileToProject("mod2/Class1.java", """
      public class Class1 {
        public Class2 getClass2() {}
        public Class2 class2Field;
      }
      """);

    myFixture.configureFromExistingVirtualFile(myFixture.addClass("""
                                                                    class Class3 {
                                                                      {
                                                                        new Class1().getClass2().m2();
                                                                        new Class1().class2Field.m2();
                                                                      }
                                                                    }
                                                                    """).getContainingFile().getVirtualFile());
    myFixture.checkHighlighting();
  }

  public void testMissedMethodInHierarchy() throws IOException {
    Module mod1 =
      PsiTestUtil.addModule(getProject(), JavaModuleType.getModuleType(), "mod1", myFixture.getTempDirFixture().findOrCreateDir("mod1"));
    Module mod2 =
      PsiTestUtil.addModule(getProject(), JavaModuleType.getModuleType(), "mod2", myFixture.getTempDirFixture().findOrCreateDir("mod2"));
    ModuleRootModificationUtil.addDependency(mod2, mod1);

    myFixture.addFileToProject("mod1/p/A.java", """
      package p;
      public class A {
         public void foo() { /* mod1 A */ }
      }
      """);
    myFixture.addFileToProject("mod1/p/B.java", """
      package p;
      public class B extends A {
         public void foo() { /* mod1 B */ }
      }
      """);
    myFixture.addFileToProject("mod1/p/C.java", """
      package p;
      public class C extends B {
         public void foo() { /* mod1 C */ }
      }
      """);
    myFixture.addFileToProject("mod2/p/A.java", """
      package p;
      public class A {
         public void foo() { /* mod2 A */ }
      }
      """);
    myFixture.addFileToProject("mod2/p/B.java", """
      package p;
      public class B extends A {
      }
      """);
    PsiFile file = myFixture.addFileToProject("mod2/p/D.java", """
      package p;
      public class D extends C {
         {
            super.foo();
         }
      }
      """);

    myFixture.configureFromExistingVirtualFile(PsiUtilCore.getVirtualFile(file));
    myFixture.checkHighlighting();
  }

  public void testClassQualifierWithInaccessibleSuper() throws IOException {
    Module mod1 =
      PsiTestUtil.addModule(getProject(), JavaModuleType.getModuleType(), "mod1", myFixture.getTempDirFixture().findOrCreateDir("mod1"));
    Module mod2 =
      PsiTestUtil.addModule(getProject(), JavaModuleType.getModuleType(), "mod2", myFixture.getTempDirFixture().findOrCreateDir("mod2"));
    ModuleRootModificationUtil.addDependency(mod1, getModule());
    ModuleRootModificationUtil.addDependency(mod2, mod1);
    myFixture.addClass("public class Class0 {}");

    myFixture.addFileToProject("mod1/Class1.java", """
      public class Class1 extends Class0 {
        public static Class1 create() {return null;}
      }
      """);

    myFixture.addFileToProject("mod2/Usage.java", """
      public class Usage {
        {
          <error descr="Cannot access Class0">Class1.create</error>();
        }
      }
      """);

    myFixture.configureFromTempProjectFile("mod2/Usage.java");
    myFixture.checkHighlighting();
  }

  public void testClassQualifierWithInaccessibleSuperUsedForConstantFieldAccess() throws IOException {
    Module mod1 =
      PsiTestUtil.addModule(getProject(), JavaModuleType.getModuleType(), "mod1", myFixture.getTempDirFixture().findOrCreateDir("mod1"));
    Module mod2 =
      PsiTestUtil.addModule(getProject(), JavaModuleType.getModuleType(), "mod2", myFixture.getTempDirFixture().findOrCreateDir("mod2"));
    ModuleRootModificationUtil.addDependency(mod1, getModule());
    ModuleRootModificationUtil.addDependency(mod2, mod1);
    myFixture.addClass("public class Class0 {}");

    myFixture.addFileToProject("mod1/Class1.java", """
      public class Class1 extends Class0 {
        public static int FOO = 1;
      }
      """);

    myFixture.addFileToProject("mod2/Usage.java", """
      public class Usage {
        {
          int a = Class1.FOO;
        }
      }
      """);

    myFixture.configureFromTempProjectFile("mod2/Usage.java");
    myFixture.checkHighlighting();
  }

  public void testClassQualifierWithInaccessibleSuperOfReturnType() throws IOException {
    Module mod1 =
      PsiTestUtil.addModule(getProject(), JavaModuleType.getModuleType(), "mod1", myFixture.getTempDirFixture().findOrCreateDir("mod1"));
    Module mod2 =
      PsiTestUtil.addModule(getProject(), JavaModuleType.getModuleType(), "mod2", myFixture.getTempDirFixture().findOrCreateDir("mod2"));
    ModuleRootModificationUtil.addDependency(mod1, getModule());
    ModuleRootModificationUtil.addDependency(mod2, mod1);
    myFixture.addClass("public class Class0 {}");

    myFixture.addFileToProject("mod1/Class1.java", """
      public class Class1 extends Class0 {}
      """);
    myFixture.addFileToProject("mod1/Factory.java", """
      public class Factory {
        public static Class1 create() {return null;}
      }
      """);

    myFixture.addFileToProject("mod2/Usage.java", """
      public class Usage {
        {
          Factory.create();
        }
      }
      """);

    myFixture.configureFromTempProjectFile("mod2/Usage.java");
    myFixture.checkHighlighting();
  }

  public void testUseOriginalPlaceClasspathForNewExpressionTypeResolving() throws IOException {
    addTwoModules();

    myFixture.addFileToProject("mod1/A.java", """
      public class A {
        public void m1();
      }
      """);

    myFixture.addFileToProject("mod2/A.java", """
      public class A {
        public void m2() {}
      }
      """);
    myFixture.addFileToProject("mod2/B.java", """
      public class B extends A {
      }
      """);

    myFixture.configureFromExistingVirtualFile(myFixture.addClass("""
                                                                    class Class3 {
                                                                      {
                                                                        new B().m1();
                                                                        new B().<error descr="Cannot resolve method 'm2' in 'B'">m2</error>();
                                                                      }
                                                                    }
                                                                    """).getContainingFile().getVirtualFile());
    myFixture.checkHighlighting();
  }

  private void addTwoModules() throws IOException {
    Module mod1 =
      PsiTestUtil.addModule(getProject(), JavaModuleType.getModuleType(), "mod1", myFixture.getTempDirFixture().findOrCreateDir("mod1"));
    Module mod2 =
      PsiTestUtil.addModule(getProject(), JavaModuleType.getModuleType(), "mod2", myFixture.getTempDirFixture().findOrCreateDir("mod2"));
    ModuleRootModificationUtil.addDependency(getModule(), mod1);
    ModuleRootModificationUtil.addDependency(getModule(), mod2);
  }

  private void addModuleChain() throws IOException {
    Module mod1 =
      PsiTestUtil.addModule(getProject(), JavaModuleType.getModuleType(), "mod1", myFixture.getTempDirFixture().findOrCreateDir("mod1"));
    Module mod2 =
      PsiTestUtil.addModule(getProject(), JavaModuleType.getModuleType(), "mod2", myFixture.getTempDirFixture().findOrCreateDir("mod2"));
    ModuleRootModificationUtil.addDependency(getModule(), mod1);
    ModuleRootModificationUtil.addDependency(mod1, mod2);
  }
  
  public void testIndirectClassInaccessible() throws IOException {
    addModuleChain();
    myFixture.addFileToProject("mod2/p/A.java", """
      package p;
      public class A {}
      """);
    myFixture.addFileToProject("mod1/p/B.java", """
      package p;
      public class B extends A {}
      """);
    myFixture.configureByText("C.java", """
      package p;
      <error descr="Cannot access p.A">class C <caret>extends B</error> {}
      """);
    myFixture.checkHighlighting();
    IntentionAction intention = myFixture.findSingleIntention("Add dependency on module 'mod2'");
    IntentionPreviewInfo preview = intention.generatePreview(getProject(), myFixture.getEditor(), myFixture.getFile());
    IntentionPreviewInfo.Html html = assertInstanceOf(preview, IntentionPreviewInfo.Html.class);
    // The current module name is a sequence of digits
    String text = html.content().toString().replaceFirst("&#39;\\d+&#39;", "&#39;module_name&#39;");
    assertEquals("Adds module &#39;mod2&#39; to the dependencies of module &#39;module_name&#39; and imports unresolved classes if necessary", text);
  }

  public void testOverridingJdkExceptions() throws IOException {
    final Module dep =
      PsiTestUtil.addModule(getProject(), JavaModuleType.getModuleType(), "dep", myFixture.getTempDirFixture().findOrCreateDir("dep"));
    ModuleRootModificationUtil.setModuleSdk(dep, ModuleRootManager.getInstance(getModule()).getSdk());
    ModuleRootModificationUtil.updateModel(getModule(), model -> {
      model.addModuleOrderEntry(dep);

      OrderEntry @NotNull [] entries = model.getOrderEntries();
      ModuleSourceOrderEntry srcEntry = ContainerUtil.findInstance(entries, ModuleSourceOrderEntry.class);
      assertNotNull(srcEntry);

      model.rearrangeOrderEntries(ArrayUtil.prepend(srcEntry, ArrayUtil.remove(entries, srcEntry)));
    });

    myFixture.addFileToProject("java/lang/IllegalArgumentException.java", """
      package java.lang;

      public class IllegalArgumentException extends Exception { }
      """);

    myFixture.addFileToProject("dep/foo/Foo.java", """
      package foo;

      public class Foo {
        public static void libraryMethod() throws IllegalArgumentException {}
      }
      """);

    myFixture.configureFromExistingVirtualFile(myFixture.addFileToProject("Bar.java", """
      class Bar {
        void caught() {
          try {
            foo.Foo.libraryMethod();
          } catch (IllegalArgumentException e) {}
        }
  
        void uncaught() {
          foo.Foo.<error descr="Unhandled exception: java.lang.IllegalArgumentException">libraryMethod</error>();
        }
      }
      """).getVirtualFile());
    myFixture.checkHighlighting();
  }
}
