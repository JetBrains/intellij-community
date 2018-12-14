// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.java.navigation;

import com.intellij.codeInsight.ShowImplementationsTestUtil;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.pom.Navigatable;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;

public class ShowImplementationHandlerTest extends JavaCodeInsightFixtureTestCase {

  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder moduleBuilder) throws Exception {
    super.tuneFixture(moduleBuilder);
    moduleBuilder.setLanguageLevel(LanguageLevel.JDK_1_8);
  }

  public void testMultipleImplsFromAbstractCall() {
    PsiFile file = myFixture.addFileToProject("Foo.java", "public abstract class Hello {" +
                                                          "    {" +
                                                          "        Runnable r = () <caret>-> {};\n" +
                                                          "    }\n" +
                                                          "}\n" +
                                                          "\n");
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());

    final PsiElement element =
      TargetElementUtil.findTargetElement(myFixture.getEditor(), TargetElementUtil.getInstance().getAllAccepted());
    assertTrue(element instanceof PsiClass);
    final String qualifiedName = ((PsiClass)element).getQualifiedName();
    assertEquals(CommonClassNames.JAVA_LANG_RUNNABLE, qualifiedName);
  }

  public void testDisableFunctionalInterfaceReferenceOnWhitespacesInside() {
    PsiFile file = myFixture.addFileToProject("Foo.java", "public abstract class Hello {" +
                                                          "    {" +
                                                          "        Runnable r = ()<caret> -> {};\n" +
                                                          "    }\n" +
                                                          "}\n" +
                                                          "\n");
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());

    final PsiElement element =
      TargetElementUtil.findTargetElement(myFixture.getEditor(), TargetElementUtil.getInstance().getAllAccepted());
    assertNull(element);
  }

  public void testFunctionExpressionsOnReference() {
    myFixture.addClass("public interface I {void m();}");
    myFixture.addClass("public class Usage {{I i = () -> {};}}");
    PsiFile file = myFixture.addFileToProject("Foo.java", "public abstract class Hello {" +
                                                          "    void foo(I i) {" +
                                                          "        i.<caret>m();\n" +
                                                          "    }\n" +
                                                          "}\n" +
                                                          "\n");
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());

    final PsiElement[] implementations = ShowImplementationsTestUtil.getImplementations();
    assertEquals(2, implementations.length);
    assertInstanceOf(implementations[1], PsiLambdaExpression.class);
  }

  public void testEnumValuesNavigation() {
    final PsiFile file = myFixture.addFileToProject("Foo.java", "public class Foo {" +
                                                                "  public enum E {;}" +
                                                                "  void foo() {" +
                                                                "    for (E e : E.va<caret>lues()){}" +
                                                                "  }" +
                                                                "}");
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
    final PsiElement element = TargetElementUtil.findTargetElement(myFixture.getEditor(), TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED);
    assertNotNull(element);
    assertInstanceOf(element, PsiMethod.class);
    assertTrue(((Navigatable)element).canNavigate());
    ((Navigatable)element).navigate(true);
    assertEquals(32, myFixture.getCaretOffset());
  }
}