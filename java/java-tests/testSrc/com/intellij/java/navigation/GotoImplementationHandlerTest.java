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
package com.intellij.java.navigation;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.navigation.GotoTargetHandler;
import com.intellij.idea.Bombed;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.util.containers.ContainerUtil;

import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

public class GotoImplementationHandlerTest extends JavaCodeInsightFixtureTestCase {
  public void testMultipleImplsFromAbstractCall() {
    PsiFile file = myFixture.addFileToProject("Foo.java", "public abstract class Hello {\n" +
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
                                                          "}");
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());

    final PsiElement[] impls = getTargets(file);
    assertEquals(2, impls.length);
  }

  public void testFromIncompleteCode() {
    PsiFile file = myFixture.addFileToProject("Foo.java", "public abstract class Hello {\n" +
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
                                                          "}\n");
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());

    final PsiElement[] impls = getTargets(file);
    assertEquals(2, impls.length);
  }

  public void testToStringOnUnqualified() {
    final PsiFile file = myFixture.addFileToProject("Foo.java", "public class Fix {\n" +
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
                                                                "}");
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());

     PlatformTestUtil.startPerformanceTest(getTestName(false), 150, () -> {
       PsiElement[] impls = getTargets(file);
       assertEquals(3, impls.length);
     }).usesAllCPUCores().assertTiming();
  }

  public void testToStringOnQualified() {
    final PsiFile file = myFixture.addFileToProject("Foo.java", "public class Fix {\n" +
                                                                "    {\n" +
                                                                "        Fix ff = new FixImpl1();\n" +
                                                                "        ff.<caret>toString();\n" +
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
                                                                "}");
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());

    PlatformTestUtil.startPerformanceTest(getTestName(false), 150, () -> {
      PsiElement[] impls = getTargets(file);
      assertEquals(3, impls.length);
    }).usesAllCPUCores().assertTiming();
  }

  public void testShowSelfNonAbstract() {
    //fails if groovy plugin is enabled: org.jetbrains.plugins.groovy.codeInsight.JavaClsMethodElementEvaluator
    PsiFile file = myFixture.addFileToProject("Foo.java", "public class Hello {\n" +
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
                                                          "}");
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());

    final PsiElement[] impls = getTargets(file);
    assertEquals(3, impls.length);
  }

  public void testMultipleImplsFromStaticCall() {
    PsiFile file = myFixture.addFileToProject("Foo.java", "public abstract class Hello {\n" +
                                                          "    static void bar (){}\n" +
                                                          "    class Hello1 extends Hello {\n" +
                                                          "    }\n" +
                                                          "    class Hello2 extends Hello {\n" +
                                                          "    }\n" +
                                                          "class D {\n" +
                                                          "    {\n" +
                                                          "        He<caret>llo.bar();\n" +
                                                          "    }\n" +
                                                          "}");
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());

    final PsiElement[] impls = getTargets(file);
    assertEquals(2, impls.length);
  }

  public void testFilterOutImpossibleVariants() {
    PsiFile file = myFixture.addFileToProject("Foo.java", "interface A {\n" +
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
                                                          "}");
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
    PsiFile file = myFixture.addFileToProject("Foo.java", "interface PackContainer {\n" +
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
                                                          "}");
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
    PsiFile file = myFixture.addFileToProject("Foo.java", "interface I {void f();}\n" +
                                                          "class A implements I { public void f(){}}\n" +
                                                          "class B implements I { public void f(){}}\n" +
                                                          "class C {\n" +
                                                          "  void foo(java.util.List<I> l) {l.stream().forEach(I::<caret>f);}" +
                                                          "}");
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
    PsiFile file = myFixture.addFileToProject("Foo.java", "interface I {}\n" +
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
                                                          "}");
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
    PsiElement[] targets = getTargets(file);
    assertSize(2, targets);
  }

  public void testStaticMethodReference() {
    PsiFile file = myFixture.addFileToProject("Foo.java",
                                                          "class C {\n" +
                                                          "  static void a(){}\n" +
                                                          "  {a<caret>();}" +
                                                          "}");
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());

    final PsiElement[] impls = getTargets(file);
    assertEquals(1, impls.length);
  }

  public void testPrivateClassInheritors() {
    PsiFile file = myFixture.addFileToProject("Foo.java",
                                                          "class C {\n" +
                                                          "  private static class Pr<caret>ivate {}\n" +
                                                          "  public static class Public extends Private {}" +
                                                          "}");
    myFixture.addClass("class Inheritor extends C.Public {}");
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());

    assertSize(2, getTargets(file));
  }

  public void testPrivateClassInheritorsInJdkDecompiled() {
    ModuleRootModificationUtil.setModuleSdk(myModule, IdeaTestUtil.getMockJdk18());

    PsiClass aClass = myFixture.getJavaFacade().findClass("java.util.ResourceBundle.CacheKeyReference");
    PsiFile file = aClass.getContainingFile();
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
    myFixture.getEditor().getCaretModel().moveToOffset(aClass.getTextOffset());

    assertSize(2, getTargets(file));
  }

  @Bombed(month = Calendar.SEPTEMBER, user = "Maxim.Mossienko", day = 15)
  public void testAnonymousAndLocalClassesInLibrary() {
    ModuleRootModificationUtil.addModuleLibrary(
      myModule, 
      "jar://" + JavaTestUtil.getJavaTestDataPath() + "/codeInsight/navigation/MyInterfaceLibrary.jar!/"
    );
    PsiFile psiFile = myFixture.addFileToProject("MyInterfaceImplementation.java",
                                               "import com.company.MyInterface;\n" +
                                               "\n" +
                                               "public class MyInterfaceImplementation implements My<caret>Interface {\n" +
                                               "    @Override\n" +
                                               "    public void doIt() {}\n" +
                                               "}\n");

    myFixture.configureFromExistingVirtualFile(psiFile.getVirtualFile());

    PsiClass implementation = myFixture.findClass("MyInterfaceImplementation");
    assertNotNull(implementation);
    PsiClass[] supers = implementation.getSupers();
    assertTrue(supers.length == 2 && "com.company.MyInterface".equalsIgnoreCase(supers[1].getQualifiedName()));
    
    PsiElement[] targets = getTargets(psiFile);
    assertSize(5, targets);

    List<String> names = ContainerUtil.map(targets, element -> ((PsiClass)element).getName());

    assertContainsElements(names, "1");
    assertContainsElements(names, "2");
    assertContainsElements(names, "MyLocalClassImplementation");
    assertContainsElements(names, "MyLocalClassImplementationInInner");
    assertContainsElements(names, "MyInterfaceImplementation");

    /* // todo
    implementation = myFixture.getJavaFacade().findClass("MyLocalClassImplementation");
    assertNull(implementation);
    implementation = myFixture.getJavaFacade().findClass("MyLocalClassImplementationInInner");
    assertNull(implementation);

    implementation = myFixture.getJavaFacade().findClass("1");
    assertNull(implementation);

    implementation = myFixture.getJavaFacade().findClass("2");
    assertNull(implementation);
    */
  }

  private PsiElement[] getTargets(PsiFile file) {
    GotoTargetHandler.GotoData gotoData = CodeInsightTestUtil.gotoImplementation(myFixture.getEditor(), file);
    assertNotNull(gotoData);
    return gotoData.targets;
  }
}