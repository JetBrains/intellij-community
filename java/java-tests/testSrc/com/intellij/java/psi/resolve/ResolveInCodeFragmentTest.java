// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.psi.resolve;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.JavaCodeFragment;
import com.intellij.psi.JavaCodeFragmentFactory;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.JavaRecursiveElementWalkingVisitor;
import com.intellij.psi.JavaResolveResult;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiCodeFragment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.impl.PsiElementFactoryImpl;
import com.intellij.psi.impl.search.JavaSourceFilterScope;
import com.intellij.psi.impl.source.tree.java.PsiReferenceExpressionImpl;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.LightResolveFixture;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.function.Consumer;

import static com.intellij.testFramework.LightResolveFixtureKt.lightResolveFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class ResolveInCodeFragmentTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightResolveFixture> resolveFixture = lightResolveFixture(projectFixture);

  @Test
  public void testLocalVariable() {
    LightJavaCodeInsightTestFixture myFixture = resolveFixture.get().getFixture();
    final PsiReference iRef = configure();

    ReadAction.runBlocking(() -> {
      PsiElement context = PsiTreeUtil.getParentOfType(iRef.getElement(), PsiCodeBlock.class);
      JavaCodeFragment codeFragment = JavaCodeFragmentFactory.getInstance(myFixture.getProject())
        .createExpressionCodeFragment(iRef.getElement().getText(), context, null, true);
      codeFragment.setVisibilityChecker(JavaCodeFragment.VisibilityChecker.EVERYTHING_VISIBLE);

      PsiElement[] fileContent = codeFragment.getChildren();
      assertEquals(1, fileContent.length);
      assertTrue(fileContent[0] instanceof PsiExpression);

      PsiExpression expr = (PsiExpression) fileContent[0];
      expr.accept(new JavaRecursiveElementWalkingVisitor() {
        @Override public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
          assertEquals(iRef.resolve(),
                       expression.resolve());
        }
      });
    });
  }

  @Test
  public void testjavaLangClass() {
    LightJavaCodeInsightTestFixture myFixture = resolveFixture.get().getFixture();
    ReadAction.runBlocking(() -> {
      PsiCodeFragment codeFragment = JavaCodeFragmentFactory.getInstance(myFixture.getProject()).createExpressionCodeFragment(
            "Boolean.getBoolean(\"true\")", null, null, true);

      PsiElement[] fileContent = codeFragment.getChildren();
      assertEquals(1, fileContent.length);
      assertTrue(fileContent[0] instanceof PsiExpression);

      PsiExpression expr = (PsiExpression) fileContent[0];
      assertNotNull(expr.getType());
      assertEquals("boolean", expr.getType().getCanonicalText());
    });
  }

  @Test
  public void testResolveFieldVsLocalWithVisiblityChecker() {
    doTestResolveWithVisibilityChecker("xxx", e -> assertInstanceOf(PsiLocalVariable.class, e));
  }

  @Test
  public void testResolveFieldVsParamWithVisiblityChecker() {
    doTestResolveWithVisibilityChecker("field", e -> assertInstanceOf(PsiParameter.class, e));
  }

  @Test
  public void testResolveFieldInStaticInnerWithVisiblityChecker() {
    doTestResolveWithVisibilityChecker("field", e -> {
      assertInstanceOf(PsiField.class, e);
      assertEquals("Inner", ((PsiField)e).getContainingClass().getName());
    });
  }

  @Test
  public void testResolveFieldInInnerWithVisiblityChecker() {
    doTestResolveWithVisibilityChecker("field", e -> {
      assertInstanceOf(PsiField.class, e);
      assertEquals("Inner", ((PsiField)e).getContainingClass().getName());
    });
  }

  private void doTestResolveWithVisibilityChecker(String field, Consumer<PsiElement> checker) {
    LightJavaCodeInsightTestFixture myFixture = resolveFixture.get().getFixture();
    PsiReference iRef = configure();

    ReadAction.runBlocking(() -> {
      JavaCodeFragment codeFragment = JavaCodeFragmentFactory.getInstance(myFixture.getProject()).createExpressionCodeFragment(
        field, iRef.getElement(), null, true);
      codeFragment.setVisibilityChecker(JavaCodeFragment.VisibilityChecker.EVERYTHING_VISIBLE);

      PsiElement[] fileContent = codeFragment.getChildren();
      assertEquals(1, fileContent.length);
      assertTrue(fileContent[0] instanceof PsiExpression);

      PsiExpression expr = (PsiExpression)fileContent[0];
      JavaResolveResult[] results = ((PsiReferenceExpressionImpl)expr).multiResolve(false);
      assertThat(results).hasSize(1);
      checker.accept(results[0].getElement());
    });
  }

  private PsiReference configure() {
    return resolveFixture.get().findReferenceAtCaret("codeFragment/" + resolveFixture.get().getTestName(false) + ".java");
  }

  @Test
  public void testResolveScopeWithFragmentContext() {
    LightJavaCodeInsightTestFixture myFixture = resolveFixture.get().getFixture();
    PsiElement physical = resolveFixture.get().findReferenceAtCaret("codeFragment/LocalVariable.java").getElement();
    ReadAction.runBlocking(() -> {
      JavaCodeFragment fragment = JavaCodeFragmentFactory.getInstance(myFixture.getProject())
        .createExpressionCodeFragment("ref", physical, null, true);
      fragment.forceResolveScope(new JavaSourceFilterScope(physical.getResolveScope()));
      assertNotSame(fragment.getResolveScope(), physical.getResolveScope());

      PsiExpression lightExpr = JavaPsiFacade.getElementFactory(myFixture.getProject()).createExpressionFromText("xxx.xxx", fragment);
      assertEquals(lightExpr.getResolveScope(), fragment.getResolveScope());
    });
  }

  @Test
  public void testClassHierarchyInNonPhysicalFile() {
    LightJavaCodeInsightTestFixture myFixture = resolveFixture.get().getFixture();
    ReadAction.runBlocking(() -> {
      PsiFile file = PsiFileFactory.getInstance(myFixture.getProject()).createFileFromText("a.java", JavaFileType.INSTANCE,
                                                                                 """
                                                                                   class Parent { void foo( ); }
                                                                                   class Child extends Parent { }
                                                                                   class User {
                                                                                       void caller() { new Child().foo(); }
                                                                                   }""", 0, true);
      PsiReference ref = file.findReferenceAt(file.getText().indexOf("foo()"));
      assertNotNull(ref);
      assertTrue(ref.getElement().getResolveScope().contains(file.getViewProvider().getVirtualFile()));
      assertInstanceOf(PsiMethod.class, ref.resolve());
    });
  }

  @Test
  public void testResolveMethodParamsFromNonPhysicalCodeBlock() {
    LightJavaCodeInsightTestFixture myFixture = resolveFixture.get().getFixture();
    ReadAction.runBlocking(() -> {
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(myFixture.getProject());
      PsiMethod method = factory.createMethodFromText("void foo(Object o);", null);
      PsiCodeBlock block = factory.createCodeBlockFromText("{ return o; }", method);
      assertInstanceOf(PsiParameter.class, block.findReferenceAt(block.getText().indexOf("o")).resolve());
    });
  }

  @Test
  public void testDropCachesOnNonPhysicalContextChange() {
    LightJavaCodeInsightTestFixture myFixture = resolveFixture.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      PsiElementFactoryImpl factory = (PsiElementFactoryImpl)JavaPsiFacade.getElementFactory(myFixture.getProject());
      PsiClass superClass = ((PsiJavaFile) PsiFileFactory.getInstance(myFixture.getProject()).createFileFromText("a.java", JavaFileType.INSTANCE, "class Super { @Deprecated void foo(){} }")).getClasses()[0];
      PsiClass subClass = ((PsiNewExpression)factory.createExpressionFromText("new Super() { void foo(){} }", superClass)).getAnonymousClass();
      assertNotNull(AnnotationUtil.findAnnotationInHierarchy(subClass.getMethods()[0], Deprecated.class));

      superClass.getMethods()[0].getModifierList().getAnnotations()[0].delete();
      assertNull(AnnotationUtil.findAnnotationInHierarchy(subClass.getMethods()[0], Deprecated.class));
    });
  }
}
