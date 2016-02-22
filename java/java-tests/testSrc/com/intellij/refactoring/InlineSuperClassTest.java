/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.inlineSuperClass.InlineSuperClassRefactoringProcessor;
import com.intellij.refactoring.util.DocCommentPolicy;
import org.jetbrains.annotations.NotNull;

/**
 * @author anna
 * @since 20-Aug-2008
 */
public class InlineSuperClassTest extends MultiFileTestCase {
  @NotNull
  @Override
  protected String getTestRoot() {
    return "/refactoring/inlineSuperClass/";
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
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
  public void testStaticMembers() { doTest(); }
  public void testSuperReference() { doTest(); }
  public void testInnerClassReference() { doTest(); }
  public void testStaticImport() { doTest(); }
  public void testNewArrayInitializerExpr() { doTest(); }
  public void testNewArrayDimensionsExpr() { doTest(); }
  public void testNewArrayComplexDimensionsExpr() { doTest(); }
  public void testSuperConstructorWithReturnInside() { doTest(true, false); }
  public void testSuperConstructorWithFieldInitialization() { doTest(); }
  public void testSuperConstructorWithParam() { doTest(); }
  public void testChildConstructorImplicitlyCallsSuper() { doTest(); }
  public void testNoChildConstructorCallsSuperDefault() { doTest(); }
  public void testReplaceGenericsInside() { doTest(); }
  public void testMultipleSubclasses() { doTestMultipleSubclasses(); }
  public void testMultipleSubstitutions() { doTestMultipleSubclasses(); }
  public void testMultipleSubclassesInheritsOneBaseBase() { doTestMultipleSubclasses(); }
  public void testInlineSuperclassExtendsList() { doTest(); }
  public void testInterfaceHierarchyWithSubstitution() { doTest(); }
  public void testTypeParameterBound() { doTest();}

  private void doTest() {
    doTest(false, false);
  }

  private void doTest(boolean fail, final boolean inlineOne) {
    try {
      doTest((rootDir, rootAfter) -> {
        GlobalSearchScope scope = GlobalSearchScope.allScope(myProject);
        PsiClass aClass = myJavaFacade.findClass("Test", scope);
        if (aClass == null) aClass = myJavaFacade.findClass("p.Test", scope);
        assertNotNull("Class Test not found", aClass);
        PsiClass superClass = myJavaFacade.findClass("Super", scope);
        if (superClass == null) superClass = myJavaFacade.findClass("p1.Super", scope);
        assertNotNull("Class Super not found", superClass);
        new InlineSuperClassRefactoringProcessor(myProject, inlineOne ? aClass : null, superClass, DocCommentPolicy.ASIS, aClass).run();
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
    doTest((rootDir, rootAfter) -> {
      GlobalSearchScope scope = GlobalSearchScope.allScope(myProject);
      PsiClass superClass = myJavaFacade.findClass("Super", scope);
      if (superClass == null) superClass = myJavaFacade.findClass("p1.Super", scope);
      assertNotNull("Class Super not found", superClass);
      PsiClass target1 = myJavaFacade.findClass("Test", scope);
      PsiClass target2 = myJavaFacade.findClass("Test1", scope);
      new InlineSuperClassRefactoringProcessor(myProject, null, superClass, DocCommentPolicy.ASIS, target1, target2).run();
    });
  }
}
