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
package com.intellij.java.psi.codeStyle;

import com.intellij.application.options.CodeStyle;
import com.intellij.application.options.CodeStyleBean;
import com.intellij.ide.codeStyleSettings.CodeStyleTestCase;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.options.SchemeImportException;
import com.intellij.psi.codeStyle.*;
import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;

public class JavaCodeStyleSettingsTest extends CodeStyleTestCase {

  public void testSettingsClone() {
    List<String> annotations = Arrays.asList("anno1", "anno2");
    JavaCodeStyleSettings original = (JavaCodeStyleSettings)JavaCodeStyleSettings.getInstance(getProject()).clone();
    original.getImportLayoutTable().addEntry(new PackageEntry(false, "test", true));
    original.setRepeatAnnotations(annotations);
    original.getPackagesToUseImportOnDemand().addEntry(new PackageEntry(false, "test2", true));
    original.FIELD_TYPE_TO_NAME.addPair("foo", "bar");
    original.STATIC_FIELD_TYPE_TO_NAME.addPair("one", "two");

    JavaCodeStyleSettings copy = (JavaCodeStyleSettings)original.clone();
    assertEquals(annotations, copy.getRepeatAnnotations());
    assertEquals("Import tables do not match", original.getImportLayoutTable(), copy.getImportLayoutTable());
    assertEquals("On demand packages do not match", original.getPackagesToUseImportOnDemand(), copy.getPackagesToUseImportOnDemand());
    assertEquals("Field type-to-name maps don not match", original.FIELD_TYPE_TO_NAME, copy.FIELD_TYPE_TO_NAME);
    assertEquals("Static field type-to-name maps don not match", original.STATIC_FIELD_TYPE_TO_NAME, copy.STATIC_FIELD_TYPE_TO_NAME);
  }

  public void testSettingsCloneNotReferencingOriginal() throws IllegalAccessException {
    JavaCodeStyleSettings original = JavaCodeStyleSettings.getInstance(getProject());
    JavaCodeStyleSettings copy = (JavaCodeStyleSettings)original.clone();
    for (Field field : copy.getClass().getDeclaredFields()) {
      if (!isPrimitiveOrString(field.getType()) && (field.getModifiers() & Modifier.PUBLIC) != 0) {
        assertNotSame("Fields '" + field.getName() + "' reference the same value", field.get(original), field.get(copy));
      }
    }
  }

  public void testImportPre173Settings() throws SchemeImportException {
    CodeStyleSettings imported = importSettings();
    CommonCodeStyleSettings commonSettings = imported.getCommonSettings(JavaLanguage.INSTANCE);
    assertEquals("testprefix", imported.getCustomSettings(JavaCodeStyleSettings.class).FIELD_NAME_PREFIX);
    assertTrue(commonSettings.WRAP_COMMENTS);
    assertFalse(imported.WRAP_COMMENTS);
  }

