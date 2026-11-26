// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring.inline;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiCall;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.inline.InlineToAnonymousClassHandler;
import com.intellij.refactoring.inline.InlineToAnonymousClassProcessor;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaDescriptorOfTestName;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class InlineToAnonymousClassTest {
  private static final LightProjectDescriptor DESCRIPTOR = lightJavaTestDescriptor(LanguageLevel.JDK_1_7);
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(DESCRIPTOR);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture =
    lightJavaCodeInsightFixture(projectFixture, lightJavaDescriptorOfTestName(DESCRIPTOR), "/java/java-tests/testData");

  @Test
  public void testSimple() {
    doTest(false, false);
  }

  @Test
  public void testChangeToSuperType() {
    doTest(false, false);
  }

  @Test
  public void testImplementsInterface() {
    doTest(false, false);
  }

  @Test
  public void testConvertToLambdaJava8() {
    doTest(false, false);
  }

  @Test
  public void testClassInitializer() {
    doTest(false, false);
  }

  @Test
  public void testConstructor() {
    doTest(false, false);
  }

  @Test
  public void testConstructorWithArguments() {
    doTest(false, false);
  }

  @Test
  public void testUnrelatedParameters() {
    doTest(false, false);
  }

  @Test
  public void testConstructorWithArgumentsInExpression() {
    doTest(false, false);
  }

  @Test
  public void testMultipleConstructors() {
    doTest(false, false);
  }

  @Test
  public void testMethodUsage() {
    doTest(false, false);
  }

  @Test
  public void testConstructorArgumentToField() {
    doTest(false, false);
  }

  @Test
  public void testField() {
    doTest(false, false);
  }

  @Test
  public void testStaticConstantField() {
    doTest(false, false);
  }

  @Test
  public void testWritableInitializedField() {
    doTest(false, false);
  }

  @Test
  public void testNullInitializedField() {
    doTest(false, false);
  }

  @Test
  public void testInnerClass() {
    doTest(false, false);
  }

  @Test
  public void testInnerClassReference() {
    doTest(false, false);
  }

  @Test
  public void testConstructorToInstanceInitializer() {
    doTest(false, false);
  }

  @Test
  public void testNewExpressionContext() {
    doTest(false, false);
  }

  @Test
  public void testWritableFieldInitializedWithParameter() {
    doTest(false, false);
  }

  @Test
  public void testFieldInitializedWithVar() {
    doTest(false, false);
  }

  @Test
  public void testFieldVsLocalConflict() {
    doTest(false, false);
  }

  @Test
  public void testFieldVsParameterConflict() {
    doTest(false, false);
  }

  @Test
  public void testGenerics() {
    doTest(false, false);
  }

  @Test
  public void testGenericsSubstitute() {
    doTest(false, false);
  }

  @Test
  public void testGenericsFieldDeclaration() {
    doTest(false, false);
  }

  @Test
  public void testGenericsRawType() {
    doTest(false, false);
  }

  @Test
  public void testGenericsInTypeParameter() {
    doTest(false, false);
  }

  @Test
  public void testQualifyInner() {
    doTest(false, false);
  }

  @Test
  public void testQualifiedNew() {
    doTest(false, false);
  }

  @Test
  public void testChainedConstructors() {
    doTest(false, false);
  }

  @Test
  public void testChainedVarargConstructors() {
    doTest(false, false);
  }
  
  @Test
  public void testChainedVarargConstructorsCalledWithArray() {
    doTest(false, false);
  }

  @Test
  public void testInlineThisOnly() {
    doTest(true, false);
  }

  @Test
  public void testArrayType() {
    doTest(false, false);
  }

  @Test
  public void testArrayTypeWithGenerics() {
    doTest(false, false);
  }

  @Test
  public void testArrayInitializer() {
    doTest(false, false);
  }

  @Test
  public void testArrayInitializer2() {
    doTest(false, false);
  }

  @Test
  public void testVarargs() {
    doTest(false, false);
  }

  @Test
  public void testSelfReference() {
    doTest(false, false);
  }

  @Test
  public void testOuterClassFieldAccess() {
    doTest(false, false);
  }

  @Test
  public void testPrivateFieldUsedFromInnerClass() {
    doTest(false, false);
  }

  @Test
  public void testOverwriteInitializer() {
    doTest(false, false);
  }

  @Test
  public void testMultipleInnerClasses() {
    doTest(false, false);
  }

  @Test
  public void testConstructorArgumentInExpression() {
    doTest(false, false);
  }

  @Test
  public void testMethodCallInNewExpression() {
    doTest(false, false);
  }

  @Test
  public void testMethodCallInNewExpressionWithParens() {
    doTest(false, false);
  }

  @Test
  public void testRedundantImplementsInterface() {
    doTest(false, false);
  }

  @Test
  public void testStringInMethodCallFromConstructor() {
    doTest(false, false);
  }

  @Test
  public void testMultipleGeneratedVars() {
    doTest(false, false);
  }

  @Test
  public void testFieldAsConstructorParameter() {
    doTest(false, false);
  }

  @Test
  public void testQualifyParentStaticReferences() {
    doTest(false, false);
  }

  @Test
  public void testLocalClass() {
    doTest(false, true);
  }

  @Test
  public void testMultipleAssignments() {
    doTest(false, true);
  }

  @Test
  public void testParamTypeReplacement() {
    doTest(false, true);
  }

  @Test
  public void testBraces() {
    doTest(false, false);
  }

  @Test
  public void testAvailableInSupers() {
    doTest(false, false);
  }

  @Test
  public void testInlineInExpressionLambda() {
    doTest(false, false);
  }

  @Test
  public void testNoFinalForJava8() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.setModuleLanguageLevel(myFixture.getModule(), LanguageLevel.HIGHEST, myFixture.getTestRootDisposable());
    doTest(true, false);
  }

  @Test
  public void testStaticMembers() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.setModuleLanguageLevel(myFixture.getModule(), LanguageLevel.JDK_16, myFixture.getTestRootDisposable());
    doTest(false, false);
  }

  @Test
  public void testSealedNoMembers() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.setModuleLanguageLevel(myFixture.getModule(), LanguageLevel.JDK_17, myFixture.getTestRootDisposable());
    doTest(false, false);
  }

  @Test
  public void testSealedParentChildWithMembers() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.setModuleLanguageLevel(myFixture.getModule(), LanguageLevel.JDK_17, myFixture.getTestRootDisposable());
    doTestCanBeInvokedOnReference(false);
  }

  @Test
  public void testNoInlineAbstract() {
    doTestNoInline("Abstract classes cannot be inlined");
  }

  @Test
  public void testNoInlineInterface() {
    doTestNoInline("Interfaces cannot be inlined");
  }

  @Test
  public void testNoInlineEnum() {
    doTestNoInline("Enums cannot be inlined");
  }

  @Test
  public void testNoInlineAnnotationType() {
    doTestNoInline("Annotation types cannot be inlined");
  }
  
  @Test
  public void testNoInlineRecordJava21() {
    doTestNoInline("Record classes cannot be inlined");
  }

  @Test
  public void testNoInlineMultipleInterfaces() {
    doTestNoInline("Classes which implement multiple interfaces cannot be inlined");
  }

  @Test
  public void testNoInlineSuperclassInterface() {
    doTestNoInline("Classes which have a superclass and implement an interface cannot be inlined");
  }

  @Test
  public void testNoInlineMethodUsage() {
    doTestNoInline("Class cannot be inlined because there are usages of its methods not inherited from its superclass or interface");
  }

  @Test
  public void testNoInlineFieldUsage() {
    doTestNoInline("Class cannot be inlined because it has usages of fields not inherited from its superclass");
  }

  @Test
  public void testNoInlineNewWithInner() {
    doTestNoInline("Class cannot be inlined because it has usages of its inner classes");
  }

  @Test
  public void testNoInlineStaticField() {
    doTestNoInline("Class cannot be inlined because it has static fields with non-constant initializers");
  }

  @Test
  public void testNoInlineStaticNonFinalField() {
    doTestNoInline("Class cannot be inlined because it has static non-final fields");
  }

  @Test
  public void testNoInlineStaticMethod() {
    doTestNoInline("Class cannot be inlined because it has static methods");
  }

  @Test
  public void testNoInlineStaticInitializer() {
    doTestNoInline("Class cannot be inlined because it has static initializers");
  }

  @Test
  public void testNoInlineClassLiteral() {
    doTestPreprocessUsages("Class cannot be inlined because it has usages of its class literal");
  }

  @Test
  public void testNoInlineCatchClause() {
    doTestPreprocessUsages("Class cannot be inlined because it is used in a 'catch' clause");
  }

  @Test
  public void testNoInlineThrowsClause() {
    doTestPreprocessUsages("Class cannot be inlined because it is used in a 'throws' clause");
  }

  @Test
  public void testNoInlineThisQualifier() {
    doTestPreprocessUsages("Class cannot be inlined because it is used as a 'this' qualifier");
  }

  @Test
  public void testNoInlineUnresolvedConstructor() {
    doTestPreprocessUsages("Class cannot be inlined because a call to its constructor is unresolved");
  }

  @Test
  public void testNoInlineUnresolvedConstructor2() {
    doTestPreprocessUsages("Class cannot be inlined because a call to its constructor is unresolved");
  }

  @Test
  public void testNoInlineStaticInnerClass() {
    doTestNoInline("Class cannot be inlined because it has static inner classes");
  }

  @Test
  public void testNoInlineReturnInConstructor() {
    doTestNoInline("Class cannot be inlined because its constructor contains 'return' statements");
  }

  @Test
  public void testNoInlineUnresolvedSuperclass() {
    doTestNoInline("Class cannot be inlined because its superclass cannot be resolved");
  }

  @Test
  public void testNoInlineUnresolvedInterface() {
    doTestNoInline("Class cannot be inlined because an interface implemented by it cannot be resolved");
  }

  @Test
  public void testNoInlineLibraryClass() {
    doTestNoInline("Library classes cannot be inlined");
  }

  @Test
  public void testNoInlineTypeParameter() {
    doTestNoInline("Type parameters cannot be inlined");
  }

  @Test
  public void testNoInlineNoUsages() {
    doTestPreprocessUsages("Class is never used");
  }

  @Test
  public void testNoInlineRecursiveAccess() {
    doTestConflict("Class cannot be inlined because it accesses its own members on another instance", 
                   "Class cannot be inlined because it accesses its own members on another instance",
                   "Class cannot be inlined because it calls its own constructor");
  }

  @Test
  public void testConflictInaccessibleOuterField() {
    doTestConflict("Field <b><code>C2.a</code></b> will not be accessible when class <b><code>C2.C2Inner</code></b> " +
                   "is inlined into method <b><code>C2User.test()</code></b>");
  }

  @Test
  public void testGetClassConflict() {
    doTestConflict("Result of getClass() invocation would be changed", "Result of getClass() invocation would be changed");
  }

  public void doTestConflict(final String... expected) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.runInCommand(() -> {
      InlineToAnonymousClassProcessor processor = prepareProcessor();
      UsageInfo[] usages = processor.findUsages();
      String[] conflicts = ArrayUtil.toStringArray(processor.getConflicts(usages).values());
      assertEquals(expected.length, conflicts.length);
      Arrays.sort(conflicts); // get stable order
      for (int i = 0; i < expected.length; i++) {
        assertEquals(expected[i], conflicts[i]);
      }
    });
  }

  private void doTestNoInline(final String expectedMessage) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    @NonNls String fileName = "/refactoring/inlineToAnonymousClass/" + myFixture.getTestName(false) + ".java";
    myFixture.configureByFile(fileName);
    myFixture.runInCommand(() -> {
      PsiClass aClass = getClassToInline();
      String message = InlineToAnonymousClassHandler.getCannotInlineMessage(aClass);
      assertEquals(expectedMessage, message);
    });
  }

  private void doTest(final boolean inlineThisOnly, final boolean searchInNonJavaFiles) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    @NonNls String fileName = "/refactoring/inlineToAnonymousClass/" + myFixture.getTestName(false) + ".java";
    myFixture.configureByFile(fileName);
    myFixture.runInCommand(() -> {
      performAction(inlineThisOnly, searchInNonJavaFiles);
      myFixture.checkResultByFile(fileName + ".after", true);
    });
  }

  private void doTestPreprocessUsages(final String expectedMessage) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile("/refactoring/inlineToAnonymousClass/" + myFixture.getTestName(false) + ".java");
    myFixture.runInCommand(() -> {
      final PsiClass aClass = getClassToInline();
      assertEquals(expectedMessage, InlineToAnonymousClassHandler.getCannotInlineMessage(aClass));
    });
  }

  private InlineToAnonymousClassProcessor prepareProcessor() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    @NonNls String fileName = "/refactoring/inlineToAnonymousClass/" + myFixture.getTestName(false) + ".java";
    myFixture.configureByFile(fileName);
    PsiClass aClass = getClassToInline();
    assertNull(InlineToAnonymousClassHandler.getCannotInlineMessage(aClass));
    return new InlineToAnonymousClassProcessor(myFixture.getProject(), aClass, null, false, false, false);
  }

  private void performAction(final boolean inlineThisOnly, final boolean searchInNonJavaFiles) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiCall callToInline = InlineToAnonymousClassHandler.findCallToInline(myFixture.getEditor());
    PsiClass classToInline = getClassToInline();
    assertNull(InlineToAnonymousClassHandler.getCannotInlineMessage(classToInline));
    assertTrue(new InlineToAnonymousClassHandler().canInlineElement(classToInline));
    final InlineToAnonymousClassProcessor processor = new InlineToAnonymousClassProcessor(myFixture.getProject(), classToInline, callToInline, inlineThisOnly,
                                                                                          false, searchInNonJavaFiles);
    UsageInfo[] usages = processor.findUsages();
    MultiMap<PsiElement, String> conflicts = processor.getConflicts(usages);
    assertEquals(0, conflicts.size());
    processor.run();
  }

  @Test
  public void testCanBeInvokedOnReference() {
    doTestCanBeInvokedOnReference(true);
  }

  @Test
  public void testCanBeInvokedOnReference1() {
    doTestCanBeInvokedOnReference(true);
  }

  @Test
  public void testCanBeInvokedOnReferenceSubstitution() {
    doTestCanBeInvokedOnReference(true);
  }

  @Test
  public void testCanBeInvokedOnReferenceSubstitution1() {
    doTestCanBeInvokedOnReference(true);
  }

  @Test
  public void testCanBeInvokedOnReferenceVarargs() {
    doTestCanBeInvokedOnReference(true);
  }

  @Test
  public void testCantBeInvokedOnReference() {
    doTestCanBeInvokedOnReference(false);
  }

  @Test
  public void testCantBeInvokedOnReference1() {
    doTestCanBeInvokedOnReference(false);
  }

  @Test
  public void testCantBeInvokedOnReferenceReturnStatement() {
    doTestCanBeInvokedOnReference(false);
  }

  @Test
  public void testCanBeInvokedOnReferenceSyncStatement() {
    doTestCanBeInvokedOnReference(true);
  }

  private void doTestCanBeInvokedOnReference(boolean canBeInvokedOnReference) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile("/refactoring/inlineToAnonymousClass/" + myFixture.getTestName(false) + ".java");
    myFixture.runInCommand(() -> {
      PsiCall callToInline = InlineToAnonymousClassHandler.findCallToInline(myFixture.getEditor());
      PsiClass classToInline = getClassToInline();
      assertNull(InlineToAnonymousClassHandler.getCannotInlineMessage(classToInline));
      final PsiClassType superType = InlineToAnonymousClassProcessor.getSuperType(classToInline);
      assertNotNull(superType);
      assertEquals(canBeInvokedOnReference, InlineToAnonymousClassHandler.canBeInvokedOnReference(callToInline, superType));
    });
  }

  private @NotNull PsiClass getClassToInline() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    int flags = TargetElementUtil.ELEMENT_NAME_ACCEPTED | TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED;
    PsiElement element = TargetElementUtil.findTargetElement(myFixture.getEditor(), flags);
    assertInstanceOf(PsiClass.class, element);
    return (PsiClass)element;
  }
}