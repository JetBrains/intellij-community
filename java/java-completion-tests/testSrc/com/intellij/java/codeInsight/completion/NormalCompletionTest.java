// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.completion;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.JavaProjectCodeInsightSettings;
import com.intellij.codeInsight.completion.CodeCompletionHandlerBase;
import com.intellij.codeInsight.completion.CompletionPhase;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.JavaPsiClassReferenceElement;
import com.intellij.codeInsight.completion.impl.CompletionServiceImpl;
import com.intellij.codeInsight.generation.EqualsHashCodeTemplatesManager;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.platform.testFramework.junit5.codeInsight.indexing.IndexingModeTemplate;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.augment.PsiAugmentProvider;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.impl.light.LightMethodBuilder;
import com.intellij.psi.impl.source.PsiExtensibleClass;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LeakHunter;
import com.intellij.testFramework.NeedsIndex;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.style.UnqualifiedFieldAccessInspection;
import one.util.streamex.MoreCollectors;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import static com.intellij.codeInsight.completion.LightCompletionFixtureKt.lightCompletionProjectFixture;
import static com.intellij.java.codeInsight.completion.NormalCompletionFixtureKt.normalCompletionFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@TestApplication
@IndexingModeTemplate
public class NormalCompletionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightCompletionProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_21);
  private final TestFixture<NormalCompletionFixture> completion = normalCompletionFixture(projectFixture);

  @Test
  public void testSimple() throws Exception {
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByFile("Simple.java");
      completion.get().assertStringItems("_local1", "_local2", "_field", "_baseField", "_method", "_baseMethod");
    });
  }

  @Test
  public void testCastToPrimitive1() {
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByFile("CastToPrimitive1.java");

      for (final LookupElement item : completion.get().getItems()) {
        if (item.getLookupString().equals("int")) return;
      }
      fail();
    });
  }

  @Test
  public void testCastToPrimitive2() {
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByFile("CastToPrimitive2.java");

      for (final LookupElement item : completion.get().getItems()) {
        if (item.getLookupString().equals("int")) return;
      }
      fail();
    });
  }

  @Test
  public void testCastToPrimitive3() {
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByFile("CastToPrimitive3.java");

      for (final LookupElement item : completion.get().getItems()) {
        if (item.getLookupString().equals("int")) return;
      }
      fail();
    });
  }

  @Test
  public void testWriteInInvokeLater() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().configureByFile("WriteInInvokeLater.java"));
  }

  @Test
  public void testQualifiedNew1() {
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configure();
      completion.get().assertStringItems("IInner", "Inner");
    });
  }

  @Test
  public void testQualifiedNew2() {
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configure();
      completion.get().assertStringItems("AnInner", "Inner");
    });
  }

  @Test
  public void testKeywordsInName() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest("a\n"));
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testSimpleVariable() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest("\n")); }

  @Test
  public void testTypeParameterItemPresentation() {
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configure();
      LookupElementPresentation presentation = NormalCompletionFixture.renderElement(completion.get().getItems()[0]);
      assertEquals("Param", presentation.getItemText());
      assertEquals(" type parameter of Foo", presentation.getTailText());
      assertNull(presentation.getTypeText());
      assertNull(presentation.getIcon());
      assertFalse(presentation.isItemTextBold());

      presentation = NormalCompletionFixture.renderElement(completion.get().getItems()[1]);
      assertEquals("Param2", presentation.getItemText());
      assertEquals(" type parameter of goo", presentation.getTailText());
    });
  }

  @Test
  public void testDisplayDefaultValueInAnnotationMethods() {
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configure();
      LookupElementPresentation presentation = NormalCompletionFixture.renderElement(completion.get().getItems()[0]);
      assertEquals("myInt", presentation.getItemText());
      assertEquals(" default 42", presentation.getTailText());
      assertTrue(presentation.getTailFragments().getFirst().isGrayed());
      assertEquals("int", presentation.getTypeText());
      assertFalse(presentation.isItemTextBold());

      presentation = NormalCompletionFixture.renderElement(completion.get().getItems()[1]);
      assertEquals("myString", presentation.getItemText());
      assertEquals(" default \"unknown\"", presentation.getTailText());
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testMethodItemPresentation() {
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configure();
      LookupElementPresentation presentation = NormalCompletionFixture.renderElement(completion.get().getItems()[0]);
      assertEquals("equals", presentation.getItemText());
      assertEquals("(Object anObject)", presentation.getTailText());
      assertEquals("boolean", presentation.getTypeText());

      assertFalse(ContainerUtil.exists(presentation.getTailFragments(), LookupElementPresentation.TextFragment::isGrayed));
      assertTrue(presentation.isItemTextBold());
    });
  }

  @Test
  public void testFieldItemPresentationGenerics() {
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configure();
      LookupElementPresentation presentation = NormalCompletionFixture.renderElement(completion.get().getItems()[0]);
      assertEquals("target", presentation.getItemText());
      assertNull(presentation.getTailText());
      assertEquals("String", presentation.getTypeText());
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testMethodItemPresentationGenerics() {
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configure();
      LookupElementPresentation presentation = NormalCompletionFixture.renderElement(completion.get().getItems()[1]);
      assertEquals("add", presentation.getItemText());
      assertEquals("(int index, String element)", presentation.getTailText());
      assertEquals("void", presentation.getTypeText());

      presentation = NormalCompletionFixture.renderElement(completion.get().getItems()[0]);
      assertEquals("(String e)", presentation.getTailText());
      assertEquals("boolean", presentation.getTypeText());

      assertFalse(ContainerUtil.exists(presentation.getTailFragments(), LookupElementPresentation.TextFragment::isGrayed));
      assertTrue(presentation.isItemTextBold());
    });
  }

  @Test
  public void testPreferLongerNamesOption() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByFile("PreferLongerNamesOption.java");

      assertEquals(3, completion.get().getItems().length);
      assertEquals("abcdEfghIjk", completion.get().getItems()[0].getLookupString());
      assertEquals("efghIjk", completion.get().getItems()[1].getLookupString());
      assertEquals("ijk", completion.get().getItems()[2].getLookupString());

      LookupManager.getInstance(myFixture.getProject()).hideActiveLookup();

      JavaCodeStyleSettings.getInstance(myFixture.getProject()).PREFER_LONGER_NAMES = false;
      completion.get().configureByFile("PreferLongerNamesOption.java");

      assertEquals(3, completion.get().getItems().length);
      assertEquals("ijk", completion.get().getItems()[0].getLookupString());
      assertEquals("efghIjk", completion.get().getItems()[1].getLookupString());
      assertEquals("abcdEfghIjk", completion.get().getItems()[2].getLookupString());
    });
  }

  @Test
  public void testSCR7208() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().configureByFile("SCR7208.java"));
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testProtectedFromSuper() {
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByFile("ProtectedFromSuper.java");
      Arrays.sort(completion.get().getItems());
      assertTrue(ContainerUtil.indexOf(Arrays.asList(completion.get().getItems()), le -> le.getLookupString().equals("xxx")) > 0, "Exception not found");
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testBeforeInitialization() {
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByFile("BeforeInitialization.java");
      assertNotNull(completion.get().getItems());
      assertTrue(completion.get().getItems().length > 0);
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testProtectedFromSuper2() {
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByFile("ProtectedFromSuper.java");
      Arrays.sort(completion.get().getItems());
      assertTrue(ContainerUtil.indexOf(Arrays.asList(completion.get().getItems()), le -> le.getLookupString().equals("xxx")) > 0, "Exception not found");
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testClassLiteralInArrayAnnoInitializer() {
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configure();
      completion.get().selectItem(ContainerUtil.find(completion.get().getItems(), item -> item.getLookupString().endsWith(".class")));
      completion.get().checkResult();
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testClassLiteralInArrayAnnoInitializer2() {
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configure();
      completion.get().selectItem(ContainerUtil.find(completion.get().getItems(), item -> item.getLookupString().endsWith(".class")));
      completion.get().checkResult();
    });
  }

  @Test
  public void testReferenceParameters() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByFile("ReferenceParameters.java");
      assertNotNull(completion.get().getItems());
      myFixture.assertPreferredCompletionItems(0, "AAAA", "AAAB");
    });
  }

  @AfterEach
  void tearDown() {
    CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION = true;
    CodeInsightSettings.getInstance().setCompletionCaseSensitive(CodeInsightSettings.FIRST_LETTER);
    CodeInsightSettings.getInstance().setSelectAutopopupSuggestionsByChars(false);
    CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET = true;
  }

  @Test
  public void testConstructorName1() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION = false;
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configure();
      assertTrue(myFixture.getLookupElementStrings().contains("ABCDE"));
    });
  }

  @Test
  public void testConstructorName2() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION = false;
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configure();
      assertTrue(myFixture.getLookupElementStrings().contains("ABCDE"));
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testObjectsInThrowsBlock() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByFile("InThrowsCompletion.java");
      assertEquals("C", myFixture.getLookupElementStrings().getFirst());
      assertTrue(myFixture.getLookupElementStrings().contains("B"));
    });
  }

  @Test
  public void testAnnoParameterValue() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configure();
      List<String> strings = myFixture.getLookupElementStrings();
      assertTrue(strings.contains("AssertionError"));
      assertFalse(strings.contains("enum"));
      assertFalse(strings.contains("final"));
      assertFalse(strings.contains("equals"));
      assertFalse(strings.contains("new"));
      assertFalse(strings.contains("null"));
      assertFalse(strings.contains("public"));
      assertFalse(strings.contains("super"));
      assertFalse(strings.contains("null"));
    });
  }

  @Test
  public void testAfterInstanceof() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByFile("AfterInstanceof.java");
      assertTrue(myFixture.getLookupElementStrings().contains("A"));
    });
  }

  @Test
  public void testAfterCast1() {
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByFile("AfterCast1.java");

      assertNotNull(completion.get().getItems());
      assertEquals(2, completion.get().getItems().length);
    });
  }

  @Test
  public void testAfterCast2() {
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByFile("AfterCast2.java");
      completion.get().checkResultByFile("AfterCast2-result.java");
    });
  }

  @Test
  public void testMethodCallForTwoLevelSelection() {
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByFile("MethodLookup.java");
      assertEquals(2, completion.get().getItems().length);
    });
  }

  @Test
  public void testMethodCallBeforeAnotherStatementWithParen() {
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByFile("MethodLookup2.java");
      completion.get().checkResultByFile("MethodLookup2_After.java");
    });
  }

  @Test
  public void testMethodCallBeforeAnotherStatementWithParen2() {
    EdtTestUtil.runInEdtAndWait(() -> {
      getCodeStyleSettings().METHOD_PARAMETERS_LPAREN_ON_NEXT_LINE = true;
      completion.get().configureByFile("MethodLookup2.java");
      completion.get().checkResultByFile("MethodLookup2_After2.java");
    });
  }

  @Test
  public void testSwitchEnumLabel() {
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByFile("SwitchEnumLabel.java");
      //first B is enum Field, second B is class. They have different presentations and handlers
      assertEquals("[A, B, C, null, B, Object]", ContainerUtil.map(completion.get().getItems(), LookupElement::getLookupString).toString());
    });
  }

  @Test
  public void testSwitchCaseWithEnumConstant() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  public void testSecondSwitchCaseWithEnumConstant() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testInsideSwitchCaseWithEnumConstant() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configure();
      myFixture.assertPreferredCompletionItems(0, "compareTo", "describeConstable", "equals");
    });
  }

  @Test
  public void testMethodInAnnotation() {
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByFile("Annotation.java");
      completion.get().checkResultByFile("Annotation_after.java");
    });
  }

  @Test
  public void testMethodInAnnotation2() {
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByFile("Annotation2.java");
      completion.get().checkResultByFile("Annotation2_after.java");
    });
  }

  @Test
  public void testMethodInAnnotation3() {
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByFile("Annotation3.java");
      completion.get().checkResultByFile("Annotation3_after.java");
    });
  }

  @Test
  public void testMethodInAnnotation5() {
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByFile("Annotation5.java");
      completion.get().checkResultByFile("Annotation5_after.java");
    });
  }

  @Test
  public void testMethodInAnnotation7() {
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByFile("Annotation7.java");
      completion.get().selectItem(completion.get().getItems()[0]);
      completion.get().checkResultByFile("Annotation7_after.java");
    });
  }

  @Test
  public void testAnnotationAttrBeforeExisting() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest("\n"));
  }

  @Test
  public void testAnnotationAttrBeforeExistingBool() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest("\n"));
  }

  @Test
  public void testEnumInAnnotation() {
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByFile("Annotation4.java");
      completion.get().checkResultByFile("Annotation4_after.java");
    });
  }

  @Test
  public void testEnumInTypeAnnotation() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  public void testSecondAttribute() {
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByFile("Annotation6.java");
      completion.get().checkResultByFile("Annotation6_after.java");
    });
  }

  @Test
  public void testIDEADEV6408() {
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByFile("IDEADEV6408.java");
      completion.get().assertFirstStringItems("boolean", "byte");
    });
  }

  @Test
  public void testMethodWithLeftParTailType() {
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByFile("MethodWithLeftParTailType.java");
      completion.get().type('(');
      completion.get().checkResultByFile("MethodWithLeftParTailType_after.java");

      completion.get().configureByFile("MethodWithLeftParTailType2.java");
      completion.get().type('(');
      completion.get().checkResultByFile("MethodWithLeftParTailType2_after.java");
    });
  }

  @Test
  public void testSuperErasure() {
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByFile("SuperErasure.java");
      completion.get().checkResultByFile("SuperErasure_after.java");
    });
  }

  @Test
  public void testMethodWithLeftParTailTypeNoPairBrace() {
    CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET = false;
    EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest("("));
  }

  @Test
  public void testMethodWithLeftParTailTypeNoPairBrace2() {
    CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET = false;

    //no tail type should work the normal way
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByFile("MethodWithLeftParTailTypeNoPairBrace.java");
      completion.get().selectItem(completion.get().getItems()[0]);
      completion.get().checkResultByFile("MethodWithLeftParTailTypeNoPairBrace_after2.java");
    });
  }

  @Test
  public void testMethodNoPairBrace() {
    CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET = false;
    EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest("\n"));
  }

  @Test
  public void testExcessSpaceInTypeCast() {
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configure();
      completion.get().selectItem(completion.get().getItems()[0]);
      completion.get().checkResult();
    });
  }

  @Test
  public void testFieldType() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  public void testFieldOfLocalClass() {
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configure();
      assertEquals("field", NormalCompletionFixture.renderElement(completion.get().getItems()[0]).getItemText());
      completion.get().type('\t');
      completion.get().checkResult();
    });
  }

  @Test
  public void testPackageInAnnoParam() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest());
  }

  @Test
  public void testAnonymousTypeParameter() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testClassLiteralInAnnoParam() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest());
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testNoForceBraces() {
    EdtTestUtil.runInEdtAndWait(() -> {
      getCodeStyleSettings().IF_BRACE_FORCE = CommonCodeStyleSettings.FORCE_BRACES_ALWAYS;
      completion.get().doTest("\n");
    });
  }

  @Test
  public void testExcludeStringBuffer() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      JavaProjectCodeInsightSettings.setExcludedNames(myFixture.getProject(), myFixture.getTestRootDisposable(), StringBuffer.class.getName());
      completion.get().configure();
      assertFalse(myFixture.getLookupElementStrings().contains("StringBuffer"));
    });
  }

  @Test
  @NeedsIndex.Full
  public void testExcludeInstanceInnerClasses() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      JavaProjectCodeInsightSettings.setExcludedNames(myFixture.getProject(), myFixture.getTestRootDisposable(), "foo");
      myFixture.addClass("package foo; public class Outer { public class Inner {} }");
      myFixture.addClass("package bar; public class Inner {}");
      completion.get().configure();
      assertEquals("bar.Inner", ((JavaPsiClassReferenceElement)myFixture.getLookupElements()[0]).getQualifiedName());
      assertEquals(myFixture.getLookupElementStrings(), List.of("Inner"));
    });
  }

  @Test
  @NeedsIndex.Full
  public void testExcludedInstanceInnerClassCreation() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      JavaProjectCodeInsightSettings.setExcludedNames(myFixture.getProject(), myFixture.getTestRootDisposable(), "foo");
      myFixture.addClass("package foo; public class Outer { public class Inner {} }");
      myFixture.addClass("package bar; public class Inner {}");
      completion.get().configure();
      assertEquals("foo.Outer.Inner", ((JavaPsiClassReferenceElement)myFixture.getLookupElements()[0]).getQualifiedName());
      assertEquals(myFixture.getLookupElementStrings(), List.of("Inner"));
    });
  }

  @Test
  @NeedsIndex.Full
  public void testExcludedInstanceInnerClassQualifiedReference() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      JavaProjectCodeInsightSettings.setExcludedNames(myFixture.getProject(), myFixture.getTestRootDisposable(), "foo");
      myFixture.addClass("package foo; public class Outer { public class Inner {} }");
      myFixture.addClass("package bar; public class Inner {}");
      completion.get().configure();
      assertEquals("foo.Outer.Inner", ((JavaPsiClassReferenceElement)myFixture.getLookupElements()[0]).getQualifiedName());
      assertEquals(myFixture.getLookupElementStrings(), List.of("Inner"));
    });
  }

  @Test
  @NeedsIndex.Full
  public void testStaticMethodOfExcludedClass() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      JavaProjectCodeInsightSettings.setExcludedNames(myFixture.getProject(), myFixture.getTestRootDisposable(), "foo");
      myFixture.addClass("package foo; public class Outer { public static void method() {} }");
      completion.get().configure();
      assertEquals(myFixture.getLookupElementStrings(), List.of("method"));
    });
  }

  @Test
  @NeedsIndex.Full
  public void testExcludeWildcards() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      JavaProjectCodeInsightSettings.setExcludedNames(myFixture.getProject(), myFixture.getTestRootDisposable(), "foo.Outer.*1*");
      myFixture.addClass("""
      package foo;
      public class Outer {
        public static void method1() { }

        public static void method2() { }

        public static void method12() { }

        public static void method42() { }
      }
      """);
      myFixture.configureByText("a.java", "class C {{ foo.Outer.m<caret> }}");
      myFixture.completeBasic();
      assertEquals(myFixture.getLookupElementStrings(), List.of("method2", "method42"));
    });
  }

  @Test
  @NeedsIndex.SmartMode(reason = "JavaGenerateMemberCompletionContributor.fillCompletionVariants works in smart mode only (for int hashCode lookup preventing autocompletion and additional \n)")
  public void testAtUnderClass() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest("\n"));
  }

  @Test
  public void testLocalClassName() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  public void testAssigningFieldForTheFirstTime() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  public void testClassTypeParameters() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configure();
      assertTrue(myFixture.getLookupElementStrings().contains("K"));
    });
  }

  @Test
  public void testClassTypeParametersGenericBounds() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configure();
      assertTrue(myFixture.getLookupElementStrings().contains("K"));
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testLocalClassTwice() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configure();
      assertIterableEquals(List.of("Zoooz", "Zooooo", "ZoneRulesException", "ZoneRulesProvider", "ZipOutputStream"), myFixture.getLookupElementStrings());
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testLocalTopLevelConflict() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configure();
      assertIterableEquals(List.of("Zoooz", "Zooooo", "ZoneRulesException", "ZoneRulesProvider", "ZipOutputStream"), myFixture.getLookupElementStrings());
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testFinalBeforeMethodCall() {
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configure();
      completion.get().assertStringItems("final", "finalize");
    });
  }

  @Test
  public void testMethodCallAfterFinally() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  public void testPrivateInAnonymous() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  public void testStaticMethodFromOuterClass() {
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configure();
      completion.get().assertStringItems("foo", "A.foo", "for");
      assertEquals("A.foo", NormalCompletionFixture.renderElement(completion.get().getItems()[1]).getItemText());
      completion.get().selectItem(completion.get().getItems()[1]);
      completion.get().checkResult();
    });
  }

  @Test
  public void testInstanceMethodFromOuterClass() {
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configure();
      completion.get().assertStringItems("foo", "A.this.foo", "for");
      assertEquals("A.this.foo", NormalCompletionFixture.renderElement(completion.get().getItems()[1]).getItemText());
      completion.get().selectItem(completion.get().getItems()[1]);
      completion.get().checkResult();
    });
  }

  @Test
  public void testMethodParenthesesSpaces() {
    EdtTestUtil.runInEdtAndWait(() -> {
      getCodeStyleSettings().SPACE_BEFORE_METHOD_CALL_PARENTHESES = true;
      getCodeStyleSettings().SPACE_WITHIN_METHOD_CALL_PARENTHESES = true;
      completion.get().doTest();
    });
  }

  @Test
  public void testMethodParenthesesSpacesArgs() {
    EdtTestUtil.runInEdtAndWait(() -> {
      getCodeStyleSettings().SPACE_BEFORE_METHOD_CALL_PARENTHESES = true;
      getCodeStyleSettings().SPACE_WITHIN_METHOD_CALL_PARENTHESES = true;
      completion.get().doTest();
    });
  }

  @Test
  public void testAtUnderClassNoModifiers() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest());
  }

  @Test
  public void testBreakInIfCondition() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  public void testAccessStaticViaInstance() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  public void testIfConditionLt() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configure();
      myFixture.assertPreferredCompletionItems(0, "getAnnotationsAreaOffset");
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary(reason = "On empty indices 'foo' is the only item, so is not filtered out in  JavaCompletionProcessor.dispreferStaticAfterInstance")
  public void testAccessStaticViaInstanceSecond() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configure();
      assertFalse(myFixture.getLookupElementStrings().contains("foo"));
      myFixture.complete(CompletionType.BASIC, 2);
      myFixture.assertPreferredCompletionItems(0, "foo");
      myFixture.type('\n');
      completion.get().checkResult();
    });
  }

  @Test
  public void testAccessInstanceFromStaticSecond() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configure();
      myFixture.complete(CompletionType.BASIC, 2);
      completion.get().checkResult();
    });
  }

  @Test
  public void testBreakLabel() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java", """
        class a {{
          foo: while (true) break <caret>
        }}""");
      completion.get().complete();
      assertEquals(List.of("foo"), myFixture.getLookupElementStrings());
    });
  }

  @Test
  public void testContinueLabel() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java", """
        class a {{
          foo: while (true) continue <caret>
        }}""");
      completion.get().complete();
      assertEquals(List.of("foo"), myFixture.getLookupElementStrings());
    });
  }

  @Test
  public void testContinueLabelTail() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java", """
        class a {{
          foo: while (true) con<caret>
        }}""");
      completion.get().complete();
      myFixture.checkResult("""
                              class a {{
                                foo: while (true) continue <caret>
                              }}""");
    });
  }

  @Test
  @NeedsIndex.SmartMode(reason = "For now ConstructorInsertHandler.createOverrideRunnable doesn't work in dumb mode")
  public void testAnonymousProcess() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.addClass("package java.lang; public class Process {}");
      myFixture.addClass("""
          import java.util. *;
          public class Process {
          }
          interface Pred<A> {
            boolean predicate(A elem);
          }
          public class ListUtils {
            public static <A> List<A> filter(List<A> list, Pred<A> pred) { }
          }""");
      completion.get().configure();
      completion.get().type('\n');
      NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
      completion.get().checkResult();
    });
  }

  @Test
  public void testNoThisInComment() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doAntiTest()); }

  @Test
  public void testIncNull() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configure();
      completion.get().checkResultByFile(myFixture.getTestName(false) + ".java");
      assertFalse(myFixture.getLookupElementStrings().contains("null"));
    });
  }

  @Test
  public void testLastExpressionInFor() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  public void testOnlyKeywordsInsideSwitch() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByFile(myFixture.getTestName(false) + ".java");
      List<String> strings = myFixture.getLookupElementStrings();
      assertNotNull(strings);
      assertThat(strings).containsOnly("case", "default");
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testBooleanLiterals() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest("\n"));
  }

  @Test
  public void testDoubleBooleanInParameter() {
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configure();
      completion.get().assertFirstStringItems("boolean", "byte");
    });
  }

  @Test
  public void testDoubleConstant() {
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configure();
      completion.get().assertStringItems("Intf.XFOO", "XFOO");
    });
  }

  @Test
  public void testNotOnlyKeywordsInsideSwitch() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest());
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testChainedCallOnNextLine() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByFile(myFixture.getTestName(false) + ".java");
      completion.get().selectItem(completion.get().getItems()[0]);
      completion.get().checkResult();
    });
  }

  @Test
  public void testFinishWithDot() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByFile(myFixture.getTestName(false) + ".java");
      completion.get().type('.');
      completion.get().checkResult();
    });
  }

  @Test
  public void testEnclosingThis() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  @NeedsIndex.Full
  public void testSeamlessConstant() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByFile(myFixture.getTestName(false) + ".java");
      completion.get().selectItem(completion.get().getItems()[0]);
      completion.get().checkResult();
    });
  }

  @Test
  public void testDefaultAnnoParam() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  public void testSpaceAfterLookupString() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByFile(myFixture.getTestName(false) + ".java");
      completion.get().type(' ');
      assertNull(completion.get().getLookup());
      completion.get().checkResult();
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testNoSpaceInParensWithoutParams() {
    EdtTestUtil.runInEdtAndWait(() -> {
      getCodeStyleSettings().SPACE_WITHIN_METHOD_CALL_PARENTHESES = true;
      try {
        completion.get().doTest();
      }
      finally {
        getCodeStyleSettings().SPACE_WITHIN_METHOD_CALL_PARENTHESES = false;
      }
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testTwoSpacesInParensWithParams() {
    EdtTestUtil.runInEdtAndWait(() -> {
      getCodeStyleSettings().SPACE_WITHIN_METHOD_CALL_PARENTHESES = true;
      completion.get().doTest();
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testQualifierAsPackage() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByFile(myFixture.getTestName(false) + ".java");
      completion.get().selectItem(completion.get().getItems()[0]);
      completion.get().checkResult();
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testQualifierAsPackage2() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest());
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testQualifierAsPackage3() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest());
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testPreselectEditorSelection() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configure();
      assertNotSame(completion.get().getLookup().getCurrentItem(), myFixture.getLookupElements()[0]);
      assertEquals("finalize", completion.get().getLookup().getCurrentItem().getLookupString());
    });
  }

  @Test
  public void testNoMethodsInNonStaticImports() {
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configure();
      completion.get().assertStringItems("*");
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testMembersInStaticImports() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest("\n")); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testPackageNamedVariableBeforeAssignment() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest());
  }

  @Test
  public void testInnerEnumConstant() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest("\n")); }

  @Test
  public void testNoExpectedReturnTypeDuplication() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configure();
      assertEquals(myFixture.getLookupElementStrings(), List.of("boolean", "byte"));
    });
  }

  @Test
  public void testNoExpectedVoidReturnTypeDuplication() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configure();
      assertEquals(myFixture.getLookupElementStrings(), List.of("void"));
    });
  }

  @Test
  public void testNoExpectedArrayTypeDuplication() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configure();
      assertEquals(myFixture.getLookupElementStrings(), List.of("char"));
    });
  }

  @Test
  public void testShadowedTypeParameter() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configure();
      assertEquals(myFixture.getLookupElementStrings(), List.of("MyParam"));
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testMethodReturnType() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest("\n")); }

  @Test
  public void testMethodReturnTypeNoSpace() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByFile(myFixture.getTestName(false) + ".java");
      completion.get().selectItem(completion.get().getItems()[0]);
      completion.get().checkResult();
    });
  }

  @Test
  public void testEnumWithoutConstants() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest());
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testDoWhileMethodCall() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest());
  }

  @Test
  public void testSecondTypeParameterExtends() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest());
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testGetterWithExistingNonEmptyParameterList() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest());
  }

  @Test
  public void testNothingAfterNumericLiteral() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doAntiTest()); }

  @Test
  public void testNothingAfterTypeParameterQualifier() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doAntiTest()); }

  @Test
  public void testExcludeVariableBeingDeclared() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doAntiTest()); }

  @Test
  public void testExcludeVariableBeingDeclared2() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doAntiTest()); }

  @Test
  public void testSpacesAroundEq() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest("=")); }

  void _testClassBeforeCast() { completion.get().doTest("\n"); }

  @Test
  public void testNoAllClassesOnQualifiedReference() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().doAntiTest());
  }

  @Test
  public void testFinishClassNameWithDot() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest("."));
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testFinishClassNameWithLParen() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest("("));
  }

  @Test
  public void testSelectNoParameterSignature() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByFile(myFixture.getTestName(false) + ".java");
      final int parametersCount = ((PsiMethod)completion.get().getLookup().getCurrentItem().getObject()).getParameterList().getParametersCount();
      assertEquals(0, parametersCount);
      completion.get().type('\n');
      completion.get().checkResult();
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testCompletionInsideClassLiteral() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByFile(myFixture.getTestName(false) + ".java");
      completion.get().type('\n');
      completion.get().checkResult();
    });
  }

  @Test
  public void testFieldNegation() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest("!")); }

  @Test
  public void testDefaultInSwitch() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  public void testBreakInSwitch() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  public void testSuperInConstructor() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest());
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testSuperInConstructorWithParams() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest());
  }

  @Test
  public void testSuperInMethod() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest());
  }

  @Test
  public void testSecondMethodParameterName() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest());
  }

  @Test
  public void testAnnotationAsUsualObject() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest());
  }

  @Test
  public void testAnnotationAsUsualObjectFromJavadoc() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest());
  }

  @Test
  public void testAnnotationAsUsualObjectInsideClass() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest());
  }

  @Test
  public void testAnnotationOnNothingParens() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest());
  }

  @Test
  public void testMultiResolveQualifier() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest());
  }

  @Test
  public void testSecondMethodParameter() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  public void testReturnInCase() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testUnboxedConstantsInCase() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  public void testAnnotationWithoutValueMethod() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByFile(myFixture.getTestName(false) + ".java");
      completion.get().assertStringItems("bar", "foo");
    });
  }

  @Test
  public void testAnnotationWithLeftParen() {
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByTestName();
      completion.get().assertStringItems("MyAnno", "MyAnnoOther");
      completion.get().type('(');
      completion.get().checkResult();
    });
  }

  @Test
  public void testAddExplicitValueInAnnotation() {
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByTestName();
      completion.get().assertStringItems("bar", "goo");
      completion.get().selectItem(completion.get().getItems()[0]);
      completion.get().checkResult();
    });
  }

  @Test
  public void testUnnecessaryMethodMerging() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByFile(myFixture.getTestName(false) + ".java");
      completion.get().assertStringItems("fofoo", "fofoo");
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testMethodMergingMinimalTail() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  public void testAnnotationQualifiedName() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest());
  }

  @Test
  @NeedsIndex.SmartMode(reason = "For now ConstructorInsertHandler.createOverrideRunnable doesn't work in dumb mode")
  public void testClassNameAnonymous() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest("\n"));
  }

  @Test
  @NeedsIndex.SmartMode(reason = "For now ConstructorInsertHandler.createOverrideRunnable doesn't work in dumb mode")
  public void testClassNameWithInner() {
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configure();
      completion.get().assertStringItems("Zzoo", "Zzoo.Impl");
      completion.get().type('\n');
      NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
      completion.get().checkResult();
    });
  }

  @Test
  public void testClassNameWithInner2() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest("\n")); }

  @Test
  public void testClassNameWithInstanceInner() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest("\n")); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testDoubleFalse() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByFile(myFixture.getTestName(false) + ".java");
      completion.get().assertFirstStringItems("false", "fefefef", "float", "finalize");
    });
  }

  @Test
  public void testSameNamedVariableInNestedClasses() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configure();
      myFixture.assertPreferredCompletionItems(0, "ffid", "Beda.this.ffid");
      completion.get().selectItem(completion.get().getItems()[1]);
      completion.get().checkResult();
    });
  }

  @Test
  public void testHonorUnderscoreInPrefix() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest());
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testNoSemicolonAfterExistingParenthesesEspeciallyIfItsACast() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testNoSemicolonInForUpdate() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest("\n")); }

  @Test
  public void testReturningTypeVariable() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  public void testReturningTypeVariable2() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  public void testReturningTypeVariable3() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testImportInGenericType() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configure();
      myFixture.complete(CompletionType.BASIC, 2);
      myFixture.type('\n');
      completion.get().checkResult();
    });
  }

  @Test
  public void testCaseTailType() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_11, () -> completion.get().doTest()));
  }

  private void doPrimitiveTypeTest() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    completion.get().configure();
    completion.get().checkResultByFile(myFixture.getTestName(false) + ".java");
    assertTrue(myFixture.getLookupElementStrings().contains("boolean"));
  }

  @Test
  public void testFinalInForLoop() {
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configure();
      completion.get().assertStringItems("final");
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testFinalInForLoop2() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configure();
      myFixture.assertPreferredCompletionItems(1, "finalize", "final");
    });
  }

  @Test
  public void testOnlyClassesInExtends() {
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configure();
      completion.get().assertStringItems("Inner");
    });
  }

  @Test
  public void testNoThisClassInExtends() {
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configure();
      completion.get().assertStringItems("Fooxxxx2");
    });
  }

  @Test
  public void testPrimitiveTypesInForLoop() { EdtTestUtil.runInEdtAndWait(() -> doPrimitiveTypeTest()); }

  @Test
  public void testPrimitiveTypesInForLoop2() { EdtTestUtil.runInEdtAndWait(() -> doPrimitiveTypeTest()); }

  @Test
  public void testPrimitiveTypesInForLoop3() { EdtTestUtil.runInEdtAndWait(() -> doPrimitiveTypeTest()); }

  @Test
  public void testPrimitiveTypesInForLoop4() { EdtTestUtil.runInEdtAndWait(() -> doPrimitiveTypeTest()); }

  @Test
  public void testPrimitiveTypesInForLoop5() { EdtTestUtil.runInEdtAndWait(() -> doPrimitiveTypeTest()); }

  @Test
  public void testPrimitiveTypesInForLoop6() { EdtTestUtil.runInEdtAndWait(() -> doPrimitiveTypeTest()); }

  @Test
  public void testPrimitiveTypesInForLoopSpace() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configure();
      myFixture.type(' ');
      completion.get().checkResultByFile(myFixture.getTestName(false) + "_after.java");
    });
  }

  @Test
  @NeedsIndex.SmartMode(reason = "Ordering requires smart mode")
  public void testSuggestInaccessibleOnSecondInvocation() {
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configure();
      completion.get().assertStringItems("_bar", "_goo");
      completion.get().complete();
      completion.get().assertStringItems("_bar", "_goo", "_foo");
      completion.get().getLookup().setCurrentItem(completion.get().getLookup().getItems().get(2));
      completion.get().selectItem(completion.get().getLookup().getItems().get(2), Lookup.NORMAL_SELECT_CHAR);
      completion.get().checkResult();
    });
  }

  @Test
  @NeedsIndex.SmartMode(reason = "Ordering requires smart mode")
  public void testSecondInvocationAfterSingleItemAutoInsertLeavesNoTailWatch() throws ClassNotFoundException {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java", """
        class Foo {
          static void _bar() {}
          private static void _foo() {}
        }
        class Bar {
          void m() {
            Foo._<caret>
          }
        }""");

      invokeBasicCompletionWithModifiers();
      assertNull(completion.get().getLookup());
      assertTrue(myFixture.getEditor().getDocument().getText().contains("Foo._bar();"));
      assertInstanceOf(CompletionPhase.InsertedSingleItem.class, CompletionServiceImpl.getCompletionPhase());

      invokeBasicCompletionWithModifiers();
      completion.get().assertStringItems("_bar", "_foo");
      completion.get().selectItem(completion.get().getLookup().getItems().get(1), Lookup.NORMAL_SELECT_CHAR);
      myFixture.checkResult("""
        class Foo {
          static void _bar() {}
          private static void _foo() {}
        }
        class Bar {
          void m() {
            Foo._foo();<caret>
          }
        }""");

      LeakHunter.checkLeak(myFixture.getEditor().getDocument(),
                           Class.forName("com.intellij.codeInsight.completion.CompletionAssertions$RangeMarkerSpy"));
    });
  }

  private void invokeBasicCompletionWithModifiers() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    CommandProcessor.getInstance().executeCommand(myFixture.getProject(), () -> {
      CodeCompletionHandlerBase.createHandler(CompletionType.BASIC).invokeCompletion(myFixture.getProject(), myFixture.getEditor(), 1, true);
    }, null, null, myFixture.getEditor().getDocument());
  }

  @Test
  public void testNoCommonPrefixInsideIdentifier() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      final String path = myFixture.getTestName(false) + ".java";
      completion.get().configureByFile(path);
      completion.get().checkResultByFile(path);
      completion.get().assertStringItems("fai1", "fai2");
    });
  }

  @Test
  @NeedsIndex.Full
  public void testProtectedInaccessibleOnSecondInvocation() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByFile(myFixture.getTestName(false) + ".java");
      myFixture.complete(CompletionType.BASIC, 2);
      myFixture.type('\n');
      completion.get().checkResult();
    });
  }

  @Test
  public void testPropertyReferencePrefix() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.addFileToProject("test.properties", "foo.bar=Foo! Bar!").getVirtualFile();
      completion.get().doAntiTest();
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testSecondAnonymousClassParameter() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  public void testSpaceAfterReturn() {
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configure();
      completion.get().type('\n');
      completion.get().checkResult();
    });
  }

  @Test
  public void testIntersectionTypeMembers() {
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configure();
      completion.get().assertStringItems("fooa", "foob");
    });
  }

  @Test
  public void testNoReturnInTernary() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  public void testWildcardsInLookup() {
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configure();
      assertNotNull(completion.get().getLookup());
      completion.get().type("*fz");
      assertNull(completion.get().getLookup());
    });
  }

  @Test
  public void testSmartEnterWrapsConstructorCall() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest(String.valueOf(Lookup.COMPLETE_STATEMENT_SELECT_CHAR))); }

  @Test
  public void testSmartEnterNoNewLine() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest(String.valueOf(Lookup.COMPLETE_STATEMENT_SELECT_CHAR))); }

  @Test
  public void testSmartEnterWithNewLine() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest(String.valueOf(Lookup.COMPLETE_STATEMENT_SELECT_CHAR))); }

  @Test
  @NeedsIndex.SmartMode(reason = "MethodCallFixer.apply needs smart mode to count number of parameters")
  public void testSmartEnterGuessArgumentCount() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest(String.valueOf(Lookup.COMPLETE_STATEMENT_SELECT_CHAR))); }

  @Test
  public void testSmartEnterInsideArrayBrackets() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest(String.valueOf(Lookup.COMPLETE_STATEMENT_SELECT_CHAR))); }

  @Test
  public void testTabReplacesMethodNameWithLocalVariableName() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest("\t")); }

  @Test
  public void testMethodParameterAnnotationClass() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  @NeedsIndex.Full
  public void testInnerAnnotation() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configure();
      assertEquals(myFixture.getLookupElementStrings(), List.of("Dependency"));
      completion.get().type('\t');
      completion.get().checkResult();
    });
  }

  @Test
  public void testPrimitiveCastOverwrite() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest("\t")); }

  @Test
  public void testClassReferenceInFor() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest(" ")); }

  @Test
  public void testClassReferenceInFor2() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest(" ")); }

  @Test
  public void testClassReferenceInFor3() {
    CodeInsightSettings.getInstance().setCompletionCaseSensitive(CodeInsightSettings.NONE);
    EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest(" "));
  }

  @Test
  public void testEnumConstantFromEnumMember() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  public void testPrimitiveMethodParameter() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  public void testPrimitiveVarargMethodParameter() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest(".")); }

  @Test
  public void testPrimitiveVarargMethodParameter2() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest(".")); }

  @Test
  public void testNewExpectedClassParens() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest("\n")); }

  @Test
  public void testQualifyInnerMembers() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest("\n")); }

  @Test
  public void testDeepInner() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configure();
      assertEquals(myFixture.getLookupElementStrings(), List.of("ClassInner1", "ClassInner1.ClassInner2"));
      completion.get().selectItem(completion.get().getLookup().getItems().get(1));
      completion.get().checkResult();
    });
  }

  @Test
  public void testSuggestExpectedTypeMembers() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest("\n")); }

  @Test
  public void testSuggestExpectedTypeMembersInCall() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest("\n")); }

  @Test
  public void testSuggestExpectedTypeMembersInAnno() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest("\n")); }

  @Test
  public void testExpectedTypesDotSelectsItem() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest(".")); }

  @Test
  @NeedsIndex.Full
  public void testExpectedTypeMembersVersusStaticImports() {
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configure();
      completion.get().assertStringItems("XFOO", "XFOX");
    });
  }

  @Test
  @NeedsIndex.Full
  public void testSuggestExpectedTypeMembersNonImported() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.addClass("package foo; public class Super { public static final Super FOO = null; }");
      myFixture.addClass("package foo; public class Usage { public static void foo(Super s) {} }");
      completion.get().doTest("\n");
    });
  }

  @Test
  @NeedsIndex.Full
  public void testStaticallyImportedInner() {
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configure();
      completion.get().assertStringItems("AIOInner", "ArrayIndexOutOfBoundsException");
    });
  }

  @Test
  public void testClassNameInIfBeforeIdentifier() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.addClass("public class ABCDEFFFFF {}");
      completion.get().doTest("\n");
    });
  }

  @Test
  public void testClassNameWithInnersTab() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest("\t")); }

  @Test
  public void testClassNameWithGenericsTab() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest("\t")); }

  @Test
  public void testLiveTemplatePrefixTab() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest("\t")); }

  @Test
  public void testOnlyAnnotationsAfterAt() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testOnlyAnnotationsAfterAt2() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest("\n")); }

  @Test
  public void testAnnotationBeforeIdentifier() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest("\n")); }

  @Test
  public void testAnnotationBeforeQualifiedReference() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest("\n")); }

  @Test
  public void testAnnotationBeforeIdentifierFinishWithSpace() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest(" ")); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testOnlyExceptionsInCatch1() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest("\n")); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testOnlyExceptionsInCatch2() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest("\n")); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testOnlyExceptionsInCatch3() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest("\n")); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testOnlyExceptionsInCatch4() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest("\n")); }

  @Test
  public void testCommaAfterVariable() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest(",")); }

  @Test
  public void testClassAngleBracket() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest("<")); }

  @Test
  public void testNoArgsMethodSpace() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest(" ")); }

  @Test
  public void testClassSquareBracket() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest("[")); }

  @Test
  public void testPrimitiveSquareBracket() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest("[")); }

  @Test
  public void testVariableSquareBracket() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest("[")); }

  @Test
  public void testMethodSquareBracket() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest("[")); }

  @Test
  public void testMethodParameterTypeDot() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doAntiTest()); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testNewGenericClass() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest("\n")); }

  @Test
  public void testNewGenericInterface() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  public void testEnumPrivateFinal() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  public void testNoFieldsInImplements() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  public void testSwitchConstantsFromReferencedClass() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest("\n")); }

  @Test
  public void testSwitchValueFinishWithColon() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest(":")); }

  @Test
  public void testUnfinishedMethodTypeParameter() {
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configure();
      completion.get().assertStringItems("MyParameter", "MySecondParameter");
    });
  }

  @Test
  public void testUnfinishedMethodTypeParameter2() {
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configure();
      completion.get().assertStringItems("MyParameter", "MySecondParameter");
    });
  }

  @Test
  @NeedsIndex.Full
  public void testSuperProtectedMethod() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.addClass("""
                           package foo;
                           public class Bar {
                               protected void foo() { }
                           }""");
      completion.get().doTest();
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testOuterSuperMethodCall() {
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configure();
      assertEquals("Class2.super.put", NormalCompletionFixture.renderElement(completion.get().getItems()[0]).getItemText());
      completion.get().type('\n');
      completion.get().checkResult();
    });
  }

  @Test
  public void testTopLevelClassesFromPackaged() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.addClass("public class Fooooo {}");
      var text = "package foo; class Bar { Fooo<caret> }";
      var file = myFixture.addFileToProject("foo/Bar.java", text);
      myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
      assertThat(myFixture.completeBasic()).isNullOrEmpty();
      myFixture.checkResult(text);
    });
  }

  @Test
  public void testRightShift() {
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configure();
      completion.get().assertStringItems("myField1", "myField2");
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testAfterCommonPrefix() {
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configure();
      completion.get().type("eq");
      completion.get().assertFirstStringItems("equals", "equalsIgnoreCase");
      completion.get().complete();
      completion.get().assertFirstStringItems("equals", "equalsIgnoreCase");
      completion.get().type('(');
      completion.get().checkResult();
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testClassNameInsideIdentifierInIf() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configure();
      myFixture.complete(CompletionType.BASIC, 2);
      completion.get().type('\n');
      completion.get().checkResult();
    });
  }

  @Test
  public void testKeywordSmartEnter() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configure();
      myFixture.assertPreferredCompletionItems(0, "null", "nullity");
      myFixture.performEditorAction(IdeActions.ACTION_CHOOSE_LOOKUP_ITEM_COMPLETE_STATEMENT);
      completion.get().checkResult();
    });
  }

  @Test
  public void testSynchronizedArgumentSmartEnter() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest(String.valueOf(Lookup.COMPLETE_STATEMENT_SELECT_CHAR))); }

  @Test
  @NeedsIndex.Full
  public void testImportStringValue() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.addClass("package foo; public class StringValue {}");
      myFixture.addClass("package java.lang; class StringValue {}");
      completion.get().configure();
      myFixture.complete(CompletionType.BASIC, 2);
      completion.get().type(' ');
      completion.get().checkResult();
    });
  }

  @Test
  public void testPrimitiveArrayWithRBrace() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest("[")); }

  @Test
  @NeedsIndex.Full
  public void testSuggestMembersOfStaticallyImportedClasses() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.addClass("""
                           package foo;
                           public class Foo {
                             public static void foo() {}
                             public static void bar() {}
                           }
                           """);
      completion.get().doTest("\n");
    });
  }

  @Test
  @NeedsIndex.Full
  public void testSuggestMembersOfStaticallyImportedClassesUnqualifiedOnly() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.addClass("""
                           package foo;
                           public class Foo {
                             public static void foo() {}
                             public static void bar() {}
                           }
                           """);
      completion.get().configure();
      completion.get().complete();
      assertThat(myFixture.getLookupElements()).hasSize(1);
      myFixture.type('\t');
      completion.get().checkResult();
    });
  }

  @Test
  @NeedsIndex.Full
  public void testSuggestMembersOfStaticallyImportedClassesConflictWithLocalMethod() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.addClass("""
                           package foo;
                           public class Foo {
                             public static void foo() {}
                             public static void bar() {}
                           }
                           """);
      completion.get().configure();
      myFixture.assertPreferredCompletionItems(0, "bar", "bar");
      assertEquals("Foo.bar", NormalCompletionFixture.renderElement(myFixture.getLookupElements()[1]).getItemText());
      myFixture.getLookup().setCurrentItem(myFixture.getLookupElements()[1]);
      myFixture.type('\t');
      completion.get().checkResult();
    });
  }

  @Test
  @NeedsIndex.Full
  public void testSuggestMembersOfStaticallyImportedClassesConflictWithLocalField() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.addClass("""
                           package foo;
                           public class Foo {
                             public static int foo = 1;
                             public static int bar = 2;
                           }
                           """);
      completion.get().configure();
      myFixture.assertPreferredCompletionItems(0, "bar", "Foo.bar");
      myFixture.getLookup().setCurrentItem(myFixture.getLookupElements()[1]);
      myFixture.type('\t');
      completion.get().checkResult();
    });
  }

  @Test
  public void testInstanceMagicMethod() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  public void testNoDotOverwrite() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest(".")); }

  @Test
  public void testNoModifierListOverwrite() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest("\t")); }

  @Test
  public void testStaticInnerExtendingOuter() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  public void testPrimitiveClass() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  public void testPrimitiveArrayClass() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  public void testPrimitiveArrayOnlyClass() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doAntiTest()); }

  @Test
  public void testPrimitiveArrayInAnno() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  public void testNewClassAngleBracket() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest("<")); }

  @Test
  public void testNewClassAngleBracketExpected() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest("<")); }

  @Test
  public void testAngleBracketInClassBody() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest("<")); }

  @Test
  public void testAngleBracketInClassBodyAfterMethod() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest("<")); }

  @Test
  public void testAngleBracketInClassBodyBeforeMethod() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest("<")); }

  @Test
  public void testAngleBracketInClassBodyBetweenMethods() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest("<")); }

  @Test
  public void testAngleBracketInFieldInitializer() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest("<")); }

  @Test
  public void testAngleBracketAfterField() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest("<")); }

  @Test
  public void testAngleBracketBeforeField() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest("<")); }

  @Test
  public void testAngleBracketAfterCommentInClassBody() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest("<")); }

  @Test
  public void testAngleBracketAfterKeyword() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest("<")); }

  @Test
  public void testNewClassSquareBracket() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest("[")); }

  @Test
  public void testMethodColon() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest(":")); }

  @Test
  public void testVariableColon() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest(":")); }

  @Test
  public void testFinishByClosingParenthesis() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest(")")); }

  @Test
  public void testNoMethodsInParameterType() {
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configure();
      completion.get().assertFirstStringItems("final", "float");
    });
  }

  @Test
  @NeedsIndex.Full
  public void testNonImportedClassInAnnotation() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.addClass("package foo; public class XInternalTimerServiceController {}");
      myFixture.configureByText("a.java", """
        class XInternalError {}

        @interface Anno { Class value(); }

        @Anno(XInternal<caret>)
        """);
      myFixture.complete(CompletionType.BASIC, 2);
      completion.get().assertFirstStringItems("XInternalError", "XInternalError.class", "XInternalTimerServiceController",
                             "XInternalTimerServiceController.class");
    });
  }

  @Test
  @NeedsIndex.Full
  public void testNonImportedAnnotationClass() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.addClass("package foo; public @interface XAnotherAnno {}");
      completion.get().configure();
      completion.get().type('X');
      completion.get().assertFirstStringItems("XAnno", "XAnotherAnno");
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testMetaAnnotation() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java", "@<caret> @interface Anno {}");
      myFixture.complete(CompletionType.BASIC);
      assertTrue(ContainerUtil.exists(myFixture.getLookup().getItems(), it -> it.getLookupString().equals("Retention")));
    });
  }

  @Test
  public void testAnnotationClassFromWithinAnnotation() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  @NeedsIndex.Full
  public void testStaticallyImportedFieldsTwice() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.addClass("""
                             class Foo {
                               public static final int aZOO;
                             }
                           """);
      myFixture.configureByText("a.java", """
          import static Foo.*
          class Bar {{
            aZ<caret>a
          }}
        """);
      assertThat(myFixture.completeBasic()).hasSize(1);
    });
  }

  @Test
  public void testStaticallyImportedFieldsTwiceSwitch() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  public void testStatementKeywords() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java", """
          class Bar {{
            <caret>xxx
          }}
        """);
      myFixture.completeBasic();
      final var strings = myFixture.getLookupElementStrings();
      assertTrue(strings.contains("if"));
      assertTrue(strings.contains("while"));
      assertTrue(strings.contains("do"));
      assertTrue(strings.contains("new"));
      assertTrue(strings.contains("try"));

      int iNew = strings.indexOf("new");
      assertFalse(strings.subList(iNew+1, strings.size()).contains("new"));
    });
  }

  @Test
  public void testExpressionKeywords() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java", """
          class Bar {{
            foo(<caret>xxx)
          }}
        """);
      myFixture.completeBasic();
      final var strings = myFixture.getLookupElementStrings();
      assertTrue(strings.contains("new"));
    });
  }

  @Test
  public void testImportAsterisk() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java", "import java.lang.<caret>");
      myFixture.completeBasic();
      myFixture.type("*;");
      myFixture.checkResult("import java.lang.*;<caret>");
    });
  }

  @Test
  public void testDontPreselectCaseInsensitivePrefixMatch() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    CodeInsightSettings.getInstance().setCompletionCaseSensitive(CodeInsightSettings.NONE);
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java", "import java.io.*; class Foo {{ int fileSize; fil<caret>x }}");
      myFixture.completeBasic();
      assertEquals("fileSize", completion.get().getLookup().getCurrentItem().getLookupString());
      myFixture.type('e');

      assertEquals("File", completion.get().getLookup().getItems().get(0).getLookupString());
      assertEquals("fileSize", completion.get().getLookup().getItems().get(1).getLookupString());
      assertSame(completion.get().getLookup().getCurrentItem(), completion.get().getLookup().getItems().get(1));
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testNoGenericsWhenChoosingWithParen() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest("Ma(")); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testNoClosingWhenChoosingWithParenBeforeIdentifier() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest("(")); }

  @Test
  public void testPackageInMemberType() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  public void testPackageInMemberTypeGeneric() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testConstantInAnno() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest("\n")); }

  @Test
  @NeedsIndex.SmartMode(reason = "Smart mode needed for EncodingReferenceInjector to provide EncodingReference with variants")
  public void testCharsetName() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.addClass("package java.nio.charset; public class Charset { public static Charset forName(String s) {} }");
      completion.get().configureByTestName();
      assertTrue(myFixture.getLookupElementStrings().contains("UTF-8"));
    });
  }

  @Test
  @NeedsIndex.Full
  public void testInnerClassInExtendsGenerics() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    var text = "package bar; class Foo extends List<Inne<caret>> { public static class Inner {} }";
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureFromExistingVirtualFile(myFixture.addClass(text).getContainingFile().getVirtualFile());
      myFixture.completeBasic();
      myFixture.type('\n');
      myFixture.checkResult(text.replace("Inne<caret>", "Foo.Inner<caret>"));
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testClassNameDot() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest(".")); }

  @Test
  @NeedsIndex.Full
  public void testClassNameDotBeforeCall() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.addClass("package foo; public class FileInputStreamSmth {}");
      myFixture.configureByFile(myFixture.getTestName(false) + ".java");
      PsiDocumentManager.getInstance(myFixture.getProject()).commitAllDocuments();
      completion.get().type('\b');
      PsiDocumentManager.getInstance(myFixture.getProject()).commitAllDocuments();
      myFixture.complete(CompletionType.BASIC, 2);
      assertNotNull(completion.get().getLookup());
      completion.get().type('.');
      completion.get().checkResult();
    });
  }

  @Test
  public void testNoReturnAfterDot() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configure();
      assertFalse(myFixture.getLookupElementStrings().contains("return"));
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testDuplicateExpectedTypeInTypeArgumentList() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configure();
      var items = ContainerUtil.filter(myFixture.getLookupElements(), it -> it.getLookupString().equals("String"));
      assertEquals(1, items.size());
      assertEquals(" (java.lang)", NormalCompletionFixture.renderElement(items.getFirst()).getTailText());
    });
  }

  @Test
  @NeedsIndex.Full
  public void testDuplicateInnerClass() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configure();
      var items = ContainerUtil.filter(myFixture.getLookupElements(), it -> it.getLookupString().equals("Inner"));
      assertEquals(1, items.size());
    });
  }

  @Test
  public void testSameSignature() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configure();
      myFixture.assertPreferredCompletionItems(0, "s", "s, file", "s, file, a");
      completion.get().getLookup().setCurrentItem(completion.get().getLookup().getItems().get(2));
      myFixture.type('\n');
      completion.get().checkResult();
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testNoParenthesesAroundCallQualifier() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  @NeedsIndex.Full
  public void testAllAssertClassesMethods() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.addClass("package foo; public class Assert { public static boolean foo() {} }");
      myFixture.addClass("package bar; public class Assert { public static boolean bar() {} }");
      completion.get().configure();
      assertEquals(List.of("Assert.bar", "Assert.foo"), myFixture.getLookupElementStrings());
      myFixture.type('\n');
      completion.get().checkResult();
    });
  }

  @Test
  @NeedsIndex.Full
  public void testCastVisually() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configure();
      var p = NormalCompletionFixture.renderElement(myFixture.getLookupElements()[0]);
      assertEquals("getValue", p.getItemText());
      assertTrue(p.isItemTextBold());
      assertEquals("Foo", p.getTypeText());
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testSuggestEmptySet() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configure();
      assertEquals("emptySet", myFixture.getLookupElementStrings().getFirst());
      completion.get().type('\n');
      completion.get().checkResult();
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testSuggestAllTypeArguments() {
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configure();
      assertEquals("String, List<String>", completion.get().getLookup().getItems().getFirst().getLookupString());
      assertEquals("String, List<String>", NormalCompletionFixture.renderElement(completion.get().getLookup().getItems().getFirst()).getItemText());
      completion.get().type('\n');
      completion.get().checkResult();
    });
  }

  @Test
  public void testNoFinalInAnonymousConstructor() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testListArrayListCast() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest("\n")); }

  @Test
  public void testInterfaceImplementationNoCast() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  @NeedsIndex.Full
  public void testStaticallyImportedMethodsBeforeExpression() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  @NeedsIndex.Full
  public void testInnerChainedReturnType() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  private CommonCodeStyleSettings getCodeStyleSettings() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    return CodeStyle.getSettings(myFixture.getProject()).getCommonSettings(JavaLanguage.INSTANCE);
  }

  @Test
  public void testCompatibleInterfacesCast() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configure();
      assertTrue(myFixture.getLookupElementStrings().containsAll(List.of("foo", "bar")));
    });
  }

  @Test
  public void testDontAutoInsertMiddleMatch() {
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configure();
      completion.get().checkResult();
      assertEquals(1, completion.get().getLookup().getItems().size());
    });
  }

  @Test
  @NeedsIndex.SmartMode(reason = "JavaGenerateMemberCompletionContributor.fillCompletionVariants works in smart mode only (for method implementations)")
  public void testImplementViaCompletion() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configure();
      myFixture.assertPreferredCompletionItems(0, "private", "protected", "public", "public void run");
      var item = completion.get().getLookup().getItems().get(3);

      var p = NormalCompletionFixture.renderElement(item);
      assertEquals("public void run", p.getItemText());
      assertEquals("(String t, int myInt) {...}", p.getTailText());
      assertEquals("Foo", p.getTypeText());

      completion.get().getLookup().setCurrentItem(item);
      myFixture.type('\n');
      completion.get().checkResult();
    });
  }

  @Test
  @NeedsIndex.SmartMode(reason = "JavaGenerateMemberCompletionContributor.fillCompletionVariants works in smart mode only (for implementing methods)")
  public void testImplementViaCompletionWithGenerics() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configure();
      myFixture.assertPreferredCompletionItems(0, "public void methodWithGenerics", "public void methodWithTypeParam");
      assertEquals("(List k) {...}", NormalCompletionFixture.renderElement(completion.get().getLookup().getItems().get(0)).getTailText());
      assertEquals("(K k) {...}", NormalCompletionFixture.renderElement(completion.get().getLookup().getItems().get(1)).getTailText());
    });
  }

  @Test
  @NeedsIndex.SmartMode(reason = "JavaGenerateMemberCompletionContributor.fillCompletionVariants provides dialog option in smart mode only")
  public void testImplementViaOverrideCompletion() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configure();
      myFixture.assertPreferredCompletionItems(0, "Override", "OverrideOnly", "Override/Implement methods…", "MustBeInvokedByOverriders", "public void run");
      completion.get().getLookup().setCurrentItem(completion.get().getLookup().getItems().get(4));
      myFixture.type('\n');
      completion.get().checkResult();
    });
  }

  @Test
  @NeedsIndex.SmartMode(reason = "JavaGenerateMemberCompletionContributor.fillCompletionVariants provides dialog option in smart mode only")
  public void testSuggestToOverrideMethodsWhenTypingOverrideAnnotation() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configure();
      myFixture.assertPreferredCompletionItems(0, "Override", "OverrideOnly", "Override/Implement methods…");
      completion.get().getLookup().setCurrentItem(completion.get().getLookup().getItems().get(2));
      myFixture.type('\n');
      NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
      completion.get().checkResult();
    });
  }

  @Test
  @NeedsIndex.SmartMode(reason = "JavaGenerateMemberCompletionContributor.fillCompletionVariants provides dialog option in smart mode only")
  public void testSuggestToOverrideMethodsWhenTypingOverrideAnnotationBeforeMethod() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configure();
      myFixture.assertPreferredCompletionItems(0, "Override", "OverrideOnly", "Override/Implement methods…");
      completion.get().getLookup().setCurrentItem(completion.get().getLookup().getItems().get(2));
      myFixture.type('\n');
      NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
      completion.get().checkResult();
    });
  }

  @Test
  @NeedsIndex.SmartMode(reason = "JavaGenerateMemberCompletionContributor.fillCompletionVariants provides dialog option in smart mode only")
  public void testSuggestToOverrideMethodsInMulticaretMode() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configure();
      myFixture.assertPreferredCompletionItems(0, "Override", "OverrideOnly", "Override/Implement methods…");
      completion.get().getLookup().setCurrentItem(completion.get().getLookup().getItems().get(2));
      myFixture.type('\n');
      NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
      completion.get().checkResult();
    });
  }

  @Test
  @NeedsIndex.SmartMode(reason = "JavaGenerateMemberCompletionContributor.fillCompletionVariants works in smart mode only (for implementing methods)")
  public void testStrikeOutDeprecatedSuperMethods() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configure();
      myFixture.assertPreferredCompletionItems(0, "void foo1", "void foo2");
      assertFalse(NormalCompletionFixture.renderElement(completion.get().getLookup().getItems().get(0)).isStrikeout());
      assertTrue(NormalCompletionFixture.renderElement(completion.get().getLookup().getItems().get(1)).isStrikeout());
    });
  }

  @Test
  @NeedsIndex.SmartMode(reason = "JavaGenerateMemberCompletionContributor.fillCompletionVariants works in smart mode only (for equals() and hashCode())")
  public void testInvokeGenerateEqualsHashCodeOnOverrideCompletion() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EqualsHashCodeTemplatesManager.getInstance().setDefaultTemplate(
      EqualsHashCodeTemplatesManager.JAVA_UTIL_OBJECTS_EQUALS_AND_HASH_CODE);
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configure();
      assertEquals(2, myFixture.getLookupElementStrings().size());
      completion.get().getLookup().setSelectedIndex(1);
      completion.get().type('\n');
      completion.get().checkResult();
    });
  }

  @Test
  @NeedsIndex.SmartMode(reason = "JavaGenerateMemberCompletionContributor.fillCompletionVariants works in smart mode only (for 'toString()')")
  public void testInvokeGenerateToStringOnOverrideCompletion() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configure();
      assertEquals(2, myFixture.getLookupElementStrings().size());
      completion.get().getLookup().setSelectedIndex(1);
      completion.get().type('\n');
      NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
      NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
      completion.get().checkResult();
    });
  }

  @Test
  @NeedsIndex.SmartMode(reason = "JavaGenerateMemberCompletionContributor.fillCompletionVariants works in smart mode only (for equals() and hashCode())")
  public void testDontGenerateEqualsHashCodeOnOverrideCompletion() {
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configure();
      completion.get().type('\n');
      completion.get().checkResult();
    });
  }

  @Test
  @NeedsIndex.SmartMode(reason = "JavaGenerateMemberCompletionContributor.fillCompletionVariants works in smart mode only (for 'toString()')")
  public void testDontGenerateToStringOnOverrideCompletion() {
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configure();
      completion.get().type('\n');
      NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
      completion.get().checkResult();
    });
  }

  @Test
  @NeedsIndex.SmartMode(reason = "JavaGenerateMemberCompletionContributor.fillCompletionVariants works in smart mode only (for getters and setters)")
  public void testAccessorViaCompletion() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configure();

      var getter = ContainerUtil.find(myFixture.getLookupElements(), it -> it.getLookupString().equals("public int getField"));
      var setter = ContainerUtil.find(myFixture.getLookupElements(), it -> it.getLookupString().equals("public void setField"));
      assertNotNull(getter, myFixture.getLookupElementStrings().toString());
      assertNotNull(setter, myFixture.getLookupElementStrings().toString());

      var p = NormalCompletionFixture.renderElement(getter);
      assertEquals(p.getItemText(), getter.getLookupString());
      assertEquals("() {...}", p.getTailText());
      assertTrue(p.getTypeText().isEmpty());

      p = NormalCompletionFixture.renderElement(setter);
      assertEquals(p.getItemText(), setter.getLookupString());
      assertEquals("(int field) {...}", p.getTailText());
      assertTrue(p.getTypeText().isEmpty());

      completion.get().getLookup().setCurrentItem(getter);
      myFixture.type('\n');
      completion.get().checkResult();
    });
  }

  @Test
  @NeedsIndex.SmartMode(reason = "JavaGenerateMemberCompletionContributor.fillCompletionVariants works in smart mode only (for getters and setters)")
  public void testNoSetterForFinalField() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configure();
      myFixture.assertPreferredCompletionItems(0, "public", "public int getFinalField");
      assertFalse(ContainerUtil.exists(myFixture.getLookupElements(), it -> it.getLookupString().equals("public void setFinalField")));
      assertFalse(ContainerUtil.exists(myFixture.getLookupElements(), it -> it.getLookupString().equals("public int getCONSTANT")));
    });
  }

  @Test
  public void testBraceOnNextLine() {
    EdtTestUtil.runInEdtAndWait(() -> {
      getCodeStyleSettings().BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE;
      completion.get().doTest();
    });
  }

  @Test
  public void testDoForceBraces() {
    EdtTestUtil.runInEdtAndWait(() -> {
      getCodeStyleSettings().DOWHILE_BRACE_FORCE = CommonCodeStyleSettings.FORCE_BRACES_ALWAYS;
      completion.get().doTest("\n");
    });
  }

  @Test
  public void testMulticaretSingleItemInsertion() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest());
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testMulticaretMethodWithParen() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest());
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testMulticaretTyping() {
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configure();
      assertNotNull(completion.get().getLookup());
      completion.get().type('p');
      assertNotNull(completion.get().getLookup());
      completion.get().type('\n');
      completion.get().checkResult();
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testMulticaretCompletionFromNonPrimaryCaret() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configure();
      myFixture.assertPreferredCompletionItems(0, "arraycopy");
    });
  }

  @Test
  public void testMulticaretCompletionFromNonPrimaryCaretWithTab() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest("\t"));
  }

  @Test
  @NeedsIndex.Full
  public void testCompleteLowercaseClassName() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.addClass("package foo; public class myClass {}");
      myFixture.configureByText("a.java", """
        class Foo extends my<caret>
        """);
      myFixture.complete(CompletionType.BASIC, 2);
      myFixture.checkResult("""
                              import foo.myClass;
                            
                              class Foo extends myClass
                              """);
    });
  }

  @Test
  public void testNoClassesWithDollar() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.addClass("package some; public class $WithDollarNonImported {}");
      myFixture.addClass("package imported; public class $WithDollarImported {}");
      completion.get().doAntiTest();
    });
  }

  @Test
  public void testClassesWithDollarInTheMiddle() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.addClass("package imported; public class Foo$WithDollarImported {}");
      completion.get().configureByTestName();
      myFixture.completeBasic();
      assertTrue(myFixture.getLookupElementStrings().contains("Foo$WithDollarImported"));
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testDontShowStaticInnerClassAfterInstanceQualifier() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java", """
        class Foo {
          static class Inner {}
        }
        class Bar {
          void foo(Foo f) {
            f.<caret>
          }
        }
        """);
      myFixture.completeBasic();
      assertFalse(myFixture.getLookupElementStrings().contains("Inner"));
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testShowStaticMemberAfterInstanceQualifierWhenNothingMatches() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java", "class Foo{{ \"\".<caret> }}");
      myFixture.completeBasic();
      assertFalse(myFixture.getLookupElementStrings().contains("valueOf"));
      ((LookupImpl)myFixture.getLookup()).hide();
      myFixture.type("val");
      myFixture.completeBasic();
      assertTrue(myFixture.getLookupElementStrings().contains("valueOf"));
    });
  }

  @Test
  public void testNoMathTargetMethods() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doAntiTest()); }

  @Test
  @NeedsIndex.Full
  public void testNoLowercaseClasses() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.addClass("package foo; public class abcdefgXxx {}");
      completion.get().doAntiTest();
      myFixture.complete(CompletionType.BASIC, 2);
      completion.get().assertStringItems("abcdefgXxx");
    });
  }

  @Test
  @NeedsIndex.Full
  public void testProtectedFieldInAnotherPackage() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.addClass("package foo; public class Super { protected String myString; }");
      completion.get().doTest();
    });
  }

  @Test
  @NeedsIndex.Full
  public void testUnimportedStaticInnerClass() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.addClass("package foo; public class Super { public static class Inner {} }");
      completion.get().doTest();
    });
  }

  @Test
  public void testNoJavaLangPackagesInImport() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doAntiTest()); }

  @Test
  @NeedsIndex.Full
  public void testNoStaticDuplicatesFromExpectedMemberFactories() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configure();
      myFixture.complete(CompletionType.BASIC, 2);
      myFixture.assertPreferredCompletionItems(0, "xcreateZoo", "xcreateElephant");
    });
  }

  @Test
  public void testNoInaccessibleCompiledElements() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configure();
      myFixture.complete(CompletionType.BASIC, 2);
      completion.get().checkResultByFile(myFixture.getTestName(false) + ".java");
      assertThat(completion.get().getItems()).isNullOrEmpty();
      assertNull(completion.get().getLookup());
    });
  }

  @Test
  @NeedsIndex.SmartMode(reason = "JavaGenerateMemberCompletionContributor.fillCompletionVariants works in smart mode only (for getters)")
  public void testCodeCleanupDuringCompletionGeneration() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java", "class Foo {int i; ge<caret>}");
      myFixture.enableInspections(new UnqualifiedFieldAccessInspection());
      myFixture.complete(CompletionType.BASIC);
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
      myFixture.checkResult("""
                              class Foo {int i;
                            
                                  public int getI() {
                                      return this.i;
                                  }
                              }""");
    });
  }

  @Test
  public void testIndentingForSwitchCase() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  public void testShowMostSpecificOverride() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configure();
      assertEquals("B", NormalCompletionFixture.renderElement(myFixture.getLookup().getItems().getFirst()).getTypeText());
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testShowMostSpecificOverrideOnlyFromClass() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configure();
      assertEquals("Door", NormalCompletionFixture.renderElement(myFixture.getLookup().getItems().getFirst()).getTypeText());
    });
  }

  @Test
  public void testNoOverrideWithMiddleMatchedName() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configure();
      assertFalse(myFixture.getLookupElementStrings().contains("public void removeTemporaryEditorNode"));
    });
  }

  @Test
  public void testShowVarInitializers() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configure();
      myFixture.assertPreferredCompletionItems(0, "FIELD1", "FIELD2", "FIELD3", "FIELD4");
      var items = myFixture.getLookup().getItems();
      assertEquals(Arrays.asList("( \"x\")", "(\"y\") {...}", null, " ( = 42)"),
                   ContainerUtil.map(items, item -> NormalCompletionFixture.renderElement(item).getTailText()));
      assertTrue(NormalCompletionFixture.renderElement(items.get(3)).getTailFragments().getFirst().isItalic());
    });
  }

  @Test
  @NeedsIndex.Full
  public void testShowNonImportedVarInitializers() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configure();
      // Format.Field, DateFormat.Field, NumberFormat.Field, etc. classes are suggested first
      myFixture.assertPreferredCompletionItems(5,
                                               "Field", "Field", "Field", "Field", "Field", "FIELD1", "FIELD2", "FIELD3", "FIELD4");
      var fieldItems = myFixture.getLookup().getItems().subList(5, 9);
      assertEquals(Arrays.asList("( \"x\") in E", "(\"y\") {...} in E", null, " ( = 42) in E"),
                   ContainerUtil.map(fieldItems, item -> NormalCompletionFixture.renderElement(item).getTailText()));
      assertTrue(NormalCompletionFixture.renderElement(fieldItems.get(3)).getTailFragments().get(0).isItalic());
      assertFalse(NormalCompletionFixture.renderElement(fieldItems.get(3)).getTailFragments().get(1).isItalic());
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testSuggestInterfaceArrayWhenObjectIsExpected() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configure();
      assertTrue(NormalCompletionFixture.renderElement(myFixture.getLookup().getItems().get(0)).getTailText().contains("{...}"));
      assertTrue(NormalCompletionFixture.renderElement(myFixture.getLookup().getItems().get(1)).getTailText().contains("[]"));
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testSuggestInterfaceArrayWhenObjectArrayIsExpected() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configure();
      assertTrue(NormalCompletionFixture.renderElement(myFixture.getLookup().getItems().get(0)).getTailText().contains("{...}"));
      assertTrue(NormalCompletionFixture.renderElement(myFixture.getLookup().getItems().get(1)).getTailText().contains("[]"));
    });
  }

  @Test
  public void testDispreferPrimitiveTypesInCallArgs() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    CodeInsightSettings.getInstance().setCompletionCaseSensitive(CodeInsightSettings.NONE);
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configure();
      myFixture.assertPreferredCompletionItems(0, "dx", "doo", "Doo", "double");
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testCopyConstructor() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest("\n")); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testGetClassType() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configure();
      assertEquals("Class<? extends Number>", NormalCompletionFixture.renderElement(myFixture.getLookupElements()[0]).getTypeText());
    });
  }

  @Test
  @NeedsIndex.Full
  public void testNonImportedClassAfterNew() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      var uClass = myFixture.addClass("package foo; public class U {}");
      myFixture.configureByText("a.java", "class X {{ new U<caret>x }}");
      myFixture.completeBasic();
      assertSame(myFixture.getLookupElements()[0].getObject(), uClass);
    });
  }

  @Test
  public void testSuggestClassNamesForLambdaParameterTypes() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest("\n")); }

  @Test
  public void testOnlyExtendsSuperInWildcard() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    CodeInsightSettings.getInstance().setCompletionCaseSensitive(CodeInsightSettings.NONE);

    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configure();
      assertEquals(myFixture.getLookupElementStrings(), List.of("extends", "super"));
      LookupManager.getInstance(myFixture.getProject()).hideActiveLookup();

      myFixture.type('n');
      assertThat(myFixture.completeBasic()).isNullOrEmpty();
      myFixture.type('\b');
      completion.get().checkResultByFile(myFixture.getTestName(false) + ".java");
    });
  }

  @Test
  @NeedsIndex.Full
  public void testChainInLambdaBinary() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      getCodeStyleSettings().ALIGN_MULTILINE_BINARY_OPERATION = true;
      myFixture.addClass("package pkg; public class PathUtil { public static String toSystemDependentName() {} }");
      completion.get().doTest("\n");
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testPairAngleBracketDisabled() {
    CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET = false;
    EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest("<"));
  }

  @Test
  public void testDuplicateGenericMethodSuggestionWhenInheritingFromRawType() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configure();
      assertEquals(myFixture.getLookupElementStrings(), List.of("indexOf"));
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testDuplicateEnumValueOf() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configure();
      assertEquals(ContainerUtil.map(myFixture.getLookupElements(),
                                     item -> NormalCompletionFixture.renderElement(item).getItemText()), List.of("Bar.valueOf", "Foo.valueOf", "Enum.valueOf"));
    });
  }

  @Test
  public void testTypeArgumentInCast() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configure();
      myFixture.assertPreferredCompletionItems(0, "String");
    });
  }

  @Test
  public void testNoCallsInPackageStatement() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doAntiTest()); }

  @Test
  @NeedsIndex.Full
  public void testTypeParameterShadowingClass() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configure();
      myFixture.assertPreferredCompletionItems(0, "Tttt", "Tttt");
      assertTrue(myFixture.getLookupElements()[0].getObject() instanceof PsiTypeParameter);
      assertFalse(myFixture.getLookupElements()[1].getObject() instanceof PsiTypeParameter);
      completion.get().selectItem(myFixture.getLookupElements()[1]);
      completion.get().checkResult();
    });
  }

  @Test
  public void testLowercaseDoesNotMatchUnderscore() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configure();
      assertEquals(myFixture.getLookupElementStrings(), List.of("web"));
    });
  }

  @Test
  public void testLocalClassPresentation() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      var cls = myFixture.addFileToProject("foo/Bar.java", """
        package foo;
        class Bar {{
            class Local {}
            Lo<caret>x
        }}""");
      myFixture.configureFromExistingVirtualFile(cls.getContainingFile().getVirtualFile());
      var item = myFixture.completeBasic()[0];
      assertTrue(NormalCompletionFixture.renderElement(item).getTailText().contains("local class"));
    });
  }

  @Test
  public void testNoDuplicateInCast() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configure();
      assertNull(myFixture.getLookupElementStrings());
    });
  }

  @Test
  public void testNoNonAnnotationMethods() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doAntiTest()); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testPreferBigDecimalToJavaUtilInner() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configure();
      myFixture.assertPreferredCompletionItems(0, "BigDecimal", "BigDecimalLayoutForm");
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testOnlyExceptionsInMultiCatch1() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest("\n")); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testOnlyExceptionsInMultiCatch2() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest("\n")); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testOnlyResourcesInResourceList1() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest("\n")); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testOnlyResourcesInResourceList2() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest("\n")); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testOnlyResourcesInResourceList3() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest("\n")); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testOnlyResourcesInResourceList4() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest("\n")); }

  @Test
  public void testOnlyResourcesInResourceList5() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest("\n")); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testMethodReferenceNoStatic() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest("\n")); }

  @Test
  public void testMethodReferenceCallContext() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest("\n")); }

  @Test
  @NeedsIndex.Full
  public void testDestroyingCompletedClassDeclaration() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest("\n")); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testResourceParentInResourceList() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByTestName();
      assertEquals("MyOuterResource", myFixture.getLookupElementStrings().getFirst());
      assertTrue(myFixture.getLookupElementStrings().contains("MyClass"));
      myFixture.type("C\n");
      completion.get().checkResultByFile(myFixture.getTestName(false) + "_after.java");
    });
  }

  @Test
  public void testAfterTryWithResources() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByTestName();
      var strings = myFixture.getLookupElementStrings();
      assertTrue(strings.containsAll(List.of("final", "finally", "int", "Util")));
    });
  }

  @Test
  public void testNewObjectHashMapWithSmartEnter() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByTestName();
      myFixture.performEditorAction(IdeActions.ACTION_CHOOSE_LOOKUP_ITEM_COMPLETE_STATEMENT);
      completion.get().checkResultByFile(myFixture.getTestName(false) + "_after.java");
    });
  }

  @Test
  @NeedsIndex.SmartMode(reason = "MethodCallFixer and MissingCommaFixer need resolve in smart mode")
  public void testCompletionShouldNotAddExtraComma() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByTestName();
      myFixture.performEditorAction(IdeActions.ACTION_CHOOSE_LOOKUP_ITEM_COMPLETE_STATEMENT);
      completion.get().checkResultByFile(myFixture.getTestName(false) + "_after.java");
    });
  }

  @Test
  @NeedsIndex.Full
  public void testCompletingClassWithSameNameAsPackage() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.addClass("package Apple; public class Apple {}");
      completion.get().doTest("\n");
    });
  }

  @Test
  public void testSuggestGetInstanceMethodName() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  @NeedsIndex.Full(reason = "AllClassesSearchExecutor.processClassNames from JavaNoVariantsDelegator.suggestNonImportedClasses uses stub indices to provide completion, so matching Scratch class is ignored, ant so is its inner class")
  public void testTabOnNewInnerClass() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByTestName();
      completion.get().getLookup().setCurrentItem(ContainerUtil.find(myFixture.getLookupElements(), item -> item.getLookupString().contains("Inner")));
      myFixture.type('\t');
      completion.get().checkResult();
    });
  }

  @Test
  @NeedsIndex.Full
  public void testRemoveUnusedImportOfSameName() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.addClass("package foo; public class List {}");
      completion.get().configureByTestName();
      completion.get().getLookup().setCurrentItem(ContainerUtil.find(
        myFixture.getLookupElements(), item -> item.getObject() instanceof PsiClass cls && "java.util.List".equals(cls.getQualifiedName())));
      myFixture.type('\n');
      completion.get().checkResult();
    });
  }

  @Test
  public void testNoDuplicationAfterNewWithExpectedTypeParameter() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java", "class Foo<T> { T t = new <caret> }");
      completion.get().complete();
      assertTrue(ContainerUtil.filter(myFixture.getLookupElements(), it -> it.getAllLookupStrings().contains("T")).size() < 2);
    });
  }

  @Test
  public void testNoDuplicationForInnerClassOnSecondInvocation() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java", """
        class Abc {
            class FooBar {}
            void foo() {
                FooBar<caret>x
            }
        }""");
      myFixture.complete(CompletionType.BASIC, 2);
      assertEquals(1, myFixture.getLookupElements().length);
    });
  }

  @Test
  public void testSmartEnterWrapsTypeArguments() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java", "class Foo<T> { F<caret>List<String> }");
      myFixture.completeBasic();
      myFixture.type(Lookup.COMPLETE_STATEMENT_SELECT_CHAR);
      myFixture.checkResult("class Foo<T> { Foo<List<String>><caret> }");
    });
  }

  @Test
  public void testNoSuggestionsAfterEnumConstant() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doAntiTest()); }

  @Test
  public void testPutCaretInsideParensInFixedPlusVarargOverloads() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest("\n")); }

  @Test
  public void testSuggestCurrentClassInSecondSuperGenericParameter() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest("\n")); }

  @Test
  @NeedsIndex.Full
  public void testAfterNewEditingPrefixBackAndForthWhenSometimesThereAreExpectedTypeSuggestionsAndSometimesNot() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.addClass("class Super {}");
      myFixture.addClass("class Sub extends Super {}");
      myFixture.addClass("package foo; public class SubOther {}");
      myFixture.configureByText("a.java", "class C { Super s = new SubO<caret>x }");

      myFixture.completeBasic();
      myFixture.assertPreferredCompletionItems(0, "SubOther");
      myFixture.type('\b');
      myFixture.assertPreferredCompletionItems(0, "Sub");
      myFixture.type('O');
      myFixture.assertPreferredCompletionItems(0, "SubOther");
    });
  }

  @Test
  public void testCorrectTypos() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java", "class MyClass { MyCals<caret> }");
      myFixture.completeBasic();
      myFixture.type('\n');
      myFixture.checkResult("class MyClass { MyClass<caret> }");
    });
  }

  @Test
  public void testRemoveParenthesesWhenReplacingEmptyCallWithConstant() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest("\t"));
  }

  @Test
  public void testNoCallsAfterAnnotationInCodeBlock() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  public void testExtendsAfterEnum() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java", "enum X ex<caret>"); // should not complete
      myFixture.completeBasic();
      myFixture.checkResult("enum X ex");
    });
  }

  @Test
  @NeedsIndex.Full
  public void testAddImportWhenCompletingInnerAfterNew() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.addClass("package p; public class Outer { public static class Inner {} }");
      completion.get().configureByTestName();
      completion.get().selectItem(ContainerUtil.find(completion.get().getItems(), it -> it.getLookupString().contains("Inner")));
      completion.get().checkResult();
    });
  }

  @Test
  public void testCompletingQualifiedClassName() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java", "class C implements java.util.Li<caret>");
      myFixture.completeBasic();
      myFixture.assertPreferredCompletionItems(0, "List");
      myFixture.type('\n');
      myFixture.checkResult("class C implements java.util.List<caret>");
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testSuggestObjectMethodsWhenSuperIsUnresolved() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      Consumer<String> checkGetClassPresent = text -> {
        myFixture.configureByText("a.java", text);
        myFixture.completeBasic();
        myFixture.assertPreferredCompletionItems(0, "getClass");
      };
      checkGetClassPresent.accept("class C extends Unresolved {{ getCl<caret>x }}");
      checkGetClassPresent.accept("class C implements Unresolved {{ getCl<caret>x }}");
      checkGetClassPresent.accept("class C extends Unresolved implements Runnable {{ getCl<caret>x }}");
      checkGetClassPresent.accept("class C extends Unresolved1 implements Unresolved2 {{ getCl<caret>x }}");
    });
  }

  @Test
  public void testSuggestInverseOfDefaultAnnoParamValueForBoolean() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByTestName();
      myFixture.assertPreferredCompletionItems(0, "smth = true", "value = false");

      var smthDefault = ContainerUtil.find(completion.get().getItems(), it -> it.getLookupString().equals("smth = false"));
      var presentation = NormalCompletionFixture.renderElement(smthDefault);
      assertEquals(" (default)", presentation.getTailText());
      assertTrue(presentation.getTailFragments().getFirst().isGrayed());

      myFixture.type('\n');
      completion.get().checkResult();
    });
  }

  @Test
  public void testCaseColonAfterStringConstant() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_11, () -> completion.get().doTest()));
  }

  @Test
  public void testOneElementArray() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByTestName();
      myFixture.assertPreferredCompletionItems(0, "aaa", "aaa[0]");
      completion.get().selectItem(completion.get().getItems()[1]);
      completion.get().checkResult();
    });
  }

  @Test
  public void testSuggestChainsOfExpectedType() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configure();
      myFixture.assertPreferredCompletionItems(0, "bar", "bar().getGoo");
      completion.get().selectItem(completion.get().getItems()[1]);
      completion.get().checkResult();
    });
  }

  @Test
  public void testTopLevelPublicClass() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  public void testTopLevelPublicClassIdentifierExists() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  public void testTopLevelPublicClassBraceExists() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  public void testNoExceptionsWhenCompletingInapplicableClassNameAfterNew() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest("\n")); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testTypeCastCompletion() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java", "class X { StringBuilder[] s = (Stri<caret>)}");
      myFixture.completeBasic();
      myFixture.type('\n');
      myFixture.checkResult("class X { StringBuilder[] s = (StringBuilder[]) <caret>}");
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testTypeCastCompletionNoClosingParenthesis() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java", "class X { StringBuilder[] s = (Stri<caret>}");
      myFixture.completeBasic();
      myFixture.type('\n');
      myFixture.checkResult("class X { StringBuilder[] s = (StringBuilder[]) <caret>}");
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testTypeCastCompletionGeneric() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java", "import java.util.*; class X { Map<String, Object> getMap() { return (Ma<caret>)}}");
      var elements = myFixture.completeBasic();
      assertTrue(elements.length > 0);
      LookupElementPresentation presentation = NormalCompletionFixture.renderElement(elements[0]);
      assertEquals("(Map<String, Object>)", presentation.getItemText());
      myFixture.type('\n');
      myFixture.checkResult("import java.util.*; class X { Map<String, Object> getMap() { return (Map<String, Object>) <caret>}}");
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testNoExtraSpaceAfterModifier() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java", "import java.util.*; class X { prot<caret> @NotNull String foo() {}}");
      myFixture.completeBasic();
      myFixture.checkResult("import java.util.*; class X { protected<caret> @NotNull String foo() {}}");
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testSuggestUTF8Charset() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java", "import java.nio.charset.Charset; class X { Charset test() {return U<caret>;}}");
      myFixture.completeBasic();
      myFixture.assertPreferredCompletionItems(0, "StandardCharsets.UTF_8", "StandardCharsets.US_ASCII",
                                               "StandardCharsets.UTF_16", "StandardCharsets.UTF_16BE", "StandardCharsets.UTF_16LE");
      myFixture.type('\n');
      myFixture.checkResult("""
                              import java.nio.charset.Charset;
                              import java.nio.charset.StandardCharsets;

                              class X { Charset test() {return StandardCharsets.UTF_8;}}""");
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testStaticFieldsInEnumInitializer() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java", """
        enum MyEnum {
            A, B, C, D, E, F;
            static int myEnumField;
            static int myEnumField2;
            static final int myEnumField3 = 10;
            static final String myEnumField4 = "";
            {
                System.out.println(myE<caret>);
            }
        }""");
      myFixture.completeBasic();
      assertEquals(myFixture.getLookupElementStrings(), List.of("myEnumField3", "myEnumField4"));
    });
  }

  @Test
  public void testQualifiedOuterClassName() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java", """
        class A {
            private static final long sss = 0L;
            static class B {
                private static final long sss = 0L;
                {
                    <caret>int i = 0;
                }
            }
        }
        """);
      myFixture.completeBasic();
      assertEquals(myFixture.getLookupElementStrings().stream().filter(it -> it.contains("sss")).toList(), List.of("A.sss", "sss"));
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testPrivateConstructor() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("A.java", "class A {{new Syst<caret>}}");
      myFixture.completeBasic();
      assertEquals(ContainerUtil.filter(myFixture.getLookupElementStrings(), it -> it.startsWith("S")),
                   List.of("System.Logger", "System.LoggerFinder"));
    });
  }

  @Test
  @NeedsIndex.SmartMode(reason = "looks like augments don't work in dumb mode")
  public void testMemberAsJavaKeyword() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      ServiceContainerUtil.registerExtension(ApplicationManager.getApplication(), PsiAugmentProvider.EP_NAME, new PsiAugmentProvider() {
        @Override
        protected <Psi extends PsiElement> @NotNull List<Psi> getAugments(@NotNull PsiElement element,
                                                                          @NotNull Class<Psi> type,
                                                                          @Nullable String nameHint) {
          if (element instanceof PsiExtensibleClass eClass && eClass.getName().equals("A") && type == PsiMethod.class) {
            PsiMethod method1 = new LightMethodBuilder(PsiManager.getInstance(myFixture.getProject()), "default").setMethodReturnType(PsiTypes.voidType());
            PsiMethod method2 = new LightMethodBuilder(PsiManager.getInstance(myFixture.getProject()), "define").setMethodReturnType(PsiTypes.voidType());
            return List.of(type.cast(method1), type.cast(method2));
          }
          return Collections.emptyList();
        }
      }, myFixture.getTestRootDisposable());
      myFixture.configureByText("A.java", "class A {\n  public void test() {\n    Runnable r = A::def<caret>\n  }\n}");
      myFixture.completeBasic();
      myFixture.checkResult("""
                              class A {
                                public void test() {
                                  Runnable r = A::define;
                                }
                              }""");
    });
  }

  @Test
  @NeedsIndex.SmartMode(reason = "isEffectivelyDeprecated needs smart mode")
  public void testNoFinalLibraryClassesInExtends() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("X.java", "class StriFoo{}final class StriBar{}class X extends Stri<caret>");
      myFixture.completeBasic();
      List<String> expected = List.of(
        "StriFoo", // non-final project class
        "StringIndexOutOfBoundsException", "StringTokenizer", "StringConcatException", "StringReader", "StringWriter",
        // non-final library classes
        "StringBufferInputStream", // deprecated library class
        "StriBar", // final project class (red)
        "StringTemplate"); // interface (red)
      assertEquals(expected, myFixture.getLookupElementStrings());
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testPrimitiveTypeAfterAnnotation() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("X.java", """
        class C {
          void m() {
            @SuppressWarnings("x") boo<caret>
          }
        }""");
      myFixture.completeBasic();
      myFixture.checkResult("""
                              class C {
                                void m() {
                                  @SuppressWarnings("x") boolean
                                }
                              }""");
    });
  }

  @Test
  @NeedsIndex.SmartMode(reason = "CatchLookupElement works only with smart mode")
  public void testAfterTry() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("Test.java", "class X{X() {try {}<caret>}}");
      myFixture.completeBasic();
      assertEquals(List.of("catch", "finally",
                           "catch (Exception e) {\n    throw new RuntimeException(e);\n}",
                           "catch (RuntimeException e) {\n    throw new RuntimeException(e);\n}"),
                   myFixture.getLookupElementStrings());
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testInsertNullable() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("Test.java", "class X {Stri<caret>}");
      myFixture.completeBasic();
      myFixture.type('?');
      myFixture.checkResult("""
                              import org.jetbrains.annotations.Nullable;

                              class X {
                                  @Nullable String
                              }""");
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testInsertNotNull() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("Test.java", "class X {Stri<caret>}");
      myFixture.completeBasic();
      myFixture.type('!');
      myFixture.checkResult("""
                              import org.jetbrains.annotations.NotNull;

                              class X {
                                  @NotNull String
                              }""");
    });
  }

  @Test
  @NeedsIndex.Full
  public void testSuperClassFieldShadowsParameter() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("Test.java", """
        class Test {
          static class X {
            int variable;
          }
         \s
          public void test(long variable) {
            new X() {
              double myDouble = vari<caret>
            };
          }
        }""");
      var lookupElements = myFixture.completeBasic();
      assertNull(lookupElements);
      myFixture.checkResult("""
                              class Test {
                                static class X {
                                  int variable;
                                }
                               \s
                                public void test(long variable) {
                                  new X() {
                                    double myDouble = variable
                                  };
                                }
                              }""");
    });
  }

  @Test
  @NeedsIndex.Full
  public void testVariableNameByTypeName() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("Test.java", "class DemoEntity {} class Test {DemoEntity <caret>}");
      myFixture.completeBasic();
      assertEquals(myFixture.getLookupElementStrings(), List.of("demoEntity", "demo", "entity"));
    });
  }

  @Test
  @NeedsIndex.Full
  public void testCompleteByEqualsAssignment() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    CodeInsightSettings.getInstance().setSelectAutopopupSuggestionsByChars(true);
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("Test.java", """
        public class Test {
          public static void main(final String[] args) {
            Test test = new Test();
            Test test2 = new Test();
            tes<caret>
          }
        }""");
      myFixture.completeBasic();
      myFixture.type('=');
      myFixture.checkResult("""
                              public class Test {
                                public static void main(final String[] args) {
                                  Test test = new Test();
                                  Test test2 = new Test();
                                  test = <caret>
                                }
                              }""");
    });
  }

  @Test
  @NeedsIndex.Full
  public void testCompleteByEqualsDeclaration() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    CodeInsightSettings.getInstance().setSelectAutopopupSuggestionsByChars(true);
    CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION = false;
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("Test.java", """
        public class Test {
          public static void main(final String[] args) {
            String str<caret>
          }
        }""");
      myFixture.completeBasic();
      myFixture.type('=');
      myFixture.checkResult("""
                              public class Test {
                                public static void main(final String[] args) {
                                  String string = <caret>
                                }
                              }""");
    });
  }

  @Test
  @NeedsIndex.Full
  public void testCompleteByEquals() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    CodeInsightSettings.getInstance().setSelectAutopopupSuggestionsByChars(true);
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("Test.java", """
        public class Test {
          public static void main(final String[] args) {
            final Test test = new Test();
            if (test.get<caret>)
          }

          public String getFoo() {
            return "";
          }
        }""");
      myFixture.completeBasic();
      myFixture.type('=');
      myFixture.checkResult("""
                              public class Test {
                                public static void main(final String[] args) {
                                  final Test test = new Test();
                                  if (test.getFoo()=)
                                }

                                public String getFoo() {
                                  return "";
                                }
                              }""");
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testSystemOutNoInitializer() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("System.java", """
        package java.lang;
        public class System {
        public static final PrintStream out = null;
        public static void setOut(PrintStream out) {}
        public void test() {System.o<caret>}
        }""");
      var elements = myFixture.completeBasic();
      assertTrue(elements.length > 0);
      var element = elements[0];
      assertEquals("out", element.getLookupString());
      LookupElementPresentation presentation = NormalCompletionFixture.renderElement(element);
      assertNull(presentation.getTailText());
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary(reason = "Control flow analysis needs standard library and necessary to provide variable initializer")
  public void testLocalFromTryBlock() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("Test.java", """
        class Test {
          public void test() {
            try {
              int myvar = foo();
            }
            catch (RuntimeException ex) {
            }
            my<caret>
          }

          native int foo();
        }""");
      myFixture.completeBasic();
      myFixture.checkResult("""
                              class Test {
                                public void test() {
                                    int myvar = 0;
                                    try {
                                        myvar = foo();
                                    } catch (RuntimeException ex) {
                                    }
                                    myvar
                                }

                                native int foo();
                              }""");
    });
  }

  @Test
  public void testLocalFromAnotherIfBranch() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("Test.java", """
        class Test {
          public void test(int x) {
            if (x > 0) {
              String result = "positive";
            } else if (x < 0) {
              re<caret>
            }
          }
        }""");
      myFixture.completeBasic();
      assertEquals("return", myFixture.getLookupElements()[0].getLookupString());
      assertEquals("record", myFixture.getLookupElements()[1].getLookupString());
      var element = myFixture.getLookupElements()[2];
      assertEquals("result", element.getLookupString());
      LookupElementPresentation presentation = NormalCompletionFixture.renderElement(element);
      assertEquals(" (from if-then block)", presentation.getTailText());
      completion.get().selectItem(element);
      myFixture.checkResult("""
                              class Test {
                                public void test(int x) {
                                    String result = null;
                                    if (x > 0) {
                                        result = "positive";
                                    } else if (x < 0) {
                                        result
                                    }
                                }
                              }""");
    });
  }

  @Test
  public void testVariableIntoScopeInAnnotation() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    String source = """
      public class Demo {
          public static void main(String[] args) {
              @SuppressWarnings(<caret>)
              final String code = "println('Hello world')";
          }
      }""";
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("Test.java", source);
      myFixture.complete(CompletionType.SMART);
      assertTrue(myFixture.getLookupElementStrings().isEmpty());
      myFixture.checkResult(source);
    });
  }

  @Test
  public void testLookupUpDownActions() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("Test.java", "class Test {<caret>}");
      myFixture.completeBasic(); // 'abstract' selected
      myFixture.assertPreferredCompletionItems(0, "abstract", "class", "enum", "final", "interface");
      myFixture.performEditorAction("EditorLookupSelectionDown"); // 'class' selected
      myFixture.performEditorAction("EditorLookupSelectionDown"); // 'enum' selected
      myFixture.performEditorAction("EditorLookupSelectionUp"); // 'class' selected
      myFixture.type('\n');
      myFixture.checkResult("class Test {class }");
    });
  }

  @Test
  @SuppressWarnings("UnnecessaryUnicodeEscape")
  public void testPinyinMatcher() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("Test.java",
                                "class Test {int get\u4F60\u597D() {return 0;} public void test() {int \u4F60\u597D = 1;nh<caret>}}");
      myFixture.completeBasic();
      assertEquals(myFixture.getLookupElementStrings(), List.of("\u4F60\u597D", "get\u4F60\u597D"));
      myFixture.type('\n');
      myFixture.checkResult("class Test {int get\u4F60\u597D() {return 0;} public void test() {int \u4F60\u597D = 1;\u4F60\u597D}}");
    });
  }

  @Test
  @SuppressWarnings("UnnecessaryUnicodeEscape")
  public void testPinyinMatcher2() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("Test.java", "class Test {static public void test() {int \u89D2\u8272 = 3;gj<caret>}}");
      myFixture.completeBasic();
      assertTrue(myFixture.getLookupElementStrings().isEmpty());
      myFixture.type('\b');
      myFixture.completeBasic();
      myFixture.checkResult("class Test {static public void test() {int \u89D2\u8272 = 3;\u89D2\u8272}}");
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testClassLiteralCompletion() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("Test.java", "class Test {Class<? extends CharSequence> get() {return String<caret>}}");
      myFixture.completeBasic();
      myFixture.assertPreferredCompletionItems(0, "String", "String.class", "StringBuffer.class", "StringBuffer", "StringBuilder.class",
                                               "StringBuilder",
                                               "StringIndexOutOfBoundsException");
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testClassLiteralCompletionClassExists() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("Test.java", "class Test {Class<? extends CharSequence> get() {return StringBu<caret>.class}}");
      myFixture.completeBasic();
      myFixture.type('\n');
      myFixture.checkResult("class Test {Class<? extends CharSequence> get() {return StringBuffer.class}}");
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testClassLiteralCompletionNoBound() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("Test.java", "class Test {Class<?> get() {return String<caret>}}");
      myFixture.completeBasic();
      myFixture.assertPreferredCompletionItems(0, "String", "String.class", "StringBuffer.class", "StringBuffer", "StringBuilder.class",
                                               "StringBuilder",
                                               "StringIndexOutOfBoundsException.class",
                                               "StringIndexOutOfBoundsException");
    });
  }

  @Test
  public void testCompleteMethodWithoutParentheses() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      var settings = EditorSettingsExternalizable.getInstance();
      settings.setInsertParenthesesAutomatically(false);
      try {
        myFixture.configureByText("Test.java", "class Test {void myMethod() { myMet<caret> }}");
        myFixture.completeBasic();
        myFixture.checkResult("class Test {void myMethod() { myMethod<caret> }}");
      }
      finally {
        settings.setInsertParenthesesAutomatically(true);
      }
    });
  }

  @Test
  public void testNoPrimitiveTypeInElseIfCondition() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("Test.java", "class X {void a(Object obj) {if(obj instanceof String) {} else if (obj in<caret>)");
      myFixture.completeBasic();
      assertNull(myFixture.getLookupElementStrings());
      myFixture.checkResult("class X {void a(Object obj) {if(obj instanceof String) {} else if (obj instanceof )");
    });
  }

  @Test
  @NeedsIndex.Full
  public void testNoSelfReferenceInEnum() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configure();
      LookupElement[] elements = myFixture.completeBasic();
      LookupElementPresentation presentation = new LookupElementPresentation();
      elements[0].renderElement(presentation);
      assertEquals("(\"1\", AN_IMESSAGE_1) (SomeClass)", presentation.getTailText());
    });
  }

  @Test
  @NeedsIndex.Full
  public void testPackagePrivateConstantInInterface() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.addClass("""
                           package com.example.x;
                         
                           public interface Exposed extends PackPrivate {
                               int value();
                           }
                           """);
      myFixture.addClass("""
                           package com.example.x;
                         
                           interface PackPrivate {
                               Exposed CONSTANT = () -> 5;
                           }
                           """);
      myFixture.configureByText("Main.java", """
        package com.example;
      
        import com.example.x.Exposed;
      
        public class Main {
            public static void main(String[] args) {
                test(CONST<caret>);
            }
      
            private static void test(Exposed exposed) {
            }
        }""");
      myFixture.completeBasic();
      myFixture.type('\n');
      myFixture.checkResult("""
        package com.example;
      
        import com.example.x.Exposed;
      
        public class Main {
            public static void main(String[] args) {
                test(Exposed.CONSTANT);
            }
      
            private static void test(Exposed exposed) {
            }
        }""");
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testDoubleNMatching() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("Test.java", """
                           class X {
                             @NN<caret>
                             void test() {}
                           }
                           """);
      myFixture.completeBasic();
      assertEquals(List.of("NonNls", "NotNull", "NotNullByDefault", "UnknownNullability"), myFixture.getLookupElementStrings());
    });
  }

  @Test
  @NeedsIndex.Full
  public void testEnumMapNoTypeParams() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("Test.java", """
        import java.util.Map;
      
        public abstract class SuperClass {
            enum X {A, B, C}
      
            void run() {
                Map<X, String> map = new EnumM<caret>
            }
        }
        """);
      myFixture.completeBasic();
      myFixture.type('\n');
      myFixture.checkResult("""
                              import java.util.EnumMap;
                              import java.util.Map;
                            
                              public abstract class SuperClass {
                                  enum X {A, B, C}
                            
                                  void run() {
                                      Map<X, String> map = new EnumMap<>()
                                  }
                              }
                              """);
    });
  }
  @Test
  @NeedsIndex.Full
  public void testTagAdd() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      Registry.get("java.completion.methods.use.tags").setValue(true, myFixture.getTestRootDisposable());
      myFixture.configureByText("Test.java", """
        import java.util.HashSet;
      
        public abstract class SuperClass {
      
            void run() {
                HashSet<Object> objects = new HashSet<>();
                objects.len<caret>;
            }
        }
        """);
      myFixture.completeBasic();
      myFixture.type('\n');
      myFixture.checkResult("""
                              import java.util.HashSet;
                            
                              public abstract class SuperClass {
                            
                                  void run() {
                                      HashSet<Object> objects = new HashSet<>();
                                      objects.size();
                                  }
                              }
                              """);
    });
  }

  @Test
  @NeedsIndex.Full
  public void testTagAddWithoutStatic() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      Registry.get("java.completion.methods.use.tags").setValue(true, myFixture.getTestRootDisposable());
      myFixture.configureByText("Test.java", """
        import java.util.HashSet;
      
        public abstract class SuperClass {
      
            void run() {
                HashSet<Object> objects = new HashSet<>();
                objects.len<caret>;
            }
      
            static void len(HashSet<Object> o){}
        }
        """);
      myFixture.complete(CompletionType.BASIC, 2);
      myFixture.type('\n');
      myFixture.checkResult("""
                              import java.util.HashSet;
                            
                              public abstract class SuperClass {
                            
                                  void run() {
                                      HashSet<Object> objects = new HashSet<>();
                                      objects.size();
                                  }
                            
                                  static void len(HashSet<Object> o){}
                              }
                              """);
    });
  }

  @Test
  @NeedsIndex.Full
  public void testTagAddInvocationCount2() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      Registry.get("java.completion.methods.use.tags").setValue(true, myFixture.getTestRootDisposable());
      myFixture.configureByText("Test.java", """
        import java.util.HashSet;
      
        public abstract class SuperClass {
      
            void run() {
                HashSet<Object> objects = new HashSet<>();
                objects.le<caret>;
            }
        }
        """);
      LookupElement[] lookupElements = myFixture.complete(CompletionType.BASIC, 1);
      assertFalse(ContainerUtil.exists(lookupElements, t -> t.getLookupString().equals("size")));

      lookupElements = myFixture.complete(CompletionType.BASIC, 2);
      assertTrue(ContainerUtil.exists(lookupElements, t -> t.getLookupString().equals("size")));
    });
  }

  @Test
  @NeedsIndex.Full
  public void testTagAddInvocationRestart() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      Registry.get("java.completion.methods.use.tags").setValue(true, myFixture.getTestRootDisposable());
      myFixture.configureByText("Test.java", """
        import java.util.HashSet;
      
        public abstract class SuperClass {
      
            void run() {
                HashSet<Object> objects = new HashSet<>();
                objects.<caret>;
            }
        }
        """);
      myFixture.complete(CompletionType.BASIC, 1);
      myFixture.type("l");
      myFixture.type("e");
      myFixture.type("n");
      myFixture.type("g");
      myFixture.type("t");
      LookupElement element = ContainerUtil.find(myFixture.getLookupElements(), t -> t.getLookupString().equals("size"));
      assertNotNull(element);
      LookupElementPresentation presentation = new LookupElementPresentation();
      element.renderElement(presentation);
      assertEquals("size() Tag: length", presentation.getItemText());
      assertEquals("int", presentation.getTypeText());
    });
  }

  @Test
  @NeedsIndex.Full
  public void testResolveToSubclassMethod() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("Test.java", """
        import java.util.List;

        public final class Complete {
          public static void main(String[] args) {
            SubClass instance;
                instance.<caret>
          }

          static class SuperClass<T> {
            public List<T> list(Object param) {return null;}
          }

          static class SubClass extends SuperClass<String> {
            @Override
            public List<String> list(Object paramName) {
              return super.list(paramName);
            }
          }

        }""");
      LookupElement[] elements = myFixture.completeBasic();
      assertNotNull(elements);
      LookupElement listElement = StreamEx.of(elements).collect(MoreCollectors.onlyOne(e -> e.getLookupString().equals("list"))).orElseThrow();
      LookupElementPresentation presentation = new LookupElementPresentation();
      listElement.renderElement(presentation);
      assertEquals("(Object paramName)", presentation.getTailText());
    });
  }

  @Test
  @NeedsIndex.Full
  public void testResolveToSubclassMethod2() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("Test.java", """
        public final class Complete {
          public static void main(String[] args) {
            SubClass instance;
            instance.<caret>
          }
      
          static class SuperClass<T> {
            public List<? extends Object> list(String param) {
              return null;
            }
          }
      
          static class SubClass extends SuperClass<String> {
            @Override
            public List<String> list(String paramName) {
              return null;
            }
          }
        }""");
      LookupElement[] elements = myFixture.completeBasic();
      assertNotNull(elements);
      LookupElement listElement = StreamEx.of(elements).collect(MoreCollectors.onlyOne(e -> e.getLookupString().equals("list"))).orElseThrow();
      LookupElementPresentation presentation = new LookupElementPresentation();
      listElement.renderElement(presentation);
      assertEquals("(String paramName)", presentation.getTailText());
      assertEquals("List<String>", presentation.getTypeText());
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testCompleteUnnamed() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("Test.java", """
        class X {
          void test() {
            String _ = "hello";
            _.t<caret>
          }
        }
        """);
      LookupElement[] elements = myFixture.completeBasic();
      assertEquals(0, elements.length);
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testSwitchUncompletedDefault() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("Test.java", """
          class Test {
              void test(Integer o) {
                  switch (o) {
                      d<caret>:
                          break;
                  }
              }
          }
        """);
      myFixture.complete(CompletionType.BASIC);
      myFixture.type('\n');
      myFixture.checkResult("""
          class Test {
              void test(Integer o) {
                  switch (o) {
                      default:
                          <caret>
                          break;
                  }
              }
          }
        """);
    });  }

  @Test
  @NeedsIndex.Full
  public void testNestedImplicitClass() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("Test.java", """
          public static class NestedClass{
      
          }
      
          public static void main(String[] args) {
               NestedCla<caret>
          }
        """);
      myFixture.complete(CompletionType.BASIC);
      myFixture.checkResult("""
          public static class NestedClass{
      
          }
      
          public static void main(String[] args) {
               NestedClass
          }
        """);
    });  }

  @Test
  @NeedsIndex.Full
  public void testNestedQualifierImplicitClass() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("Test.java", """
          public static class Nested {
              public static class Nested2ClassMore {
              }
          }
      
      
          public void main(String[] args) {
              Nested nested = new Nested();
          }
      
          public void t(Nested2ClassMo<caret> nested2) {
      
          }
        """);
      myFixture.complete(CompletionType.BASIC);
      myFixture.type('\n');
      myFixture.checkResult("""
          public static class Nested {
              public static class Nested2ClassMore {
              }
          }
      
      
          public void main(String[] args) {
              Nested nested = new Nested();
          }
      
          public void t(Nested.Nested2ClassMore nested2) {
      
          }
        """);
    });  }

  @Test
  public void testOuterVariableNotShadowedByPrivateField() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    // IDEA-340271
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("Test.java", """
        class Super {
          private int variable;
        }
        class Use {
          void test(int variable) {
            //noinspection ResultOfObjectAllocationIgnored
            new Super() {
              void m() {
                System.out.println(var<caret>);
              }
            };
          }
        }
        """);
      myFixture.completeBasic();
      myFixture.checkResult("""
        class Super {
          private int variable;
        }
        class Use {
          void test(int variable) {
            //noinspection ResultOfObjectAllocationIgnored
            new Super() {
              void m() {
                System.out.println(variable);
              }
            };
          }
        }
        """);
    });
  }

  @Test
  public void testDecoratedModCommand() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest("\n")); } // IJPL-245403

  @Test
  public void testOuterVariableNotShadowedByPrivateField2() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    // IDEA-340271
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("Test.java", """
        class C {
          class Super {
            private int variable;
          }
          class Use {
            void test(int variable) {
              //noinspection ResultOfObjectAllocationIgnored
              new Super() {
                void m() {
                  System.out.println(var<caret>);
                }
              };
            }
          }
        }
        """);
      myFixture.completeBasic();
      myFixture.checkResult("""
        class C {
          class Super {
            private int variable;
          }
          class Use {
            void test(int variable) {
              //noinspection ResultOfObjectAllocationIgnored
              new Super() {
                void m() {
                  System.out.println(variable);
                }
              };
            }
          }
        }
        """);
    });
  }

  @Test
  public void testCompletionWithBrokenClass() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("UICallback.java", """
        public <caret>interface UICallback {
        }
        """);
      LookupElement[] elements = myFixture.completeBasic();
      for (LookupElement element : elements) {
        if (!element.getLookupString().equals("UICallback")) {
          continue;
        }
        completion.get().selectItem(element);
        break;
      }

      myFixture.checkResult("""
        public UICallbackinterface UICallback {
        }
        """);
    });
  }

  @Test
  public void testNoSuggestionsAfterDotAtClassLevel() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doAntiTest()); }
  @Test
  public void testNoSuggestionsAfterDotInParameter() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doAntiTest()); }

  @Test
  public void testNoSuggestionsAfterDotWithLetter() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      String path = "/" + myFixture.getTestName(false) + ".java";
      myFixture.configureFromExistingVirtualFile(myFixture.copyFileToProject(path, StringUtil.getShortName(path, '/')));
      completion.get().setItems(myFixture.complete(CompletionType.BASIC, 0));
      completion.get().checkResultByFile(myFixture.getTestName(false) + ".java");
      assertThat(completion.get().getItems()).isNullOrEmpty();
      assertNull(completion.get().getLookup());
    });
  }

  @Test
  public void testSuggestionsAfterDotAtClassLevel() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByTestName();
      myFixture.completeBasic();
      assertTrue(myFixture.getLookupElementStrings().contains("A"));
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testAmbiguousCallLeastUpperBound() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByTestName();
      myFixture.completeBasic();
      myFixture.checkResultByFile(myFixture.getTestName(false) + "_after.java");
    });
  }

  @Test
  public void testDotInsideConstructor() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByTestName();
      myFixture.completeBasic();
      myFixture.checkResultByFile(myFixture.getTestName(false) + "_after.java");
    });
  }
}