  public void testBeanSerialization() throws IOException {
    CodeStyleBean bean = CodeStyle.getBean(getProject(), JavaLanguage.INSTANCE);
    Element root = XmlSerializer.serialize(bean);
    assertXmlOutputEquals(
      "<JavaCodeStyleBean>\n" +
      "  <option name=\"alignConsecutiveVariableDeclarations\" value=\"false\" />\n" +
      "  <option name=\"alignGroupFieldDeclarations\" value=\"false\" />\n" +
      "  <option name=\"alignMultilineAnnotationParameters\" value=\"false\" />\n" +
      "  <option name=\"alignMultilineArrayInitializerExpression\" value=\"false\" />\n" +
      "  <option name=\"alignMultilineAssignment\" value=\"false\" />\n" +
      "  <option name=\"alignMultilineBinaryOperation\" value=\"false\" />\n" +
      "  <option name=\"alignMultilineChainedMethods\" value=\"false\" />\n" +
      "  <option name=\"alignMultilineExtendsList\" value=\"false\" />\n" +
      "  <option name=\"alignMultilineFor\" value=\"true\" />\n" +
      "  <option name=\"alignMultilineMethodBrackets\" value=\"false\" />\n" +
      "  <option name=\"alignMultilineParameters\" value=\"true\" />\n" +
      "  <option name=\"alignMultilineParametersInCalls\" value=\"false\" />\n" +
      "  <option name=\"alignMultilineParenthesizedExpression\" value=\"false\" />\n" +
      "  <option name=\"alignMultilineResources\" value=\"true\" />\n" +
      "  <option name=\"alignMultilineTernaryOperation\" value=\"false\" />\n" +
      "  <option name=\"alignMultilineThrowsList\" value=\"false\" />\n" +
      "  <option name=\"alignSubsequentSimpleMethods\" value=\"false\" />\n" +
      "  <option name=\"alignThrowsKeyword\" value=\"false\" />\n" +
      "  <option name=\"annotationParameterWrap\" value=\"NONE\" />\n" +
      "  <option name=\"arrayInitializerLeftBraceOnNextLine\" value=\"false\" />\n" +
      "  <option name=\"arrayInitializerRightBraceOnNextLine\" value=\"false\" />\n" +
      "  <option name=\"arrayInitializerWrap\" value=\"NONE\" />\n" +
      "  <option name=\"assertStatementColonOnNextLine\" value=\"false\" />\n" +
      "  <option name=\"assertStatementWrap\" value=\"NONE\" />\n" +
      "  <option name=\"assignmentWrap\" value=\"NONE\" />\n" +
      "  <option name=\"binaryOperationSignOnNextLine\" value=\"false\" />\n" +
      "  <option name=\"binaryOperationWrap\" value=\"NONE\" />\n" +
      "  <option name=\"blankLinesAfterAnonymousClassHeader\" value=\"0\" />\n" +
      "  <option name=\"blankLinesAfterClassHeader\" value=\"0\" />\n" +
      "  <option name=\"blankLinesAfterImports\" value=\"1\" />\n" +
      "  <option name=\"blankLinesAfterPackage\" value=\"1\" />\n" +
      "  <option name=\"blankLinesAroundClass\" value=\"1\" />\n" +
      "  <option name=\"blankLinesAroundField\" value=\"0\" />\n" +
      "  <option name=\"blankLinesAroundFieldInInterface\" value=\"0\" />\n" +
      "  <option name=\"blankLinesAroundInitializer\" value=\"1\" />\n" +
      "  <option name=\"blankLinesAroundMethod\" value=\"1\" />\n" +
      "  <option name=\"blankLinesAroundMethodInInterface\" value=\"1\" />\n" +
      "  <option name=\"blankLinesBeforeClassEnd\" value=\"0\" />\n" +
      "  <option name=\"blankLinesBeforeImports\" value=\"1\" />\n" +
      "  <option name=\"blankLinesBeforeMethodBody\" value=\"0\" />\n" +
      "  <option name=\"blankLinesBeforePackage\" value=\"0\" />\n" +
      "  <option name=\"blockCommentAtFirstColumn\" value=\"true\" />\n" +
      "  <option name=\"braceStyle\" value=\"EndOfLine\" />\n" +
      "  <option name=\"callParametersLeftParenOnNextLine\" value=\"false\" />\n" +
      "  <option name=\"callParametersRightParenOnNextLine\" value=\"false\" />\n" +
      "  <option name=\"callParametersWrap\" value=\"NONE\" />\n" +
      "  <option name=\"caseStatementOnNewLine\" value=\"true\" />\n" +
      "  <option name=\"catchOnNewLine\" value=\"false\" />\n" +
      "  <option name=\"classAnnotationWrap\" value=\"ALWAYS\" />\n" +
      "  <option name=\"classBraceStyle\" value=\"EndOfLine\" />\n" +
      "  <option name=\"classCountToUseImportOnDemand\" value=\"5\" />\n" +
      "  <option name=\"classNamesInJavadoc\" value=\"1\" />\n" +
      "  <option name=\"continuationIndent\" value=\"8\" />\n" +
      "  <option name=\"doNotIndentTopLevelClassMembers\" value=\"false\" />\n" +
      "  <option name=\"doNotWrapAfterSingleAnnotation\" value=\"false\" />\n" +
      "  <option name=\"doWhileBraceForce\" value=\"Never\" />\n" +
      "  <option name=\"elseOnNewLine\" value=\"false\" />\n" +
      "  <option name=\"enableJavadocFormatting\" value=\"true\" />\n" +
      "  <option name=\"enumConstantsWrap\" value=\"NONE\" />\n" +
      "  <option name=\"extendsKeywordWrap\" value=\"NONE\" />\n" +
      "  <option name=\"extendsListWrap\" value=\"NONE\" />\n" +
      "  <option name=\"fieldAnnotationWrap\" value=\"ALWAYS\" />\n" +
      "  <option name=\"fieldNamePrefix\" value=\"\" />\n" +
      "  <option name=\"fieldNameSuffix\" value=\"\" />\n" +
      "  <option name=\"finallyOnNewLine\" value=\"false\" />\n" +
      "  <option name=\"forBraceForce\" value=\"Never\" />\n" +
      "  <option name=\"forStatementLeftParenOnNextLine\" value=\"false\" />\n" +
      "  <option name=\"forStatementRightParenOnNextLine\" value=\"false\" />\n" +
      "  <option name=\"forStatementWrap\" value=\"NONE\" />\n" +
      "  <option name=\"generateFinalLocals\" value=\"false\" />\n" +
      "  <option name=\"generateFinalParameters\" value=\"false\" />\n" +
      "  <option name=\"ifBraceForce\" value=\"Never\" />\n" +
      "  <option name=\"indent\" value=\"4\" />\n" +
      "  <option name=\"indentCaseFromSwitch\" value=\"true\" />\n" +
      "  <option name=\"insertInnerClassImports\" value=\"false\" />\n" +
      "  <option name=\"insertOverrideAnnotation\" value=\"true\" />\n" +
      "  <option name=\"javaDocAddBlankAfterDescription\" value=\"true\" />\n" +
      "  <option name=\"javaDocAddBlankAfterParamComments\" value=\"false\" />\n" +
      "  <option name=\"javaDocAddBlankAfterReturn\" value=\"false\" />\n" +
      "  <option name=\"javaDocAlignExceptionComments\" value=\"true\" />\n" +
      "  <option name=\"javaDocAlignParamComments\" value=\"true\" />\n" +
      "  <option name=\"javaDocDoNotWrapOneLineComments\" value=\"false\" />\n" +
      "  <option name=\"javaDocIndentOnContinuation\" value=\"false\" />\n" +
      "  <option name=\"javaDocKeepEmptyException\" value=\"true\" />\n" +
      "  <option name=\"javaDocKeepEmptyLines\" value=\"true\" />\n" +
      "  <option name=\"javaDocKeepEmptyParameter\" value=\"true\" />\n" +
      "  <option name=\"javaDocKeepEmptyReturn\" value=\"true\" />\n" +
      "  <option name=\"javaDocKeepInvalidTags\" value=\"true\" />\n" +
      "  <option name=\"javaDocLeadingAsterisksAreEnabled\" value=\"true\" />\n" +
      "  <option name=\"javaDocPAtEmptyLines\" value=\"true\" />\n" +
      "  <option name=\"javaDocParamDescriptionOnNewLine\" value=\"false\" />\n" +
      "  <option name=\"javaDocPreserveLineFeeds\" value=\"false\" />\n" +
      "  <option name=\"javaDocUseThrowsNotException\" value=\"true\" />\n" +
      "  <option name=\"keepBlankLinesBeforeRightBrace\" value=\"2\" />\n" +
      "  <option name=\"keepBlankLinesInCode\" value=\"2\" />\n" +
      "  <option name=\"keepBlankLinesInDeclarations\" value=\"2\" />\n" +
      "  <option name=\"keepControlStatementInOneLine\" value=\"true\" />\n" +
      "  <option name=\"keepFirstColumnComment\" value=\"true\" />\n" +
      "  <option name=\"keepIndentsOnEmptyLines\" value=\"false\" />\n" +
      "  <option name=\"keepLineBreaks\" value=\"true\" />\n" +
      "  <option name=\"keepMultipleExpressionsInOneLine\" value=\"false\" />\n" +
      "  <option name=\"keepSimpleBlocksInOneLine\" value=\"false\" />\n" +
      "  <option name=\"keepSimpleClassesInOneLine\" value=\"false\" />\n" +
      "  <option name=\"keepSimpleLambdasInOneLine\" value=\"false\" />\n" +
      "  <option name=\"keepSimpleMethodsInOneLine\" value=\"false\" />\n" +
      "  <option name=\"labelIndent\" value=\"0\" />\n" +
      "  <option name=\"labelIndentAbsolute\" value=\"false\" />\n" +
      "  <option name=\"lambdaBraceStyle\" value=\"EndOfLine\" />\n" +
      "  <option name=\"layoutStaticImportsSeparately\" value=\"true\" />\n" +
      "  <option name=\"lineCommentAddSpace\" value=\"false\" />\n" +
      "  <option name=\"lineCommentAtFirstColumn\" value=\"true\" />\n" +
      "  <option name=\"localVariableNamePrefix\" value=\"\" />\n" +
      "  <option name=\"localVariableNameSuffix\" value=\"\" />\n" +
      "  <option name=\"methodAnnotationWrap\" value=\"ALWAYS\" />\n" +
      "  <option name=\"methodBraceStyle\" value=\"EndOfLine\" />\n" +
      "  <option name=\"methodCallChainWrap\" value=\"NONE\" />\n" +
      "  <option name=\"methodParametersLeftParenOnNextLine\" value=\"false\" />\n" +
      "  <option name=\"methodParametersRightParenOnNextLine\" value=\"false\" />\n" +
      "  <option name=\"methodParametersWrap\" value=\"NONE\" />\n" +
      "  <option name=\"modifierListWrap\" value=\"false\" />\n" +
      "  <option name=\"namesCountToUseImportOnDemand\" value=\"3\" />\n" +
      "  <option name=\"parameterAnnotationWrap\" value=\"NONE\" />\n" +
      "  <option name=\"parameterNamePrefix\" value=\"\" />\n" +
      "  <option name=\"parameterNameSuffix\" value=\"\" />\n" +
      "  <option name=\"parenthesesExpressionLeftParenWrap\" value=\"false\" />\n" +
      "  <option name=\"parenthesesExpressionRightParenWrap\" value=\"false\" />\n" +
      "  <option name=\"placeAssignmentSignOnNextLine\" value=\"false\" />\n" +
      "  <option name=\"preferLongerNames\" value=\"true\" />\n" +
      "  <option name=\"preferParametersWrap\" value=\"false\" />\n" +
      "  <option name=\"repeatSynchronized\" value=\"true\" />\n" +
      "  <option name=\"replaceCast\" value=\"false\" />\n" +
      "  <option name=\"replaceInstanceOf\" value=\"false\" />\n" +
      "  <option name=\"replaceNullCheck\" value=\"true\" />\n" +
      "  <option name=\"resourceListLeftParenOnNextLine\" value=\"false\" />\n" +
      "  <option name=\"resourceListRightParenOnNextLine\" value=\"false\" />\n" +
      "  <option name=\"resourceListWrap\" value=\"NONE\" />\n" +
      "  <option name=\"rightMargin\" value=\"120\" />\n" +
      "  <option name=\"smartTabs\" value=\"false\" />\n" +
      "  <option name=\"spaceAfterClosingAngleBracketInTypeArgument\" value=\"false\" />\n" +
      "  <option name=\"spaceAfterColon\" value=\"true\" />\n" +
      "  <option name=\"spaceAfterComma\" value=\"true\" />\n" +
      "  <option name=\"spaceAfterCommaInTypeArguments\" value=\"true\" />\n" +
      "  <option name=\"spaceAfterForSemicolon\" value=\"true\" />\n" +
      "  <option name=\"spaceAfterQuest\" value=\"true\" />\n" +
      "  <option name=\"spaceAfterTypeCast\" value=\"true\" />\n" +
      "  <option name=\"spaceAroundAdditiveOperators\" value=\"true\" />\n" +
      "  <option name=\"spaceAroundAssignmentOperators\" value=\"true\" />\n" +
      "  <option name=\"spaceAroundBitwiseOperators\" value=\"true\" />\n" +
      "  <option name=\"spaceAroundEqualityOperators\" value=\"true\" />\n" +
      "  <option name=\"spaceAroundLambdaArrow\" value=\"true\" />\n" +
      "  <option name=\"spaceAroundLogicalOperators\" value=\"true\" />\n" +
      "  <option name=\"spaceAroundMethodRefDblColon\" value=\"false\" />\n" +
      "  <option name=\"spaceAroundMultiplicativeOperators\" value=\"true\" />\n" +
      "  <option name=\"spaceAroundRelationalOperators\" value=\"true\" />\n" +
      "  <option name=\"spaceAroundShiftOperators\" value=\"true\" />\n" +
      "  <option name=\"spaceAroundTypeBoundsInTypeParameters\" value=\"true\" />\n" +
      "  <option name=\"spaceAroundUnaryOperator\" value=\"false\" />\n" +
      "  <option name=\"spaceBeforeAnnotationArrayInitializerLeftBrace\" value=\"false\" />\n" +
      "  <option name=\"spaceBeforeAnotationParameterList\" value=\"false\" />\n" +
      "  <option name=\"spaceBeforeArrayInitializerLeftBrace\" value=\"false\" />\n" +
      "  <option name=\"spaceBeforeCatchKeyword\" value=\"true\" />\n" +
      "  <option name=\"spaceBeforeCatchLeftBrace\" value=\"true\" />\n" +
      "  <option name=\"spaceBeforeCatchParentheses\" value=\"true\" />\n" +
      "  <option name=\"spaceBeforeClassLeftBrace\" value=\"true\" />\n" +
      "  <option name=\"spaceBeforeColon\" value=\"true\" />\n" +
      "  <option name=\"spaceBeforeComma\" value=\"false\" />\n" +
      "  <option name=\"spaceBeforeDoLeftBrace\" value=\"true\" />\n" +
      "  <option name=\"spaceBeforeElseKeyword\" value=\"true\" />\n" +
      "  <option name=\"spaceBeforeElseLeftBrace\" value=\"true\" />\n" +
      "  <option name=\"spaceBeforeFinallyKeyword\" value=\"true\" />\n" +
      "  <option name=\"spaceBeforeFinallyLeftBrace\" value=\"true\" />\n" +
      "  <option name=\"spaceBeforeForLeftBrace\" value=\"true\" />\n" +
      "  <option name=\"spaceBeforeForParentheses\" value=\"true\" />\n" +
      "  <option name=\"spaceBeforeForSemicolon\" value=\"false\" />\n" +
      "  <option name=\"spaceBeforeIfLeftBrace\" value=\"true\" />\n" +
      "  <option name=\"spaceBeforeIfParentheses\" value=\"true\" />\n" +
      "  <option name=\"spaceBeforeMethodCallParentheses\" value=\"false\" />\n" +
      "  <option name=\"spaceBeforeMethodLeftBrace\" value=\"true\" />\n" +
      "  <option name=\"spaceBeforeMethodParentheses\" value=\"false\" />\n" +
      "  <option name=\"spaceBeforeOpeningAngleBracketInTypeParameter\" value=\"false\" />\n" +
      "  <option name=\"spaceBeforeQuest\" value=\"true\" />\n" +
      "  <option name=\"spaceBeforeSwitchLeftBrace\" value=\"true\" />\n" +
      "  <option name=\"spaceBeforeSwitchParentheses\" value=\"true\" />\n" +
      "  <option name=\"spaceBeforeSynchronizedLeftBrace\" value=\"true\" />\n" +
      "  <option name=\"spaceBeforeSynchronizedParentheses\" value=\"true\" />\n" +
      "  <option name=\"spaceBeforeTryLeftBrace\" value=\"true\" />\n" +
      "  <option name=\"spaceBeforeTryParentheses\" value=\"true\" />\n" +
      "  <option name=\"spaceBeforeTypeParameterList\" value=\"false\" />\n" +
      "  <option name=\"spaceBeforeWhileKeyword\" value=\"true\" />\n" +
      "  <option name=\"spaceBeforeWhileLeftBrace\" value=\"true\" />\n" +
      "  <option name=\"spaceBeforeWhileParentheses\" value=\"true\" />\n" +
      "  <option name=\"spaceWithinAnnotationParentheses\" value=\"false\" />\n" +
      "  <option name=\"spaceWithinArrayInitializerBraces\" value=\"false\" />\n" +
      "  <option name=\"spaceWithinBraces\" value=\"false\" />\n" +
      "  <option name=\"spaceWithinBrackets\" value=\"false\" />\n" +
      "  <option name=\"spaceWithinCastParentheses\" value=\"false\" />\n" +
      "  <option name=\"spaceWithinCatchParentheses\" value=\"false\" />\n" +
      "  <option name=\"spaceWithinEmptyArrayInitializerBraces\" value=\"false\" />\n" +
      "  <option name=\"spaceWithinEmptyMethodCallParentheses\" value=\"false\" />\n" +
      "  <option name=\"spaceWithinEmptyMethodParentheses\" value=\"false\" />\n" +
      "  <option name=\"spaceWithinForParentheses\" value=\"false\" />\n" +
      "  <option name=\"spaceWithinIfParentheses\" value=\"false\" />\n" +
      "  <option name=\"spaceWithinMethodCallParentheses\" value=\"false\" />\n" +
      "  <option name=\"spaceWithinMethodParentheses\" value=\"false\" />\n" +
      "  <option name=\"spaceWithinParentheses\" value=\"false\" />\n" +
      "  <option name=\"spaceWithinSwitchParentheses\" value=\"false\" />\n" +
      "  <option name=\"spaceWithinSynchronizedParentheses\" value=\"false\" />\n" +
      "  <option name=\"spaceWithinTryParentheses\" value=\"false\" />\n" +
      "  <option name=\"spaceWithinWhileParentheses\" value=\"false\" />\n" +
      "  <option name=\"spacesWithinAngleBrackets\" value=\"false\" />\n" +
      "  <option name=\"specialElseIfTreatment\" value=\"true\" />\n" +
      "  <option name=\"staticFieldNamePrefix\" value=\"\" />\n" +
      "  <option name=\"staticFieldNameSuffix\" value=\"\" />\n" +
      "  <option name=\"subclassNamePrefix\" value=\"\" />\n" +
      "  <option name=\"subclassNameSuffix\" value=\"Impl\" />\n" +
      "  <option name=\"tabSize\" value=\"4\" />\n" +
      "  <option name=\"ternaryOperationSignsOnNextLine\" value=\"false\" />\n" +
      "  <option name=\"ternaryOperationWrap\" value=\"NONE\" />\n" +
      "  <option name=\"testNamePrefix\" value=\"\" />\n" +
      "  <option name=\"testNameSuffix\" value=\"Test\" />\n" +
      "  <option name=\"throwsKeywordWrap\" value=\"NONE\" />\n" +
      "  <option name=\"throwsListWrap\" value=\"NONE\" />\n" +
      "  <option name=\"useExternalAnnotations\" value=\"false\" />\n" +
      "  <option name=\"useFqClassNames\" value=\"false\" />\n" +
      "  <option name=\"useSingleClassImports\" value=\"true\" />\n" +
      "  <option name=\"useTabCharacter\" value=\"false\" />\n" +
      "  <option name=\"variableAnnotationWrap\" value=\"NONE\" />\n" +
      "  <option name=\"visibility\" value=\"public\" />\n" +
      "  <option name=\"whileBraceForce\" value=\"Never\" />\n" +
      "  <option name=\"whileOnNewLine\" value=\"false\" />\n" +
      "  <option name=\"wrapComments\" value=\"false\" />\n" +
      "  <option name=\"wrapFirstMethodInCallChain\" value=\"false\" />\n" +
      "  <option name=\"wrapLongLines\" value=\"false\" />\n" +
      "  <option name=\"wrapOnTyping\" value=\"DEFAULT\" />\n" +
      "</JavaCodeStyleBean>",

      root);
  }

  private static boolean isPrimitiveOrString(Class type) {
    return type.isPrimitive() || type.equals(String.class);
  }

  @Override
  protected String getBasePath() {
    return PathManagerEx.getTestDataPath() + "/codeStyle";
  }
}
