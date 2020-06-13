// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.navigation;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.codeInsight.navigation.GotoTargetHandler;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.lang.annotations.Language;

import java.util.Arrays;
import java.util.List;

public class GotoImplementationHandlerTest extends JavaCodeInsightFixtureTestCase {
  public void testMultipleImplsFromAbstractCall() {
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

  public void testFromIncompleteCode() {
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

  public void testToStringOnUnqualifiedPerformance() {
    @Language("JAVA") @SuppressWarnings("ALL")
    String fileText = "public class Fix {\n" +
                  "    {\n" +
                  "        <caret>toString();\n" +
                  "    }\n" +
                  "}\n" +
                  "class FixImpl1 extends Fix {\n" +
                  "    @Override\n" +
                  "    public String toString() {\n" +
                  "        return \"Impl1\";\n" +
                  "    }\n" +
                  "}\n" +
                  "class FixImpl2 extends Fix {\n" +
                  "    @Override\n" +
                  "    public String toString() {\n" +
                  "        return \"Impl2\";\n" +
                  "    }\n" +
                  "}";
    final PsiFile file = myFixture.addFileToProject("Foo.java", fileText);
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());

     PlatformTestUtil.startPerformanceTest(getTestName(false), 150, () -> {
       PsiElement[] impls = getTargets(file);
       assertEquals(3, impls.length);
     }).usesAllCPUCores().assertTiming();
  }

  public void testToStringOnQualifiedPerformance() {
    @SuppressWarnings("ALL") @Language("JAVA")
    String fileText = "public class Fix {\n" +
                  "    {\n" +
                  "        Fix ff = getFix();\n" +
                  "        ff.<caret>toString();\n" +
                  "    }\n" +
                  "    \n" +
                  "    Fix getFix() {return new FixImpl1();}\n" +
                  "}\n" +
                  "class FixImpl1 extends Fix {\n" +
                  "    @Override\n" +
                  "    public String toString() {\n" +
                  "        return \"Impl1\";\n" +
                  "    }\n" +
                  "}\n" +
                  "class FixImpl2 extends Fix {\n" +
                  "    @Override\n" +
                  "    public String toString() {\n" +
                  "        return \"Impl2\";\n" +
                  "    }\n" +
                  "}";
    final PsiFile file = myFixture.addFileToProject("Foo.java", fileText);
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());

    PlatformTestUtil.startPerformanceTest(getTestName(false), 150, () -> {
      PsiElement[] impls = getTargets(file);
      assertEquals(3, impls.length);
    }).usesAllCPUCores().assertTiming();
  }

