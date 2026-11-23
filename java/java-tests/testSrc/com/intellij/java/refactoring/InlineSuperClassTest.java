// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.LightMultiFileFixture;
import com.intellij.refactoring.inlineSuperClass.InlineSuperClassRefactoringProcessor;
import com.intellij.refactoring.util.DocCommentPolicy;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.refactoring.LightMultiFileFixtureKt.lightMultiFileFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author anna
 */
@TestApplication
public class InlineSuperClassTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_LATEST_WITH_LATEST_JDK);
  private final TestFixture<LightMultiFileFixture> multiFile =
    lightMultiFileFixture(projectFixture, JavaTestUtil.getJavaTestDataPath() + "/refactoring/inlineSuperClass/");

  @Test
  public void testInlineOneClass() { doTest(false, true); }
  @Test
  public void testInlineOneClassWithConflicts() { doTest(true, true); }
  @Test
  public void testAbstractOverrides() { doTest(); }
  @Test
  public void testSimple() { doTest(); }
  @Test
  public void testSimpleGenerics() { doTest(); }
  @Test
  public void testConflictGenerics() { doTest(true, false); }
  @Test
  public void testImports() { doTest(); }
  @Test
  public void testGenerics() { doTest(); }
  @Test
  public void testNewExpr() { doTest(); }
  @Test
  public void testConflictConstructors() { doTest(true, false); }
  @Test
  public void testConflictMultipleConstructors() { doTest(true, false); }
  @Test
  public void testMultipleConstructors() { doTest(); }
  @Test
  public void testImplicitChildConstructor() { doTest(); }
  @Test
  public void testClassInitializers() { doTest(); }
  @Test
  public void testStaticMembers() { doTest(); }
  @Test
  public void testSuperReference() { doTest(); }
  @Test
  public void testProtectedMember() { doTest(); }
  @Test
  public void testInnerClassReference() { doTest(); }
  @Test
  public void testStaticImport() { doTest(); }
  @Test
  public void testNewArrayInitializerExpr() { doTest(); }
  @Test
  public void testNewArrayDimensionsExpr() { doTest(); }
  @Test
  public void testNewArrayComplexDimensionsExpr() { doTest(); }
  @Test
  public void testChainedConstructors() { doTest(); }
  @Test
  public void testSuperConstructorWithReturnInside() { doTest(true, false); }
  @Test
  public void testSuperConstructorWithFieldInitialization() { doTest(); }
  @Test
  public void testSuperConstructorWithParam() { doTest(); }
  @Test
  public void testChildConstructorImplicitlyCallsSuper() { doTest(); }
  @Test
  public void testNoChildConstructorCallsSuperDefault() { doTest(); }
  @Test
  public void testReplaceGenericsInside() { doTest(); }
  @Test
  public void testMultipleSubclasses() { doTestMultipleSubclasses(); }
  @Test
  public void testMultipleSubstitutions() { doTestMultipleSubclasses(); }
  @Test
  public void testMultipleSubclassesInheritsOneBaseBase() { doTestMultipleSubclasses(); }
  @Test
  public void testInlineClassUsedInJavadocLink() { doTest(); }
  @Test
  public void testInlineSuperclassExtendsList() { doTest(); }
  @Test
  public void testInterfaceHierarchyWithSubstitution() { doTest(); }
  @Test
  public void testTypeParameterBound() { doTest();}
  @Test
  public void testInlineInterfaceDoNotChangeConstructor() { doTest(); }
  @Test
  public void testArrayTypeElements() { doTest(); }
  @Test
  public void testReferencesOnInnerClasses() { doTest(); }
  @Test
  public void testConflictOnMemberNotAccessibleThroughInheritor() { doTest(true, false); }
  @Test
  public void testOneAndKeepReferencesInAnotherInheritor() {
    doTest(false, true);
  }
  @Test
  public void testThisQualificationInsideAnonymous() { doTest(); }
  @Test
  public void testOrderOfInnerClasses() { doTest(); }
  @Test
  public void testSuperMethodWithoutBody() { doTest(); }
  @Test
  public void testSealedAbstractParentOneInheritor() { doTest(false, true); }
  @Test
  public void testSealedParentManyInheritors() { doTest(false, true); }
  @Test
  public void testSealedParentNonSealedInheritor() { doTest(false, true); }
  @Test
  public void testSealedGrandParentNonSealedInheritor() { doTest(false, true); }
  @Test
  public void testSealedParentInlineAll() { doTest(); }
  @Test
  public void testMultipleSealedParents() { doTest(false, true); }
  @Test
  public void testMultipleSuperCalls() { doTest(false, true); }

  private void doTest() {
    doTest(false, false);
  }

  private void doTest(boolean fail, final boolean inlineOne) {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    try {
      multiFile.get().doTest(() -> {
        GlobalSearchScope scope = GlobalSearchScope.allScope(myFixture.getProject());
        PsiClass aClass = myFixture.getJavaFacade().findClass("Test", scope);
        if (aClass == null) aClass = myFixture.getJavaFacade().findClass("p.Test", scope);
        assertNotNull(aClass, "Class Test not found");
        PsiClass superClass = myFixture.getJavaFacade().findClass("Super", scope);
        if (superClass == null) superClass = myFixture.getJavaFacade().findClass("p1.Super", scope);
        assertNotNull(superClass, "Class Super not found");
        new InlineSuperClassRefactoringProcessor(myFixture.getProject(), inlineOne ? aClass : null, superClass, DocCommentPolicy.ASIS).run();
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
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    multiFile.get().doTest(() -> {
      GlobalSearchScope scope = GlobalSearchScope.allScope(myFixture.getProject());
      PsiClass superClass = myFixture.getJavaFacade().findClass("Super", scope);
      if (superClass == null) superClass = myFixture.getJavaFacade().findClass("p1.Super", scope);
      assertNotNull(superClass, "Class Super not found");
      new InlineSuperClassRefactoringProcessor(myFixture.getProject(), null, superClass, DocCommentPolicy.ASIS).run();
    });
  }
}
