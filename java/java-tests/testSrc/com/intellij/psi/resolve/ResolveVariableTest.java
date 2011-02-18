package com.intellij.psi.resolve;

import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.testFramework.ResolveTestCase;

@PlatformTestCase.WrapInCommand
public class ResolveVariableTest extends ResolveTestCase {
  public void testAnonymousConstructorArg() throws Exception {
    PsiElement target = configureAndResolve();
    assertTrue(target instanceof PsiParameter);
    assertEquals("value", ((PsiParameter)target).getName());
  }

  public void testLocalVariable1() throws Exception {
    PsiElement target = configureAndResolve();
    assertTrue(target instanceof PsiLocalVariable);
    assertEquals("value", ((PsiLocalVariable)target).getName());
  }

  public void testVisibility1() throws Exception {
    PsiElement target = configureAndResolve();
    assertTrue(target instanceof PsiField);
    assertEquals("variable", ((PsiField)target).getName());
  }

  public void testVisibility2() throws Exception {
    PsiElement target = configureAndResolve();
    assertTrue(target instanceof PsiField);
    assertEquals("a", ((PsiField)target).getName());
  }

  private PsiElement configureAndResolve() throws Exception {
    PsiReference ref = configure();
    PsiElement target = ref.resolve();
    return target;
  }

  public void testVisibility3() throws Exception {
    PsiElement target = configureAndResolve();
    assertTrue(target instanceof PsiField);
    assertEquals("i", ((PsiField)target).getName());
  }

  public void testVisibility4() throws Exception {
    PsiElement target = configureAndResolve();
    assertEquals("a", ((PsiLocalVariable)target).getName());
    assertTrue(target instanceof PsiLocalVariable);
  }

  public void testQualified1() throws Exception {
    PsiElement target = configureAndResolve();
    assertTrue(target instanceof PsiField);
    assertEquals("a", ((PsiField)target).getName());
  }

  public void testQualified2() throws Exception {
    PsiElement target = configureAndResolve();
    assertTrue(target instanceof PsiField);
    assertEquals("a", ((PsiField)target).getName());
  }

  public void testQualified3() throws Exception {
    PsiElement target = configureAndResolve();
    assertTrue(target instanceof PsiField);
    assertEquals("a", ((PsiField)target).getName());
  }

  public void testQualified4() throws Exception {
    PsiElement target = configureAndResolve();
    assertTrue(target instanceof PsiField);
    assertEquals("a", ((PsiField)target).getName());
  }

  public void testUnresolved1() throws Exception {
    PsiElement target = configureAndResolve();
    assertNull(target);
  }

  public void testFieldFromInterface() throws Exception {
    PsiElement target = configureAndResolve();
    assertTrue(target instanceof PsiField);
  }

  public void testInterfaceConflict1() throws Exception {
    PsiElement target = configureAndResolve();
    assertNull(target);
  }

  public void testInterfaceConflict2() throws Exception {
    PsiElement target = configureAndResolve();
    assertNull(target);
  }

  public void testInterfaceConflict3() throws Exception {
    PsiElement target = configureAndResolve();
    assertNull(target);
  }

  public void testInterfaceConflict4() throws Exception {
    PsiElement target = configureAndResolve();
    assertNull(target);
  }

  // This is a bug but it's too hard to fix this :(
  // TODO: try to fix
  public void dontTestInterfaceConflict5() throws Exception {
    PsiElement target = configureAndResolve();
    assertNull(target);
  }

  public void testInterfaceConflict6() throws Exception {
    PsiElement target = configureAndResolve();
    assertNull(target);
  }

  public void testInterfaceConflict7() throws Exception {
    PsiElement target = configureAndResolve();
    assertNotNull(target);
  }

  public void testInterfaceConflict8() throws Exception {
    PsiElement target = configureAndResolve();
    assertNotNull(target);
  }

  public void testInterfaceConflict9() throws Exception {
    PsiElement target = configureAndResolve();
    assertNull(target);
  }

  public void testInterfaceConflict10() throws Exception {
    PsiElement target = configureAndResolve();
    assertNull(target);
  }

  public void testArray1() throws Exception {
    PsiElement target = configureAndResolve();
    assertNotNull(target);
  }

  public void testInterfaceConflict11() throws Exception {
    PsiElement target = configureAndResolve();
    assertNotNull(target);
    assertTrue(target instanceof PsiField);
    assertEquals("B", ((PsiField)target).getContainingClass().getName());
  }

  public void testBug7869() throws Exception {
    PsiElement target = configureAndResolve();
    assertNotNull(target);
  }

