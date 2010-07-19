package com.intellij.refactoring.inline;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.psi.PsiCall;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.LightCodeInsightTestCase;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NonNls;

import java.util.Iterator;

/**
 * @author yole
 */
public class InlineToAnonymousClassTest extends LightCodeInsightTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  protected Sdk getProjectJDK() {
    return JavaSdkImpl.getMockJdk17("java 1.5");
  }

  public void testSimple() throws Exception {
    doTest(false, false);
  }

  public void testChangeToSuperType() throws Exception {
    doTest(false, false);
  }

  public void testImplementsInterface() throws Exception {
    doTest(false, false);
  }

  public void testClassInitializer() throws Exception {
    doTest(false, false);
  }

  public void testConstructor() throws Exception {
    doTest(false, false);
  }

  public void testConstructorWithArguments() throws Exception {
    doTest(false, false);
  }

  public void testConstructorWithArgumentsInExpression() throws Exception {
    doTest(false, false);
  }

  public void testMultipleConstructors() throws Exception {
    doTest(false, false);
  }

  public void testMethodUsage() throws Exception {
    doTest(false, false);
  }

  public void testConstructorArgumentToField() throws Exception {
    doTest(false, false);
  }

  public void testField() throws Exception {
    doTest(false, false);
  }

  public void testStaticConstantField() throws Exception {
    doTest(false, false);
  }

  public void testWritableInitializedField() throws Exception {
    doTest(false, false);
  }

  public void testNullInitializedField() throws Exception {
    doTest(false, false);
  }

  public void testInnerClass() throws Exception {
    doTest(false, false);
  }

  public void testConstructorToInstanceInitializer() throws Exception {
    doTest(false, false);
  }

  public void testNewExpressionContext() throws Exception {
    doTest(false, false);
  }

  public void testWritableFieldInitializedWithParameter() throws Exception {
    doTest(false, false);
  }

  public void testFieldInitializedWithVar() throws Exception {
    doTest(false, false);
  }

  public void testFieldVsLocalConflict() throws Exception {
    doTest(false, false);
  }

  public void testFieldVsParameterConflict() throws Exception {
    doTest(false, false);
  }

  public void testGenerics() throws Exception {
    doTest(false, false);
  }

  public void testGenericsSubstitute() throws Exception {
    doTest(false, false);
  }

  public void testGenericsFieldDeclaration() throws Exception {
    doTest(false, false);
  }

  public void testGenericsRawType() throws Exception {
    doTest(false, false);
  }

  public void testGenericsInTypeParameter() throws Exception {
    doTest(false, false);
  }

  public void testQualifyInner() throws Exception {
    doTest(false, false);
  }

  public void testQualifiedNew() throws Exception {
    doTest(false, false);
  }

  public void testChainedConstructors() throws Exception {
    doTest(false, false);
  }

  public void testChainedVarargConstructors() throws Exception {
    doTest(false, false);
  }

  public void testInlineThisOnly() throws Exception {
    doTest(true, false);
  }

  public void testArrayType() throws Exception {
    doTest(false, false);
  }

  public void testArrayTypeWithGenerics() throws Exception {
    doTest(false, false);
  }

  public void testArrayInitializer() throws Exception {
    doTest(false, false);
  }

  public void testVarargs() throws Exception {
    doTest(false, false);
  }

  public void testSelfReference() throws Exception {
    doTest(false, false);
  }

  public void testOuterClassFieldAccess() throws Exception {
    doTest(false, false);
  }

  public void testPrivateFieldUsedFromInnerClass() throws Exception {
    doTest(false, false);
  }

  public void testOverwriteInitializer() throws Exception {
    doTest(false, false);
  }

  public void testMultipleInnerClasses() throws Exception {
    doTest(false, false);
  }
  
  public void testConstructorArgumentInExpression() throws Exception {
    doTest(false, false);
  }

  public void testMethodCallInNewExpression() throws Exception {
    doTest(false, false);
  }

  public void testMethodCallInNewExpressionWithParens() throws Exception {
    doTest(false, false);
  }

  public void testRedundantImplementsInterface() throws Exception {
    doTest(false, false);
  }

  public void testStringInMethodCallFromConstructor() throws Exception {
    doTest(false, false);
  }

  public void testMultipleGeneratedVars() throws Exception {
    doTest(false, false);
  }

  public void testFieldAsConstructorParameter() throws Exception {
    doTest(false, false);
  }

  public void testQualifyParentStaticReferences() throws Exception {
    doTest(false, false);
  }

  public void testLocalClass() throws Exception {
    doTest(false, true);
  }

  public void testMultipleAssignments() throws Exception {
    doTest(false, true);
  }

  public void testParamTypeReplacement() throws Exception {
    doTest(false, true);
  }

  public void testNoInlineAbstract() throws Exception {
    doTestNoInline("Abstract classes cannot be inlined");
  }

  public void testNoInlineInterface() throws Exception {
    doTestNoInline("Interfaces cannot be inlined");
  }

  public void testNoInlineEnum() throws Exception {
    doTestNoInline("Enums cannot be inlined");
  }

  public void testNoInlineAnnotationType() throws Exception {
    doTestNoInline("Annotation types cannot be inlined");
  }

  public void testNoInlineMultipleInterfaces() throws Exception {
    doTestNoInline("Classes which implement multiple interfaces cannot be inlined");
  }

  public void testNoInlineSuperclassInterface() throws Exception {
    doTestNoInline("Classes which have a superclass and implement an interface cannot be inlined");
  }

  public void testNoInlineMethodUsage() throws Exception {
    doTestNoInline("Class cannot be inlined because it has usages of methods not inherited from its superclass or interface");
  }

  public void testNoInlineFieldUsage() throws Exception {
    doTestNoInline("Class cannot be inlined because it has usages of fields not inherited from its superclass");
  }

  public void testNoInlineNewWithInner() throws Exception {
    doTestNoInline("Class cannot be inlined because it has usages of its inner classes");
  }

  public void testNoInlineStaticField() throws Exception {
    doTestNoInline("Class cannot be inlined because it has static fields with non-constant initializers");
  }

  public void testNoInlineStaticNonFinalField() throws Exception {
    doTestNoInline("Class cannot be inlined because it has static non-final fields");
  }

  public void testNoInlineStaticMethod() throws Exception {
    doTestNoInline("Class cannot be inlined because it has static methods");
  }

  public void testNoInlineStaticInitializer() throws Exception {
    doTestNoInline("Class cannot be inlined because it has static initializers");
  }

  public void testNoInlineClassLiteral() throws Exception {
    doTestPreprocessUsages("Class cannot be inlined because it has usages of its class literal");
  }

  public void testNoInlineCatchClause() throws Exception {
    doTestPreprocessUsages("Class cannot be inlined because it is used in a 'catch' clause");
  }

  public void testNoInlineThrowsClause() throws Exception {
    doTestPreprocessUsages("Class cannot be inlined because it is used in a 'throws' clause");
  }

  public void testNoInlineThisQualifier() throws Exception {
    doTestPreprocessUsages("Class cannot be inlined because it is used as a 'this' qualifier");
  }

  public void testNoInlineUnresolvedConstructor() throws Exception {
    doTestPreprocessUsages("Class cannot be inlined because a call to its constructor is unresolved");
  }

  public void testNoInlineUnresolvedConstructor2() throws Exception {
    doTestPreprocessUsages("Class cannot be inlined because a call to its constructor is unresolved");
  }

  public void testNoInlineStaticInnerClass() throws Exception {
    doTestNoInline("Class cannot be inlined because it has static inner classes");
  }

  public void testNoInlineReturnInConstructor() throws Exception {
    doTestNoInline("Class cannot be inlined because its constructor contains 'return' statements");
  }

  public void testNoInlineUnresolvedSuperclass() throws Exception {
    doTestNoInline("Class cannot be inlined because its superclass cannot be resolved");
  }

  public void testNoInlineUnresolvedInterface() throws Exception {
    doTestNoInline("Class cannot be inlined because an interface implemented by it cannot be resolved");
  }

  public void testNoInlineLibraryClass() throws Exception {
    doTestNoInline("Library classes cannot be inlined");
  }

  public void testNoInlineNoUsages() throws Exception {
    doTestPreprocessUsages("Class is never used");
  }

  public void testNoInlineRecursiveAccess() throws Exception {
    doTestConflict("Class cannot be inlined because a call to its member inside body", "Class cannot be inlined because a call to its member inside body");
  }

  public void testConflictInaccessibleOuterField() throws Exception {
    doTestConflict(
      "Field <b><code>C2.a</code></b> that is used in inlined method is not accessible from call site(s) in method <b><code>C2User.test()</code></b>");
  }

  public void testGetClassConflict() throws Exception {
    doTestConflict("Result of getClass() invocation would be changed", "Result of getClass() invocation would be changed");
  }

  public void doTestConflict(final String... expected) throws Exception {
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

  private void doTestNoInline(final String expectedMessage) throws Exception {
    String name = getTestName(false);
    @NonNls String fileName = "/refactoring/inlineToAnonymousClass/" + name + ".java";
    configureByFile(fileName);
    PsiElement element = TargetElementUtilBase
      .findTargetElement(myEditor, TargetElementUtilBase.ELEMENT_NAME_ACCEPTED | TargetElementUtilBase.REFERENCED_ELEMENT_ACCEPTED);
    assertInstanceOf(element, PsiClass.class);

    String message = InlineToAnonymousClassHandler.getCannotInlineMessage((PsiClass) element);
    assertEquals(expectedMessage, message);
  }

  private void doTest(final boolean inlineThisOnly, final boolean searchInNonJavaFiles) throws Exception {
    String name = getTestName(false);
    @NonNls String fileName = "/refactoring/inlineToAnonymousClass/" + name + ".java";
    configureByFile(fileName);
    performAction(inlineThisOnly, searchInNonJavaFiles);
    checkResultByFile(null, fileName + ".after", true);
  }

  private void doTestPreprocessUsages(final String expectedMessage) throws Exception {
    configureByFile("/refactoring/inlineToAnonymousClass/" + getTestName(false) + ".java");
    PsiElement element = TargetElementUtilBase.findTargetElement(myEditor, TargetElementUtilBase
      .ELEMENT_NAME_ACCEPTED | TargetElementUtilBase.REFERENCED_ELEMENT_ACCEPTED);
    assertInstanceOf(element, PsiClass.class);
    final PsiClass psiClass = (PsiClass)element;
    assertEquals(expectedMessage, InlineToAnonymousClassHandler.getCannotInlineMessage(psiClass));
  }

  private InlineToAnonymousClassProcessor prepareProcessor() throws Exception {
    String name = getTestName(false);
    @NonNls String fileName = "/refactoring/inlineToAnonymousClass/" + name + ".java";
    configureByFile(fileName);
    PsiElement element = TargetElementUtilBase.findTargetElement(myEditor, TargetElementUtilBase
      .ELEMENT_NAME_ACCEPTED | TargetElementUtilBase.REFERENCED_ELEMENT_ACCEPTED);
    assertInstanceOf(element, PsiClass.class);

    assertEquals(null, InlineToAnonymousClassHandler.getCannotInlineMessage((PsiClass) element));
    return new InlineToAnonymousClassProcessor(getProject(), (PsiClass) element, null, false, false, false);
  }

  private void performAction(final boolean inlineThisOnly, final boolean searchInNonJavaFiles) {
    PsiElement element = TargetElementUtilBase
      .findTargetElement(myEditor, TargetElementUtilBase.ELEMENT_NAME_ACCEPTED | TargetElementUtilBase.REFERENCED_ELEMENT_ACCEPTED);
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

  public void testCanBeInvokedOnReference() throws Exception {
    doTestCanBeInvokedOnReference(true);
  }

  public void testCanBeInvokedOnReference1() throws Exception {
    doTestCanBeInvokedOnReference(true);
  }

  public void testCanBeInvokedOnReferenceSubstitution() throws Exception {
    doTestCanBeInvokedOnReference(true);
  }

  public void testCanBeInvokedOnReferenceSubstitution1() throws Exception {
    doTestCanBeInvokedOnReference(true);
  }

  public void testCanBeInvokedOnReferenceVarargs() throws Exception {
    doTestCanBeInvokedOnReference(true);
  }

  public void testCantBeInvokedOnReference() throws Exception {
    doTestCanBeInvokedOnReference(false);
  }

  public void testCantBeInvokedOnReference1() throws Exception {
    doTestCanBeInvokedOnReference(false);
  }

  public void testCantBeInvokedOnReferenceReturnStatement() throws Exception {
    doTestCanBeInvokedOnReference(false);
  }

  public void testCanBeInvokedOnReferenceSyncStatement() throws Exception {
    doTestCanBeInvokedOnReference(true);
  }

  private void doTestCanBeInvokedOnReference(boolean canBeInvokedOnReference) throws Exception {
    configureByFile("/refactoring/inlineToAnonymousClass/" + getTestName(false) + ".java");
    PsiElement element = TargetElementUtilBase
      .findTargetElement(myEditor, TargetElementUtilBase.ELEMENT_NAME_ACCEPTED | TargetElementUtilBase.REFERENCED_ELEMENT_ACCEPTED);
    PsiCall callToInline = InlineToAnonymousClassHandler.findCallToInline(myEditor);
    PsiClass classToInline = (PsiClass) element;
    assertEquals(null, InlineToAnonymousClassHandler.getCannotInlineMessage(classToInline));
    final PsiClassType superType = InlineToAnonymousClassProcessor.getSuperType(classToInline);
    assertTrue(superType != null);
    assertEquals(canBeInvokedOnReference, InlineToAnonymousClassHandler.canBeInvokedOnReference(callToInline, superType));
  }
}