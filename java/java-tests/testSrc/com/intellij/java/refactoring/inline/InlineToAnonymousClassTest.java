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
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;

/**
 * @author yole
 */
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

  public void testNoFinalForJava8() {
    setLanguageLevel(LanguageLevel.HIGHEST);
    doTest(true, false);
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
    doTestConflict("Class cannot be inlined because a call to its member inside body", "Class cannot be inlined because a call to its member inside body");
  }

  public void testConflictInaccessibleOuterField() {
    doTestConflict(
      "Field <b><code>C2.a</code></b> that is used in inlined method is not accessible from call site(s) in method <b><code>C2User.test()</code></b>");
  }

  public void testGetClassConflict() {
    doTestConflict("Result of getClass() invocation would be changed", "Result of getClass() invocation would be changed");
  }

  public void doTestConflict(final String... expected) {
    InlineToAnonymousClassProcessor processor = prepareProcessor();
    UsageInfo[] usages = processor.findUsages();
    MultiMap<PsiElement,String> conflicts = processor.getConflicts(usages);
    assertEquals(expected.length, conflicts.size());
    final Iterator<? extends String> iterator = conflicts.values().iterator();
    for (String s : expected) {
      assertTrue(iterator.hasNext());
      assertEquals(s, iterator.next());
    }
  }

  private void doTestNoInline(final String expectedMessage) {
    String name = getTestName(false);
    @NonNls String fileName = "/refactoring/inlineToAnonymousClass/" + name + ".java";
    configureByFile(fileName);
    PsiElement element = TargetElementUtil
      .findTargetElement(myEditor, TargetElementUtil.ELEMENT_NAME_ACCEPTED | TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED);
    assertInstanceOf(element, PsiClass.class);

    String message = InlineToAnonymousClassHandler.getCannotInlineMessage((PsiClass) element);
    assertEquals(expectedMessage, message);
  }

  private void doTest(final boolean inlineThisOnly, final boolean searchInNonJavaFiles) {
    String name = getTestName(false);
    @NonNls String fileName = "/refactoring/inlineToAnonymousClass/" + name + ".java";
    configureByFile(fileName);
    performAction(inlineThisOnly, searchInNonJavaFiles);
    checkResultByFile(null, fileName + ".after", true);
  }

  private void doTestPreprocessUsages(final String expectedMessage) {
    configureByFile("/refactoring/inlineToAnonymousClass/" + getTestName(false) + ".java");
    PsiElement element = TargetElementUtil.findTargetElement(myEditor, TargetElementUtil
                                                                         .ELEMENT_NAME_ACCEPTED |
                                                                       TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED);
    assertInstanceOf(element, PsiClass.class);
    final PsiClass psiClass = (PsiClass)element;
    assertEquals(expectedMessage, InlineToAnonymousClassHandler.getCannotInlineMessage(psiClass));
  }

  private InlineToAnonymousClassProcessor prepareProcessor() {
    String name = getTestName(false);
    @NonNls String fileName = "/refactoring/inlineToAnonymousClass/" + name + ".java";
    configureByFile(fileName);
    PsiElement element = TargetElementUtil.findTargetElement(myEditor, TargetElementUtil
                                                                         .ELEMENT_NAME_ACCEPTED |
                                                                       TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED);
    assertInstanceOf(element, PsiClass.class);

    assertEquals(null, InlineToAnonymousClassHandler.getCannotInlineMessage((PsiClass) element));
    return new InlineToAnonymousClassProcessor(getProject(), (PsiClass) element, null, false, false, false);
  }

  private void performAction(final boolean inlineThisOnly, final boolean searchInNonJavaFiles) {
    PsiElement element = TargetElementUtil
      .findTargetElement(myEditor, TargetElementUtil.ELEMENT_NAME_ACCEPTED | TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED);
    PsiCall callToInline = InlineToAnonymousClassHandler.findCallToInline(myEditor);
    PsiClass classToInline = (PsiClass) element;
    assertEquals(null, InlineToAnonymousClassHandler.getCannotInlineMessage(classToInline));
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
    PsiElement element = TargetElementUtil
      .findTargetElement(myEditor, TargetElementUtil.ELEMENT_NAME_ACCEPTED | TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED);
    PsiCall callToInline = InlineToAnonymousClassHandler.findCallToInline(myEditor);
    PsiClass classToInline = (PsiClass) element;
    assertEquals(null, InlineToAnonymousClassHandler.getCannotInlineMessage(classToInline));
    final PsiClassType superType = InlineToAnonymousClassProcessor.getSuperType(classToInline);
    assertTrue(superType != null);
    assertEquals(canBeInvokedOnReference, InlineToAnonymousClassHandler.canBeInvokedOnReference(callToInline, superType));
  }

  @Override
  protected LanguageLevel getDefaultLanguageLevel() {
    return LanguageLevel.JDK_1_7;
  }
}