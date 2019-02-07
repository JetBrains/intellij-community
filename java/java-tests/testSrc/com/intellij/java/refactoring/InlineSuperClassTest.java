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
package com.intellij.java.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.LightMultiFileTestCase;
import com.intellij.refactoring.inlineSuperClass.InlineSuperClassRefactoringProcessor;
import com.intellij.refactoring.util.DocCommentPolicy;

/**
 * @author anna
 */
public class InlineSuperClassTest extends LightMultiFileTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/refactoring/inlineSuperClass/";
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
