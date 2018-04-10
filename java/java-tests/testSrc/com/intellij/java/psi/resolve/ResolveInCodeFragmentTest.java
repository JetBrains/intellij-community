/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.java.psi.resolve;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiElementFactoryImpl;
import com.intellij.psi.impl.search.JavaSourceFilterScope;
import com.intellij.psi.impl.source.tree.java.PsiReferenceExpressionImpl;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.ResolveTestCase;

import java.util.function.Consumer;

/**
 * @author max
 */
public class ResolveInCodeFragmentTest extends ResolveTestCase {
  public void testLocalVariable() throws Exception {
    final PsiReference iRef = configure();

    PsiElement context = PsiTreeUtil.getParentOfType(iRef.getElement(), PsiCodeBlock.class);
    JavaCodeFragment codeFragment = JavaCodeFragmentFactory.getInstance(myProject)
      .createExpressionCodeFragment(iRef.getElement().getText(), context, null, true);
    codeFragment.setVisibilityChecker(JavaCodeFragment.VisibilityChecker.EVERYTHING_VISIBLE);

    PsiElement[] fileContent = codeFragment.getChildren();
    assertEquals(1, fileContent.length);
    assertTrue(fileContent[0] instanceof PsiExpression);

    PsiExpression expr = (PsiExpression) fileContent[0];
    expr.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override public void visitReferenceExpression(PsiReferenceExpression expression) {
        assertEquals(iRef.resolve(),
                     expression.resolve());
      }
    });
  }

  public void testjavaLangClass() {
    PsiCodeFragment codeFragment = JavaCodeFragmentFactory.getInstance(myProject).createExpressionCodeFragment(
          "Boolean.getBoolean(\"true\")", null, null, true);

    PsiElement[] fileContent = codeFragment.getChildren();
    assertEquals(1, fileContent.length);
    assertTrue(fileContent[0] instanceof PsiExpression);

    PsiExpression expr = (PsiExpression) fileContent[0];
    assertNotNull(expr.getType());
    assertEquals("boolean", expr.getType().getCanonicalText());
  }

  public void testResolveFieldVsLocalWithVisiblityChecker() throws Exception {
    doTestResolveWithVisibilityChecker("xxx", e -> assertInstanceOf(e, PsiLocalVariable.class));
  }

  public void testResolveFieldVsParamWithVisiblityChecker() throws Exception {
    doTestResolveWithVisibilityChecker("field", e -> assertInstanceOf(e, PsiParameter.class));
  }

  public void testResolveFieldInStaticInnerWithVisiblityChecker() throws Exception {
    doTestResolveWithVisibilityChecker("field", e -> {
      assertInstanceOf(e, PsiField.class);
      assertEquals("Inner", ((PsiField)e).getContainingClass().getName());
    });
  }

  public void testResolveFieldInInnerWithVisiblityChecker() throws Exception {
    doTestResolveWithVisibilityChecker("field", e -> {
      assertInstanceOf(e, PsiField.class);
      assertEquals("Inner", ((PsiField)e).getContainingClass().getName());
    });
  }

  private void doTestResolveWithVisibilityChecker(String field, Consumer<PsiElement> checker) throws Exception {
    PsiReference iRef = configure();

    JavaCodeFragment codeFragment = JavaCodeFragmentFactory.getInstance(myProject).createExpressionCodeFragment(
      field, iRef.getElement(), null, true);
    codeFragment.setVisibilityChecker(JavaCodeFragment.VisibilityChecker.EVERYTHING_VISIBLE);

    PsiElement[] fileContent = codeFragment.getChildren();
    assertEquals(1, fileContent.length);
    assertTrue(fileContent[0] instanceof PsiExpression);

    PsiExpression expr = (PsiExpression)fileContent[0];
    JavaResolveResult[] results = ((PsiReferenceExpressionImpl)expr).multiResolve(false);
    assertSize(1, results);
    checker.accept(results[0].getElement());
  }

  private PsiReference configure() throws Exception {
    return configureByFile("codeFragment/" + getTestName(false) + ".java");
  }

  public void testResolveScopeWithFragmentContext() throws Exception {
    PsiElement physical = configureByFile("codeFragment/LocalVariable.java").getElement();
    JavaCodeFragment fragment = JavaCodeFragmentFactory.getInstance(myProject)
      .createExpressionCodeFragment("ref", physical, null, true);
    fragment.forceResolveScope(new JavaSourceFilterScope(physical.getResolveScope()));
    assertFalse(fragment.getResolveScope().equals(physical.getResolveScope()));

    PsiExpression lightExpr = JavaPsiFacade.getElementFactory(myProject).createExpressionFromText("xxx.xxx", fragment);
    assertEquals(lightExpr.getResolveScope(), fragment.getResolveScope());
  }

  public void testClassHierarchyInNonPhysicalFile() {
    PsiFile file = PsiFileFactory.getInstance(myProject).createFileFromText("a.java", JavaFileType.INSTANCE,
                                                                            "class Parent { void foo( ); }\n" +
                                                                            "class Child extends Parent { }\n" +
                                                                            "class User {\n" +
                                                                            "    void caller() { new Child().foo(); }\n" +
                                                                            "}", 0, true);
    PsiReference ref = file.findReferenceAt(file.getText().indexOf("foo()"));
    assertNotNull(ref);
    assertTrue(ref.getElement().getResolveScope().contains(file.getViewProvider().getVirtualFile()));
    assertInstanceOf(ref.resolve(), PsiMethod.class);
  }

  public void testResolveMethodParamsFromNonPhysicalCodeBlock() {
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(getProject());
    PsiMethod method = factory.createMethodFromText("void foo(Object o);", null);
    PsiCodeBlock block = factory.createCodeBlockFromText("{ return o; }", method);
    assertInstanceOf(block.findReferenceAt(block.getText().indexOf("o")).resolve(), PsiParameter.class);
  }

  public void testDropCachesOnNonPhysicalContextChange() {
    PsiElementFactoryImpl factory = (PsiElementFactoryImpl)JavaPsiFacade.getElementFactory(getProject());
    PsiClass superClass = ((PsiJavaFile) PsiFileFactory.getInstance(myProject).createFileFromText("a.java", JavaFileType.INSTANCE, "class Super { @Deprecated void foo(){} }")).getClasses()[0];
    PsiClass subClass = ((PsiNewExpression)factory.createExpressionFromText("new Super() { void foo(){} }", superClass)).getAnonymousClass();
    assertNotNull(AnnotationUtil.findAnnotationInHierarchy(subClass.getMethods()[0], Deprecated.class));

    superClass.getMethods()[0].getModifierList().getAnnotations()[0].delete();
    assertNull(AnnotationUtil.findAnnotationInHierarchy(subClass.getMethods()[0], Deprecated.class));
  }
}
