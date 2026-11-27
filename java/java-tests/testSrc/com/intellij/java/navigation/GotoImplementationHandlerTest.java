// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.navigation;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.codeInsight.navigation.GotoTargetHandler;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.IndexingTestUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static com.intellij.testFramework.JavaCodeInsightFixtureKt.javaModuleCodeInsightFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class GotoImplementationHandlerTest {
  private final TestFixture<JavaCodeInsightTestFixture> codeInsightFixture = javaModuleCodeInsightFixture(PathManager.getHomePath());

  @Test
  public void testMultipleImplsFromAbstractCall() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    @Language("JAVA") @SuppressWarnings("ALL")
    String fileText = "public abstract class Hello {\n" +
                  "    abstract void foo();\n" +
                  "\n" +
                  "    class A {\n" +
                  "        {\n" +
                  "            fo<caret>o();\n" +
                  "        }\n" +
                  "    }\n" +
                  "    class Hello1 extends Hello {\n" +
                  "        void foo() {}\n" +
                  "    }\n" +
                  "    class Hello2 extends Hello {\n" +
                  "        void foo() {}\n" +
                  "    }\n" +
                  "}";
    PsiFile file = myFixture.addFileToProject("Foo.java", fileText);
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());

    final PsiElement[] impls = getTargets(file);
    assertEquals(2, impls.length);
  }

  @Test
  public void testFromIncompleteCode() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    @Language("JAVA") @SuppressWarnings("ALL")
    String fileText = "public abstract class Hello {\n" +
                      "    abstract void foo();\n" +
                      "\n" +
                      "    class A {\n" +
                      "        {\n" +
                      "            Hello<caret>\n" +
                      "        }\n" +
                      "    }\n" +
                      "    class Hello1 extends Hello {\n" +
                      "        void foo() {}\n" +
                      "    }\n" +
                      "}" +
                      "class Hello2 extends Hello {\n" +
                      "    void foo() {}\n" +
                      "}\n";
    PsiFile file = myFixture.addFileToProject("Foo.java", fileText);
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());

    final PsiElement[] impls = getTargets(file);
    assertEquals(2, impls.length);
  }

  @Test
  public void testShowSelfNonAbstract() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    //fails if groovy plugin is enabled: org.jetbrains.plugins.groovy.codeInsight.JavaClsMethodElementEvaluator
    @Language("JAVA") @SuppressWarnings("ALL")
    String fileText = "public class Hello {\n" +
                      "    void foo(){}\n" +
                      "\n" +
                      "    class A {\n" +
                      "        {\n" +
                      "            fo<caret>o();\n" +
                      "        }\n" +
                      "    }\n" +
                      "    class Hello1 extends Hello {\n" +
                      "        void foo() {}\n" +
                      "    }\n" +
                      "    class Hello2 extends Hello {\n" +
                      "        void foo() {}\n" +
                      "    }\n" +
                      "}";
    PsiFile file = myFixture.addFileToProject("Foo.java", fileText);
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());

    final PsiElement[] impls = getTargets(file);
    assertEquals(3, impls.length);
  }

  @Test
  public void testMultipleImplsFromStaticCall() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    @Language("JAVA") @SuppressWarnings("ALL")
    String fileText = "public abstract class Hello {\n" +
                      "    static void bar (){}\n" +
                      "    class Hello1 extends Hello {\n" +
                      "    }\n" +
                      "    class Hello2 extends Hello {\n" +
                      "    }\n" +
                      "class D {\n" +
                      "    {\n" +
                      "        He<caret>llo.bar();\n" +
                      "    }\n" +
                      "}}";
    PsiFile file = myFixture.addFileToProject("Foo.java", fileText);
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());

    final PsiElement[] impls = getTargets(file);
    assertEquals(2, impls.length);
  }

  @Test
  public void testFilterOutImpossibleVariants() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    @Language("JAVA") @SuppressWarnings("ALL")
    String fileText = "interface A {\n" +
                      "    void save();\n" +
                      "}\n" +
                      "interface B extends A {\n" +
                      "    void foo();\n" +
                      "}\n" +
                      "class X implements B {\n" +
                      "    public void foo() { }\n" +
                      "    public void save(){}\n" +
                      "}\n" +
                      "class Y implements A {\n" +
                      "    public void save(){}\n" +
                      "}\n" +
                      "class App {\n" +
                      "    private B b;\n" +
                      "    private void some() {\n" +
                      "        b.sa<caret>ve();\n" +
                      "    }\n" +
                      "}";
    PsiFile file = myFixture.addFileToProject("Foo.java", fileText);
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());

    final PsiElement[] impls = getTargets(file);
    ReadAction.runBlocking(() -> {
      assertEquals(1, impls.length);
      final PsiElement method = impls[0];
      assertInstanceOf(PsiMethod.class, method);
      final PsiClass aClass = ((PsiMethod)method).getContainingClass();
      assertNotNull(aClass);
      assertEquals("X", aClass.getName());
    });
  }

  @Test
  public void testImplicitInheritance() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    @Language("JAVA") @SuppressWarnings("ALL")
    String fileText = "interface PackContainer {\n" +
                      "    void foo();\n" +
                      "}\n" +
                      "interface PsiPackage extends PackContainer {}\n" +
                      "class PsiPackageBase implements PackContainer {\n" +
                      "    public void foo() {}\n" +
                      "}\n" +
                      "class PsiPackageImpl extends PsiPackageBase implements PsiPackage {}\n" +
                      "\n" +
                      "class Foo {\n" +
                      "    class Bar {\n" +
                      "        void bar(PsiPackage i) {\n" +
                      "            i.fo<caret>o();\n" +
                      "        }\n" +
                      "    }\n" +
                      "}";
    PsiFile file = myFixture.addFileToProject("Foo.java", fileText);
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());

    final PsiElement[] impls = getTargets(file);
    ReadAction.runBlocking(() -> {
      assertEquals(1, impls.length);
      final PsiElement method = impls[0];
      assertInstanceOf(PsiMethod.class, method);
      final PsiClass aClass = ((PsiMethod)method).getContainingClass();
      assertNotNull(aClass);
      assertEquals("PsiPackageBase", aClass.getName());
    });
  }

  @Test
  public void testMethodReferences() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    @Language("JAVA") @SuppressWarnings("ALL")
    String fileText = "interface I {void f();}\n" +
                  "class A implements I { public void f(){}}\n" +
                  "class B implements I { public void f(){}}\n" +
                  "class C {\n" +
                  "  void foo(java.util.List<I> l) {l.stream().forEach(I::<caret>f);}" +
                  "}";
    PsiFile file = myFixture.addFileToProject("Foo.java", fileText);
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());

    final PsiElement[] impls = getTargets(file);
    ReadAction.runBlocking(() -> {
      assertEquals(2, impls.length);
      // target are non-deterministic now
      Arrays.sort(impls, (o1, o2) -> {
        String name1 = ((PsiMethod)o1).getContainingClass().getName();
        String name2 = ((PsiMethod)o2).getContainingClass().getName();
        return StringUtil.compare(name1, name2, false);
      });
      final PsiElement method = impls[0];
      assertInstanceOf(PsiMethod.class, method);
      final PsiClass aClass = ((PsiMethod)method).getContainingClass();
      assertNotNull(aClass);
      assertEquals("A", aClass.getName());
    });
  }

  @Test
  public void testMethodImplementationsOnTypeVariable() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    @Language("JAVA") @SuppressWarnings("ALL")
    String fileText = "interface I {}\n" +
                      "interface Im {\n" +
                      "    void m();\n" +
                      "}\n" +
                      "class Im1 implements Im {\n" +
                      "    public void m() {}\n" +
                      "}\n" +
                      "class Im2 implements Im {\n" +
                      "    public void m() {}\n" +
                      "}\n" +
                      "class JavaClass<T extends K, K extends I & Im> {\n" +
                      "    void  a(T t){\n" +
                      "        t.<caret>m();\n" +
                      "    }\n" +
                      "}";
    PsiFile file = myFixture.addFileToProject("Foo.java", fileText);
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
    PsiElement[] targets = getTargets(file);
    assertEquals(2, targets.length);
  }

  @Test
  public void testStaticMethodReference() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    @Language("JAVA") @SuppressWarnings("ALL")
    String fileText = "class C {\n" +
                  "  static void a(){}\n" +
                  "  {a<caret>();}" +
                  "}";
    PsiFile file = myFixture.addFileToProject("Foo.java",
                                              fileText);
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());

    final PsiElement[] impls = getTargets(file);
    assertEquals(1, impls.length);
  }

  @Test
  public void testPrivateClassInheritors() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    @Language("JAVA")
    String fileText = """
      class C {
        private static class Pr<caret>ivate {}
        public static class Public extends Private {}}""";
    PsiFile file = myFixture.addFileToProject("Foo.java",
                                              fileText);
    myFixture.addClass("class Inheritor extends C.Public {}");
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());

    assertEquals(2, getTargets(file).length);
  }

  @Test
  public void testPrivateClassInheritorsInJdkDecompiled() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    Sdk sdk = IdeaTestUtil.getMockJdk18();
    WriteAction.runAndWait(() -> ProjectJdkTable.getInstance().addJdk(sdk, myFixture.getTestRootDisposable()));
    ModuleRootModificationUtil.setModuleSdk(myFixture.getModule(), sdk);
    IndexingTestUtil.waitUntilIndexesAreReady(myFixture.getProject());

    PsiClass aClass = ReadAction.computeBlocking(() -> myFixture.getJavaFacade().findClass("java.util.ResourceBundle.CacheKeyReference"));
    PsiFile file = ReadAction.computeBlocking(() -> aClass.getContainingFile());
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
    EdtTestUtil.runInEdtAndWait(() -> myFixture.getEditor().getCaretModel().moveToOffset(aClass.getTextOffset()));

    assertEquals(2, getTargets(file).length);
  }

  @Test
  public void testScopeForPrivateMethod() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    @Language("JAVA") @SuppressWarnings("ALL")
    String text = "class Foo {" +
                  " {f<caret>oo();}" +
                  " private void foo() {}" +
                  "}";
    PsiFile file = myFixture.configureByText(JavaFileType.INSTANCE, text);
    PsiClass inheritor = myFixture.addClass("class FooImpl extends Foo {}");
    SearchScope scope = ReadAction.computeBlocking(() -> TargetElementUtil.getInstance().getSearchScope(myFixture.getEditor(), ((PsiJavaFile)file).getClasses()[0].getMethods()[0]));
    assertFalse(scope.contains(PsiUtilCore.getVirtualFile(inheritor)));
  }

  @Test
  public void testAnonymousAndLocalClassesInLibrary() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    ModuleRootModificationUtil.addModuleLibrary(
      myFixture.getModule(),
      "jar://" + JavaTestUtil.getJavaTestDataPath() + "/codeInsight/navigation/MyInterfaceLibrary.jar!/"
    );
    @Language("JAVA")
    String fileText = """
      import com.company.MyInterface;

      public class MyInterfaceImplementation implements My<caret>Interface {
          @Override
          public void doIt() {}
      }
      """;
    PsiFile psiFile = myFixture.addFileToProject("MyInterfaceImplementation.java",
                                                 fileText);

    myFixture.configureFromExistingVirtualFile(psiFile.getVirtualFile());

    ReadAction.runBlocking(() -> {
      PsiClass implementation = myFixture.findClass("MyInterfaceImplementation");
      assertNotNull(implementation);
      PsiClass[] supers = implementation.getSupers();
      assertTrue(supers.length == 2 && "com.company.MyInterface".equalsIgnoreCase(supers[1].getQualifiedName()));
    });

    PsiElement[] targets = getTargets(psiFile);
    assertEquals(5, targets.length);

    ReadAction.runBlocking(() -> {
      List<String> names = ContainerUtil.map(targets, element -> ((PsiClass)element).getName());

      for(PsiElement element:targets) {
        PsiClass psiClass = (PsiClass)element;
        String name = psiClass.getName();
        if ("1".equals(name) || "2".equals(name)) {
          assertNull(psiClass.getModifierList());
          assertTrue(psiClass.hasModifierProperty(PsiModifier.FINAL));
          assertInstanceOf(PsiAnonymousClass.class, psiClass);
        }
        else if (!"MyInterfaceImplementation".equals(name)) {
          assertNotNull(psiClass.getModifierList());
        }
      }

      assertThat(names).contains("1");
      assertThat(names).contains("2");
      assertThat(names).contains("MyLocalClassImplementation");
      assertThat(names).contains("MyLocalClassImplementationInInner");
      assertThat(names).contains("MyInterfaceImplementation");

      PsiClass implementation = myFixture.getJavaFacade().findClass("MyLocalClassImplementation");
      assertNull(implementation);
      implementation = myFixture.getJavaFacade().findClass("MyLocalClassImplementationInInner");
      assertNull(implementation);

      implementation = myFixture.getJavaFacade().findClass("1");
      assertNull(implementation);

      implementation = myFixture.getJavaFacade().findClass("2");
      assertNull(implementation);
    });
  }

  private PsiElement[] getTargets(PsiFile file) {
    GotoTargetHandler.GotoData gotoData = EdtTestUtil.runInEdtAndGet(() -> CodeInsightTestUtil.gotoImplementation(codeInsightFixture.get().getEditor(), file));
    assertNotNull(gotoData);
    return gotoData.targets;
  }
}