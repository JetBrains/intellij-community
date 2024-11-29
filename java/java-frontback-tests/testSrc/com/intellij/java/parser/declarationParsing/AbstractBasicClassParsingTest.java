// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.parser.declarationParsing;

import com.intellij.java.parser.AbstractBasicJavaParsingTestCase;
import com.intellij.java.parser.AbstractBasicJavaParsingTestConfigurator;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;

public abstract class AbstractBasicClassParsingTest extends AbstractBasicJavaParsingTestCase {
  public AbstractBasicClassParsingTest(@NotNull AbstractBasicJavaParsingTestConfigurator configurator) {
    super("parser-full/declarationParsing/class", configurator);
  }

  public void testNoClass() { doTest(true); }

  public void testNoType() { doTest(true); }

  public void testSemicolon() { doTest(true); }

  public void testSemicolon2() { doTest(true); }

  public void testParametrizedClass() { doTest(true); }

  public void testPines() { doTest(true); }

  public void testForError() { doTest(true); }

  public void testEnum1() { doTest(true); }

  public void testEnum2() { doTest(true); }

  public void testEnumWithConstants1() { doTest(true); }

  public void testEnumWithConstants2() { doTest(true); }

  public void testEnumWithConstants3() { doTest(true); }

  public void testEnumWithConstants4() { doTest(true); }

  public void testEnumWithConstants5() { doTest(true); }

  public void testEnumWithConstants6() { doTest(true); }

  public void testEnumWithConstantsDoubleComma() { doTest(true); }

  public void testEnumWithInitializedConstants() { doTest(true); }

  public void testEnumWithAnnotatedConstants() { doTest(true); }

  public void testEnumWithImport() { doTest(true); }

  public void testEnumWithoutConstants() { doTest(true); }

  public void testEmptyImportList() { doTest(true); }

  public void testLongClass() { doTest(false); }

  public void testIncompleteAnnotation() { doTest(true); }

  public void testExtraOpeningBraceInMethod() { doTest(true); }

  public void testExtraClosingBraceInMethod() { doTest(true); }

  public void testErrors0() { doTest(true); }

  public void testErrors1() { doTest(true); }

  public void testErrors2() { doTest(true); }

  public void testErrors3() { doTest(true); }

  public void testErrors4() { doTest(true); }

  public void testErrors5() { doTest(true); }

  public void testRecord() { doTest(true); }

  public void testRecordWithComponents() { doTest(true); }

  public void testRecordNoClosingParenthesis() { doTest(true); }

  public void testRecordNoComponents() { doTest(true); }

  public void testRecordWithTypeParameters() { doTest(true); }

  public void testRecordNoClosingTypeBracket() { doTest(true); }

  public void testRecordWithModifiers() { doTest(true); }
  
  public void testRecordIncomplete() { doTest(true); }

  public void testRecordInCodeBlock() { doTest(true); }
  
  public void testRecordInCodeBlockIncompleteBeforeDeclaration() { doTest(true); }
  
  public void testRecordInCodeBlockIncompleteBeforeIfStatement() { doTest(true); }
  
  public void testVarIncomplete() { doTest(true); }

  public void testLocalRecord() { doTest(true); }

  public void testLocalRecordWithTypeParams() { doTest(true); }

  public void testLocalRecordWithoutParens() { doTest(true); }

  public void testCompactConstructor0() { doTest(true); }

  public void testCompactConstructor1() { doTest(true); }

  public void testRecordTypeInOlderJava() {
    setLanguageLevel(LanguageLevel.JDK_13);
    doTest(true);
  }

  public void testSealedInterface() {
    doTest(true);
  }
  public void testSealedClassOldLanguageLevel() {
    setLanguageLevel(LanguageLevel.JDK_1_8);
    doTest(true);
  }
  public void testNonSealedClass() {
    doTest(true);
  }

  public void testLocalSealedClass() { doTest(true); }

  public void testProvidesList() {
    doTest(true);
  }

  public void testImplicitClass0() {
    doTest(true);
  }
  public void testImplicitClass1() {
    doTest(true);
  }
  public void testImplicitClass2() {
    doTest(true);
  }
  public void testImplicitClass3() {
    doTest(true);
  }
  public void testImplicitClass4() {
    doTest(true);
  }
  public void testImplicitClass5() {
    doTest(true);
  }
  public void testImplicitClassJavaDoc() {
    doTest(true);
  }
}
