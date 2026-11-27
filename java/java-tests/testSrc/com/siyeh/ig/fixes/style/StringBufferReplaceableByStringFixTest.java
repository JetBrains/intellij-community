// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes.style;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.IGQuickFixesFixture;
import com.siyeh.ig.style.StringBufferReplaceableByStringInspection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.siyeh.ig.IGQuickFixesFixtureKt.igQuickFixesFixture;

@TestApplication
public class StringBufferReplaceableByStringFixTest {
  private final TestFixture<IGQuickFixesFixture> ig =
    igQuickFixesFixture(() -> List.of(new StringBufferReplaceableByStringInspection()), "style/replace_with_string", null,
                        builder -> builder.addJdkVersion(LanguageLevel.JDK_1_8));

  private void doTestFor(String builderClassName) {
    ig.get().doTest(CommonQuickFixBundle.message("fix.replace.x.with.y", builderClassName, "String"));
  }

  @Test
  public void testSimpleStringBuffer() { doTestFor("StringBuffer"); }
  @Test
  public void testStringBuilderAppend() { doTestFor("StringBuilder"); }
  @Test
  public void testStringBuilderAppendSubString() { doTestFor("StringBuilder"); }
  @Test
  public void testStringBufferVariable() { doTestFor("StringBuffer"); }
  @Test
  public void testStringBufferVariable2() { doTestFor("StringBuffer"); }
  @Test
  public void testStartsWithPrimitive() { doTestFor("StringBuffer"); }
  @Test
  public void testStartsWithPrimitive2() { doTestFor("StringBuilder"); }
  @Test
  public void testStartsWithPrimitive3() { doTestFor("StringBuilder"); }
  @Test
  public void testStartsWithPrimitive4() { doTestFor("StringBuilder"); }
  @Test
  public void testStartsWithPrimitive5() { doTestFor("StringBuilder"); }
  @Test
  public void testStartsWithPrimitive6() { doTestFor("StringBuilder"); }
  @Test
  public void testPrecedence() { doTestFor("StringBuilder"); }
  @Test
  public void testPrecedence2() { doTestFor("StringBuilder"); }
  @Test
  public void testPrecedence3() { doTestFor("StringBuilder"); }
  @Test
  public void testPrecedence4() { doTestFor("StringBuilder"); }
  @Test
  public void testNonString1() { doTestFor("StringBuilder"); }
  @Test
  public void testNonString2() { doTestFor("StringBuilder"); }
  @Test
  public void testMarathon() { doTestFor("StringBuilder"); }
  @Test
  public void testArray() { doTestFor("StringBuilder"); }
  @Test
  public void testArray2() { doTestFor("StringBuilder"); }
  @Test
  public void testArray3() { doTestFor("StringBuilder"); }
  @Test
  public void testConstructorArgument() { doTestFor("StringBuilder"); }
  @Test
  public void testConstructorArgument2() { doTestFor("StringBuilder"); }
  @Test
  public void testNoConstructorArgument() { doTestFor("StringBuilder"); }
  @Test
  public void testCharLiteral() { doTestFor("StringBuilder"); }
  @Test
  public void testEscape() { doTestFor("StringBuilder"); }
  @Test
  public void testUnescape() { doTestFor("StringBuilder"); }
  @Test
  public void testMethodCallOnString() { doTestFor("StringBuilder"); }
  @Test
  public void testComplex1() { doTestFor("StringBuilder"); }
  @Test
  public void testComplex2() { doTestFor("StringBuilder"); }
  @Test
  public void testLinebreaks() { doTestFor("StringBuilder"); }
  @Test
  public void testSlashSlashInLiteral() { doTestFor("StringBuilder"); }
  @Test
  public void testHelperVariable() { doTestFor("StringBuilder"); }
  @Test
  public void testComment1() { doTestFor("StringBuffer"); }
  @Test
  public void testComment2() { doTestFor("StringBuilder"); }
  @Test
  public void testComment3() { doTestFor("StringBuffer"); }
  @Test
  public void testComment4() { doTestFor("StringBuilder"); }
  @Test
  public void testComment5() { doTestFor("StringBuilder"); }
  @Test
  public void testMultipleComments() { doTestFor("StringBuilder"); }
  @Test
  public void testMultipleCommentsMissingSemicolon() { doTestFor("StringBuilder"); }
  @Test
  public void testMultipleCommentsNoWhitespace() { doTestFor("StringBuilder"); }
  @Test
  public void testImplicitToString() { doTestFor("StringBuilder"); }
  @Test
  public void testImplicitToString2() { doTestFor("StringBuilder"); }
  @Test
  public void testImplicitToString3() { doTestFor("StringBuilder"); }
  @Test
  public void testJoiner() { doTestFor("StringJoiner"); }
  @Test
  public void testJoiner2() { doTestFor("StringJoiner"); }
  @Test
  public void testCharacterPlusAppend() { doTestFor("StringBuilder"); }
  @Test
  public void testSideEffect() { doTestFor("StringBuilder"); }
  @Test
  public void testSideEffect2() { doTestFor("StringBuilder"); }
  @Test
  public void testSideEffect3() { doTestFor("StringBuilder"); }
  @Test
  public void testSideEffect4() { doTestFor("StringBuilder"); }

  @Test
  public void testComplexSignOnNextLine() {
    JavaCodeInsightTestFixture myFixture = ig.get().getFixture();
    final CommonCodeStyleSettings settings = CodeStyle.getSettings(myFixture.getProject()).getCommonSettings(JavaLanguage.INSTANCE);
    settings.BINARY_OPERATION_SIGN_ON_NEXT_LINE = true;
    try {
      doTestFor("StringBuilder");
    }
    finally {
      settings.BINARY_OPERATION_SIGN_ON_NEXT_LINE = false;
    }
  }

}
