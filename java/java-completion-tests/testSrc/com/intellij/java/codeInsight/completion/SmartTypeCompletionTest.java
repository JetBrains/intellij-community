// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.completion;

import com.intellij.JavaTestUtil;
import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.JavaProjectCodeInsightSettings;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.LightCompletionFixture;
import com.intellij.codeInsight.completion.StaticallyImportable;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.codeInsight.template.SmartCompletionContextType;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.impl.TemplateContextTypes;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.platform.testFramework.junit5.codeInsight.indexing.IndexingModeTemplate;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiClass;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.util.PsiUtil;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.NeedsIndex;
import com.intellij.testFramework.fixtures.CodeInsightTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.containers.ContainerUtil;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.codeInsight.completion.LightCompletionFixtureKt.lightCompletionFixture;
import static com.intellij.codeInsight.completion.LightCompletionFixtureKt.lightCompletionProjectFixture;
import static com.intellij.java.codeInsight.completion.NormalCompletionFixture.renderElement;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
@IndexingModeTemplate
public class SmartTypeCompletionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightCompletionProjectFixture();
  private final TestFixture<LightCompletionFixture> completion =
    lightCompletionFixture(projectFixture, JavaTestUtil.getJavaTestDataPath() + "/codeInsight/completion/smartType/");

  @Test
  public void testParenAfterCast1() {
    String path = "/parenAfterCast";

    EdtTestUtil.runInEdtAndWait(() -> {
      configureByFile(path + "/before1.java");
      completion.get().checkResultByFile(path + "/after1.java");
    });
  }

  @Test
  public void testParenAfterCast2() {
    String path = "/parenAfterCast";

    EdtTestUtil.runInEdtAndWait(() -> {
      configureByFile(path + "/before2.java");
      completion.get().checkResultByFile(path + "/after2.java");
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testParenAfterCast3() {
    String path = "/parenAfterCast";

    EdtTestUtil.runInEdtAndWait(() -> {
      configureByFile(path + "/before3.java");
      completion.get().checkResultByFile(path + "/after3.java");
    });
  }

  @Test
  public void testParenAfterCall1() {
    String path = "/parenAfterCall";

    EdtTestUtil.runInEdtAndWait(() -> {
      configureByFile(path + "/before1.java");
      completion.get().checkResultByFile(path + "/after1.java");
    });
  }

  @Test
  public void testParenAfterCall2() {
    String path = "/parenAfterCall";

    EdtTestUtil.runInEdtAndWait(() -> {
      configureByFile(path + "/before2.java");
      completion.get().checkResultByFile(path + "/after2.java");
    });
  }

  @Test
  public void testParenAfterCall3() {
    String path = "/parenAfterCall";

    EdtTestUtil.runInEdtAndWait(() -> {
      configureByFile(path + "/before3.java");
      completion.get().checkResultByFile(path + "/after3.java");
    });
  }

  @Test
  public void testParenAfterCall4() {
    String path = "/parenAfterCall";

    EdtTestUtil.runInEdtAndWait(() -> {
      configureByFile(path + "/before4.java");
      completion.get().checkResultByFile(path + "/after4.java");
    });
  }

  @Test
  public void testParenAfterCall5() {
    String path = "/parenAfterCall";

    EdtTestUtil.runInEdtAndWait(() -> {
      configureByFile(path + "/before5.java");
      completion.get().checkResultByFile(path + "/after5.java");
    });
  }

  @Test
  public void testParenAfterCall6() {
    String path = "/parenAfterCall";

    EdtTestUtil.runInEdtAndWait(() -> {
      configureByFile(path + "/before6.java");
      completion.get().checkResultByFile(path + "/after6.java");
    });
  }

  @Test
  public void testParenAfterCall1_SpaceWithinMethodCallParens() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    String path = "/parenAfterCall";

    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByFile(path + "/before1.java");
      getCodeStyleSettings().SPACE_WITHIN_METHOD_CALL_PARENTHESES = true;
      complete();
      completion.get().checkResultByFile(path + "/after1_space.java");
    });
  }

  @Test
  public void testParenAfterIf1() {
    String path = "/parenAfterIf";

    EdtTestUtil.runInEdtAndWait(() -> {
      configureByFile(path + "/before1.java");
      completion.get().checkResultByFile(path + "/after1.java");
    });
  }

  @Test
  public void testParenAfterIf2() {
    String path = "/parenAfterIf";

    EdtTestUtil.runInEdtAndWait(() -> {
      configureByFile(path + "/before2.java");
      completion.get().checkResultByFile(path + "/after2.java");
    });
  }

  @Test
  public void testForceLookupForAbstractClasses() {
    String path = "/afterNew";

    EdtTestUtil.runInEdtAndWait(() -> {
      configureByFile(path + "/before9.java");
      completion.get().checkResultByFile(path + "/after9.java");
    });
  }

  @Test
  public void testAfterNew1() {
    String path = "/afterNew";

    EdtTestUtil.runInEdtAndWait(() -> {
      configureByFile(path + "/before1.java");
      select();
      completion.get().checkResultByFile(path + "/after1.java");
    });
  }

  @Test
  public void testAfterNew2() {
    String path = "/afterNew";

    EdtTestUtil.runInEdtAndWait(() -> {
      configureByFile(path + "/before2.java");
      select();
      completion.get().checkResultByFile(path + "/after2.java");
    });
  }

  @Test
  public void testAfterNew3() {
    String path = "/afterNew";

    EdtTestUtil.runInEdtAndWait(() -> {
      configureByFile(path + "/before3.java");
      select();
      completion.get().checkResultByFile(path + "/after3.java");
    });
  }

  @Test
  @NeedsIndex.Full
  public void testAfterNew4() {
    String path = "/afterNew";

    EdtTestUtil.runInEdtAndWait(() -> {
      configureByFile(path + "/before4.java");
      select();
      completion.get().checkResultByFile(path + "/after4.java");
    });
  }

  @Test
  @NeedsIndex.Full
  public void testAfterNew5() {
    String path = "/afterNew";

    EdtTestUtil.runInEdtAndWait(() -> {
      configureByFile(path + "/before5.java");
      select();
      completion.get().checkResultByFile(path + "/after5.java");
    });
  }

  @Test
  @NeedsIndex.Full
  public void testAfterNew6() {
    String path = "/afterNew";

    EdtTestUtil.runInEdtAndWait(() -> {
      configureByFile(path + "/before6.java");
      select();
      completion.get().checkResultByFile(path + "/after6.java");
    });
  }

  @Test
  @NeedsIndex.Full
  public void testAfterNew7() {
    String path = "/afterNew";

    EdtTestUtil.runInEdtAndWait(() -> {
      configureByFile(path + "/before7.java");
      select();
      completion.get().checkResultByFile(path + "/after7.java");
    });
  }

  @Test
  @NeedsIndex.Full
  public void testAfterNew8() {
    String path = "/afterNew";

    EdtTestUtil.runInEdtAndWait(() -> {
      configureByFile(path + "/before8.java");
      select();
      completion.get().checkResultByFile(path + "/after8.java");
    });
  }

  @Test
  @NeedsIndex.Full
  public void testAfterNew9() {
    String path = "/afterNew";

    EdtTestUtil.runInEdtAndWait(() -> {
      configureByFile(path + "/before10.java");
      select();
      completion.get().checkResultByFile(path + "/after10.java");
    });
  }

  @Test
  public void testAfterNew10() {
    String path = "/afterNew";

    EdtTestUtil.runInEdtAndWait(() -> {
      configureByFile(path + "/before12.java");
      //select();
      completion.get().checkResultByFile(path + "/after12.java");
    });
  }

  @Test
  public void testAfterNew11() {
    String path = "/afterNew";

    EdtTestUtil.runInEdtAndWait(() -> {
      configureByFile(path + "/before13.java");
      //select();
      completion.get().checkResultByFile(path + "/after13.java");
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testAfterThrowNew1() {
    String path = "/afterNew";

    EdtTestUtil.runInEdtAndWait(() -> {
      configureByFile(path + "/before14.java");
      select();
      completion.get().checkResultByFile(path + "/after14.java");
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testAfterThrowNew2() {
    String path = "/afterNew";

    EdtTestUtil.runInEdtAndWait(() -> {
      configureByFile(path + "/before15.java");
      select();
      completion.get().checkResultByFile(path + "/after15.java");
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testAfterThrowNew3() {
    String path = "/afterNew";

    EdtTestUtil.runInEdtAndWait(() -> {
      configureByFile(path + "/before16.java");
      select();
      completion.get().checkResultByFile(path + "/after16.java");
    });
  }

  @Test
  public void testCastInThrow() { doTest(); }
  @Test
  @NeedsIndex.ForStandardLibrary
  public void testNonExistentGenericAfterNew() { doTest('\n'); }

  @Test
  public void testParenAfterNewWithinInnerExpr() {
    String path = "/afterNew";

    EdtTestUtil.runInEdtAndWait(() -> {
      configureByFile(path + "/LastArgInInnerNewBefore.java");
      completion.get().checkResultByFile(path + "/LastArgInInnerNewAfter.java");

      //configureByFile(path + "/LastArgInInnerNewBefore2.java");
      //performAction();
      //checkResultByFile(path + "/LastArgInInnerNewAfter2.java");

      configureByFile(path + "/LastArgInInnerNewBefore3.java");
      completion.get().checkResultByFile(path + "/LastArgInInnerNewAfter3.java");

      configureByFile(path + "/LastArgInInnerNewBefore4.java");
      completion.get().checkResultByFile(path + "/LastArgInInnerNewAfter4.java");
    });
  }

  @Test
  public void testReturn1() {
    String path = "/return";
    EdtTestUtil.runInEdtAndWait(() -> {
      configureByFile(path + "/before1.java");
      completion.get().checkResultByFile(path + "/after1.java");
    });
  }

  @Test
  public void testReturn2() {
    String path = "/return";

    EdtTestUtil.runInEdtAndWait(() -> {
      configureByFile(path + "/before2.java");
      completion.get().checkResultByFile(path + "/after2.java");
    });
  }

  @Test
  public void testReturn3() {
    String path = "/return";

    EdtTestUtil.runInEdtAndWait(() -> {
      configureByFile(path + "/before3.java");
      completion.get().type('\n');
      completion.get().checkResultByFile(path + "/after3.java");
    });
  }

  @Test
  public void testGenerics1() {
    String path = "/generics";

    EdtTestUtil.runInEdtAndWait(() -> {
      configureByFile(path + "/before1.java");
      completion.get().checkResultByFile(path + "/after1.java");
    });
  }

  @Test
  public void testGenerics2() {
    String path = "/generics";

    EdtTestUtil.runInEdtAndWait(() -> {
      configureByFile(path + "/before2.java");
      completion.get().checkResultByFile(path + "/after2.java");
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testGenerics3() {
    String path = "/generics";

    EdtTestUtil.runInEdtAndWait(() -> {
      configureByFile(path + "/before3.java");
      completion.get().checkResultByFile(path + "/after3.java");
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testGenerics4() {
    String path = "/generics";

    EdtTestUtil.runInEdtAndWait(() -> {
      configureByFile(path + "/before4.java");
      completion.get().checkResultByFile(path + "/after4.java");
    });
  }

  @Test
  public void testGenerics5() {
    String path = "/generics";

    EdtTestUtil.runInEdtAndWait(() -> {
      configureByFile(path + "/before5.java");
      completion.get().checkResultByFile(path + "/after5.java");
    });
  }

  @Test
  @NeedsIndex.Full
  public void testAfterInstanceOf1() {
    String path = "/afterInstanceOf";

    EdtTestUtil.runInEdtAndWait(() -> {
      configureByFile(path + "/before1.java");
      completion.get().checkResultByFile(path + "/after1.java");
    });
  }

  @Test
  @NeedsIndex.Full
  public void testAfterInstanceOf2() {
    String path = "/afterInstanceOf";

    EdtTestUtil.runInEdtAndWait(() -> {
      configureByFile(path + "/before2.java");
      completion.get().checkResultByFile(path + "/after2.java");
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testInsideCatch() { doTest(); }
  @Test
  @NeedsIndex.ForStandardLibrary
  public void testInsideCatchFinal() { doTest(); }
  @Test
  @NeedsIndex.ForStandardLibrary
  public void testInsideCatchWithoutThrow() { doTest(); }

  @Test
  public void testGenerics6() {
    String path = "/generics";

    EdtTestUtil.runInEdtAndWait(() -> {
      configureByFile(path + "/before6.java");
      completion.get().checkResultByFile(path + "/after6.java");
    });
  }

  @Test
  public void testWildcardNew1() {
    String path = "/generics";

    EdtTestUtil.runInEdtAndWait(() -> {
      configureByFile(path + "/before7.java");
      completion.get().checkResultByFile(path + "/after7.java");
    });
  }

  @Test
  public void testWildcardNew2() {
    String path = "/generics";

    EdtTestUtil.runInEdtAndWait(() -> {
      configureByFile(path + "/before8.java");
      completion.get().checkResultByFile(path + "/after8.java");
    });
  }

  @Test
  public void testWildcardEliminated() {
    String path = "/generics";

    EdtTestUtil.runInEdtAndWait(() -> {
      configureByFile(path + "/before9.java");
      completion.get().selectItem(completion.get().getItems()[1]);
      completion.get().checkResultByFile(path + "/after9.java");
    });
  }

  @Test
  public void testBug1() { doTest(); }

  @Test
  public void testQualifiedThis() { doTest(); }

  @Test
  public void testBug2() {
    configureByFile("/Bug2.java");
  }

  @Test
  public void testSillyAssignment1() {
    EdtTestUtil.runInEdtAndWait(() -> {
      configureByFile("/Silly1.java");
      completion.get().checkResultByFile("/Silly1.java");
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testVarargs1() { doTest('\n'); }

  @Test
  public void testEnumConstInSwitch() { doTest(); }

  @Test
  public void testEnumConstInSwitchOutside() { doTest(); }

  @Test
  public void testIntConstInSwitch() { doTest(); }

  @Test
  public void testDoubleEmptyArray() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      configureByTestName();
      completion.get().checkResultByFile("/"+myFixture.getTestName(false) + ".java");
      assertEquals(2, completion.get().getItems().length);
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testCollectionsEmptySetInMethodCall() { doTest(); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testCollectionsEmptySetInTernary() { doTest(); }

  @Test
  public void testStringConstantInAnno() { doTest(); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testCollectionsEmptySetInTernary2() { doTest(); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testConstructorOnSeparateLineInMethodCall() { doTest(); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testConstructorWithExistingParens() { doTest(); }

  @Test
  public void testMethodAnnotationNamedParameter() { doTest(); }

  @Test
  public void testInheritedClass() { doTest(); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testClassLiteralInAnno1() { doTest(); }

  @Test
  public void testMeaninglessExplicitWildcardParam() { doTest(); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testExplicitWildcardArrayParam() { doTest(); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testCatchInAnonymous() { doTest(); }

  @Test
  public void testThrowRuntimeException() { doTest(); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testParameterizedConstructor() { doTest(); }

  @Test
  public void testNewInnerClassNameShortPrefix() { doTest('\n'); }

  @Test
  public void testNewInnerOfParameterizedClass() { doTest(); }

  @Test
  public void testQualifiedThisInAnonymousConstructor() { doTest(); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testExceptionTwice() { doTest(); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testExceptionTwice2() { doTest(); }

  @Test
  public void testNewInnerRunnable() { doTest(); }

  @Test
  public void testArrayAccessIndex() { doTest(); }

  @Test
  @NeedsIndex.ForStandardLibrary(reason = "Need to resolve java.lang.String")
  public void testThrowExceptionConstructor() { doTest('\n'); }

  @Test
  public void testJavadocThrows() { doTest(); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testMethodThrows() { doTest(); }

  @Test
  public void testDoNotExcludeAssignedVariable() { doTest(); }

  @Test
  public void testArrayIndexTailType() { doTest(); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testPrivateOverloads() { doTest(); }
  @Test
  public void testInaccessibleMethodArgument() { doTest(); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testPolyadicExpression() { doTest(); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testCastAutoboxing() {
    doItemTest();
  }
  @Test
  @NeedsIndex.ForStandardLibrary
  public void testCastAutoboxing2() {
    doItemTest();
  }
  @Test
  @NeedsIndex.ForStandardLibrary
  public void testCastAutoboxing3() {
    doItemTest();
  }
  @Test
  public void testCastWildcards() { doTest(); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testNoSecondMethodTypeArguments() { doTest(Lookup.REPLACE_SELECT_CHAR); }

  @Test
  public void testNoFieldsInSuperConstructorCall() { doTest(); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testChainMethodsInSuperConstructorCall() { doTest(); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testNoUninitializedFieldsInConstructor() {
    EdtTestUtil.runInEdtAndWait(() -> {
      configureByTestName();
      completion.get().assertStringItems("aac", "aab", "hashCode");
    });
  }

  @Test
  public void testNoUninitializedSuperFieldsInConstructor() {
    EdtTestUtil.runInEdtAndWait(() -> {
      configureByTestName();
      completion.get().assertStringItems("input", "baseConstant");
    });
  }

  @Test
  public void testFieldsSetInAnotherConstructor() { doTest(); }
  @Test
  public void testFieldsSetAbove() { doTest(); }

  @Test
  public void testHonorSelection() {
    EdtTestUtil.runInEdtAndWait(() -> {
      configureByTestName();
      select();
      checkResultByTestName();
    });
  }

  @Test
  public void testTypeParametersInheritors() {
    EdtTestUtil.runInEdtAndWait(() -> {
      configureByTestName();
      completion.get().assertStringItems("Foo", "Bar", "Goo");
      select();
      checkResultByTestName();
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testVoidExpectedType() {
    EdtTestUtil.runInEdtAndWait(() -> {
      configureByTestName();
      completion.get().assertStringItems("notify", "notifyAll", "wait", "wait", "wait", "equals", "hashCode", "toString", "getClass");
      completion.get().type("eq");
      assertEquals("equals", assertThat(completion.get().getLookup().getItems()).singleElement().actual().getLookupString());
      select();
      checkResultByTestName();
    });
  }

  @Test
  public void testDoubleSemicolonPenetration() { doTest(); }

  @Test
  public void testTypeParametersInheritorsComma() { doTest(); }

  @Test
  public void testTypeParametersInheritorsInExpression() { doTest(); }

  //do we need to see all Object inheritors at all?
  public void _testTypeParametersObjectInheritors() { doTest(); }

  @Test
  public void testDoubleThis() {
    EdtTestUtil.runInEdtAndWait(() -> {
      doTest();
      assertNull(completion.get().getItems());
    });
  }

  @Test
  public void testSmartFinish() { doTest(Lookup.COMPLETE_STATEMENT_SELECT_CHAR); }

  @Test
  public void testSillyAssignmentInTernary() { doTest(); }

  @Test
  public void testSameFieldInAnotherObject() { doTest(); }

  @Test
  public void testUnqualifiedConstantInSwitch() { doTest(); }

  @Test
  public void testAmbiguousConstant() { doTest(); }

  @Test
  public void testSameNamedFieldAndLocal() { doTest(); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testNoTailWhenNoPairBracket() { doTestNoPairBracket(Lookup.NORMAL_SELECT_CHAR); }

  @Test
  public void testNoTailWhenNoPairBracket2() { doTestNoPairBracket(Lookup.NORMAL_SELECT_CHAR); }

  @Test
  @NeedsIndex.SmartMode(reason = "For now ConstructorInsertHandler.createOverrideRunnable doesn't work in dumb mode")
  public void testAnonymousNoPairBracket() { doTestNoPairBracket(Lookup.NORMAL_SELECT_CHAR); }

  private void doTestNoPairBracket(final char c) {
    boolean old = CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET;
    CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET = false;
    try {
      doTest(c);
    }
    finally {
      CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET = old;
    }
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testNoConstructorTailWhenNoPairBracket() { doTestNoPairBracket(Lookup.NORMAL_SELECT_CHAR); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testConstructorNoPairBracketSemicolon() { doTestNoPairBracket(';'); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testMethodNoPairBracketComma() { doTestNoPairBracket(','); }

  @Test
  public void testAbstractClassTwice() {
    EdtTestUtil.runInEdtAndWait(() -> {
      configureByTestName();
      assertThat(completion.get().getItems()).hasSize(1);
    });
  }

  @Test
  public void testConstantTwice() { doTest(); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testConstantTwice2() {
    EdtTestUtil.runInEdtAndWait(() -> {
      configureByTestName();
      assertEquals(2, completion.get().getItems().length);
    });
  }

  @Test
  public void testNoKeyConstant() {
    EdtTestUtil.runInEdtAndWait(() -> {
      configureByTestName();
      completion.get().assertStringItems("A_KEY", "create");
    });
  }

  @Test
  public void testUserDataListAddAll() {
    doTest();
  }

  @Test
  public void testStaticSubclass() {
    doTest();
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testMethodCallDot() { doTest('\n'); }
  @Test
  public void testNegateVariable() { doTest(); }

  @Test
  public void testExclamationMethodFinish() { doTest('!'); }
  @Test
  public void testExclamationVariableFinish() { doTest('!'); }
  @Test
  @NeedsIndex.ForStandardLibrary
  public void testExclamationStaticFieldFinish() { doTest('!'); }
  @Test
  public void testExclamationFinishNonBoolean() { doTest('!'); }

  @Test
  public void testExcludeDeclaredConstant() { doTest(); }

  @Test
  public void testTabMethodInBinaryExpression() { doTest('\t'); }

  @Test
  public void testIfConditionBinaryExpression() { doTest(); }

  @Test
  public void testDelegationToParent() { doTest('\t'); }

  @Test
  public void testBeforeBinaryExpressionInMethodCall() { doTest(); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testAssignableToAfterCast() { doTest(); }

  @Test
  public void testInstanceMethodParametersFromStaticContext() { doTest(); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testInstanceMethodParametersFromStaticContext2() { doTest(); }

  @Test
  public void testBeforeCastToArray() { doTest(); }

  @Test
  public void testHidingFields() { doTest(); }

  @Test
  public void testVoidCast() { doAntiTest(); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testNoGenericMethodAutoInsertion() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      configureByTestName();
      myFixture.assertPreferredCompletionItems(0, "valueOf");
    });
  }

  @Test
  public void testAutoInsertMethodReturningClassTypeParam() { doActionTest(); }

  @Test
  public void testIntPlusLongNotDouble() { doTest(); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testNestedAssignments() { doTest(); }

  @Test
  @NeedsIndex.SmartMode(reason = "AbstractExpectedTypeSkipper works in smart mode only")
  public void testAfterNewInTernary() { doTest(); }

  @Test
  public void testSuggestAnythingWhenWildcardExpected() {
    EdtTestUtil.runInEdtAndWait(() -> {
      configureByTestName();
      completion.get().assertStringItems("X", "Y", "Z");
    });
  }

  @Test
  public void testNewVararg() {
    EdtTestUtil.runInEdtAndWait(() -> {
      configureByTestName();
      completion.get().assertStringItems("Foo", "Foo", "Foo");
      assertEquals("{...} (default package)", renderElement(completion.get().getItems()[0]).getTailText());
      assertEquals("[] (default package)", renderElement(completion.get().getItems()[1]).getTailText());
      assertEquals("[]{...} (default package)", renderElement(completion.get().getItems()[2]).getTailText());
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testNewVararg2() {
    EdtTestUtil.runInEdtAndWait(() -> {
      configureByTestName();
      completion.get().assertStringItems("String", "String", "String");
      assertEquals(" (java.lang)", renderElement(completion.get().getItems()[0]).getTailText());
      assertEquals("[] (java.lang)", renderElement(completion.get().getItems()[1]).getTailText());
      assertEquals("[]{...} (java.lang)", renderElement(completion.get().getItems()[2]).getTailText());
    });
  }

  @Test
  public void testNewByteArray() {
    EdtTestUtil.runInEdtAndWait(() -> {
      configureByTestName();
      completion.get().assertStringItems("byte");
      assertEquals("[]", renderElement(completion.get().getItems()[0]).getTailText());
    });
  }

  @Test
  public void testNewByteArray2() {
    EdtTestUtil.runInEdtAndWait(() -> {
      configureByTestName();
      completion.get().assertStringItems("byte", "byte");
      assertEquals("[]", renderElement(completion.get().getItems()[0]).getTailText());
      assertEquals("[]{...}", renderElement(completion.get().getItems()[1]).getTailText());
    });
  }

  @Test
  public void testInsideStringLiteral() { doAntiTest(); }

  @Test
  public void testDefaultAnnoParam() { doTest(); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testNewWithTypeParameterErasure() { doTest(); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testEverythingDoubles() {
    EdtTestUtil.runInEdtAndWait(() -> {
      configureByTestName();
      completion.get().assertStringItems("hashCode", "indexOf", "lastIndexOf", "size");
    });
  }

  @Test
  public void testNonStaticInnerClass() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      configureByTestName();
      assertThat(completion.get().getItems()).isEmpty();
      completion.get().checkResultByFile("/" + myFixture.getTestName(false) + ".java");
    });
  }

  //todo 2nd completion
  public void _testDefaultAnnoParam2() { doTest(); }

  @Test
  public void testAnnotationValue() {doTest(); }

  @Test
  public void testLiveTemplate() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      final Template template = TemplateManager.getInstance(myFixture.getProject()).createTemplate("foo", "zzz");
      template.addTextSegment("FooFactory.createFoo()");
      SmartCompletionContextType completionContextType = TemplateContextTypes.getByClass(SmartCompletionContextType.class);
      ((TemplateImpl)template).getTemplateContext().setEnabled(completionContextType, true);
      CodeInsightTestUtil.addTemplate(template, myFixture.getTestRootDisposable());
      doTest();
    });
  }

  @Test
  public void testInThisExpression() { doTest(); }

  @Test
  public void testSuggestNull() { doTest(); }

  @Test
  public void testNoNullAfterDot() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      configureByTestName();
      assertThat(completion.get().getItems()).isEmpty();
      completion.get().checkResultByFile("/" + myFixture.getTestName(false) + ".java");
    });
  }

  @Test
  public void testDefaultAnnoMethodValue() { doTest(); }

  @Test
  @NeedsIndex.SmartMode(reason = "For now ConstructorInsertHandler.createOverrideRunnable doesn't work in dumb mode")
  public void testNewAnonymousFunction() { doTest(); }

  @Test
  @NeedsIndex.SmartMode(reason = "For now ConstructorInsertHandler.createOverrideRunnable doesn't work in dumb mode")
  public void testNewRunnableInsideMethod() {
    EdtTestUtil.runInEdtAndWait(() -> {
      CommonCodeStyleSettings settings = getCodeStyleSettings();
      boolean lParenOnNextLine = settings.CALL_PARAMETERS_LPAREN_ON_NEXT_LINE;
      try {
        settings.CALL_PARAMETERS_LPAREN_ON_NEXT_LINE = true;
        doTest();
      } finally {
        settings.CALL_PARAMETERS_LPAREN_ON_NEXT_LINE = lParenOnNextLine;
      }
    });
  }

  @Test
  @NeedsIndex.SmartMode(reason = "For now ConstructorInsertHandler.createOverrideRunnable doesn't work in dumb mode")
  public void testNewRunnableInsideMethodMultiParams() {
    EdtTestUtil.runInEdtAndWait(() -> {
      CommonCodeStyleSettings settings = getCodeStyleSettings();
      boolean lParenOnNextLine = settings.CALL_PARAMETERS_LPAREN_ON_NEXT_LINE;
      boolean rParenOnNextLine = settings.CALL_PARAMETERS_RPAREN_ON_NEXT_LINE;
      try {
        settings.CALL_PARAMETERS_LPAREN_ON_NEXT_LINE = true;
        settings.CALL_PARAMETERS_RPAREN_ON_NEXT_LINE = true;
        doTest();
      } finally {
        settings.CALL_PARAMETERS_LPAREN_ON_NEXT_LINE = lParenOnNextLine;
        settings.CALL_PARAMETERS_RPAREN_ON_NEXT_LINE = rParenOnNextLine;
      }
    });
  }

  @Test
  public void testUseIntConstantsFromTargetClass() { doTest(); }
  @Test
  @NeedsIndex.ForStandardLibrary
  public void testUseObjectConstantsFromTargetClass() { doTest(); }
  @Test
  public void testUseIntConstantsFromTargetClassReturnValue() { doTest(); }
  @Test
  public void testUseIntConstantsFromConstructedClass() { doTest(); }
  @Test
  public void testUseIntConstantsInPlus() { doTest(); }
  @Test
  public void testUseIntConstantsInOr() { doTest(); }

  @Test
  public void testExtraSemicolonAfterMethodParam() {
    EdtTestUtil.runInEdtAndWait(() -> {
      getCodeStyleSettings().SPACE_WITHIN_METHOD_CALL_PARENTHESES = true;
      doTest();
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testNoSemicolonInsideParentheses() { doTest(); }

  @Test
  public void testAssignFromTheSameFieldOfAnotherObject() {
    doTest();
  }

  @Test
  public void testTailAfterInstanceOf() {
    doTest();
  }

  @Test
  public void testReplaceWholeReferenceChain() { doTest(Lookup.REPLACE_SELECT_CHAR); }

  @Test
  public void testDoubleTrueInOverloadedMethodCall() { doTest(Lookup.REPLACE_SELECT_CHAR); }

  @Test
  public void testMethodColon() { doFirstItemTest(':'); }
  @Test
  public void testVariableColon() { doFirstItemTest(':'); }
  @Test
  public void testConditionalColonOnNextLine() { doFirstItemTest(':'); }

  private void doFirstItemTest(char c) {
    EdtTestUtil.runInEdtAndWait(() -> {
      configureByTestName();
      select(c);
      checkResultByTestName();
    });
  }

  @Test
  public void testOneElementArray() { doTest(); }

  @Test
  public void testCastToArray() { doTest(); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testCommaDoublePenetration() {
    doFirstItemTest(',');
  }

  @Test
  public void testSuperMethodArguments() {
    EdtTestUtil.runInEdtAndWait(() -> {
      configureByTestName();
      completion.get().getLookup().setCurrentItem(completion.get().getLookup().getItems().get(1));
      select();
      checkResultByTestName();
    });
  }

  @Test
  public void testDelegateMethodArguments() {
    EdtTestUtil.runInEdtAndWait(() -> {
      configureByTestName();
      completion.get().getLookup().setCurrentItem(completion.get().getLookup().getItems().get(1));
      select();
      checkResultByTestName();
    });
  }

  @Test
  public void testSameMethodArgumentsInIf() {
    EdtTestUtil.runInEdtAndWait(() -> {
      configureByTestName();
      completion.get().getLookup().setCurrentItem(completion.get().getLookup().getItems().get(1));
      select();
      checkResultByTestName();
    });
  }

  @Test
  public void testSuperConstructorArguments() {
    EdtTestUtil.runInEdtAndWait(() -> {
      configureByTestName();
      completion.get().getLookup().setCurrentItem(completion.get().getLookup().getItems().get(2));
      select();
      checkResultByTestName();
    });
  }

  @Test
  public void testSameNamedArguments() {
    EdtTestUtil.runInEdtAndWait(() -> {
      configureByTestName();
      completion.get().getLookup().setCurrentItem(completion.get().getLookup().getItems().get(4));
      select();
      checkResultByTestName();
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testSameNamedArgumentsDelegation() {
    EdtTestUtil.runInEdtAndWait(() -> {
      configureByTestName();
      completion.get().getLookup().setCurrentItem(completion.get().getLookup().getItems().get(1));
      select();
      checkResultByTestName();
    });
  }

  @Test
  public void testSameSignatureWithGenerics() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      configureByTestName();
      myFixture.assertPreferredCompletionItems(0, "i", "z", "zz", "i, z, zz");
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testSameSignatureWithoutClosingParen() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      configureByTestName();
      myFixture.assertPreferredCompletionItems(0, "someString", "someString, number");
      completion.get().getLookup().setCurrentItem(completion.get().getLookup().getItems().get(1));
      select();
      checkResultByTestName();
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testSuggestTypeParametersInTypeArgumentList() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      configureByTestName();
      myFixture.assertPreferredCompletionItems(0, "T", "String");
    });
  }

  @Test
  @NeedsIndex.SmartMode(reason = "For now ConstructorInsertHandler.createOverrideRunnable doesn't work in dumb mode")
  public void testWrongAnonymous() {
    EdtTestUtil.runInEdtAndWait(() -> {
      configureByTestName();
      select();
      checkResultByTestName();
    });
  }

  @Test
  public void testAfterNewWithGenerics() {
    doActionTest();
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testClassLiteral() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      doActionTest();
      completion.get().assertStringItems("String.class");

      LookupElement item = myFixture.getLookupElements()[0];
      LookupElementPresentation p = renderElement(item);
      assertEquals("String.class", p.getItemText());
      assertEquals(" (java.lang)", p.getTailText());
      assertEquals("", p.getTypeText());

      assertInstanceOf(PsiClass.class, item.getPsiElement());
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testNoClassLiteral() {
    EdtTestUtil.runInEdtAndWait(() -> {
      doActionTest();
      completion.get().assertStringItems("Object.class", "getClass", "forName", "forName");
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testClassLiteralForRawClassTypeArgument() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java", """
        class a {{
          Class<Class> classClass = Cla<caret>
        }}""");
      complete();
      completion.get().selectItem(ContainerUtil.find(completion.get().getItems(), item -> item.getLookupString().equals("Class.class")));
      myFixture.checkResult("""
        class a {{
          Class<Class> classClass = Class.class;<caret>
        }}""");
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testNoClassLiteralForParameterizedClassTypeArgument() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java", """
        class a {{
          Class<Class<?>> classClass = Cla<caret>
        }}""");
      complete();
      assertFalse(completion.get().getItems() != null && ContainerUtil.exists(completion.get().getItems(), item -> item.getLookupString().equals("Class.class")));
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testClassLiteralInAnno2() {
    doItemTest();
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testClassLiteralInheritors() {
    doItemTest();
  }

  @Test
  @NeedsIndex.SmartMode(reason = "For now ConstructorInsertHandler.createOverrideRunnable doesn't work in dumb mode")
  public void testInsertOverride() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      JavaCodeStyleSettings styleSettings = JavaCodeStyleSettings.getInstance(myFixture.getProject());
      styleSettings.INSERT_OVERRIDE_ANNOTATION = true;
      doItemTest();
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testForeach() {
    doActionTest();
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testIDEADEV2626() {
    doItemTest();
  }

  @Test
  @NeedsIndex.SmartMode(reason = "For now ConstructorInsertHandler.createOverrideRunnable doesn't work in dumb mode")
  public void testDontSuggestWildcardGenerics() { doItemTest(); }

  @Test
  public void testCastWith2TypeParameters() { doTest(); }
  @Test
  @NeedsIndex.ForStandardLibrary
  public void testClassLiteralInArrayAnnoInitializer() { doTest(); }
  @Test
  @NeedsIndex.ForStandardLibrary
  public void testClassLiteralInArrayAnnoInitializer2() { doTest(); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testAnnotation() {
    EdtTestUtil.runInEdtAndWait(() -> {
      configureByTestName();
      completion.get().assertStringItems("ElementType.ANNOTATION_TYPE", "ElementType.CONSTRUCTOR",
                        "ElementType.FIELD", "ElementType.LOCAL_VARIABLE",
                        "ElementType.METHOD", "ElementType.PACKAGE", "ElementType.PARAMETER",
                        "ElementType.TYPE" /*, "ElementType.TYPE_PARAMETER", "ElementType.TYPE_USE"*/);
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testAnnotation2() {
    EdtTestUtil.runInEdtAndWait(() -> {
      configureByTestName();
      completion.get().assertStringItems("RetentionPolicy.CLASS", "RetentionPolicy.RUNTIME", "RetentionPolicy.SOURCE");
    });
  }
  @Test
  @NeedsIndex.ForStandardLibrary
  public void testAnnotation2_2() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      configureByTestName();
      assertThat(myFixture.getLookupElementStrings()).containsOnly("RetentionPolicy.CLASS", "RetentionPolicy.SOURCE", "RetentionPolicy.RUNTIME");
    });
  }

  @Test
  public void testAnnotation3() {
    doTest();
  }

  @Test
  public void testAnnotation3_2() {
    doTest();
  }

  @Test
  public void testAnnotation4() {
    EdtTestUtil.runInEdtAndWait(() -> {
      configureByTestName();
      checkResultByTestName();

      completion.get().assertStringItems("false", "true");
    });
  }

  @Test
  public void testAnnotation5() {
    EdtTestUtil.runInEdtAndWait(() -> {
      configureByTestName();
      checkResultByTestName();

      completion.get().assertStringItems("CONNECTION", "NO_CONNECTION");
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testAnnotation6() {
    EdtTestUtil.runInEdtAndWait(() -> {
      configureByTestName();

      completion.get().assertStringItems("ElementType.ANNOTATION_TYPE", "ElementType.CONSTRUCTOR",
                        "ElementType.FIELD", "ElementType.LOCAL_VARIABLE",
                        "ElementType.METHOD", "ElementType.PACKAGE", "ElementType.PARAMETER",
                        "ElementType.TYPE"/*, "ElementType.TYPE_PARAMETER", "ElementType.TYPE_USE"*/);
    });
  }

  @Test
  public void testArrayClone() {
    doTest();
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testIDEADEV5150() {
    doTest('\n');
  }

  @Test
  public void testIDEADEV7835() {
    doTest();
  }

  @Test
  public void testTypeArgs1() {
    doTest();
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testTypeArgs2() {
    doTest();
  }
  @Test
  @NeedsIndex.ForStandardLibrary
  public void testTypeArgsOverwrite() { doTest(); }

  @Test
  @NeedsIndex.SmartMode(reason = "For now ConstructorInsertHandler.createOverrideRunnable doesn't work in dumb mode")
  public void testIfConditionExpectedType() { doTest(); }

  @Test
  public void testUnboundTypeArgs() { doTest(); }
  @Test
  public void testUnboundTypeArgs2() { doTest(); }
  @Test
  public void testSameTypeArg() { doTest(); }

  @Test
  public void testIDEADEV2668() {
    doTest();
  }

  @Test
  public void testExcessiveTail() { doTest(); }
  @Test
  @NeedsIndex.ForStandardLibrary
  public void testSeveralTypeArguments() { doTest(); }
  @Test
  public void testSeveralTypeArgumentsSomeUnknown() { doTest(); }

  @Test
  public void testExtendsInTypeCast() {
    doTest();
  }

  @Test
  public void testTabMethodCall() {
    doFirstItemTest(Lookup.REPLACE_SELECT_CHAR);
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testConstructorArgsSmartEnter() { doTest(Lookup.COMPLETE_STATEMENT_SELECT_CHAR); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testIDEADEV13148() {
    EdtTestUtil.runInEdtAndWait(() -> {
      configureByFile("/IDEADEV13148.java");
      completion.get().assertStringItems("false", "true"); //todo don't suggest boolean literals in synchronized
    });
  }

  @Test
  public void testSuggestNames() {
    EdtTestUtil.runInEdtAndWait(() -> {
      configureByTestName();
      completion.get().assertStringItems("arrayList", "list");
    });
  }

  @Test
  public void testOverloadedMethods() {
    doTest();
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testNoCommaBeforeVarargs() { doTest(); }

  @Test
  public void testEnumField() {
    doItemTest();
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testEnumField1() {
    EdtTestUtil.runInEdtAndWait(() -> {
      configureByTestName();
      checkResultByTestName();
      assertEquals(4, completion.get().getItems().length);
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testInsertTypeParametersOnImporting() { doTest('\n'); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testEmptyListInReturn() { doItemTest(); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testEmptyListInReturn2() { doTest(); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testEmptyListInReturnTernary() { doItemTest(); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testEmptyListBeforeSemicolon() { doItemTest(); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testEmptyListWithCollectionsPrefix() { doItemTest(); }

  @Test
  public void testForeachLoopVariableInIterableExpression() { doAntiTest(); }

  @Test
  @NeedsIndex.Full
  public void testStaticallyImportedMagicMethod() {
    EdtTestUtil.runInEdtAndWait(() -> {
      configureByTestName();
      completion.get().assertStringItems("foo");
      completion.get().selectItem(completion.get().getItems()[0], '\t');
      checkResultByTestName();
    });
  }

  public void _testCallVarargArgument() { doTest(); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testTabToReplaceClassKeyword() {
    EdtTestUtil.runInEdtAndWait(() -> {
      configureByTestName();
      completion.get().selectItem(completion.get().getItems()[0], Lookup.REPLACE_SELECT_CHAR);
      checkResultByTestName();
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testNoTypeParametersForToArray() {
    doTest();
  }

  @Test
  @NeedsIndex.Full
  public void testStaticallyImportedField() { doTest('\n'); }
  @Test
  @NeedsIndex.Full
  public void testSiblingOfAStaticallyImportedField() { doTest(); }
  @Test
  @NeedsIndex.ForStandardLibrary
  public void testPrimitiveArrayClassInMethod() { doTest(); }
  @Test
  @NeedsIndex.ForStandardLibrary
  public void testPrimitiveClassInAnno() { doTest(); }
  @Test
  @NeedsIndex.Full
  public void testNewInnerClassOfSuper() { doTest(); }
  @Test
  @NeedsIndex.ForStandardLibrary
  public void testAssertThatMatcher() { doTest(); }

  @Test
  public void testInferFromCall() {
    doTest();
  }

  @Test
  public void testInferFromCall1() {
    doTest();
  }

  @Test
  public void testCastToParameterizedType() { doActionTest(); }

  @Test
  public void testInnerEnumInMethod() {
    doItemTest();
  }

  @Test
  public void testEnumAsDefaultAnnotationParam() { doTest(); }

  @Test
  public void testBreakLabel() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText(
        "a.java",
        """
          class a{{
            foo: while (true) break <caret>
          }}""");
      complete();
      myFixture.checkResult(
        """
          class a{{
            foo: while (true) break foo;<caret>
          }}""");
    });
  }

  @Test
  public void testContinueLabel() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText(
        "a.java",
        """
          class a{{
            foo: while (true) continue <caret>
          }}""");
      complete();
      myFixture.checkResult(
        """
          class a{{
            foo: while (true) continue foo;<caret>
          }}""");
    });
  }

  @Test
  @NeedsIndex.SmartMode(reason = "For now ConstructorInsertHandler.createOverrideRunnable doesn't work in dumb mode")
  public void testNewAbstractInsideAnonymous() { doTest(); }

  @Test
  public void testFilterPrivateConstructors() { doAntiTest(); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testExplicitMethodTypeParametersQualify() { doTest(); }
  @Test
  public void testExplicitMethodTypeParametersOverZealous() { doTest(); }
  @Test
  @NeedsIndex.ForStandardLibrary
  public void testExplicitMethodTypeParametersFromSuperClass() { doTest(); }

  @Test
  public void testWildcardedInstanceof() { doTest(); }
  @Test
  public void testWildcardedInstanceof2() { doTest(); }
  @Test
  public void testWildcardedInstanceof3() { doTest(); }

  @Test
  public void testCheckStaticImportsType() { doAntiTest(); }
  @Test
  public void testThisFieldAssignedToItself() { doAntiTest(); }

  @Test
  public void testCaseMissingEnumValue() { doTest(); }
  @Test
  public void testCaseMissingEnumValue2() { doTest(); }

  @Test
  public void testNoHiddenParameter() { doTest(); }

  @Test
  public void testTypeVariableInstanceOf() {
    EdtTestUtil.runInEdtAndWait(() -> {
      configureByTestName();
      performAction();
      completion.get().assertStringItems("Bar", "Goo");
    });
  }

  @Test
  @NeedsIndex.SmartMode(reason = "AbstractExpectedTypeSkipper works in smart mode only")
  public void testAutoImportExpectedType() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      configureByTestName();
      myFixture.assertPreferredCompletionItems(1, "List", "ArrayList", "AbstractList");
    });
  }

  @Test
  @NeedsIndex.SmartMode(reason = "AbstractExpectedTypeSkipper works in smart mode only")
  public void testExpectedTypeAutoImportHonorsExcludes() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      JavaProjectCodeInsightSettings.setExcludedNames(myFixture.getProject(), myFixture.getTestRootDisposable(), "java.awt");
      configureByTestName();
      myFixture.assertPreferredCompletionItems(1, "List", "ArrayList", "AbstractList");
    });
  }

  @Test
  @NeedsIndex.SmartMode(reason = "For now ConstructorInsertHandler.createOverrideRunnable doesn't work in dumb mode")
  public void testNoWrongSubstitutorFromStats() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      doTest();
      FileDocumentManager.getInstance().saveDocument(myFixture.getEditor().getDocument());
      doTest(); // stats are changed now
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testCommonPrefixWithSelection() {
    doItemTest();
  }

  @Test
  @NeedsIndex.SmartMode(reason = "For now ConstructorInsertHandler.createOverrideRunnable doesn't work in dumb mode")
  public void testNewAbstractClassWithConstructorArgs() {
    doItemTest();
  }

  @Test
  public void testArrayInitializerBeforeVarargs() { doTest(); }
  @Test
  @NeedsIndex.Full
  public void testDuplicateMembersFromSuperClass() { doTest(); }
  @Test
  public void testInnerAfterNew() { doTest(); }
  @Test
  @NeedsIndex.Full
  public void testOuterAfterNew() { doTest(); }
  @Test
  @NeedsIndex.ForStandardLibrary
  public void testEverythingInStringConcatenation() { doTest(); }
  @Test
  @NeedsIndex.ForStandardLibrary
  public void testGetClassWhenClassExpected() { doTest(); }

  @Test
  public void testMemberImportStatically() {
    EdtTestUtil.runInEdtAndWait(() -> {
      configureByTestName();
      StaticallyImportable item = completion.get().getItems()[0].as(StaticallyImportable.CLASS_CONDITION_KEY);
      assertNotNull(item);
      assertTrue(item.canBeImported());
      assertTrue(completion.get().getItems()[1].as(StaticallyImportable.CLASS_CONDITION_KEY).canBeImported());
      item.setShouldBeImported(true);
      completion.get().type('\n');
      checkResultByTestName();
    });
  }

  @Test
  public void testNoNewEnum() {
    EdtTestUtil.runInEdtAndWait(() -> {
      configureByTestName();
      completion.get().assertStringItems("Foo");
    });
  }

  @Test
  @NeedsIndex.Full
  public void testDuplicateMembersFromSuperClassInAnotherFile() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.addClass("class Super { public static final Super FOO = null; }");
      doTest();
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testInsideGenericClassLiteral() {
    EdtTestUtil.runInEdtAndWait(() -> {
      configureByTestName();
      completion.get().assertStringItems("String.class", "StringBuffer.class", "StringBuilder.class");
    });
  }

  @Test
  public void testArrayAnnoParameter() {
    doActionTest();
  }

  @Test
  @NeedsIndex.Full
  public void testInnerClassImports() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      JavaCodeStyleSettings settings = JavaCodeStyleSettings.getInstance(myFixture.getProject());
      settings.INSERT_INNER_CLASS_IMPORTS = true;
      myFixture.addClass("package java.awt.geom; public class Point2D { public static class Double {} }");
      doActionTest();
    });
  }

  @Test
  public void testCastWithGenerics() {
    doActionTest();
  }

  @Test
  public void testInnerEnum() {
    EdtTestUtil.runInEdtAndWait(() -> {
      configureByTestName();

      completion.get().getLookup().setCurrentItem(ContainerUtil.find(completion.get().getItems(), lookupItem -> "Bar.Fubar.Bar".equals(lookupItem.getLookupString())));
      select('\n');
      checkResultByTestName();
    });
  }

  @Test
  @NeedsIndex.Full
  public void testQualifiedAfterNew() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.addClass("package foo; public interface Foo<T> {}");
      myFixture.addClass("package bar; public class Bar implements foo.Foo {}");
      doTest();
    });
  }
  @Test
  @NeedsIndex.Full
  public void testAfterQualifiedNew() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.addClass("class Aa { public class B { } }");
      doTest();
    });
  }

  @Test
  @NeedsIndex.Full
  public void testTabAfterNew() {
    doFirstItemTest('\t');
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testSuggestMethodReturnType() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      configureByTestName();
      myFixture.assertPreferredCompletionItems(0, "Serializable", "CharSequence", "Object");
    });
  }

  @Test
  public void testSuggestMethodReturnTypeAnonymous() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      configureByTestName();
      assertIterableEquals(List.of("Object"), myFixture.getLookupElementStrings());
    });
  }

  @Test
  @NeedsIndex.Full
  public void testSuggestCastReturnTypeByCalledMethod() { doTest(); }

  @Test
  public void testOnlyInterfacesInImplements() { doTest(); }

  @Test
  public void testNonStaticField() { doAntiTest(); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testLocalClassInExpectedTypeArguments() { doTest(); }

  private void doActionTest() {
    EdtTestUtil.runInEdtAndWait(() -> {
      configureByTestName();
      checkResultByTestName();
    });
  }

  private void doItemTest() {
    doFirstItemTest('\n');
  }

  private void performAction() {
    complete();
  }

  private void doTest() {
    doTest(Lookup.NORMAL_SELECT_CHAR);
  }

  private void doTest(final char c) {
    boolean old = CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_SMART_TYPE_COMPLETION;
    if (c != Lookup.NORMAL_SELECT_CHAR) {
      CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_SMART_TYPE_COMPLETION = false;
    }

    EdtTestUtil.runInEdtAndWait(() -> {
      try {
        configureByTestName();
        if (completion.get().getItems() != null) {
          select(c);
        }
        checkResultByTestName();
      }
      finally {
        if (c != Lookup.NORMAL_SELECT_CHAR) {
          CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_SMART_TYPE_COMPLETION = old;
        }
      }

    });
  }

  private void checkResultByTestName() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().checkResultByFile("/" + myFixture.getTestName(false) + "-out.java");
    });
  }

  private void complete() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().setItems(myFixture.complete(CompletionType.SMART));
    });
  }

  private void configureByFile(String path) {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureFromExistingVirtualFile(myFixture.copyFileToProject(path, StringUtil.getShortName(path, '/')));
      complete();
    });
  }

  private void configureByTestName() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      configureByFile("/" + myFixture.getTestName(false) + ".java");
    });
  }

  private void doAntiTest() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      configureByTestName();
      completion.get().checkResultByFile(myFixture.getTestName(false) + ".java");
      assertThat(completion.get().getItems()).isEmpty();
      assertNull(completion.get().getLookup());
    });
  }

  private void select() {
    select(Lookup.NORMAL_SELECT_CHAR);
  }

  private void select(final char c) {
    EdtTestUtil.runInEdtAndWait(() -> {
      final Lookup lookup = completion.get().getLookup();
      if (lookup != null) {
        completion.get().selectItem(lookup.getCurrentItem(), c);
      }
    });
  }

  @Test
  public void testSpaceAfterCommaInMethodCall() {
    EdtTestUtil.runInEdtAndWait(() -> {
      getCodeStyleSettings().SPACE_AFTER_COMMA = false;
      doTest(',');
    });
  }

  private CommonCodeStyleSettings getCodeStyleSettings() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    return EdtTestUtil.runInEdtAndGet(() -> {
      return CodeStyle.getSettings(myFixture.getProject()).getCommonSettings(JavaLanguage.INSTANCE);
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary(reason = "On empty indices 'get2' is filtered out by unmatching type in ReferenceExpressionCompletionContributor.addSmartReferenceSuggestions")
  public void testOnlyCompatibleTypes() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      configureByTestName();
      assertIterableEquals(List.of("get2"), myFixture.getLookupElementStrings());
    });
  }

  @Test
  public void testQualifyOuterClassCall() { doActionTest(); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testExpressionSubtypesInCast() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      configureByTestName();
      myFixture.assertPreferredCompletionItems(0, "String", "StringBuffer", "StringBuilder");
    });
  }

  @Test
  public void testStaticBuilder() { doTest(); }
  @Test
  public void testStaticBuilderWithArguments() { doTest(); }
  @Test
  @NeedsIndex.Full
  public void testStaticBuilderWithInterfaceAndGenerics() { doTest(); }

  @Test
  public void testStaticBuilderWithGenerics() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      configureByTestName();
      assertEquals("Map.builder().get(...)", renderElement(completion.get().getItems()[0]).getItemText());
      myFixture.type('\t');
      checkResultByTestName();
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testFreeGenericsAfterClassLiteral() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      configureByTestName();
      myFixture.assertPreferredCompletionItems(0, "String.class", "tryCast");
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testNewHashMapTypeArguments() { doTest(); }
  @Test
  @NeedsIndex.ForStandardLibrary
  public void testNewMapTypeArguments() { doTest(); }
  @Test
  @NeedsIndex.ForStandardLibrary
  public void testNewMapObjectTypeArguments() { doTest(); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testNoUnrelatedMethodSuggestion() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      configureByTestName();
      assertIterableEquals(List.of("this"), myFixture.getLookupElementStrings());
    });
  }

  @Test
  @NeedsIndex.Full
  public void testLog4jLevel() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.addClass("package org.apache.log4j; " +
                         "public class Category { " +
                         "  public void log(Priority priority, Object message); " +
                         "}" +
                         "public class Priority { " +
                         "  final static public Priority FATAL;" + //deprecated
                         "}" +
                         "public class Level extends Priority { " +
                         "  final static public Level FATAL;" +
                         "}");
      doTest('\n');
    });
  }

  @Test
  public void testOldInferenceExponential() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    // This test specifically tests the problem in pre-Java-8 inference
    EdtTestUtil.runInEdtAndWait(() -> {
      assertTrue(PsiUtil.getLanguageLevel(myFixture.getProject()).isLessThan(LanguageLevel.JDK_1_8));
      doTest();
    });
  }

  @Test
  public void testSuggestChainsWhenNoDirectMatches() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureFromExistingVirtualFile(myFixture.copyFileToProject("second/MethodAsQualifier.java", "a.java"));
      myFixture.complete(CompletionType.SMART);
      completion.get().checkResultByFile("second/MethodAsQualifier-out.java");
    });
  }

  @Test
  public void testNoSemicolonAfterNonLastVariableInitializer() { doTest(); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testSuggestExceptionTypes() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("Test.java", """
        import java.io.*;

        class X {
          void test() {
            try {
              new FileInputStream("/etc/passwd");
            }
            catch(<caret>)
          }
        }
        class MyException extends FileNotFoundException {}
        class My2Exception extends MyException {}""");
      myFixture.complete(CompletionType.SMART);
      myFixture.assertPreferredCompletionItems(0, "FileNotFoundException", "IOException", "Exception", "Throwable",
                                               "MyException", "My2Exception",
                                               "RuntimeException", "ArithmeticException", "ArrayIndexOutOfBoundsException");
    });
  }
}
