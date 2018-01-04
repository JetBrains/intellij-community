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
package com.intellij.java.codeInsight.completion;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.completion.LightCompletionTestCase;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import org.jetbrains.annotations.NotNull;

/**
 * @author ik
 * @since 20.02.2003
 */
public class KeywordCompletionTest extends LightCompletionTestCase {
  private static final String BASE_PATH = "/codeInsight/completion/keywords/";

  private static final String[] FILE_SCOPE_KEYWORDS = {
    "package", "public", "private", "import", "final", "class", "interface", "abstract", "enum", "default", null};
  private static final String[] CLASS_SCOPE_KEYWORDS = {
    "public", "private", "protected", "import", "final", "class", "interface", "abstract", "enum", "default", null};
  private static final String[] CLASS_SCOPE_KEYWORDS_2 = {
    "package", "public", "private", "protected", "transient", "volatile", "static", "import", "final", "class", "interface", "abstract", "default"};
  private static final String[] INTERFACE_SCOPE_KEYWORDS = {
    "package", "public", "private", "protected", "transient", "volatile", "static", "import", "final", "class", "interface", "abstract", "default"};

  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void testFileScope1() { doTest(8, FILE_SCOPE_KEYWORDS); }
  public void testFileScopeAfterComment() { doTest(4, "package", "class", "import", "public", "private"); }
  public void testFileScopeAfterJavaDoc() { doTest(4, "package", "class", "import", "public", "private"); }
  public void testFileScopeAfterJavaDocInsideModifierList() { doTest(2, "class", "public"); }
  public void testFileScope2() { doTest(7, CLASS_SCOPE_KEYWORDS); }
  public void testClassScope1() { doTest(5, CLASS_SCOPE_KEYWORDS); }
  public void testClassScope2() { doTest(4, CLASS_SCOPE_KEYWORDS); }
  public void testClassScope3() { doTest(0, CLASS_SCOPE_KEYWORDS); }
  public void testClassScope4() { doTest(10, CLASS_SCOPE_KEYWORDS_2); }
  public void testInterfaceScope() { setLanguageLevel(LanguageLevel.JDK_1_8); doTest(8, INTERFACE_SCOPE_KEYWORDS); }
  public void testAfterAnnotations() { doTest(6, "public", "final", "class", "interface", "abstract", "enum", null); }
  public void testAfterAnnotationsWithParams() { doTest(6, "public", "final", "class", "interface", "abstract", "enum", null); }
  public void testAfterAnnotationsWithParamsInClass() { doTest(7, "public", "private", "final", "class", "interface", "abstract", "enum"); }
  public void testExtends1() { doTest(2, "extends", "implements", null); }
  public void testExtends2() { doTest(1, "extends", "implements", "AAA", "BBB", "instanceof"); }
  public void testExtends3() { doTest(2, "extends", "implements", "AAA", "BBB", "CCC", "instanceof"); }
  public void testExtends4() { doTest(2, "extends", "implements", "AAA", "BBB", "CCC", "instanceof"); }
  public void testExtends5() { doTest(1, "extends", "implements", "AAA", "BBB", "CCC", "instanceof"); }
  public void testExtends6() { doTest(1, "extends", "implements", "AAA", "BBB", "CCC", "instanceof"); }
  public void testExtends7() { doTest(); }
  public void testExtends8() { doTest(); }
  public void testExtends9() { doTest(); }
  public void testExtends10() { doTest(); }
  public void testExtends11() { doTest(); }
  public void testExtends12() { doTest(); }
  public void testExtends13() { doTest(); }
  public void testExtendsAfterClassGenerics() { doTest(2, "extends", "implements"); }
  public void testSynchronized1() { doTest(); }

  public void testSynchronized2() {
    CodeStyleSettingsManager.getSettings(getProject()).getCommonSettings(JavaLanguage.INSTANCE).SPACE_BEFORE_SYNCHRONIZED_PARENTHESES = false;
    doTest();
  }

  public void testMethodScope1() { doTest(1, "throws"); }
  public void testMethodScope2() { doTest(1, "final", "public", "static", "volatile", "abstract"); }
  public void testMethodScope3() { doTest(1, "final", "public", "static", "volatile", "abstract", "throws", "instanceof"); }
  public void testMethodScope4() { doTest(6, "final", "try", "for", "while", "return", "throw"); }
  public void testMethodScope5() { doTest(); }
  public void testElseAfterSemicolon() { doTest(1, "else"); }
  public void testElseAfterRBrace() { doTest(); }
  public void testExtraBracketAfterFinally1() { doTest(); }
  public void testExtraBracketAfterFinally2() { doTest(); }
  public void testExtendsInCastTypeParameters() { doTest(); }
  public void testExtendsInCastTypeParameters2() { doTest(2, "extends", "super"); }
  public void testExtendsWithRightContextInClassTypeParameters() { doTest(); }
  public void testTrueInVariableDeclaration() { doTest(); }
  public void testNullInIf() { doTest(); }
  public void testNullInReturn() { doTest(); }
  public void testExtendsInMethodParameters() { doTest(); }
  public void testInstanceOf1() { doTest(); }
  public void testInstanceOf2() { doTest(); }
  public void testInstanceOf3() { doTest(); }
  public void testCatchFinally() { doTest(2, "catch", "finally"); }
  public void testSecondCatch() { doTest(2, "catch", "finally"); }
  public void testSuper1() { doTest(1, "super"); }
  public void testSuper2() { doTest(0, "super"); }
  public void testSuper3() { doTest(); }
  public void testSuper4() { doTest(0, "class"); }
  public void testContinue() { doTest(); }
  public void testThrowsOnSeparateLine() { doTest(); }
  public void testDefaultInAnno() { doTest(); }
  public void testNullInMethodCall() { doTest(); }
  public void testNullInMethodCall2() { doTest(); }
  public void testNewInMethodRefs() { doTest(1, "new", "null", "true", "false"); }
  public void testNewInCast() { doTest(2, "new", "null", "true", "false"); }
  public void testSpaceAfterInstanceof() { doTest(); }
  public void testInstanceofAfterUnresolved() { doTest(1, "instanceof"); }
  public void testInstanceofAfterStatementStart() { doTest(1, "instanceof"); }