  public void testShowSelfNonAbstract() {
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

  public void testMultipleImplsFromStaticCall() {
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

  public void testFilterOutImpossibleVariants() {
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
    assertEquals(1, impls.length);
    final PsiElement method = impls[0];
    assertTrue(method instanceof PsiMethod);
    final PsiClass aClass = ((PsiMethod)method).getContainingClass();
    assertNotNull(aClass);
    assertEquals("X", aClass.getName());
  }

  public void testImplicitInheritance() {
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
    assertEquals(1, impls.length);
    final PsiElement method = impls[0];
    assertTrue(method instanceof PsiMethod);
    final PsiClass aClass = ((PsiMethod)method).getContainingClass();
    assertNotNull(aClass);
    assertEquals("PsiPackageBase", aClass.getName());
  }

  public void testMethodReferences() {
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
    assertEquals(2, impls.length);
    // target are non-deterministic now
    Arrays.sort(impls, (o1, o2) -> {
      String name1 = ((PsiMethod)o1).getContainingClass().getName();
      String name2 = ((PsiMethod)o2).getContainingClass().getName();
      return StringUtil.compare(name1, name2, false);
    });
    final PsiElement method = impls[0];
    assertTrue(method instanceof PsiMethod);
    final PsiClass aClass = ((PsiMethod)method).getContainingClass();
    assertNotNull(aClass);
    assertEquals("A", aClass.getName());
  }

  public void testMethodImplementationsOnTypeVariable() {
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
    assertSize(2, targets);
  }

  public void testStaticMethodReference() {
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

  public void testPrivateClassInheritors() {
    @Language("JAVA")
    String fileText = "class C {\n" +
                  "  private static class Pr<caret>ivate {}\n" +
                  "  public static class Public extends Private {}" +
                  "}";
    PsiFile file = myFixture.addFileToProject("Foo.java",
                                              fileText);
    myFixture.addClass("class Inheritor extends C.Public {}");
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());

    assertSize(2, getTargets(file));
  }

  public void testPrivateClassInheritorsInJdkDecompiled() {
    ModuleRootModificationUtil.setModuleSdk(getModule(), IdeaTestUtil.getMockJdk18());

    PsiClass aClass = myFixture.getJavaFacade().findClass("java.util.ResourceBundle.CacheKeyReference");
    PsiFile file = aClass.getContainingFile();
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
    myFixture.getEditor().getCaretModel().moveToOffset(aClass.getTextOffset());

    assertSize(2, getTargets(file));
  }

  public void testScopeForPrivateMethod() {
    @Language("JAVA") @SuppressWarnings("ALL")
    String text = "class Foo {" +
                  " {f<caret>oo();}" +
                  " private void foo() {}" +
                  "}";
    PsiFile file = myFixture.configureByText(JavaFileType.INSTANCE, text);
    PsiClass inheritor = myFixture.addClass("class FooImpl extends Foo {}");
    SearchScope scope = TargetElementUtil.getInstance().getSearchScope(myFixture.getEditor(), ((PsiJavaFile)file).getClasses()[0].getMethods()[0]);
    assertFalse(scope.contains(PsiUtilCore.getVirtualFile(inheritor)));
  }

  public void testAnonymousAndLocalClassesInLibrary() {
    ModuleRootModificationUtil.addModuleLibrary(
      getModule(),
      "jar://" + JavaTestUtil.getJavaTestDataPath() + "/codeInsight/navigation/MyInterfaceLibrary.jar!/"
    );
    @Language("JAVA")
    String fileText = "import com.company.MyInterface;\n" +
                      "\n" +
                      "public class MyInterfaceImplementation implements My<caret>Interface {\n" +
                      "    @Override\n" +
                      "    public void doIt() {}\n" +
                      "}\n";
    PsiFile psiFile = myFixture.addFileToProject("MyInterfaceImplementation.java",
                                                 fileText);

    myFixture.configureFromExistingVirtualFile(psiFile.getVirtualFile());

    PsiClass implementation = myFixture.findClass("MyInterfaceImplementation");
    assertNotNull(implementation);
    PsiClass[] supers = implementation.getSupers();
    assertTrue(supers.length == 2 && "com.company.MyInterface".equalsIgnoreCase(supers[1].getQualifiedName()));

    PsiElement[] targets = getTargets(psiFile);
    assertSize(5, targets);

    List<String> names = ContainerUtil.map(targets, element -> ((PsiClass)element).getName());

    for(PsiElement element:targets) {
      PsiClass psiClass = (PsiClass)element;
      String name = psiClass.getName();
      if ("1".equals(name) || "2".equals(name)) {
        assertNull(psiClass.getModifierList());
        assertTrue(psiClass.hasModifierProperty(PsiModifier.FINAL));
        assertInstanceOf(psiClass, PsiAnonymousClass.class);
      }
      else if (!"MyInterfaceImplementation".equals(name)) {
        assertNotNull(psiClass.getModifierList());
      }
    }

    assertContainsElements(names, "1");
    assertContainsElements(names, "2");
    assertContainsElements(names, "MyLocalClassImplementation");
    assertContainsElements(names, "MyLocalClassImplementationInInner");
    assertContainsElements(names, "MyInterfaceImplementation");

    implementation = myFixture.getJavaFacade().findClass("MyLocalClassImplementation");
    assertNull(implementation);
    implementation = myFixture.getJavaFacade().findClass("MyLocalClassImplementationInInner");
    assertNull(implementation);

    implementation = myFixture.getJavaFacade().findClass("1");
    assertNull(implementation);

    implementation = myFixture.getJavaFacade().findClass("2");
    assertNull(implementation);
  }

  private PsiElement[] getTargets(PsiFile file) {
    GotoTargetHandler.GotoData gotoData = CodeInsightTestUtil.gotoImplementation(myFixture.getEditor(), file);
    assertNotNull(gotoData);
    return gotoData.targets;
  }
}