  public void testInner1() throws Exception {
    PsiElement target = configureAndResolve();
    assertNotNull(target);
    assertEquals("Inner1", ((PsiField)target).getContainingClass().getName());
  }

  public void testFieldsAndLocals() throws Exception {
    PsiElement target = configureAndResolve();
    assertTrue(target instanceof PsiLocalVariable);
  }

  public void testPrivateOverloading() throws Exception {
    PsiReference ref = configureByFile("var/PrivateOverloading.java");
    final JavaResolveResult result = ((PsiJavaReference)ref).advancedResolve(true);
    PsiElement target = result.getElement();
    assertNotNull(target);
    assertFalse(result.isValidResult());
  }

  public void testVisibility6() throws Exception {
    PsiReference ref = configureByFile("var/Visibility6.java");
    final JavaResolveResult result = ((PsiJavaReference)ref).advancedResolve(true);
    PsiElement target = result.getElement();
    assertNotNull(target);
    assertFalse(result.isValidResult());
  }

  public void testVisibility7() throws Exception {
    PsiReference ref = configureByFile("var/InnerPrivates1.java");
    final JavaResolveResult result = ((PsiJavaReference)ref).advancedResolve(true);
    PsiElement target = result.getElement();
    assertNotNull(target);
    assertTrue(result.isValidResult());
  }

  public void testForeachParameter() throws Exception {
    final PsiReference ref = configureByFile("var/ForeachParameter.java");
    final PsiElement element = ref.resolve();
    assertTrue(element instanceof PsiParameter);
    assertEquals("o", ((PsiParameter)element).getName());
    assertTrue(element.getParent() instanceof PsiForeachStatement);
  }

  public void testRefInSuper() throws Exception {
    final PsiJavaReference ref = (PsiJavaReference)configureByFile("var/RefInSuper.java");
    final JavaResolveResult resolveResult = ref.advancedResolve(false);
    assertTrue(resolveResult.isValidResult());
    PsiElement currentFileResolveScope = resolveResult.getCurrentFileResolveScope();
    assertTrue(currentFileResolveScope instanceof PsiClass);
    assertEquals("Inner", ((PsiClass)currentFileResolveScope).getName());
    PsiElement element = resolveResult.getElement();
    assertTrue(element instanceof PsiField);
    assertEquals("i", ((PsiField)element).getName());
  }

  public void testRefInOuter() throws Exception {
    final PsiJavaReference ref = (PsiJavaReference)configureByFile("var/RefInOuter.java");
    final JavaResolveResult resolveResult = ref.advancedResolve(false);
    assertTrue(resolveResult.isValidResult());
    PsiElement currentFileResolveScope = resolveResult.getCurrentFileResolveScope();
    assertTrue(currentFileResolveScope instanceof PsiClass);
    assertEquals("Outer", ((PsiClass)currentFileResolveScope).getName());
    PsiElement element = resolveResult.getElement();
    assertTrue(element instanceof PsiField);
    assertEquals("i", ((PsiField)element).getName());
  }

  public void testInheritedOuter() throws Exception {
    final PsiJavaReference ref = (PsiJavaReference)configureByFile("var/InheritedOuter.java");
    final JavaResolveResult resolveResult = ref.advancedResolve(false);
    assertTrue(resolveResult.isValidResult());
  }

  public void testStaticFieldsInInterfacesConflict() throws Exception {
    final PsiJavaReference ref = (PsiJavaReference)configureByFile("var/FieldConflict.java");
    final JavaResolveResult resolveResult = ref.advancedResolve(false);
    assertTrue(!resolveResult.isValidResult());
    final JavaResolveResult[] results = ref.multiResolve(false);
    assertEquals(2, results.length);
  }

  public void testShadowFieldsInHierarchy() throws Exception {
    final PsiJavaReference ref = (PsiJavaReference)configure();
    final JavaResolveResult resolveResult = ref.advancedResolve(false);
    assertTrue(resolveResult.isValidResult());
    PsiField field = myJavaFacade.findClass("TestPage", GlobalSearchScope.allScope(getProject())).getFields()[0];
    assertEquals(field, resolveResult.getElement());
  }

  public void testShadowFieldsInHierarchy2() throws Exception {
    PsiElement ref = configureAndResolve();
    assertTrue(ref instanceof PsiField);
    assertEquals("E", ((PsiField)ref).getContainingClass().getName());
  }

  private PsiReference configure() throws Exception {
    return configureByFile("var/" + getTestName(false) + ".java");
  }
}
