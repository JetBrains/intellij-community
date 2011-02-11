package com.intellij.psi.resolve;

import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.testFramework.ResolveTestCase;

public class ResolveMethodTest extends ResolveTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    LanguageLevelProjectExtension.getInstance(myJavaFacade.getProject()).setLanguageLevel(LanguageLevel.JDK_1_5);
  }

  @Override
  protected Sdk getTestProjectJdk() {
    return JavaSdkImpl.getMockJdk14();
  }

  private PsiElement resolve() throws Exception {
    PsiReference ref = configureByFile("method/" + getTestName(false) + ".java");
    return ref.resolve();
  }
  private JavaResolveResult advancedResolve() throws Exception {
    PsiJavaCodeReferenceElement ref = (PsiJavaCodeReferenceElement)configureByFile("method/" + getTestName(false) + ".java");
    return ref.advancedResolve(true);
  }

  public void testShortVsInt() throws Exception {
    PsiElement target = resolve();
    assertTrue(target instanceof PsiMethod);
    PsiParameter parm = ((PsiMethod)target).getParameterList().getParameters()[0];
    assertEquals(PsiType.INT, parm.getType());
  }

  public void testSimple() throws Exception {
    PsiElement target = resolve();
    assertTrue(target instanceof PsiMethod);
    assertEquals("method", ((PsiMethod) target).getName());
  }

  public void testIndexOf() throws Exception{
    PsiElement target = resolve();
    assertTrue(target instanceof PsiMethod);
    PsiParameter parm = ((PsiMethod)target).getParameterList().getParameters()[0];
    assertEquals(PsiType.INT, parm.getType());
  }

  public void testSuper1() throws Exception{
    PsiElement target = resolve();
    assertTrue(target instanceof PsiMethod);
  }

  public void testInherit1() throws Exception{
    PsiElement target = resolve();
    assertTrue(target instanceof PsiMethod);
    assertTrue(target.getParent() instanceof PsiClass);
    assertEquals("B", ((PsiClass)target.getParent()).getQualifiedName());
  }

  public void testNextMethod() throws Exception{
    PsiElement target = resolve();
    assertTrue(target instanceof PsiMethod);
    assertEquals(PsiType.BOOLEAN, ((PsiMethod)target).getParameterList().getParameters()[1].getType());
  }

  public void testMethodConflict1() throws Exception{
    PsiElement target = resolve();
    assertNull(target);
  }

  public void testValueOf() throws Exception{
    PsiElement target = resolve();
    assertTrue(target instanceof PsiMethod);
    assertEquals(PsiType.INT, ((PsiMethod)target).getParameterList().getParameters()[0].getType());
  }

  public void testMethodFromAnonBase() throws Exception{
    PsiElement target = resolve();
    assertTrue(target instanceof PsiMethod);
  }

  public void testSCR5859() throws Exception{
    PsiElement target = resolve();
    assertTrue(target instanceof PsiMethod);
    PsiParameter[] parms = ((PsiMethod)target).getParameterList().getParameters();
    assertEquals("Derived", parms[0].getType().getPresentableText());
    assertEquals("Derived", parms[1].getType().getPresentableText());
  }

  public void testPreferPublic() throws Exception{
    PsiElement target = resolve();
    assertTrue(target instanceof PsiMethod);
    assertTrue(((PsiMethod)target).hasModifierProperty(PsiModifier.PUBLIC));
  }

  public void testRemove1() throws Exception{
    PsiElement target = resolve();
    assertTrue(target instanceof PsiMethod);
    assertTrue(target.getParent() instanceof PsiClass);
    assertEquals("Remove1.A", ((PsiClass)target.getParent()).getQualifiedName());
  }

  public void testRemove2() throws Exception{
    PsiElement target = resolve();
    assertTrue(target instanceof PsiMethod);
    assertTrue(target.getParent() instanceof PsiClass);
    assertEquals("java.util.AbstractCollection", ((PsiClass)target.getParent()).getQualifiedName());
  }

  public void testArray1() throws Exception{
    PsiElement target = resolve();
    assertTrue(target instanceof PsiMethod);
  }

  public void testCycle1() throws Exception{
    PsiElement target = resolve();
    assertTrue(target instanceof PsiMethod);
    assertTrue(target.getParent() instanceof PsiClass);
    assertEquals("Cycle1.C", ((PsiClass)target.getParent()).getQualifiedName());
  }

  public void testAnonymousSuper1() throws Exception{
    PsiElement target = resolve();
    assertTrue(target instanceof PsiMethod);
    assertTrue(target.getParent() instanceof PsiClass);
    assertEquals("Test1.A", ((PsiClass)target.getParent()).getQualifiedName());
  }

  public void testBug7968() throws Exception{
    PsiElement target = resolve();
    assertTrue(target instanceof PsiMethod);
    assertTrue(target.getParent() instanceof PsiClass);
    assertEquals("Bug7968.Bar", ((PsiClass)target.getParent()).getQualifiedName());
  }

  public void testInheranceWithExtendsConflict() throws Exception{
    PsiElement target = resolve();
    assertTrue(target instanceof PsiMethod);
    assertTrue(target.getParent() instanceof PsiClass);
    assertEquals("B", ((PsiClass)target.getParent()).getQualifiedName());
  }

  public void testSout() throws Exception {
    PsiElement target = resolve();
    assertTrue(target instanceof PsiMethod);
    assertEquals("println", ((PsiMethod)target).getName());
  }

  /*
  public void testSCR5134() throws Exception{
    PsiReference ref = configureByFile("method/SCR5134.java");
    PsiElement target = ref.resolve();
    assertTrue(target instanceof PsiMethod);
    PsiParameter parm = ((PsiMethod)target).getParameterList().getParameters()[0];
    assertTrue(parm.getType().getText().equals("Integer"));
  }
  */

  public void testPartlyImplement1() throws Exception{
    PsiElement target = resolve();
    assertTrue(target instanceof PsiMethod);
    assertTrue(target.getParent() instanceof PsiClass);
    assertEquals("A.Predicate", ((PsiClass)target.getParent()).getQualifiedName());
  }

  public void testTestOverloading1() throws Exception{
    PsiElement target = resolve();
    assertTrue(target instanceof PsiMethod);
    assertTrue(target.getParent() instanceof PsiClass);
    assertEquals("B", ((PsiClass) target.getParent()).getName());
  }


  public void testSuperOfObject() throws Exception {
    PsiElement target = resolve();
    assertTrue(target instanceof PsiMethod);
    final PsiMethod method = (PsiMethod)target;
    assertEquals("clone", method.getName());
    assertEquals("java.lang.Object", method.getContainingClass().getQualifiedName());
  }


  public void testStaticVSNonStatic() throws Exception{
    PsiElement target = resolve();
    assertTrue(target instanceof PsiMethod);
    PsiMethod method = (PsiMethod) target;
    assertEquals(1, method.getParameterList().getParametersCount());
  }

  public void testClone() throws Exception{
    PsiElement target = resolve();
    assertTrue(target instanceof PsiMethod);
  }

  public void testThrowWithoutNew() throws Exception{
    PsiElement target = resolve();
    assertNull(target);
  }

  public void testThrowWithoutNew2() throws Exception {
    PsiElement target = advancedResolve().getElement();
    assertTrue(target instanceof PsiClass);
  }

  public void testNotAccessibleAccessClass() throws Exception{
    JavaResolveResult result = advancedResolve();
    assertFalse(result.isAccessible());
  }

  public void testInnerClass() throws Exception{
    JavaResolveResult result = advancedResolve();
    assertNotNull(result.getElement());
    assertTrue(result instanceof MethodCandidateInfo);
    assertTrue(!result.isValidResult());
    assertTrue(!((MethodCandidateInfo)result).isApplicable());
    final PsiClass aClass = ((PsiMethod)result.getElement()).getContainingClass();
    assertNotNull(aClass.getContainingClass());
  }

  public void testPrivateInSuperInner() throws Exception{
    JavaResolveResult result = advancedResolve();
    assertNotNull(result.getElement());
    assertTrue(result instanceof MethodCandidateInfo);
    assertTrue(!result.isValidResult());
    assertTrue(!result.isStaticsScopeCorrect());
  }

  public void testPrivateInSuperInner1() throws Exception{
    JavaResolveResult result = advancedResolve();
    assertNotNull(result.getElement());
    assertTrue(result instanceof MethodCandidateInfo);
    assertTrue(result.isValidResult());
  }

  public void testProtectedM() throws Exception {
    JavaResolveResult result = advancedResolve();
    assertTrue(result.getElement() instanceof PsiMethod);
    assertFalse(result.isAccessible());
  }

  // This test complile but it seems to be a bug.
  //public void testDependingParams1() throws Exception{
  //  PsiJavaReference ref = (PsiJavaReference) configureByFile("method/generics/DependingParams.java");
  //  final JavaResolveResult result = ref.advancedResolve(true);
  //  assertTrue(result.isValidResult());
  //}

  public void testImplementOrder() throws Exception {
    PsiElement target = resolve();
    assertTrue(target instanceof PsiMethod);
    PsiMethod method = (PsiMethod) target;
    assertEquals("II", method.getContainingClass().getName());
  }

  public void testObjectVsInterface() throws Exception {
    PsiElement target = resolve();
    assertTrue(target instanceof PsiMethod);
    PsiMethod method = (PsiMethod) target;
    assertEquals("PublicCloneable", method.getContainingClass().getName());
  }
}
