// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.LightMultiFileTestCase;
import com.intellij.refactoring.inlineSuperClass.InlineSuperClassRefactoringProcessor;
import com.intellij.refactoring.util.DocCommentPolicy;
import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;

/**
 * @author anna
 */
public class InlineSuperClassTest extends LightMultiFileTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/refactoring/inlineSuperClass/";
  }

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_LATEST_WITH_LATEST_JDK;
  }

  public void testInlineOneClass() { doTest(false, true); }
  public void testInlineOneClassWithConflicts() { doTest(true, true); }
  public void testAbstractOverrides() { doTest(); }
  public void testSimple() { doTest(); }
  public void testSimpleGenerics() { doTest(); }
  public void testConflictGenerics() { doTest(true, false); }
  public void testImports() { doTest(); }
  public void testGenerics() { doTest(); }
  public void testNewExpr() { doTest(); }
  public void testConflictConstructors() { doTest(true, false); }
  public void testConflictMultipleConstructors() { doTest(true, false); }
  public void testMultipleConstructors() { doTest(); }
  public void testImplicitChildConstructor() { doTest(); }
  public void testClassInitializers() { doTest(); }
  public void testStaticMembers() { doTest(); }
  public void testSuperReference() { doTest(); }
  public void testProtectedMember() { doTest(); }
  public void testInnerClassReference() { doTest(); }
  public void testStaticImport() { doTest(); }
  public void testNewArrayInitializerExpr() { doTest(); }
  public void testNewArrayDimensionsExpr() { doTest(); }
  public void testNewArrayComplexDimensionsExpr() { doTest(); }
  public void testChainedConstructors() { doTest(); }
  public void testSuperConstructorWithReturnInside() { doTest(true, false); }
  public void testSuperConstructorWithFieldInitialization() { doTest(); }
  public void testSuperConstructorWithParam() { doTest(); }
  public void testChildConstructorImplicitlyCallsSuper() { doTest(); }
  public void testNoChildConstructorCallsSuperDefault() { doTest(); }
  public void testReplaceGenericsInside() { doTest(); }
  public void testMultipleSubclasses() { doTestMultipleSubclasses(); }
  public void testMultipleSubstitutions() { doTestMultipleSubclasses(); }
  public void testMultipleSubclassesInheritsOneBaseBase() { doTestMultipleSubclasses(); }
  public void testInlineClassUsedInJavadocLink() { doTest(); }
  public void testInlineSuperclassExtendsList() { doTest(); }
  public void testInterfaceHierarchyWithSubstitution() { doTest(); }
  public void testTypeParameterBound() { doTest();}
  public void testInlineInterfaceDoNotChangeConstructor() { doTest(); }
  public void testArrayTypeElements() { doTest(); }
  public void testReferencesOnInnerClasses() { doTest(); }
  public void testConflictOnMemberNotAccessibleThroughInheritor() { doTest(true, false); }
  public void testOneAndKeepReferencesInAnotherInheritor() {
    doTest(false, true);
  }
  public void testThisQualificationInsideAnonymous() { doTest(); }
  public void testOrderOfInnerClasses() { doTest(); }
  public void testSuperMethodWithoutBody() { doTest(); }
  public void testSealedAbstractParentOneInheritor() { doTest(false, true); }
  public void testSealedParentManyInheritors() { doTest(false, true); }
  public void testSealedParentNonSealedInheritor() { doTest(false, true); }
  public void testSealedGrandParentNonSealedInheritor() { doTest(false, true); }
  public void testSealedParentInlineAll() { doTest(); }
  public void testMultipleSealedParents() { doTest(false, true); }
  public void testMultipleSuperCalls() { doTest(false, true); }

  private void doTest() {
    doTest(false, false);
  }

  private void doTest(boolean fail, final boolean inlineOne) {
    try {
      doTest(() -> {
        GlobalSearchScope scope = GlobalSearchScope.allScope(getProject());
        PsiClass aClass = myFixture.getJavaFacade().findClass("Test", scope);
        if (aClass == null) aClass = myFixture.getJavaFacade().findClass("p.Test", scope);
        assertNotNull("Class Test not found", aClass);
        PsiClass superClass = myFixture.getJavaFacade().findClass("Super", scope);
        if (superClass == null) superClass = myFixture.getJavaFacade().findClass("p1.Super", scope);
        assertNotNull("Class Super not found", superClass);
        new InlineSuperClassRefactoringProcessor(getProject(), inlineOne ? aClass : null, superClass, DocCommentPolicy.ASIS).run();
      });
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      if (fail) {
        return;
      }
      else {
        throw e;
      }
    }
    if (fail) {
      fail("Conflict was not detected");
    }
  }

  private void doTestMultipleSubclasses() {
    doTest(() -> {
      GlobalSearchScope scope = GlobalSearchScope.allScope(getProject());
      PsiClass superClass = myFixture.getJavaFacade().findClass("Super", scope);
      if (superClass == null) superClass = myFixture.getJavaFacade().findClass("p1.Super", scope);
      assertNotNull("Class Super not found", superClass);
      new InlineSuperClassRefactoringProcessor(getProject(), null, superClass, DocCommentPolicy.ASIS).run();
    });
  }
}
