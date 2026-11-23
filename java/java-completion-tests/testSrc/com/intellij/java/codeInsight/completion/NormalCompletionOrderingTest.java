// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.completion;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.JavaPsiClassReferenceElement;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.codeInsight.template.impl.LiveTemplateCompletionContributor;
import com.intellij.ide.ui.UISettings;
import com.intellij.internal.DumpLookupElementWeights;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.platform.testFramework.junit5.codeInsight.indexing.IndexingModeTemplate;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.NeedsIndex;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.ui.JBColor;
import com.intellij.util.containers.ContainerUtil;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.intellij.codeInsight.completion.LightCompletionFixtureKt.lightCompletionProjectFixture;
import static com.intellij.java.codeInsight.completion.CompletionSortingFixtureKt.completionSortingFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
@IndexingModeTemplate
public class NormalCompletionOrderingTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightCompletionProjectFixture();
  private final TestFixture<CompletionSortingFixture> completion =
    completionSortingFixture(projectFixture, CompletionType.BASIC, JavaTestUtil.getJavaTestDataPath() + BASE_PATH);

  private static final String BASE_PATH = "/codeInsight/completion/normalSorting";

  @Test
  public void testDontPreferRecursiveMethod() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "registrar", "register"));
  }

  @Test
  public void testDontPreferRecursiveMethod2() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "return", "register"));
  }

  @Test
  public void testDelegatingConstructorCall() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "element"));
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testPreferAnnotationMethods() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "name", "value", "String", "Foo", "Anno"));
  }

  @Test
  public void testPreferSuperMethods() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "foo", "bar"));
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testSubstringVsSubSequence() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "substring", "substring", "subSequence"));
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testReturnF() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "false", "float", "finalize"));
  }

  @Test
  public void testPreferDefaultTypeToExpected() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "getName", "getNameIdentifier"));
  }

  @Test
  @NeedsIndex.SmartMode(reason = "Smart completion in dumb mode is not supported for html")
  public void testShorterPrefixesGoFirst() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().invokeCompletion(myFixture.getTestName(false) + ".html");
      completion.get().assertPreferredItems(0, "p", "param", "pre");
      completion.get().incUseCount(2);
      completion.get().assertPreferredItems(0, "p", "pre", "param");
    });
  }

  @Test
  public void testUppercaseMatters2() {
    CodeInsightSettings.getInstance().setCompletionCaseSensitive(CodeInsightSettings.ALL);
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "classLoader", "classLoader2"));
  }

  @Test
  public void testShorterShouldBePreselected() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "foo", "fooLongButOfDefaultType"));
  }

  @Test
  public void testGenericMethodsWithBoundParametersAreStillBetterThanClassLiteral() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "getService", "getService", "class"));
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testGenericityDoesNotMatterWhenNoTypeIsExpected() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "generic", "nonGeneric", "clone", "equals"));
  }

  @Test
  public void testClassStaticMembersInVoidContext() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "booleanMethod", "voidMethod", "AN_OBJECT", "BOOLEAN", "class"));
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testPreferClassLiteralWhenClassIsExpected() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "class"));
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testJComponentInstanceMembers() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "getAccessibleContext", "getUI", "getUIClassID"));
  }

  @Test
  public void testClassStaticMembersInBooleanContext() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      final String path = myFixture.getTestName(false) + ".java";
      myFixture.configureByFile(path);
      myFixture.complete(CompletionType.BASIC, 2);
      completion.get().assertPreferredItems(0, "booleanMethod", "BOOLEAN", "AN_OBJECT", "class", "Inner", "voidMethod");
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary(reason = "String class used")
  public void testDispreferDeclared() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "aabbb", "aaa"));
  }

  @Test
  @NeedsIndex.Full
  public void testDispreferImpls() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.addClass("package foo; public class Xxx {}");
      configureSecondCompletion();
      completion.get().assertPreferredItems(0, "Xxx", "XxxEx", "XxxImpl", "Xxy");
    });
  }

  @Test
  @NeedsIndex.Full
  public void testPreferOwnInnerClasses() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "YyyXxx", "YyyZzz"));
  }

  @Test
  @NeedsIndex.Full
  public void testPreferTopLevelClasses() {
    EdtTestUtil.runInEdtAndWait(() -> {
      configureSecondCompletion();
      completion.get().assertPreferredItems(0, "XxxYyy", "XxzYyy");
    });
  }

  private void configureSecondCompletion() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    completion.get().configureNoCompletion(myFixture.getTestName(false) + ".java");
    myFixture.complete(CompletionType.BASIC, 2);
  }

  @Test
  @NeedsIndex.Full
  public void testImplsAfterNew() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.addClass("package foo; public interface Xxx {}");
      configureSecondCompletion();
      completion.get().assertPreferredItems(0, "XxxImpl", "Xxx");
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testAfterThrowNew() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "Exception", "RuntimeException"));
  }

  @Test
  @NeedsIndex.Full
  public void testPreferLessHumps() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.addClass("package foo; public interface XaYa {}");
      myFixture.addClass("package foo; public interface XyYa {}");
      configureSecondCompletion();
      completion.get().assertPreferredItems(0, "XaYa", "XaYaEx", "XaYaImpl", "XyYa", "XyYaXa");
    });
  }

  @Test
  public void testPreferLessParameters() {
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().checkPreferredItems(0, "foo", "foo", "foo", "fox");
      final List<LookupElement> items = completion.get().getLookup().getItems();
      assertEquals(0, ((PsiMethod)items.get(0).getObject()).getParameterList().getParametersCount());
      assertEquals(1, ((PsiMethod)items.get(1).getObject()).getParameterList().getParametersCount());
      assertEquals(2, ((PsiMethod)items.get(2).getObject()).getParameterList().getParametersCount());
    });
  }

  @Test
  @NeedsIndex.Full
  public void testStatsForClassNameInExpression() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.addClass("package foo; public interface FooBar {}");
      myFixture.addClass("package foo; public interface FooBee {}");

      completion.get().invokeCompletion(myFixture.getTestName(false) + ".java");
      configureSecondCompletion();
      completion.get().incUseCount(1);
      completion.get().assertPreferredItems(0, "FooBee", "FooBar");
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testDispreferFinalize() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "final", "finalize"));
  }

  @Test
  public void testPreferNewExpectedInner() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "Foooo.Bar", "Foooo"));
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testDeclaredMembersGoFirst() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().invokeCompletion(myFixture.getTestName(false) + ".java");
      completion.get().assertStringItems("fromThis", "overridden", "fromSuper", "equals", "hashCode", "toString", "getClass", "notify", "notifyAll", "wait",
                        "wait", "wait");
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testLocalVarsOverMethods() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "value", "isValidateRoot", "isValid", "validate", "validateTree"));
  }

  @Test
  public void testCurrentClassBest() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "XcodeProjectTemplate", "XcodeConfigurable"));
  }

  @Test
  @NeedsIndex.Full
  public void testFqnStats() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.addClass("public interface Baaaaaaar {}");
      myFixture.addClass("package boo; public interface Baaaaaaar {}");
      myFixture.addClass("package zoo; public interface Baaaaaaar {}");

      configureSecondCompletion();

      assertEquals("Baaaaaaar", ((JavaPsiClassReferenceElement)completion.get().getLookup().getItems().get(0)).getQualifiedName());
      assertEquals("boo.Baaaaaaar", ((JavaPsiClassReferenceElement)completion.get().getLookup().getItems().get(1)).getQualifiedName());
      assertEquals("zoo.Baaaaaaar", ((JavaPsiClassReferenceElement)completion.get().getLookup().getItems().get(2)).getQualifiedName());
      completion.get().incUseCount(2);

      assertEquals("Baaaaaaar", ((JavaPsiClassReferenceElement)completion.get().getLookup().getItems().get(0)).getQualifiedName());// same package
      assertEquals("zoo.Baaaaaaar", ((JavaPsiClassReferenceElement)completion.get().getLookup().getItems().get(1)).getQualifiedName());
      assertEquals("boo.Baaaaaaar", ((JavaPsiClassReferenceElement)completion.get().getLookup().getItems().get(2)).getQualifiedName());
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testSkipLifted() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "hashCodeMine", "hashCode"));
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testDispreferInnerClasses() {
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().checkPreferredItems(0);//no chosen items
      assertFalse(completion.get().getLookup().getItems().get(0).getObject() instanceof PsiClass);
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testPreferSameNamedMethods() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "foo", "boo", "doo", "hashCode"));
  }

  @Test
  public void testPreferInterfacesInImplements() {
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().checkPreferredItems(0, "XFooIntf", "XFoo", "XFooClass");
      assertEquals(JBColor.foreground(), NormalCompletionFixture.renderElement(completion.get().getLookup().getItems().get(0)).getItemTextForeground());
      assertEquals(JBColor.RED, NormalCompletionFixture.renderElement(completion.get().getLookup().getItems().get(1)).getItemTextForeground());
      assertEquals(JBColor.RED, NormalCompletionFixture.renderElement(completion.get().getLookup().getItems().get(2)).getItemTextForeground());
    });
  }

  @Test
  public void testPreferClassesInExtends() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "FooClass", "Foo_Intf"));
  }

  @Test
  public void testPreferClassStaticMembers() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "Zoo.A", "Zoo", "Zoo.B", "Zoo.C", "Zoo.D", "Zoo.E", "Zoo.F", "Zoo.G", "Zoo.H"));
  }

  @Test
  public void testPreferFinallyToFinal() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "finally", "final"));
  }

  @Test
  public void testPreferReturn() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "return", "rLocal", "rParam", "rMethod"));
  }

  @Test
  public void testPreferReturnBeforeExpression() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "return", "rLocal", "rParam", "rMethod"));
  }

  @Test
  public void testPreferReturnBeforeExpression2() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "return", "retainAll"));
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testPreferReturnInSingleStatementPlace() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "return", "registerKeyboardAction"));
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testPreferContinueInsideLoops() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "continue", "color", "computeVisibleRect"));
  }

  @Test
  public void testPreferModifiers() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "private", "protected", "public"));
  }

  @Test
  public void testPreferEnumConstants() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "MyEnum.bar", "MyEnum", "MyEnum.foo"));
  }

  @Test
  @NeedsIndex.Full
  public void testPreferExpectedEnumConstantsDespiteStats() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().checkPreferredItems(0, "const1", "const2", "constx1", "constx2");
      myFixture.getLookup().setCurrentItem(myFixture.getLookupElements()[2]);

      myFixture.type("\n);\nmethodX(cons");
      myFixture.completeBasic();
      completion.get().assertPreferredItems(0, "constx1", "constx2", "const1", "const2");
    });
  }

  @Test
  public void testPreferExpectedEnumConstantsInComparison() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().checkPreferredItems(0, "MyEnum.const1", "MyEnum", "MyEnum.const2");
      completion.get().incUseCount(myFixture.getLookupElementStrings().indexOf("String"));// select some unrelated class
      completion.get().assertPreferredItems(0, "MyEnum.const1", "MyEnum", "MyEnum.const2");
    });
  }

  @Test
  public void testPreferElse() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "else", "element"));
  }

  @Test
  public void testPreferMoreMatching() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "FooOCSomething", "FooObjectCollector"));
  }

  @Test
  public void testPreferSamePackageOverImported() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.addClass("package bar; public class Bar1 {}");
      myFixture.addClass("package bar; public class Bar2 {}");
      myFixture.addClass("package bar; public class Bar3 {}");
      myFixture.addClass("package bar; public class Bar4 {}");
      myFixture.addClass("class Bar9 {}");
      myFixture.addClass("package doo; public class Bar0 {}");

      completion.get().checkPreferredItems(0, "Bar9", "Bar1", "Bar2", "Bar3", "Bar4");
    });
  }

  @Test
  @NeedsIndex.Full
  public void testPreselectMostRelevantInTheMiddleAlpha() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    UISettings.getInstance().setSortLookupElementsLexicographically(true);

    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.addClass("package foo; public class ELXaaaaaaaaaaaaaaaaaaaa {}");
      completion.get().invokeCompletion(myFixture.getTestName(false) + ".java");
      myFixture.completeBasic();
      LookupImpl lookup = completion.get().getLookup();
      completion.get().assertPreferredItems(lookup.getList().getSelectedIndex());
      assertEquals("ELXaaaaaaaaaaaaaaaaaaaa", lookup.getItems().get(0).getLookupString());
      assertEquals("ELXEMENT_A", lookup.getCurrentItem().getLookupString());
    });
  }

  @Test
  public void testReallyAlphaSorting() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    UISettings.getInstance().setSortLookupElementsLexicographically(true);

    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().invokeCompletion(myFixture.getTestName(false) + ".java");
      assertEquals(myFixture.getLookupElementStrings().stream().sorted().toList(), myFixture.getLookupElementStrings());
    });
  }

  @Test
  @NeedsIndex.Full
  public void testAlphaSortPackages() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    UISettings.getInstance().setSortLookupElementsLexicographically(true);

    ArrayList<String> pkgs = new ArrayList<>(Arrays.asList("bar", "foo", "goo", "roo", "zoo"));
    EdtTestUtil.runInEdtAndWait(() -> {
      for (String s : pkgs) {
        myFixture.addClass("package " + s + "; public class Foox {}");
      }

      completion.get().invokeCompletion(myFixture.getTestName(false) + ".java");
      for (int i = 0; i < pkgs.size(); i++) {
        assertTrue(NormalCompletionFixture.renderElement(myFixture.getLookupElements()[i]).getTailText().contains(pkgs.get(i)));
      }
    });
  }

  @Test
  public void testAlphaSortingStartMatchesFirst() {
    UISettings.getInstance().setSortLookupElementsLexicographically(true);
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "xxbar", "xxfoo", "xxgoo", "barxx", "fooxx", "gooxx"));
  }

  @Test
  @NeedsIndex.Full
  public void testSortSameNamedVariantsByProximity() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.addClass("public class Bar {}");
      for (int i = 0; i < 10; i++) {
        myFixture.addClass("public class Bar" + i + " {}");
        myFixture.addClass("public class Bar" + i + "Colleague {}");
      }

      myFixture.addClass("package bar; public class Bar {}");
      myFixture.configureByFile(myFixture.getTestName(false) + ".java");
      myFixture.complete(CompletionType.BASIC, 2);
      completion.get().assertPreferredItems(0, "Bar", "Bar");
      List<LookupElement> items = completion.get().getLookup().getItems();
      assertEquals("Bar", ((JavaPsiClassReferenceElement)items.get(0)).getQualifiedName());
      assertEquals("bar.Bar", ((JavaPsiClassReferenceElement)items.get(1)).getQualifiedName());
    });
  }

  @Test
  public void testCaseInsensitivePrefixMatch() {
    CodeInsightSettings.getInstance().setCompletionCaseSensitive(CodeInsightSettings.NONE);
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(1, "Foo", "foo1", "foo2"));
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testPreferKeywordsToVoidMethodsInExpectedTypeContext() {
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().checkPreferredItems(0, "noo", "new", "null", "noo2", "new File", "new File", "new File", "new File", "clone", "toString", "notify",
                          "notifyAll");
    });
  }

  @Test
  public void testPreferBetterMatchingConstantToMethods() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "serial", "superExpressionInIllegalContext"));
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testPreferApplicableAnnotations() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.addClass(
        "\nimport java.lang.annotation.ElementType;\nimport java.lang.annotation.Target;\n\n@Target(ElementType.ANNOTATION_TYPE)\n@interface TtMetaAnno {}\n\n@Target(ElementType.LOCAL_VARIABLE)\n@interface TtLocalAnno {}");
      completion.get().checkPreferredItems(0, "TtMetaAnno", "TtLocalAnno");
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testPreferApplicableAnnotationsMethod() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.addClass(
        "\nimport java.lang.annotation.ElementType;\nimport java.lang.annotation.Target;\n\n@Target(ElementType.TYPE)\n@interface TxClassAnno {}\n\ninterface TxANotAnno {}\n\n@Target(ElementType.METHOD)\n@interface TxMethodAnno {}");
      completion.get().checkPreferredItems(0, "TxMethodAnno", "TxClassAnno");
      assertFalse(myFixture.getLookupElementStrings().contains("TxANotAnno"));
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testJComponentAddNewWithStats() {
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().invokeCompletion("/../smartTypeSorting/JComponentAddNew.java");
      completion.get().assertPreferredItems(0, "FooBean3", "JComponent", "Component");
      completion.get().incUseCount(2);//Component
      completion.get().assertPreferredItems(0, "Component", "FooBean3", "JComponent");
    });
  }

  @Test
  public void testDispreferReturnBeforeStatement() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "reaction", "rezet", "return"));
  }

  @Test
  public void testDispreferReturnInConstructor() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "reaction", "rezet", "return"));
  }

  @Test
  public void testDispreferReturnInVoidMethodTopLevel() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "reaction", "rezet", "return"));
  }

  @Test
  public void testDispreferReturnAsLoopBody() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "returnMethod", "return"));
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testDispreferReturnInVoidLambda() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "reaction", "rezet", "return"));
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testDoNotPreferGetClass() {
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().checkPreferredItems(0, "get", "getClass");
      completion.get().incUseCount(1);
      completion.get().assertPreferredItems(0, "getClass", "get");
      completion.get().incUseCount(1);
      completion.get().assertPreferredItems(0, "get", "getClass");
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testEqualsStats() {
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().checkPreferredItems(0, "equals", "equalsIgnoreCase");
      completion.get().incUseCount(1);
      completion.get().assertPreferredItems(0, "equalsIgnoreCase", "equals");
      completion.get().incUseCount(1);
      completion.get().checkPreferredItems(0, "equals", "equalsIgnoreCase");
    });
  }

  @Test
  public void testPreferClassToItsConstants() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "MyCalendar.FIELD_COUNT", "MyCalendar", "MyCalendar.AM"));
  }

  @Test
  @NeedsIndex.Full
  public void testPreferLocalsToStaticsInSecondCompletion() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.addClass("public class FooZoo { public static void fooBar() {}  }");
      myFixture.addClass("public class fooAClass {}");
      completion.get().configureNoCompletion(myFixture.getTestName(false) + ".java");
      myFixture.complete(CompletionType.BASIC, 2);
      completion.get().assertPreferredItems(0, "fooy", "foox", "fooAClass", "fooBar");
    });
  }

  @Test
  public void testChangePreselectionOnSecondInvocation() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureNoCompletion(myFixture.getTestName(false) + ".java");
      myFixture.complete(CompletionType.BASIC);
      completion.get().assertPreferredItems(0, "fooZooGoo", "fooZooImpl");
      myFixture.complete(CompletionType.BASIC);
      completion.get().assertPreferredItems(0, "fooZoo", "fooZooGoo", "fooZooImpl");
    });
  }

  @Test
  public void testUnderscoresDontMakeMatchMiddle() {
    CodeInsightSettings.getInstance().setCompletionCaseSensitive(CodeInsightSettings.NONE);
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "fooBar", "_fooBar", "FooBar"));
  }

  @Test
  public void testDispreferUnderscoredCaseMatch() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "fooBar", "__foo_bar"));
  }

  @Test
  public void testStatisticsMattersOnNextCompletion() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByFile(myFixture.getTestName(false) + ".java");
      myFixture.completeBasic();
      assertNotNull(completion.get().getLookup());
      assertNotEquals("JComponent", completion.get().getLookup().getCurrentItem().getLookupString());
      myFixture.type("ponent c;\nJCom");
      myFixture.completeBasic();
      assertNotNull(completion.get().getLookup());
      assertEquals("JComponent", completion.get().getLookup().getCurrentItem().getLookupString());
    });
  }

  @Test
  public void testPreferFieldToMethod() {
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().checkPreferredItems(0, "size", "size");
      assertTrue(completion.get().getLookup().getItems().get(0).getObject() instanceof PsiField);
      assertTrue(completion.get().getLookup().getItems().get(1).getObject() instanceof PsiMethod);
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testPreselectLastChosen() {
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().checkPreferredItems(0, "add", "addAll");
      for (int i = 0; i <= 10; i++) {
        completion.get().incUseCount(1);
      }

      completion.get().assertPreferredItems(0, "addAll", "add");
      completion.get().incUseCount(1);
      completion.get().assertPreferredItems(0, "add", "addAll");
    });
  }

  @Test
  public void testDontPreselectLastChosenWithUnrelatedPrefix() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().invokeCompletion(myFixture.getTestName(false) + ".java");
      myFixture.type(";\nmycl");
      myFixture.completeBasic();
      completion.get().assertPreferredItems(0, "myClass", "myExtendsClause");
    });
  }

  @Test
  public void testCommonPrefixMoreImportantThanExpectedType() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "myStep", "myCurrentStep"));
  }

  @Test
  public void testStatsMoreImportantThanExpectedType() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().invokeCompletion(myFixture.getTestName(false) + ".java");
      completion.get().assertPreferredItems(0, "getNumber", "getNumProvider");
      completion.get().getLookup().setCurrentItem(completion.get().getLookup().getItems().get(1));
      myFixture.type("\n);\ntest(getnu");
      myFixture.completeBasic();
      completion.get().assertPreferredItems(0, "getNumProvider", "getNumber");
    });
  }

  @Test
  public void testIfConditionStats() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().invokeCompletion(myFixture.getTestName(false) + ".java");
      myFixture.completeBasic();
      myFixture.type("cont");
      completion.get().assertPreferredItems(0, "containsAll", "contains");
      myFixture.getLookup().setCurrentItem(myFixture.getLookupElements()[1]);
      myFixture.type("\nc)) {\nif (set.");
      myFixture.completeBasic();
      myFixture.type("cont");
      completion.get().assertPreferredItems(0, "contains", "containsAll");
    });
  }

  @Test
  public void testDeepestSuperMethodStats() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().invokeCompletion(myFixture.getTestName(false) + ".java");
      completion.get().assertPreferredItems(0, "addX", "addY");
      myFixture.type("y\n;set1.ad");

      myFixture.completeBasic();
      completion.get().assertPreferredItems(0, "addY", "addX");
      myFixture.type("x\n;set2.ad");

      myFixture.completeBasic();
      completion.get().assertPreferredItems(0, "addX", "addY");
    });
  }

  @Test
  public void testCommonPrefixMoreImportantThanKind() {
    CodeInsightSettings.getInstance().setCompletionCaseSensitive(CodeInsightSettings.NONE);
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "PsiElement", "psiElement"));
  }

  @Test
  public void testNoExpectedTypeInStringConcatenation() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "vx"));
  }

  @Test
  public void testLocalVarsOverStats() {
    CodeInsightSettings.getInstance().setCompletionCaseSensitive(CodeInsightSettings.NONE);
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().checkPreferredItems(0, "psiElement", "PsiElement");
      completion.get().incUseCount(1);
      completion.get().assertPreferredItems(0, "psiElement", "PsiElement");
    });
  }

  @Test
  public void testHonorRecency() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().invokeCompletion(myFixture.getTestName(false) + ".java");
      myFixture.completeBasic();
      myFixture.type("setou\nz.");

      myFixture.completeBasic();
      myFixture.type("set");
      completion.get().assertPreferredItems(0, "setOurText", "setText");
      myFixture.type("te");
      completion.get().assertPreferredItems(0, "setText", "setOurText");
      myFixture.type("\nz.");

      myFixture.completeBasic();
      myFixture.type("set");
      completion.get().assertPreferredItems(0, "setText", "setOurText");
    });
  }

  @Test
  public void testPreferString() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "String", "System", "Short"));
  }

  @Test
  public void testAnnotationEnum() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "MyEnum.BAR", "MyEnum", "MyEnum.FOO"));
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testPreferClassesOfExpectedClassType() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.addClass("class XException extends Exception {}");
      completion.get().checkPreferredItems(0, "XException", "XException.class", "XClass", "XIntf");
    });
  }

  @Test
  public void testNoNumberValueOf() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "value"));
  }

  @Test
  public void testNoBooleansInMultiplication() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "fact"));
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testPreferAnnotationsToInterfaceKeyword() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "Deprecated", "Override", "SuppressWarnings"));
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testPreferThrownExceptionsInCatch() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "final", "FileNotFoundException", "File"));
  }

  @Test
  public void testHonorFirstLetterCase() {
    CodeInsightSettings.getInstance().setCompletionCaseSensitive(CodeInsightSettings.NONE);
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "posIdMap", "PImageDecoder", "PNGImageDecoder"));
  }

  @Test
  @NeedsIndex.Full
  public void testGlobalStaticMemberStats() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureNoCompletion(myFixture.getTestName(false) + ".java");
      myFixture.complete(CompletionType.BASIC, 2);
      completion.get().assertPreferredItems(0, "newLinkedSet0", "newLinkedSet1", "newLinkedSet2");
      CompletionSortingFixture.imitateItemSelection(completion.get().getLookup(), 1);
      myFixture.complete(CompletionType.BASIC, 2);
      completion.get().assertPreferredItems(0, "newLinkedSet1", "newLinkedSet0", "newLinkedSet2");
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testStaticMemberTypes() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "newMap", "newList"));
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testNoStatsInSuperInvocation() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().checkPreferredItems(0, "put", "putAll");

      myFixture.type("\n");
      assertTrue(myFixture.getEditor().getDocument().getText().contains("put"));

      myFixture.type(");\nsuper.");
      myFixture.completeBasic();

      completion.get().assertPreferredItems(0, "get");
    });
  }

  @Test
  public void testLiveTemplateOrdering() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      LiveTemplateCompletionContributor.setShowTemplatesInTests(true, myFixture.getTestRootDisposable());
      completion.get().checkPreferredItems(0, "return");
      assertTrue(ContainerUtil.exists(completion.get().getLookup().getItems(), it -> it.getLookupString().equals("ritar")));
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testPreferLocalToExpectedTypedMethod() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "event", "equals"));
  }

  @Test
  public void testDispreferJustUsedEnumConstantsInSwitch() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().checkPreferredItems(0, "BAR", "FOO", "GOO");
      myFixture.type("\nbreak;\ncase ");

      LookupElement[] items = myFixture.completeBasic();
      completion.get().assertPreferredItems(0, "FOO", "GOO", "BAR");

      assertEquals(JBColor.foreground(), NormalCompletionFixture.renderElement(items[0]).getItemTextForeground());
      assertEquals(JBColor.RED, NormalCompletionFixture.renderElement(ContainerUtil.find(items, it -> it.getLookupString().equals("BAR")))
                     .getItemTextForeground());
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testPreferValueTypesReturnedFromMethod() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().checkPreferredItems(0, "StringBuffer", "String", "Serializable", "SomeInterface", "SomeInterface", "SomeOtherClass");
      assertEquals("SomeInterface<String>", NormalCompletionFixture.renderElement(myFixture.getLookupElements()[3]).getItemText());
      assertEquals("SomeInterface", NormalCompletionFixture.renderElement(myFixture.getLookupElements()[4]).getItemText());
    });
  }

  @Test
  @NeedsIndex.Full
  public void testPreferCastTypesHavingSpecifiedMethod() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "MainClass1", "MainClass2", "Maa"));
  }

  @Test
  public void testNaturalSorting() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "fun1", "fun2", "fun10"));
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testPreferVarsHavingReferencedMember() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "xzMap", "xaString"));
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testPreferCollectionsStaticOfExpectedType() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "unmodifiableList", "unmodifiableCollection"));
  }

  @Test
  @NeedsIndex.SmartMode(reason = "isEffectivelyDeprecated needs smart mode")
  public void testDispreferDeprecatedMethodWithUnresolvedQualifier() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.addClass("package foo; public class Assert { public static void assertTrue() {} }");
      myFixture.addClass(
        "package bar; @Deprecated public class Assert { public static void assertTrue() {} public static void assertTrue2() {} }");
      completion.get().checkPreferredItems(0, "Assert.assertTrue", "Assert.assertTrue", "Assert.assertTrue2");

      LookupElementPresentation p = NormalCompletionFixture.renderElement(myFixture.getLookup().getItems().get(0));
      assertTrue(p.getTailText().contains("foo"));
      assertFalse(p.isStrikeout());

      p = NormalCompletionFixture.renderElement(myFixture.getLookup().getItems().get(1));
      assertTrue(p.getTailText().contains("bar"));
      assertTrue(p.isStrikeout());
    });
  }

  @Test
  public void testPreferClassKeywordWhenExpectedClassType() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "class"));
  }

  @Test
  public void testPreferBooleanKeywordsWhenExpectedBoolean() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "false", "factory"));
  }

  @Test
  public void testPreferBooleanKeywordsWhenExpectedBoolean2() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "false", "factory"));
  }

  @Test
  @NeedsIndex.Full
  public void testPreferExplicitlyImportedStaticMembers() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.addClass(
        "\nclass ContainerUtilRt {\n  static void newHashSet();\n  static void newHashSet2();\n}\nclass ContainerUtil extends ContainerUtilRt {\n  static void newHashSet();\n  static void newHashSet3();\n}\n");
      completion.get().checkPreferredItems(0, "newHashSet", "newHashSet", "newHashSet3", "newHashSet2");
      assertEquals("ContainerUtil", ((PsiMethod)myFixture.getLookupElements()[0].getPsiElement()).getContainingClass().getName());
    });
  }

  @Test
  public void testPreferCatchAndFinallyAfterTry() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "catch", "finally"));
  }

  @Test
  @NeedsIndex.Full
  public void testPreselectClosestExactPrefixItem() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    UISettings.getInstance().setSortLookupElementsLexicographically(true);
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.addClass("package pack1; public class SameNamed {}");
      myFixture.addClass("package pack2; public class SameNamed {}");
      completion.get().checkPreferredItems(1, "SameNamed", "SameNamed");
      assertTrue(NormalCompletionFixture.renderElement(myFixture.getLookupElements()[0]).getTailText().contains("pack1"));
      assertTrue(NormalCompletionFixture.renderElement(myFixture.getLookupElements()[1]).getTailText().contains("pack2"));
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testPreferExpectedMethodTypeArg() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().checkPreferredItems(0, "String", "Usage");

      int typeArgOffset = myFixture.getEditor().getCaretModel().getOffset();

      // increase usage stats of ArrayList
      LookupManager.getInstance(myFixture.getProject()).hideActiveLookup();
      myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_LINE_END);
      myFixture.type("\nArrayLi");
      myFixture.completeBasic();
      myFixture.type(" l");
      myFixture.completeBasic();
      myFixture.type("\n;");
      assertTrue(myFixture.getEditor().getDocument().getText().contains("ArrayList list;"));

      // check String is still preferred
      myFixture.getEditor().getCaretModel().moveToOffset(typeArgOffset);
      myFixture.completeBasic();
      myFixture.assertPreferredCompletionItems(0, "String", "Usage");
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testMethodStatisticsPerQualifierType() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().checkPreferredItems(0, "charAt");
      myFixture.type("eq\n);\n");
      assertTrue(myFixture.getEditor().getDocument().getText().contains("equals();\n"));

      myFixture.type("this.");
      myFixture.completeBasic();
      completion.get().assertPreferredItems(0, "someMethod");
    });
  }

  @Test
  @NeedsIndex.Full
  public void testPreferImportedClassesAmongstSameNamed() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.addClass("package foo; public class String {}");

      // use foo.String
      myFixture.configureByText("a.java", "class Foo { Stri<caret> }");
      myFixture.completeBasic();
      myFixture.getLookup().setCurrentItem(ContainerUtil.find(myFixture.getLookupElements(), it -> it.getLookupString().equals("String") &&
                                                                                                   NormalCompletionFixture.renderElement(it)
                                                                                                     .getTailText().contains("foo")));
      myFixture.type("\n");
      myFixture.checkResult("import foo.String;\n\nclass Foo { String<caret>\n}");

      // assert non-imported String is not preselected when completing in another file
      myFixture.configureByText("b.java", "class Bar { Stri<caret> }");
      myFixture.completeBasic();
      myFixture.assertPreferredCompletionItems(0, "String", "String");
      assertTrue(NormalCompletionFixture.renderElement(myFixture.getLookupElements()[0]).getTailText().contains("java.lang"));
    });
  }

  @Test
  public void testClassNameStatisticsDoesntDependOnExpectedType() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().checkPreferredItems(0, "ConflictsUtil", "ContainerUtil");
      myFixture.getLookup().setCurrentItem(myFixture.getLookupElements()[1]);
      myFixture.type("\n.foo();\nlong l = ConUt");
      myFixture.completeBasic();

      completion.get().assertPreferredItems(0, "ContainerUtil", "ConflictsUtil");
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testPreferListAddWithoutIndex() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().checkPreferredItems(0, "add", "add", "addAll", "addAll");
      assertTrue(NormalCompletionFixture.renderElement(myFixture.getLookupElements()[1]).getTailText().contains("int index"));
      assertTrue(NormalCompletionFixture.renderElement(myFixture.getLookupElements()[3]).getTailText().contains("int index"));
    });
  }

  @Test
  @NeedsIndex.Full
  public void testPreferExpectedTypeConstantOverSameNamedClass() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.addClass("package another; public class JSON {}");
      completion.get().checkPreferredItems(0, "Point.JSON", "JSON");
    });
  }

  @Test
  public void testPreferExpectedEnumConstantInAnnotationAttribute() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().checkPreferredItems(0, "MyEnum.bar", "MyEnum", "MyEnum.foo");
      int unrelatedItem = ContainerUtil.indexOf(myFixture.getLookupElementStrings(), it -> it.contains("Throwable"));
      completion.get().incUseCount(unrelatedItem);
      //nothing should change
      completion.get().assertPreferredItems(0, "MyEnum.bar", "MyEnum", "MyEnum.foo");
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testPreferExpectedTypeFieldOverUnexpectedLocalVariables() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "field", "local"));
  }

  @Test
  public void testPreferConflictingFieldAfterThis() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "text"));
  }

  @Test
  public void testDoNotPreselectShorterDeprecatedClasses() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(1, "XLong", "XLonger"));
  }

  @Test
  public void testSignatureBeforeStats() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java",
                                "\nclass Foo { \n  void foo(int a, int b) {\n    Stri<caret>\n    bar();\n  }\n  \n  void bar(int a, int b) {} \n}");
      myFixture.completeBasic();
      myFixture.assertPreferredCompletionItems(0, "String");
      myFixture.type("\n");// increase String statistics
      myFixture.type("foo;\nbar(");
      myFixture.completeBasic();
      myFixture.assertPreferredCompletionItems(0, "a", "b", "a, b");
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void test_selecting_static_field_after_static_method() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java", "class Foo { { System.<caret> } }");
      myFixture.completeBasic();
      myFixture.type("ex\n2);\n");// select 'exit'

      myFixture.type("System.");
      myFixture.completeBasic();
      myFixture.assertPreferredCompletionItems(0, "exit");
      myFixture.type("ou\n;\n");// select 'out'

      myFixture.type("System.");
      myFixture.completeBasic();
      myFixture.assertPreferredCompletionItems(0, "out", "exit");
    });
  }

  @Test
  @NeedsIndex.SmartMode(reason = "JavaGenerateMemberCompletionContributor.fillCompletionVariants works in smart mode only (for getters)")
  public void testPreferTypeToGeneratedMethod() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "SomeClass", "public SomeClass getZoo"));
  }

  @Test
  @NeedsIndex.SmartMode(reason = "JavaGenerateMemberCompletionContributor.fillCompletionVariants works in smart mode only (for getters and equals())")
  public void testPreferPrimitiveTypeToGeneratedMethod() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "boolean", "public boolean isZoo", "public boolean equals"));
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testPreferExceptionsInCatch() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java", "class Foo { { Enu<caret> } }");
      myFixture.completeBasic();
      myFixture.type("m\n");// select 'Enum'
      myFixture.type("; try {} catch(E");
      myFixture.completeBasic();
      myFixture.assertPreferredCompletionItems(0, "Exception", "Error");
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testPreferExceptionsInThrowsList() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "IllegalStateException", "IllegalAccessException", "IllegalArgumentException"));
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testPreferExceptionsInJavadocThrows() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "IllegalArgumentException", "IllegalAccessException", "IllegalStateException"));
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testPreferExpectedTypeArguments() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "BlaOperation"));
  }

  @Test
  public void testPreferFinalBeforeVariable() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "final", "find1"));
  }

  @Test
  @NeedsIndex.SmartMode(reason = "AbstractExpectedTypeSkipper works in smart mode only")
  public void testDispreferMultiMethodInterfaceAfterNew() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(1, "Intf", "IntfImpl"));
  }

  @Test
  public void testDispreferAlreadyCalledBuilderMethods() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "addInt", "append", "c", "d", "mayCallManyTimes", "putLong"));
  }

  @Test
  public void testSelectAbstractClassWithNoAbstractMethods() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "AbstractListener", "Listener"));
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testPreferPrintln() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java", "class Foo { { System.out.pri<caret>x } }");
      myFixture.completeBasic();
      myFixture.assertPreferredCompletionItems(0, "println", "print");
      myFixture.type("\t");
      myFixture.checkResult("class Foo { { System.out.println(<caret>); } }");
    });
  }

  @Test
  public void testPreferLocalArrayVariableToItsChains() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "arrayVariable"));
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void test_void_method_in_nonvoid_context() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java", "class X { String getName() {return \"\";} void test() {System.out.println(this.n<caret>);}}");
      myFixture.completeBasic();
      completion.get().assertStringItems("getName", "clone", "toString", "notify", "notifyAll", "finalize");
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void test_void_context_replace() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java", "class X { String getName() {return \"\";} void test() {this.n<caret>otify();}}");
      myFixture.completeBasic();
      completion.get().assertStringItems("notify", "notifyAll", "getName", "clone", "toString", "finalize");
    });
  }

  @Test
  @NeedsIndex.Full
  public void test_import_nested_classes_order() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.addClass("public class Cls { public static class TestImport {}}");
      myFixture.addClass("public class Cls2 { public static class TestImport {}}");
      myFixture.addClass("package demo;public class TestImport {}");
      myFixture.addClass("public class TestImport {}");
      myFixture.configureByText("a.java", "import demo.*;import static Cls2.*; class Test {TestIm<caret>}");
      myFixture.completeBasic();
      LookupElement[] elements = myFixture.getLookupElements();
      assertEquals(4, elements.length);
      List<String> weights = DumpLookupElementWeights.getLookupElementWeights(completion.get().getLookup(), false);
      assertEquals("TestImport", elements[0].as(JavaPsiClassReferenceElement.class).getQualifiedName());
      assertTrue(weights.get(0).contains("explicitlyImported=CLASS_DECLARED_IN_SAME_PACKAGE_TOP_LEVEL,"));// same package
      assertEquals("demo.TestImport", elements[1].as(JavaPsiClassReferenceElement.class).getQualifiedName());
      assertTrue(weights.get(1).contains("explicitlyImported=CLASS_ON_DEMAND_TOP_LEVEL,"));// on-demand import
      assertEquals("Cls2.TestImport", elements[2].as(JavaPsiClassReferenceElement.class).getQualifiedName());
      assertTrue(weights.get(2).contains("explicitlyImported=CLASS_ON_DEMAND_NESTED,"));// same package, nested class imported
      assertEquals("Cls.TestImport", elements[3].as(JavaPsiClassReferenceElement.class).getQualifiedName());
      assertTrue(weights.get(3)
                   .contains("explicitlyImported=CLASS_DECLARED_IN_SAME_PACKAGE_NESTED,"));// same package but nested class not imported
    });
  }

  @Test
  @NeedsIndex.SmartMode(reason = "Ordering requires smart mode")
  public void test_discourage_experimental() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.addClass("package org.jetbrains.annotations;public class ApiStatus{public @interface Experimental {}}");
      myFixture.addClass("class Cls {@org.jetbrains.annotations.ApiStatus.Experimental public void methodA() {} public void methodB() {}}");
      myFixture.configureByText("a.java", "class Test {void t(Cls cls) {cls.me<caret>}}");
      myFixture.completeBasic();
      assertEquals(List.of("methodB", "methodA"), myFixture.getLookupElementStrings());
    });
  }

  @Test
  @NeedsIndex.Full
  public void testCompletionRuleCaseOrdering() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_21, () -> {
        myFixture.configureByFile(myFixture.getTestName(false) + ".java");
        myFixture.completeBasic();
        completion.get().assertPreferredItems(0, "casecase", "case", "case A", "case B", "case null", "case null, default");
      });
    });
  }

  @Test
  @NeedsIndex.Full
  public void testCompletionCaseOrdering() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_21, () -> {
        myFixture.configureByFile(myFixture.getTestName(false) + ".java");
        myFixture.completeBasic();
        completion.get().assertPreferredItems(0, "casecase", "case", "case null", "case null, default", "case A", "case B");
      });
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testPreferLocalOverTypeMatching() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("Test.java", """
        import java.util.*;
      
        public class Test {
          void test(Set<String> additional, Set<String> expected) {
            if (add<caret>)
          }
        }
        """);
      myFixture.completeBasic();
      completion.get().assertPreferredItems(0, "additional", "additional.add", "additional.addAll");
    });
  }
  
}