  public void testInstanceofNegation() {
    configureByTestName();
    selectItem(myItems[0], '!');
    checkResultByTestName();
  }

  public void testNoPrimitivesInBooleanAnnotationAttribute() { doTest(1, "true", "int", "boolean"); }
  public void testNoPrimitivesInIntAnnotationValueAttribute() { doTest(0, "true", "int", "boolean"); }
  public void testNoPrimitivesInEnumAnnotationAttribute() { doTest(0, "true", "int", "boolean"); }
  public void testPrimitivesInClassAnnotationValueAttribute() { doTest(2, "true", "int", "boolean"); }
  public void testPrimitivesInClassAnnotationAttribute() { doTest(3, "true", "int", "boolean"); }
  public void testPrimitivesInMethodReturningArray() { doTest(2, "true", "byte", "boolean"); }
  public void testPrimitivesInMethodReturningClass() { doTest(3, "byte", "boolean", "void"); }

  public void testNoClassKeywordsInLocalArrayInitializer() { doTest(0, "class", "interface", "enum"); }
  public void testNoClassKeywordsInFieldArrayInitializer() { doTest(0, "class", "interface", "enum"); }

  public void testImportStatic() { doTest(1, "static"); }
  public void testAbstractInInterface() { doTest(1, "abstract"); }
  public void testCharInAnnotatedParameter() { doTest(1, "char"); }
  public void testReturnInTernary() { doTest(1, "return"); }
  public void testReturnInRussian() { doTest(1, "return"); }
  public void testFinalAfterParameterAnno() { doTest(2, "final", "float", "class"); }
  public void testFinalAfterParameterAnno2() { doTest(2, "final", "float", "class"); }
  public void testFinalAfterCase() { doTest(3, "final", "float", "class"); }
  public void testNoCaseInsideWhileInSwitch() { doTest(0, "case", "default"); }
  public void testFinalInCatch() { doTest(1, "final"); }
  public void testFinalInIncompleteCatch() { doTest(1, "final"); }
  public void testFinalInCompleteCatch() { doTest(1, "final"); }
  public void testFinalInTryWithResources() { doTest(1, "final", "class"); }
  public void testFinalInCompleteTryWithResources() { doTest(1, "final", "float", "class"); }
  public void testFinalInLambda() { doTest(2, "final", "float"); }
  public void testNoFinalAfterTryBody() { doTest(1, "final", "finally"); }
  public void testClassInMethod() { doTest(2, "class", "char"); }
  public void testClassInMethodOvertype() { doTest(2, "class", "char"); }
  public void testIntInClassArray() { doTest(2, "int", "char", "final"); }
  public void testIntInClassArray2() { doTest(2, "int", "char", "final"); }
  public void testIntInClassArray3() { doTest(2, "int", "char", "final"); }
  public void testArrayClass() { doTest(1, "class", "interface"); }
  public void testIntInGenerics() { doTest(2, "int", "char", "final"); }
  public void testIntInGenerics2() { doTest(2, "int", "char", "final"); }
  public void testBreakInLabeledBlock() { doTest(1, "break label", "continue"); }
  public void testPrimitiveInForLoop() { doTest(1, "int"); }
  public void testNoStatementInForLoopCondition() { doTest(0, "synchronized", "if"); }
  public void testNoStatementInForLoopUpdate() { doTest(0, "synchronized", "if"); }
  public void testPrivateInJava9Interface() { setLanguageLevel(LanguageLevel.JDK_1_9); doTest(); }
  public void testQualifiedNew() { doTest(1, "new"); }

  public void testOverwriteCatch() {
    configureByTestName();
    selectItem(myItems[0], Lookup.REPLACE_SELECT_CHAR);
    checkResultByTestName();
  }

  public void testFinalAfterAnnotationAttributes() { doTest(); }

  public void testTryInExpression() {
    configureByTestName();
    assertEquals("toString", myItems[0].getLookupString());
    assertEquals("this", myItems[1].getLookupString());
  }

  private void doTest() {
    configureByTestName();
    checkResultByTestName();
  }

  private void configureByTestName() {
    configureByFile(BASE_PATH + getTestName(true) + ".java");
  }

  private void checkResultByTestName() {
    checkResultByFile(BASE_PATH + getTestName(true) + "_after.java");
  }

  // todo: check included/excluded variants separately
  protected void doTest(int finalCount, String... values) {
    configureByTestName();
    testByCount(finalCount, values);
  }
}