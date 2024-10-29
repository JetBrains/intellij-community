// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring.inline;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.java.refactoring.LightRefactoringTestCase;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiCall;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.inline.InlineToAnonymousClassHandler;
import com.intellij.refactoring.inline.InlineToAnonymousClassProcessor;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public class InlineToAnonymousClassTest extends LightRefactoringTestCase {
  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void testSimple() {
    doTest(false, false);
  }

  public void testChangeToSuperType() {
    doTest(false, false);
  }

  public void testImplementsInterface() {
    doTest(false, false);
  }

  public void testConvertToLambdaJava8() {
    doTest(false, false);
  }

  public void testClassInitializer() {
    doTest(false, false);
  }

  public void testConstructor() {
    doTest(false, false);
  }

  public void testConstructorWithArguments() {
    doTest(false, false);
  }

  public void testUnrelatedParameters() {
    doTest(false, false);
  }

  public void testConstructorWithArgumentsInExpression() {
    doTest(false, false);
  }

  public void testMultipleConstructors() {
    doTest(false, false);
  }

  public void testMethodUsage() {
    doTest(false, false);
  }

  public void testConstructorArgumentToField() {
    doTest(false, false);
  }

  public void testField() {
    doTest(false, false);
  }

  public void testStaticConstantField() {
    doTest(false, false);
  }

  public void testWritableInitializedField() {
    doTest(false, false);
  }

  public void testNullInitializedField() {
    doTest(false, false);
  }

  public void testInnerClass() {
    doTest(false, false);
  }

  public void testConstructorToInstanceInitializer() {
    doTest(false, false);
  }

  public void testNewExpressionContext() {
    doTest(false, false);
  }

  public void testWritableFieldInitializedWithParameter() {
    doTest(false, false);
  }

  public void testFieldInitializedWithVar() {
    doTest(false, false);
  }

  public void testFieldVsLocalConflict() {
    doTest(false, false);
  }

  public void testFieldVsParameterConflict() {
    doTest(false, false);
  }

  public void testGenerics() {
    doTest(false, false);
  }

  public void testGenericsSubstitute() {
    doTest(false, false);
  }

  public void testGenericsFieldDeclaration() {
    doTest(false, false);
  }

  public void testGenericsRawType() {
    doTest(false, false);
  }

  public void testGenericsInTypeParameter() {
    doTest(false, false);
  }

  public void testQualifyInner() {
    doTest(false, false);
  }

  public void testQualifiedNew() {
    doTest(false, false);
  }

  public void testChainedConstructors() {
    doTest(false, false);
  }

  public void testChainedVarargConstructors() {
    doTest(false, false);
  }

  public void testInlineThisOnly() {
    doTest(true, false);
  }

  public void testArrayType() {
    doTest(false, false);
  }

  public void testArrayTypeWithGenerics() {
    doTest(false, false);
  }

  public void testArrayInitializer() {
    doTest(false, false);
  }

  public void testArrayInitializer2() {
    doTest(false, false);
  }

  public void testVarargs() {
    doTest(false, false);
  }

  public void testSelfReference() {
    doTest(false, false);
  }

  public void testOuterClassFieldAccess() {
    doTest(false, false);
  }

  public void testPrivateFieldUsedFromInnerClass() {
    doTest(false, false);
  }

  public void testOverwriteInitializer() {
    doTest(false, false);
  }

  public void testMultipleInnerClasses() {
    doTest(false, false);
  }

  public void testConstructorArgumentInExpression() {
    doTest(false, false);
  }

  public void testMethodCallInNewExpression() {
    doTest(false, false);
  }

  public void testMethodCallInNewExpressionWithParens() {
    doTest(false, false);
  }

  public void testRedundantImplementsInterface() {
    doTest(false, false);
  }

  public void testStringInMethodCallFromConstructor() {
    doTest(false, false);
  }

  public void testMultipleGeneratedVars() {
    doTest(false, false);
  }

  public void testFieldAsConstructorParameter() {
    doTest(false, false);
  }

  public void testQualifyParentStaticReferences() {
    doTest(false, false);
  }

  public void testLocalClass() {
    doTest(false, true);
  }

  public void testMultipleAssignments() {
    doTest(false, true);
  }

  public void testParamTypeReplacement() {
    doTest(false, true);
  }

  public void testBraces() {
    doTest(false, false);
  }

  public void testAvailableInSupers() {
    doTest(false, false);
  }

  public void testInlineInExpressionLambda() {
    doTest(false, false);
  }

  public void testNoFinalForJava8() {
    setLanguageLevel(LanguageLevel.HIGHEST);
    doTest(true, false);
  }

  public void testStaticMembers() {
    setLanguageLevel(LanguageLevel.JDK_16);
    doTest(false, false);
  }

  public void testSealedNoMembers() {
    setLanguageLevel(LanguageLevel.JDK_17);
    doTest(false, false);
  }

  public void testSealedParentChildWithMembers() {
    setLanguageLevel(LanguageLevel.JDK_17);
    doTestCanBeInvokedOnReference(false);
  }

  public void testNoInlineAbstract() {
    doTestNoInline("Abstract classes cannot be inlined");
  }

  public void testNoInlineInterface() {
    doTestNoInline("Interfaces cannot be inlined");
  }

  public void testNoInlineEnum() {
    doTestNoInline("Enums cannot be inlined");
  }

  public void testNoInlineAnnotationType() {
    doTestNoInline("Annotation types cannot be inlined");
  }
  
  public void testNoInlineRecordJava21() {
    doTestNoInline("Record classes cannot be inlined");
  }

  public void testNoInlineMultipleInterfaces() {
    doTestNoInline("Classes which implement multiple interfaces cannot be inlined");
  }

  public void testNoInlineSuperclassInterface() {
    doTestNoInline("Classes which have a superclass and implement an interface cannot be inlined");
  }

  public void testNoInlineMethodUsage() {
    doTestNoInline("Class cannot be inlined because there are usages of its methods not inherited from its superclass or interface");
  }

  public void testNoInlineFieldUsage() {
    doTestNoInline("Class cannot be inlined because it has usages of fields not inherited from its superclass");
  }

  public void testNoInlineNewWithInner() {
    doTestNoInline("Class cannot be inlined because it has usages of its inner classes");
  }

  public void testNoInlineStaticField() {
    doTestNoInline("Class cannot be inlined because it has static fields with non-constant initializers");
  }

  public void testNoInlineStaticNonFinalField() {
    doTestNoInline("Class cannot be inlined because it has static non-final fields");
  }

  public void testNoInlineStaticMethod() {
    doTestNoInline("Class cannot be inlined because it has static methods");
  }

  public void testNoInlineStaticInitializer() {
    doTestNoInline("Class cannot be inlined because it has static initializers");
  }

  public void testNoInlineClassLiteral() {
    doTestPreprocessUsages("Class cannot be inlined because it has usages of its class literal");
  }

  public void testNoInlineCatchClause() {
    doTestPreprocessUsages("Class cannot be inlined because it is used in a 'catch' clause");
  }

  public void testNoInlineThrowsClause() {
    doTestPreprocessUsages("Class cannot be inlined because it is used in a 'throws' clause");
  }

  public void testNoInlineThisQualifier() {
    doTestPreprocessUsages("Class cannot be inlined because it is used as a 'this' qualifier");
  }

  public void testNoInlineUnresolvedConstructor() {
    doTestPreprocessUsages("Class cannot be inlined because a call to its constructor is unresolved");
  }

  public void testNoInlineUnresolvedConstructor2() {
    doTestPreprocessUsages("Class cannot be inlined because a call to its constructor is unresolved");
  }

  public void testNoInlineStaticInnerClass() {
    doTestNoInline("Class cannot be inlined because it has static inner classes");
  }

  public void testNoInlineReturnInConstructor() {
    doTestNoInline("Class cannot be inlined because its constructor contains 'return' statements");
  }

  public void testNoInlineUnresolvedSuperclass() {
    doTestNoInline("Class cannot be inlined because its superclass cannot be resolved");
  }

  public void testNoInlineUnresolvedInterface() {
    doTestNoInline("Class cannot be inlined because an interface implemented by it cannot be resolved");
  }

  public void testNoInlineLibraryClass() {
    doTestNoInline("Library classes cannot be inlined");
  }

  public void testNoInlineTypeParameter() {
    doTestNoInline("Type parameters cannot be inlined");
  }

  public void testNoInlineNoUsages() {
    doTestPreprocessUsages("Class is never used");
  }

  public void testNoInlineRecursiveAccess() {
    doTestConflict("Class cannot be inlined because it accesses its own members on another instance", 
                   "Class cannot be inlined because it accesses its own members on another instance",
                   "Class cannot be inlined because it calls its own constructor");
  }

  public void testConflictInaccessibleOuterField() {
    doTestConflict("Field <b><code>C2.a</code></b> will not be accessible when class <b><code>C2.C2Inner</code></b> " +
                   "is inlined into method <b><code>C2User.test()</code></b>");
  }

  public void testGetClassConflict() {
    doTestConflict("Result of getClass() invocation would be changed", "Result of getClass() invocation would be changed");
  }

  public void doTestConflict(final String... expected) {
    InlineToAnonymousClassProcessor processor = prepareProcessor();
    UsageInfo[] usages = processor.findUsages();
    String[] conflicts = ArrayUtil.toStringArray(processor.getConflicts(usages).values());
    assertEquals(expected.length, conflicts.length);
    Arrays.sort(conflicts); // get stable order
    for (int i = 0; i < expected.length; i++) {
      assertEquals(expected[i], conflicts[i]);
    }
  }

  private void doTestNoInline(final String expectedMessage) {
    @NonNls String fileName = "/refactoring/inlineToAnonymousClass/" + getTestName(false) + ".java";
    configureByFile(fileName);
    PsiClass aClass = getClassToInline();
    String message = InlineToAnonymousClassHandler.getCannotInlineMessage(aClass);
    assertEquals(expectedMessage, message);
  }

  private void doTest(final boolean inlineThisOnly, final boolean searchInNonJavaFiles) {
    @NonNls String fileName = "/refactoring/inlineToAnonymousClass/" + getTestName(false) + ".java";
    configureByFile(fileName);
    performAction(inlineThisOnly, searchInNonJavaFiles);
    checkResultByFile(null, fileName + ".after", true);
  }

  private void doTestPreprocessUsages(final String expectedMessage) {
    configureByFile("/refactoring/inlineToAnonymousClass/" + getTestName(false) + ".java");
    final PsiClass aClass = getClassToInline();
    assertEquals(expectedMessage, InlineToAnonymousClassHandler.getCannotInlineMessage(aClass));
  }

  private InlineToAnonymousClassProcessor prepareProcessor() {
    @NonNls String fileName = "/refactoring/inlineToAnonymousClass/" + getTestName(false) + ".java";
    configureByFile(fileName);
    PsiClass aClass = getClassToInline();
    assertNull(InlineToAnonymousClassHandler.getCannotInlineMessage(aClass));
    return new InlineToAnonymousClassProcessor(getProject(), aClass, null, false, false, false);
  }

  private void performAction(final boolean inlineThisOnly, final boolean searchInNonJavaFiles) {
    PsiCall callToInline = InlineToAnonymousClassHandler.findCallToInline(getEditor());
    PsiClass classToInline = getClassToInline();
    assertNull(InlineToAnonymousClassHandler.getCannotInlineMessage(classToInline));
    assertTrue(new InlineToAnonymousClassHandler().canInlineElement(classToInline));
    final InlineToAnonymousClassProcessor processor = new InlineToAnonymousClassProcessor(getProject(), classToInline, callToInline, inlineThisOnly,
                                                                                          false, searchInNonJavaFiles);
    UsageInfo[] usages = processor.findUsages();
    MultiMap<PsiElement, String> conflicts = processor.getConflicts(usages);
    assertEquals(0, conflicts.size());
    processor.run();
  }

  public void testCanBeInvokedOnReference() {
    doTestCanBeInvokedOnReference(true);
  }

  public void testCanBeInvokedOnReference1() {
    doTestCanBeInvokedOnReference(true);
  }

  public void testCanBeInvokedOnReferenceSubstitution() {
    doTestCanBeInvokedOnReference(true);
  }

  public void testCanBeInvokedOnReferenceSubstitution1() {
    doTestCanBeInvokedOnReference(true);
  }

  public void testCanBeInvokedOnReferenceVarargs() {
    doTestCanBeInvokedOnReference(true);
  }

  public void testCantBeInvokedOnReference() {
    doTestCanBeInvokedOnReference(false);
  }

  public void testCantBeInvokedOnReference1() {
    doTestCanBeInvokedOnReference(false);
  }

  public void testCantBeInvokedOnReferenceReturnStatement() {
    doTestCanBeInvokedOnReference(false);
  }

  public void testCanBeInvokedOnReferenceSyncStatement() {
    doTestCanBeInvokedOnReference(true);
  }

  private void doTestCanBeInvokedOnReference(boolean canBeInvokedOnReference) {
    configureByFile("/refactoring/inlineToAnonymousClass/" + getTestName(false) + ".java");
    PsiCall callToInline = InlineToAnonymousClassHandler.findCallToInline(getEditor());
    PsiClass classToInline = getClassToInline();
    assertNull(InlineToAnonymousClassHandler.getCannotInlineMessage(classToInline));
    final PsiClassType superType = InlineToAnonymousClassProcessor.getSuperType(classToInline);
    assertNotNull(superType);
    assertEquals(canBeInvokedOnReference, InlineToAnonymousClassHandler.canBeInvokedOnReference(callToInline, superType));
  }

  private @NotNull PsiClass getClassToInline() {
    int flags = TargetElementUtil.ELEMENT_NAME_ACCEPTED | TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED;
    PsiElement element = TargetElementUtil.findTargetElement(getEditor(), flags);
    assertInstanceOf(element, PsiClass.class);
    return (PsiClass)element;
  }

  @Override
  protected LanguageLevel getDefaultLanguageLevel() {
    return LanguageLevel.JDK_1_7;
  }
}