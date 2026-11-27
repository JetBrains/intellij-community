// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.java.navigation;

import com.intellij.codeInsight.ShowImplementationsTestUtil;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.pom.Navigatable;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLambdaExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.JavaCodeInsightFixtureKt.javaModuleCodeInsightFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** See also {@link com.intellij.codeInsight.ShowImplementationsTest} */
@TestApplication
public class ShowImplementationHandlerTest {
  private final TestFixture<JavaCodeInsightTestFixture> codeInsightFixture =
    javaModuleCodeInsightFixture(PathManager.getHomePath(), true, moduleBuilder -> moduleBuilder.setLanguageLevel(LanguageLevel.JDK_1_8));

  @Test
  public void testMultipleImplsFromAbstractCall() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiFile file = myFixture.addFileToProject("Foo.java", """
      public abstract class Hello {    {        Runnable r = () <caret>-> {};
          }
      }

      """);
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());

    final PsiElement element =
      ReadAction.computeBlocking(() -> TargetElementUtil.findTargetElement(myFixture.getEditor(), TargetElementUtil.getInstance().getAllAccepted()));
    assertInstanceOf(PsiClass.class, element);
    final String qualifiedName = ((PsiClass)element).getQualifiedName();
    assertEquals(CommonClassNames.JAVA_LANG_RUNNABLE, qualifiedName);
  }

  @Test
  public void testDisableFunctionalInterfaceReferenceOnWhitespacesInside() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiFile file = myFixture.addFileToProject("Foo.java", """
      public abstract class Hello {    {        Runnable r = ()<caret> -> {};
          }
      }

      """);
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());

    final PsiElement element =
      ReadAction.computeBlocking(() -> TargetElementUtil.findTargetElement(myFixture.getEditor(), TargetElementUtil.getInstance().getAllAccepted()));
    assertNull(element);
  }

  @Test
  public void testFunctionExpressionsOnReference() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("public interface I {void m();}");
    myFixture.addClass("public class Usage {{I i = () -> {};}}");
    PsiFile file = myFixture.addFileToProject("Foo.java", """
      public abstract class Hello {    void foo(I i) {        i.<caret>m();
          }
      }

      """);
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());

    final PsiElement[] implementations = EdtTestUtil.runInEdtAndGet(() -> ShowImplementationsTestUtil.getImplementations());
    assertEquals(2, implementations.length);
    assertInstanceOf(PsiLambdaExpression.class, implementations[1]);
  }

  @Test
  public void testEnumValuesNavigation() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final PsiFile file = myFixture.addFileToProject("Foo.java", "public class Foo {" +
                                                                "  public enum E {;}" +
                                                                "  void foo() {" +
                                                                "    for (E e : E.va<caret>lues()){}" +
                                                                "  }" +
                                                                "}");
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
    final PsiElement element = ReadAction.computeBlocking(() -> TargetElementUtil.findTargetElement(myFixture.getEditor(), TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED));
    assertNotNull(element);
    assertInstanceOf(PsiMethod.class, element);
    EdtTestUtil.runInEdtAndWait(() -> {
      assertTrue(((Navigatable)element).canNavigate());
      ((Navigatable)element).navigate(true);
      assertEquals(32, myFixture.getCaretOffset());
    });
  }
}