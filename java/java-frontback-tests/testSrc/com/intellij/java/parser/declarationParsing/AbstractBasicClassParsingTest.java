// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.parser.declarationParsing;

import com.intellij.java.parser.AbstractBasicJavaParsingTestCase;
import com.intellij.java.parser.AbstractBasicJavaParsingTestConfigurator;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

public abstract class AbstractBasicClassParsingTest extends AbstractBasicJavaParsingTestCase {
  public AbstractBasicClassParsingTest(@NotNull AbstractBasicJavaParsingTestConfigurator configurator) {
    super("parser-full/declarationParsing/class", configurator);
  }

  @Test
  public void testNoClass() { doTest(true); }

  @Test
  public void testNoType() { doTest(true); }

  @Test
  public void testSemicolon() { doTest(true); }
  @Test
  public void testSemicolon2() { doTest(true); }

  @Test
  public void testParametrizedClass() { doTest(true); }

  @Test
  public void testPines() { doTest(true); }

  @Test
  public void testForError() { doTest(true); }

  @Test
  public void testEnum1() { doTest(true); }
  @Test
  public void testEnum2() { doTest(true); }
  @Test
  public void testEnumWithConstants1() { doTest(true); }
  @Test
  public void testEnumWithConstants2() { doTest(true); }
  @Test
  public void testEnumWithConstants3() { doTest(true); }
  @Test
  public void testEnumWithConstants4() { doTest(true); }
  @Test
  public void testEnumWithConstants5() { doTest(true); }
  @Test
  public void testEnumWithConstants6() { doTest(true); }
  @Test
  public void testEnumWithConstantsDoubleComma() { doTest(true); }
  @Test
  public void testEnumWithInitializedConstants() { doTest(true); }
  @Test
  public void testEnumWithAnnotatedConstants() { doTest(true); }
  @Test
  public void testEnumWithImport() { doTest(true); }
  @Test
  public void testEnumWithoutConstants() { doTest(true); }

  @Test
  public void testEmptyImportList() { doTest(true); }

  @Test
  public void testLongClass() { doTest(false); }

  @Test
  public void testIncompleteAnnotation() { doTest(true); }

  @Test
  public void testExtraOpeningBraceInMethod() { doTest(true); }
  @Test
  public void testExtraClosingBraceInMethod() { doTest(true); }

  @Test
  public void testErrors0() { doTest(true); }
  @Test
  public void testErrors1() { doTest(true); }
  @Test
  public void testErrors2() { doTest(true); }
  @Test
  public void testErrors3() { doTest(true); }
  @Test
  public void testErrors4() { doTest(true); }
  @Test
  public void testErrors5() { doTest(true); }

  @Test
  public void testRecord() { doTest(true); }
  @Test
  public void testRecordWithComponents() { doTest(true); }
  @Test
  public void testRecordNoClosingParenthesis() { doTest(true); }
  @Test
  public void testRecordNoComponents() { doTest(true); }
  @Test
  public void testRecordWithTypeParameters() { doTest(true); }
  @Test
  public void testRecordNoClosingTypeBracket() { doTest(true); }
  @Test
  public void testRecordWithModifiers() { doTest(true); }
  @Test
  public void testRecordIncomplete() { doTest(true); }
  @Test
  public void testRecordInCodeBlock() { doTest(true); }
  @Test
  public void testRecordInCodeBlockIncompleteBeforeDeclaration() { doTest(true); }
  @Test
  public void testRecordInCodeBlockIncompleteBeforeIfStatement() { doTest(true); }
  
  @Test
  public void testVarIncomplete() { doTest(true); }

  @Test
  public void testLocalRecord() { doTest(true); }
  @Test
  public void testLocalRecordWithTypeParams() { doTest(true); }
  @Test
  public void testLocalRecordWithoutParens() { doTest(true); }

  @Test
  public void testCompactConstructor0() { doTest(true); }
  @Test
  public void testCompactConstructor1() { doTest(true); }

  @Test
  public void testRecordTypeInOlderJava() {
    setLanguageLevel(LanguageLevel.JDK_13);
    doTest(true);
  }

  @Test
  public void testValueClass() { doTest(true); }
  @Test
  public void testNoSoftKeywordInLambdaParameters() { doTest(true); }

  @Test
  public void testSealedInterface() { doTest(true); }
  @Test
  public void testSealedClassOldLanguageLevel() {
    setLanguageLevel(LanguageLevel.JDK_1_8);
    doTest(true);
  }
  @Test
  public void testNonSealedClass() { doTest(true); }
  @Test
  public void testLocalSealedClass() { doTest(true); }

  @Test
  public void testProvidesList() { doTest(true); }

  @Test
  public void testImplicitClass0() { doTest(true); }
  @Test
  public void testImplicitClass1() { doTest(true); }
  @Test
  public void testImplicitClass2() { doTest(true); }
  @Test
  public void testImplicitClass3() { doTest(true); }
  @Test
  public void testImplicitClass4() { doTest(true); }
  @Test
  public void testImplicitClass5() { doTest(true); }
  @Test
  public void testImplicitClassJavaDoc() { doTest(true); }

  @Test
  public void testIncompleteClass() { doTest(true); }
  @Test
  public void testIncorrectModifier() { doTest(true); }
}
