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
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.testFramework.NeedsIndex;
import com.intellij.ui.JBColor;
import com.intellij.util.containers.ContainerUtil;
import junit.framework.TestCase;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertNotEquals;

public class NormalCompletionOrderingTest extends CompletionSortingTestCase {
  public NormalCompletionOrderingTest() {
    super(CompletionType.BASIC);
  }

  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + BASE_PATH;
  }

  public void testDontPreferRecursiveMethod() {
    checkPreferredItems(0, "registrar", "register");
  }

  public void testDontPreferRecursiveMethod2() {
    checkPreferredItems(0, "return", "register");
  }

  public void testDelegatingConstructorCall() {
    checkPreferredItems(0, "element");
  }

  @NeedsIndex.ForStandardLibrary
  public void testPreferAnnotationMethods() {
    checkPreferredItems(0, "name", "value", "String", "Foo", "Anno");
  }

  public void testPreferSuperMethods() {
    checkPreferredItems(0, "foo", "bar");
  }

  @NeedsIndex.ForStandardLibrary
  public void testSubstringVsSubSequence() {
    checkPreferredItems(0, "substring", "substring", "subSequence");
  }

  @NeedsIndex.ForStandardLibrary
  public void testReturnF() {
    checkPreferredItems(0, "false", "float", "finalize");
  }

  public void testPreferDefaultTypeToExpected() {
    checkPreferredItems(0, "getName", "getNameIdentifier");
  }

  @NeedsIndex.SmartMode(reason = "Smart completion in dumb mode is not supported for html")
  public void testShorterPrefixesGoFirst() {
    invokeCompletion(getTestName(false) + ".html");
    assertPreferredItems(0, "p", "param", "pre");
    incUseCount(2);
    assertPreferredItems(0, "p", "pre", "param");
  }

  public void testUppercaseMatters2() {
    CodeInsightSettings.getInstance().setCompletionCaseSensitive(CodeInsightSettings.ALL);
    checkPreferredItems(0, "classLoader", "classLoader2");
  }

  public void testShorterShouldBePreselected() {
    checkPreferredItems(0, "foo", "fooLongButOfDefaultType");
  }

  public void testGenericMethodsWithBoundParametersAreStillBetterThanClassLiteral() {
    checkPreferredItems(0, "getService", "getService", "class");
  }

  @NeedsIndex.ForStandardLibrary
  public void testGenericityDoesNotMatterWhenNoTypeIsExpected() {
    checkPreferredItems(0, "generic", "nonGeneric", "clone", "equals");
  }

  public void testClassStaticMembersInVoidContext() {
    checkPreferredItems(0, "booleanMethod", "voidMethod", "AN_OBJECT", "BOOLEAN", "class");
  }

  @NeedsIndex.ForStandardLibrary
  public void testPreferClassLiteralWhenClassIsExpected() {
    checkPreferredItems(0, "class");
  }

  @NeedsIndex.ForStandardLibrary
  public void testJComponentInstanceMembers() {
    checkPreferredItems(0, "getAccessibleContext", "getUI", "getUIClassID");
  }

  public void testClassStaticMembersInBooleanContext() {
    final String path = getTestName(false) + ".java";
    myFixture.configureByFile(path);
    myFixture.complete(CompletionType.BASIC, 2);
    assertPreferredItems(0, "booleanMethod", "BOOLEAN", "AN_OBJECT", "class", "Inner", "voidMethod");
  }

  public void testDispreferDeclared() {
    checkPreferredItems(0, "aabbb", "aaa");
  }

  @NeedsIndex.Full
  public void testDispreferImpls() {
    myFixture.addClass("package foo; public class Xxx {}");
    configureSecondCompletion();
    assertPreferredItems(0, "Xxx", "XxxEx", "XxxImpl", "Xxy");
  }

  @NeedsIndex.Full
  public void testPreferOwnInnerClasses() {
    checkPreferredItems(0, "YyyXxx", "YyyZzz");
  }

  @NeedsIndex.Full
  public void testPreferTopLevelClasses() {
    configureSecondCompletion();
    assertPreferredItems(0, "XxxYyy", "XxzYyy");
  }

  private void configureSecondCompletion() {
    configureNoCompletion(getTestName(false) + ".java");
    myFixture.complete(CompletionType.BASIC, 2);
  }

  @NeedsIndex.Full
  public void testImplsAfterNew() {
    myFixture.addClass("package foo; public interface Xxx {}");
    configureSecondCompletion();
    assertPreferredItems(0, "XxxImpl", "Xxx");
  }

  @NeedsIndex.ForStandardLibrary
  public void testAfterThrowNew() {
    checkPreferredItems(0, "Exception", "RuntimeException");
  }

  @NeedsIndex.Full
  public void testPreferLessHumps() {
    myFixture.addClass("package foo; public interface XaYa {}");
    myFixture.addClass("package foo; public interface XyYa {}");
    configureSecondCompletion();
    assertPreferredItems(0, "XaYa", "XaYaEx", "XaYaImpl", "XyYa", "XyYaXa");
  }

  public void testPreferLessParameters() {
    checkPreferredItems(0, "foo", "foo", "foo", "fox");
    final List<LookupElement> items = getLookup().getItems();
    TestCase.assertEquals(0, ((PsiMethod)items.get(0).getObject()).getParameterList().getParametersCount());
    TestCase.assertEquals(1, ((PsiMethod)items.get(1).getObject()).getParameterList().getParametersCount());
    TestCase.assertEquals(2, ((PsiMethod)items.get(2).getObject()).getParameterList().getParametersCount());
  }

  @NeedsIndex.Full
  public void testStatsForClassNameInExpression() {
    myFixture.addClass("package foo; public interface FooBar {}");
    myFixture.addClass("package foo; public interface FooBee {}");

    invokeCompletion(getTestName(false) + ".java");
    configureSecondCompletion();
    incUseCount(1);
    assertPreferredItems(0, "FooBee", "FooBar");
  }

  @NeedsIndex.ForStandardLibrary
  public void testDispreferFinalize() {
    checkPreferredItems(0, "final", "finalize");
  }

  public void testPreferNewExpectedInner() {
    checkPreferredItems(0, "Foooo.Bar", "Foooo");
  }

  @NeedsIndex.ForStandardLibrary
  public void testDeclaredMembersGoFirst() {
    invokeCompletion(getTestName(false) + ".java");
    assertStringItems("fromThis", "overridden", "fromSuper", "equals", "hashCode", "toString", "getClass", "notify", "notifyAll", "wait",
                      "wait", "wait");
  }

  @NeedsIndex.ForStandardLibrary
  public void testLocalVarsOverMethods() {
    checkPreferredItems(0, "value", "isValidateRoot", "isValid", "validate", "validateTree");
  }

  public void testCurrentClassBest() {
    checkPreferredItems(0, "XcodeProjectTemplate", "XcodeConfigurable");
  }

  @NeedsIndex.Full
  public void testFqnStats() {
    myFixture.addClass("public interface Baaaaaaar {}");
    myFixture.addClass("package boo; public interface Baaaaaaar {}");
    myFixture.addClass("package zoo; public interface Baaaaaaar {}");

    configureSecondCompletion();

    TestCase.assertEquals("Baaaaaaar", ((JavaPsiClassReferenceElement)getLookup().getItems().get(0)).getQualifiedName());
    TestCase.assertEquals("boo.Baaaaaaar", ((JavaPsiClassReferenceElement)getLookup().getItems().get(1)).getQualifiedName());
    TestCase.assertEquals("zoo.Baaaaaaar", ((JavaPsiClassReferenceElement)getLookup().getItems().get(2)).getQualifiedName());
    incUseCount(2);

    TestCase.assertEquals("Baaaaaaar", ((JavaPsiClassReferenceElement)getLookup().getItems().get(0)).getQualifiedName());// same package
    TestCase.assertEquals("zoo.Baaaaaaar", ((JavaPsiClassReferenceElement)getLookup().getItems().get(1)).getQualifiedName());
    TestCase.assertEquals("boo.Baaaaaaar", ((JavaPsiClassReferenceElement)getLookup().getItems().get(2)).getQualifiedName());
  }

  @NeedsIndex.ForStandardLibrary
  public void testSkipLifted() {
    checkPreferredItems(0, "hashCodeMine", "hashCode");
  }

  @NeedsIndex.ForStandardLibrary
  public void testDispreferInnerClasses() {
    checkPreferredItems(0);//no chosen items
    TestCase.assertFalse(getLookup().getItems().get(0).getObject() instanceof PsiClass);
  }

  @NeedsIndex.ForStandardLibrary
  public void testPreferSameNamedMethods() {
    checkPreferredItems(0, "foo", "boo", "doo", "hashCode");
  }

  public void testPreferInterfacesInImplements() {
    checkPreferredItems(0, "XFooIntf", "XFoo", "XFooClass");
    assertEquals(NormalCompletionTestCase.renderElement(getLookup().getItems().get(0)).getItemTextForeground(), JBColor.foreground());
    assertEquals(NormalCompletionTestCase.renderElement(getLookup().getItems().get(1)).getItemTextForeground(), JBColor.RED);
    assertEquals(NormalCompletionTestCase.renderElement(getLookup().getItems().get(2)).getItemTextForeground(), JBColor.RED);
  }

  public void testPreferClassesInExtends() {
    checkPreferredItems(0, "FooClass", "Foo_Intf");
  }

  public void testPreferClassStaticMembers() {
    checkPreferredItems(0, "Zoo.A", "Zoo", "Zoo.B", "Zoo.C", "Zoo.D", "Zoo.E", "Zoo.F", "Zoo.G", "Zoo.H");
  }

  public void testPreferFinallyToFinal() {
    checkPreferredItems(0, "finally", "final");
  }

  public void testPreferReturn() {
    checkPreferredItems(0, "return", "rLocal", "rParam", "rMethod");
  }

  public void testPreferReturnBeforeExpression() {
    checkPreferredItems(0, "return", "rLocal", "rParam", "rMethod");
  }

  public void testPreferReturnBeforeExpression2() {
    checkPreferredItems(0, "return", "retainAll");
  }

  @NeedsIndex.ForStandardLibrary
  public void testPreferReturnInSingleStatementPlace() {
    checkPreferredItems(0, "return", "registerKeyboardAction");
  }

  @NeedsIndex.ForStandardLibrary
  public void testPreferContinueInsideLoops() {
    checkPreferredItems(0, "continue", "color", "computeVisibleRect");
  }

  public void testPreferModifiers() {
    checkPreferredItems(0, "private", "protected", "public");
  }

  public void testPreferEnumConstants() {
    checkPreferredItems(0, "MyEnum.bar", "MyEnum", "MyEnum.foo");
  }

  @NeedsIndex.Full
  public void testPreferExpectedEnumConstantsDespiteStats() {
    checkPreferredItems(0, "const1", "const2", "constx1", "constx2");
    myFixture.getLookup().setCurrentItem(myFixture.getLookupElements()[2]);

    myFixture.type("\n);\nmethodX(cons");
    myFixture.completeBasic();
    assertPreferredItems(0, "constx1", "constx2", "const1", "const2");
  }

  public void testPreferExpectedEnumConstantsInComparison() {
    checkPreferredItems(0, "MyEnum.const1", "MyEnum", "MyEnum.const2");
    incUseCount(myFixture.getLookupElementStrings().indexOf("String"));// select some unrelated class
    assertPreferredItems(0, "MyEnum.const1", "MyEnum", "MyEnum.const2");
  }

  public void testPreferElse() {
    checkPreferredItems(0, "else", "element");
  }

  public void testPreferMoreMatching() {
    checkPreferredItems(0, "FooOCSomething", "FooObjectCollector");
  }

  public void testPreferSamePackageOverImported() {
    myFixture.addClass("package bar; public class Bar1 {}");
    myFixture.addClass("package bar; public class Bar2 {}");
    myFixture.addClass("package bar; public class Bar3 {}");
    myFixture.addClass("package bar; public class Bar4 {}");
    myFixture.addClass("class Bar9 {}");
    myFixture.addClass("package doo; public class Bar0 {}");

    checkPreferredItems(0, "Bar9", "Bar1", "Bar2", "Bar3", "Bar4");
  }

  @NeedsIndex.Full
  public void testPreselectMostRelevantInTheMiddleAlpha() {
    UISettings.getInstance().setSortLookupElementsLexicographically(true);

    myFixture.addClass("package foo; public class ELXaaaaaaaaaaaaaaaaaaaa {}");
    invokeCompletion(getTestName(false) + ".java");
    myFixture.completeBasic();
    LookupImpl lookup = getLookup();
    assertPreferredItems(lookup.getList().getSelectedIndex());
    TestCase.assertEquals("ELXaaaaaaaaaaaaaaaaaaaa", lookup.getItems().get(0).getLookupString());
    TestCase.assertEquals("ELXEMENT_A", lookup.getCurrentItem().getLookupString());
  }

  public void testReallyAlphaSorting() {
    UISettings.getInstance().setSortLookupElementsLexicographically(true);

    invokeCompletion(getTestName(false) + ".java");
    assertEquals(myFixture.getLookupElementStrings().stream().sorted().toList(), myFixture.getLookupElementStrings());
  }

  @NeedsIndex.Full
  public void testAlphaSortPackages() {
    UISettings.getInstance().setSortLookupElementsLexicographically(true);

    ArrayList<String> pkgs = new ArrayList<>(Arrays.asList("bar", "foo", "goo", "roo", "zoo"));
    for (String s : pkgs) {
      myFixture.addClass("package " + s + "; public class Foox {}");
    }

    invokeCompletion(getTestName(false) + ".java");
    for (int i = 0; i < pkgs.size(); i++) {
      assertTrue(NormalCompletionTestCase.renderElement(myFixture.getLookupElements()[i]).getTailText().contains(pkgs.get(i)));
    }
  }

  public void testAlphaSortingStartMatchesFirst() {
    UISettings.getInstance().setSortLookupElementsLexicographically(true);
    checkPreferredItems(0, "xxbar", "xxfoo", "xxgoo", "barxx", "fooxx", "gooxx");
  }

  @NeedsIndex.Full
  public void testSortSameNamedVariantsByProximity() {
    myFixture.addClass("public class Bar {}");
    for (int i = 0; i < 10; i++) {
      myFixture.addClass("public class Bar" + i + " {}");
      myFixture.addClass("public class Bar" + i + "Colleague {}");
    }

    myFixture.addClass("package bar; public class Bar {}");
    myFixture.configureByFile(getTestName(false) + ".java");
    myFixture.complete(CompletionType.BASIC, 2);
    assertPreferredItems(0, "Bar", "Bar");
    List<LookupElement> items = getLookup().getItems();
    TestCase.assertEquals(((JavaPsiClassReferenceElement)items.get(0)).getQualifiedName(), "Bar");
    TestCase.assertEquals(((JavaPsiClassReferenceElement)items.get(1)).getQualifiedName(), "bar.Bar");
  }

  public void testCaseInsensitivePrefixMatch() {
    CodeInsightSettings.getInstance().setCompletionCaseSensitive(CodeInsightSettings.NONE);
    checkPreferredItems(1, "Foo", "foo1", "foo2");
  }

  @NeedsIndex.ForStandardLibrary
  public void testPreferKeywordsToVoidMethodsInExpectedTypeContext() {
    checkPreferredItems(0, "noo", "new", "null", "noo2", "new File", "new File", "new File", "new File", "clone", "toString", "notify",
                        "notifyAll");
  }

  public void testPreferBetterMatchingConstantToMethods() {
    checkPreferredItems(0, "serial", "superExpressionInIllegalContext");
  }

  @NeedsIndex.ForStandardLibrary
  public void testPreferApplicableAnnotations() {
    myFixture.addClass(
      "\nimport java.lang.annotation.ElementType;\nimport java.lang.annotation.Target;\n\n@Target(ElementType.ANNOTATION_TYPE)\n@interface TMetaAnno {}\n\n@Target(ElementType.LOCAL_VARIABLE)\n@interface TLocalAnno {}");
    checkPreferredItems(0, "TMetaAnno", "Target", "TabLayoutPolicy", "TabPlacement");
  }

  @NeedsIndex.ForStandardLibrary
  public void testPreferApplicableAnnotationsMethod() {
    myFixture.addClass(
      "\nimport java.lang.annotation.ElementType;\nimport java.lang.annotation.Target;\n\n@Target(ElementType.TYPE)\n@interface TxClassAnno {}\n\ninterface TxANotAnno {}\n\n@Target(ElementType.METHOD)\n@interface TxMethodAnno {}");
    checkPreferredItems(0, "TxMethodAnno", "TxClassAnno");
    assertFalse(myFixture.getLookupElementStrings().contains("TxANotAnno"));
  }

  @NeedsIndex.ForStandardLibrary
  public void testJComponentAddNewWithStats() {
    invokeCompletion("/../smartTypeSorting/JComponentAddNew.java");
    assertPreferredItems(0, "FooBean3", "JComponent", "Component");
    incUseCount(2);//Component
    assertPreferredItems(0, "Component", "FooBean3", "JComponent");
  }

  public void testDispreferReturnBeforeStatement() {
    checkPreferredItems(0, "reaction", "rezet", "return");
  }

  public void testDispreferReturnInConstructor() {
    checkPreferredItems(0, "reaction", "rezet", "return");
  }

  public void testDispreferReturnInVoidMethodTopLevel() {
    checkPreferredItems(0, "reaction", "rezet", "return");
  }

  public void testDispreferReturnAsLoopBody() {
    checkPreferredItems(0, "returnMethod", "return");
  }

  @NeedsIndex.ForStandardLibrary
  public void testDispreferReturnInVoidLambda() {
    checkPreferredItems(0, "reaction", "rezet", "return");
  }

  @NeedsIndex.ForStandardLibrary
  public void testDoNotPreferGetClass() {
    checkPreferredItems(0, "get", "getClass");
    incUseCount(1);
    assertPreferredItems(0, "getClass", "get");
    incUseCount(1);
    assertPreferredItems(0, "get", "getClass");
  }

  @NeedsIndex.ForStandardLibrary
  public void testEqualsStats() {
    checkPreferredItems(0, "equals", "equalsIgnoreCase");
    incUseCount(1);
    assertPreferredItems(0, "equalsIgnoreCase", "equals");
    incUseCount(1);
    checkPreferredItems(0, "equals", "equalsIgnoreCase");
  }

  public void testPreferClassToItsConstants() {
    checkPreferredItems(0, "MyCalendar.FIELD_COUNT", "MyCalendar", "MyCalendar.AM");
  }

  @NeedsIndex.Full
  public void testPreferLocalsToStaticsInSecondCompletion() {
    myFixture.addClass("public class FooZoo { public static void fooBar() {}  }");
    myFixture.addClass("public class fooAClass {}");
    configureNoCompletion(getTestName(false) + ".java");
    myFixture.complete(CompletionType.BASIC, 2);
    assertPreferredItems(0, "fooy", "foox", "fooAClass", "fooBar");
  }

  public void testChangePreselectionOnSecondInvocation() {
    configureNoCompletion(getTestName(false) + ".java");
    myFixture.complete(CompletionType.BASIC);
    assertPreferredItems(0, "fooZooGoo", "fooZooImpl");
    myFixture.complete(CompletionType.BASIC);
    assertPreferredItems(0, "fooZoo", "fooZooGoo", "fooZooImpl");
  }

  public void testUnderscoresDontMakeMatchMiddle() {
    CodeInsightSettings.getInstance().setCompletionCaseSensitive(CodeInsightSettings.NONE);
    checkPreferredItems(0, "fooBar", "_fooBar", "FooBar");
  }

  public void testDispreferUnderscoredCaseMatch() {
    checkPreferredItems(0, "fooBar", "__foo_bar");
  }

  public void testStatisticsMattersOnNextCompletion() {
    configureByFile(getTestName(false) + ".java");
    myFixture.completeBasic();
    assertNotNull(getLookup());
    assertNotEquals("JComponent", getLookup().getCurrentItem().getLookupString());
    myFixture.type("ponent c;\nJCom");
    myFixture.completeBasic();
    assertNotNull(getLookup());
    assertEquals("JComponent", getLookup().getCurrentItem().getLookupString());
  }

  public void testPreferFieldToMethod() {
    checkPreferredItems(0, "size", "size");
    assertTrue(getLookup().getItems().get(0).getObject() instanceof PsiField);
    assertTrue(getLookup().getItems().get(1).getObject() instanceof PsiMethod);
  }

  @NeedsIndex.ForStandardLibrary
  public void testPreselectLastChosen() {
    checkPreferredItems(0, "add", "addAll");
    for (int i = 0; i <= 10; i++) {
      incUseCount(1);
    }

    assertPreferredItems(0, "addAll", "add");
    incUseCount(1);
    assertPreferredItems(0, "add", "addAll");
  }

  public void testDontPreselectLastChosenWithUnrelatedPrefix() {
    invokeCompletion(getTestName(false) + ".java");
    myFixture.type(";\nmycl");
    myFixture.completeBasic();
    assertPreferredItems(0, "myClass", "myExtendsClause");
  }

  public void testCommonPrefixMoreImportantThanExpectedType() {
    checkPreferredItems(0, "myStep", "myCurrentStep");
  }

  public void testStatsMoreImportantThanExpectedType() {
    invokeCompletion(getTestName(false) + ".java");
    assertPreferredItems(0, "getNumber", "getNumProvider");
    getLookup().setCurrentItem(getLookup().getItems().get(1));
    myFixture.type("\n);\ntest(getnu");
    myFixture.completeBasic();
    assertPreferredItems(0, "getNumProvider", "getNumber");
  }

  public void testIfConditionStats() {
    invokeCompletion(getTestName(false) + ".java");
    myFixture.completeBasic();
    myFixture.type("cont");
    assertPreferredItems(0, "containsAll", "contains");
    myFixture.getLookup().setCurrentItem(myFixture.getLookupElements()[1]);
    myFixture.type("\nc)) {\nif (set.");
    myFixture.completeBasic();
    myFixture.type("cont");
    assertPreferredItems(0, "contains", "containsAll");
  }

  public void testDeepestSuperMethodStats() {
    invokeCompletion(getTestName(false) + ".java");
    assertPreferredItems(0, "addX", "addY");
    myFixture.type("y\n;set1.ad");

    myFixture.completeBasic();
    assertPreferredItems(0, "addY", "addX");
    myFixture.type("x\n;set2.ad");

    myFixture.completeBasic();
    assertPreferredItems(0, "addX", "addY");
  }

  public void testCommonPrefixMoreImportantThanKind() {
    CodeInsightSettings.getInstance().setCompletionCaseSensitive(CodeInsightSettings.NONE);
    checkPreferredItems(0, "PsiElement", "psiElement");
  }

  public void testNoExpectedTypeInStringConcatenation() {
    checkPreferredItems(0, "vx");
  }

  public void testLocalVarsOverStats() {
    CodeInsightSettings.getInstance().setCompletionCaseSensitive(CodeInsightSettings.NONE);
    checkPreferredItems(0, "psiElement", "PsiElement");
    incUseCount(1);
    assertPreferredItems(0, "psiElement", "PsiElement");
  }

  public void testHonorRecency() {
    invokeCompletion(getTestName(false) + ".java");
    myFixture.completeBasic();
    myFixture.type("setou\nz.");

    myFixture.completeBasic();
    myFixture.type("set");
    assertPreferredItems(0, "setOurText", "setText");
    myFixture.type("te");
    assertPreferredItems(0, "setText", "setOurText");
    myFixture.type("\nz.");

    myFixture.completeBasic();
    myFixture.type("set");
    assertPreferredItems(0, "setText", "setOurText");
  }

  public void testPreferString() {
    checkPreferredItems(0, "String", "System", "Short");
  }

  public void testAnnotationEnum() {
    checkPreferredItems(0, "MyEnum.BAR", "MyEnum", "MyEnum.FOO");
  }

  @NeedsIndex.ForStandardLibrary
  public void testPreferClassesOfExpectedClassType() {
    myFixture.addClass("class XException extends Exception {}");
    checkPreferredItems(0, "XException.class", "XException", "XClass", "XIntf");
  }

  public void testNoNumberValueOf() {
    checkPreferredItems(0, "value");
  }

  public void testNoBooleansInMultiplication() {
    checkPreferredItems(0, "fact");
  }

  @NeedsIndex.ForStandardLibrary
  public void testPreferAnnotationsToInterfaceKeyword() {
    checkPreferredItems(0, "Deprecated", "Override", "SuppressWarnings");
  }

  @NeedsIndex.ForStandardLibrary
  public void testPreferThrownExceptionsInCatch() {
    checkPreferredItems(0, "final", "FileNotFoundException", "File");
  }

  public void testHonorFirstLetterCase() {
    CodeInsightSettings.getInstance().setCompletionCaseSensitive(CodeInsightSettings.NONE);
    checkPreferredItems(0, "posIdMap", "PImageDecoder", "PNGImageDecoder");
  }

  @NeedsIndex.Full
  public void testGlobalStaticMemberStats() {
    configureNoCompletion(getTestName(false) + ".java");
    myFixture.complete(CompletionType.BASIC, 2);
    assertPreferredItems(0, "newLinkedSet0", "newLinkedSet1", "newLinkedSet2");
    CompletionSortingTestCase.imitateItemSelection(getLookup(), 1);
    myFixture.complete(CompletionType.BASIC, 2);
    assertPreferredItems(0, "newLinkedSet1", "newLinkedSet0", "newLinkedSet2");
  }

  @NeedsIndex.ForStandardLibrary
  public void testStaticMemberTypes() {
    checkPreferredItems(0, "newMap", "newList");
  }

  @NeedsIndex.ForStandardLibrary
  public void testNoStatsInSuperInvocation() {
    checkPreferredItems(0, "put", "putAll");

    myFixture.type("\n");
    assertTrue(myFixture.getEditor().getDocument().getText().contains("put"));

    myFixture.type(");\nsuper.");
    myFixture.completeBasic();

    assertPreferredItems(0, "get");
  }

  public void testLiveTemplateOrdering() {
    LiveTemplateCompletionContributor.setShowTemplatesInTests(true, myFixture.getTestRootDisposable());
    checkPreferredItems(0, "return");
    assertTrue(ContainerUtil.exists(getLookup().getItems(), it -> it.getLookupString().equals("ritar")));
  }

  @NeedsIndex.ForStandardLibrary
  public void testPreferLocalToExpectedTypedMethod() {
    checkPreferredItems(0, "event", "equals");
  }

  public void testDispreferJustUsedEnumConstantsInSwitch() {
    checkPreferredItems(0, "BAR", "FOO", "GOO");
    myFixture.type("\nbreak;\ncase ");

    LookupElement[] items = myFixture.completeBasic();
    assertPreferredItems(0, "FOO", "GOO", "BAR");

    assertEquals(NormalCompletionTestCase.renderElement(items[0]).getItemTextForeground(), JBColor.foreground());
    assertEquals(NormalCompletionTestCase.renderElement(ContainerUtil.find(items, it -> it.getLookupString().equals("BAR")))
                   .getItemTextForeground(), JBColor.RED);
  }

  @NeedsIndex.ForStandardLibrary
  public void testPreferValueTypesReturnedFromMethod() {
    checkPreferredItems(0, "StringBuffer", "String", "Serializable", "SomeInterface", "SomeInterface", "SomeOtherClass");
    assertEquals("SomeInterface<String>", NormalCompletionTestCase.renderElement(myFixture.getLookupElements()[3]).getItemText());
    assertEquals("SomeInterface", NormalCompletionTestCase.renderElement(myFixture.getLookupElements()[4]).getItemText());
  }

  @NeedsIndex.Full
  public void testPreferCastTypesHavingSpecifiedMethod() {
    checkPreferredItems(0, "MainClass1", "MainClass2", "Maa");
  }

  public void testNaturalSorting() {
    checkPreferredItems(0, "fun1", "fun2", "fun10");
  }

  @NeedsIndex.ForStandardLibrary
  public void testPreferVarsHavingReferencedMember() {
    checkPreferredItems(0, "xzMap", "xaString");
  }

  @NeedsIndex.ForStandardLibrary
  public void testPreferCollectionsStaticOfExpectedType() {
    checkPreferredItems(0, "unmodifiableList", "unmodifiableCollection");
  }

  @NeedsIndex.SmartMode(reason = "isEffectivelyDeprecated needs smart mode")
  public void testDispreferDeprecatedMethodWithUnresolvedQualifier() {
    myFixture.addClass("package foo; public class Assert { public static void assertTrue() {} }");
    myFixture.addClass(
      "package bar; @Deprecated public class Assert { public static void assertTrue() {} public static void assertTrue2() {} }");
    checkPreferredItems(0, "Assert.assertTrue", "Assert.assertTrue", "Assert.assertTrue2");

    LookupElementPresentation p = NormalCompletionTestCase.renderElement(myFixture.getLookup().getItems().get(0));
    assertTrue(p.getTailText().contains("foo"));
    assertFalse(p.isStrikeout());

    p = NormalCompletionTestCase.renderElement(myFixture.getLookup().getItems().get(1));
    assertTrue(p.getTailText().contains("bar"));
    assertTrue(p.isStrikeout());
  }

  public void testPreferClassKeywordWhenExpectedClassType() {
    checkPreferredItems(0, "class");
  }

  public void testPreferBooleanKeywordsWhenExpectedBoolean() {
    checkPreferredItems(0, "false", "factory");
  }

  public void testPreferBooleanKeywordsWhenExpectedBoolean2() {
    checkPreferredItems(0, "false", "factory");
  }

  @NeedsIndex.Full
  public void testPreferExplicitlyImportedStaticMembers() {
    myFixture.addClass(
      "\nclass ContainerUtilRt {\n  static void newHashSet();\n  static void newHashSet2();\n}\nclass ContainerUtil extends ContainerUtilRt {\n  static void newHashSet();\n  static void newHashSet3();\n}\n");
    checkPreferredItems(0, "newHashSet", "newHashSet", "newHashSet3", "newHashSet2");
    assertEquals("ContainerUtil", ((PsiMethod)myFixture.getLookupElements()[0].getPsiElement()).getContainingClass().getName());
  }

  public void testPreferCatchAndFinallyAfterTry() {
    checkPreferredItems(0, "catch", "finally");
  }

  @NeedsIndex.Full
  public void testPreselectClosestExactPrefixItem() {
    UISettings.getInstance().setSortLookupElementsLexicographically(true);
    myFixture.addClass("package pack1; public class SameNamed {}");
    myFixture.addClass("package pack2; public class SameNamed {}");
    checkPreferredItems(1, "SameNamed", "SameNamed");
    assertTrue(NormalCompletionTestCase.renderElement(myFixture.getLookupElements()[0]).getTailText().contains("pack1"));
    assertTrue(NormalCompletionTestCase.renderElement(myFixture.getLookupElements()[1]).getTailText().contains("pack2"));
  }

  @NeedsIndex.ForStandardLibrary
  public void testPreferExpectedMethodTypeArg() {
    checkPreferredItems(0, "String", "Usage");

    int typeArgOffset = myFixture.getEditor().getCaretModel().getOffset();

    // increase usage stats of ArrayList
    LookupManager.getInstance(getProject()).hideActiveLookup();
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
  }

  @NeedsIndex.ForStandardLibrary
  public void testMethodStatisticsPerQualifierType() {
    checkPreferredItems(0, "charAt");
    myFixture.type("eq\n);\n");
    assertTrue(myFixture.getEditor().getDocument().getText().contains("equals();\n"));

    myFixture.type("this.");
    myFixture.completeBasic();
    assertPreferredItems(0, "someMethod");
  }

  @NeedsIndex.Full
  public void testPreferImportedClassesAmongstSameNamed() {
    myFixture.addClass("package foo; public class String {}");

    // use foo.String
    myFixture.configureByText("a.java", "class Foo { Stri<caret> }");
    myFixture.completeBasic();
    myFixture.getLookup().setCurrentItem(ContainerUtil.find(myFixture.getLookupElements(), it -> it.getLookupString().equals("String") &&
                                                                                                 NormalCompletionTestCase.renderElement(it)
                                                                                                   .getTailText().contains("foo")));
    myFixture.type("\n");
    myFixture.checkResult("import foo.String;\n\nclass Foo { String<caret>\n}");

    // assert non-imported String is not preselected when completing in another file
    myFixture.configureByText("b.java", "class Bar { Stri<caret> }");
    myFixture.completeBasic();
    myFixture.assertPreferredCompletionItems(0, "String", "String");
    assertTrue(NormalCompletionTestCase.renderElement(myFixture.getLookupElements()[0]).getTailText().contains("java.lang"));
  }

  public void testClassNameStatisticsDoesntDependOnExpectedType() {
    checkPreferredItems(0, "ConflictsUtil", "ContainerUtil");
    myFixture.getLookup().setCurrentItem(myFixture.getLookupElements()[1]);
    myFixture.type("\n.foo();\nlong l = ConUt");
    myFixture.completeBasic();

    assertPreferredItems(0, "ContainerUtil", "ConflictsUtil");
  }

  @NeedsIndex.ForStandardLibrary
  public void testPreferListAddWithoutIndex() {
    checkPreferredItems(0, "add", "add", "addAll", "addAll");
    assertTrue(NormalCompletionTestCase.renderElement(myFixture.getLookupElements()[1]).getTailText().contains("int index"));
    assertTrue(NormalCompletionTestCase.renderElement(myFixture.getLookupElements()[3]).getTailText().contains("int index"));
  }

  @NeedsIndex.Full
  public void testPreferExpectedTypeConstantOverSameNamedClass() {
    myFixture.addClass("package another; public class JSON {}");
    checkPreferredItems(0, "Point.JSON", "JSON");
  }

  public void testPreferExpectedEnumConstantInAnnotationAttribute() {
    checkPreferredItems(0, "MyEnum.bar", "MyEnum", "MyEnum.foo");
    int unrelatedItem = ContainerUtil.indexOf(myFixture.getLookupElementStrings(), it -> it.contains("Throwable"));
    incUseCount(unrelatedItem);
    //nothing should change
    assertPreferredItems(0, "MyEnum.bar", "MyEnum", "MyEnum.foo");
  }

  public void testPreferExpectedTypeFieldOverUnexpectedLocalVariables() {
    checkPreferredItems(0, "field", "local");
  }

  public void testPreferConflictingFieldAfterThis() {
    checkPreferredItems(0, "text");
  }

  public void testDoNotPreselectShorterDeprecatedClasses() {
    checkPreferredItems(1, "XLong", "XLonger");
  }

  public void testSignatureBeforeStats() {
    myFixture.configureByText("a.java",
                              "\nclass Foo { \n  void foo(int a, int b) {\n    Stri<caret>\n    bar();\n  }\n  \n  void bar(int a, int b) {} \n}");
    myFixture.completeBasic();
    myFixture.assertPreferredCompletionItems(0, "String");
    myFixture.type("\n");// increase String statistics
    myFixture.type("foo;\nbar(");
    myFixture.completeBasic();
    myFixture.assertPreferredCompletionItems(0, "a", "b", "a, b");
  }

  @NeedsIndex.ForStandardLibrary
  public void test_selecting_static_field_after_static_method() {
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
  }

  @NeedsIndex.SmartMode(reason = "JavaGenerateMemberCompletionContributor.fillCompletionVariants works in smart mode only (for getters)")
  public void testPreferTypeToGeneratedMethod() {
    checkPreferredItems(0, "SomeClass", "public SomeClass getZoo");
  }

  @NeedsIndex.SmartMode(reason = "JavaGenerateMemberCompletionContributor.fillCompletionVariants works in smart mode only (for getters and equals())")
  public void testPreferPrimitiveTypeToGeneratedMethod() {
    checkPreferredItems(0, "boolean", "public boolean isZoo", "public boolean equals");
  }

  @NeedsIndex.ForStandardLibrary
  public void testPreferExceptionsInCatch() {
    myFixture.configureByText("a.java", "class Foo { { Enu<caret> } }");
    myFixture.completeBasic();
    myFixture.type("m\n");// select 'Enum'
    myFixture.type("; try {} catch(E");
    myFixture.completeBasic();
    myFixture.assertPreferredCompletionItems(0, "Exception", "Error");
  }

  @NeedsIndex.ForStandardLibrary
  public void testPreferExceptionsInThrowsList() {
    checkPreferredItems(0, "IllegalStateException", "IllegalAccessException", "IllegalArgumentException");
  }

  @NeedsIndex.ForStandardLibrary
  public void testPreferExceptionsInJavadocThrows() {
    checkPreferredItems(0, "IllegalArgumentException", "IllegalAccessException", "IllegalStateException");
  }

  @NeedsIndex.ForStandardLibrary
  public void testPreferExpectedTypeArguments() {
    checkPreferredItems(0, "BlaOperation");
  }

  public void testPreferFinalBeforeVariable() {
    checkPreferredItems(0, "final", "find1");
  }

  @NeedsIndex.SmartMode(reason = "AbstractExpectedTypeSkipper works in smart mode only")
  public void testDispreferMultiMethodInterfaceAfterNew() {
    checkPreferredItems(1, "Intf", "IntfImpl");
  }

  public void testDispreferAlreadyCalledBuilderMethods() {
    checkPreferredItems(0, "addInt", "append", "c", "d", "mayCallManyTimes", "putLong");
  }

  public void testSelectAbstractClassWithNoAbstractMethods() {
    checkPreferredItems(0, "AbstractListener", "Listener");
  }

  @NeedsIndex.ForStandardLibrary
  public void testPreferPrintln() {
    myFixture.configureByText("a.java", "class Foo { { System.out.pri<caret>x } }");
    myFixture.completeBasic();
    myFixture.assertPreferredCompletionItems(0, "println", "print");
    myFixture.type("\t");
    myFixture.checkResult("class Foo { { System.out.println(<caret>); } }");
  }

  public void testPreferLocalArrayVariableToItsChains() {
    checkPreferredItems(0, "arrayVariable");
  }

  @NeedsIndex.ForStandardLibrary
  public void test_void_method_in_nonvoid_context() {
    myFixture.configureByText("a.java", "class X { String getName() {return \"\";} void test() {System.out.println(this.n<caret>);}}");
    myFixture.completeBasic();
    assertStringItems("getName", "clone", "toString", "notify", "notifyAll", "finalize");
  }

  @NeedsIndex.ForStandardLibrary
  public void test_void_context_replace() {
    myFixture.configureByText("a.java", "class X { String getName() {return \"\";} void test() {this.n<caret>otify();}}");
    myFixture.completeBasic();
    assertStringItems("notify", "notifyAll", "getName", "clone", "toString", "finalize");
  }

  @NeedsIndex.Full
  public void test_import_nested_classes_order() {
    myFixture.addClass("public class Cls { public static class TestImport {}}");
    myFixture.addClass("public class Cls2 { public static class TestImport {}}");
    myFixture.addClass("package demo;public class TestImport {}");
    myFixture.addClass("public class TestImport {}");
    myFixture.configureByText("a.java", "import demo.*;import static Cls2.*; class Test {TestIm<caret>}");
    myFixture.completeBasic();
    LookupElement[] elements = myFixture.getLookupElements();
    assertEquals(4, elements.length);
    List<String> weights = DumpLookupElementWeights.getLookupElementWeights(getLookup(), false);
    assertEquals("TestImport", elements[0].as(JavaPsiClassReferenceElement.class).getQualifiedName());
    assertTrue(weights.get(0).contains("explicitlyImported=CLASS_DECLARED_IN_SAME_PACKAGE_TOP_LEVEL,"));// same package
    assertEquals("demo.TestImport", elements[1].as(JavaPsiClassReferenceElement.class).getQualifiedName());
    assertTrue(weights.get(1).contains("explicitlyImported=CLASS_ON_DEMAND_TOP_LEVEL,"));// on-demand import
    assertEquals("Cls2.TestImport", elements[2].as(JavaPsiClassReferenceElement.class).getQualifiedName());
    assertTrue(weights.get(2).contains("explicitlyImported=CLASS_ON_DEMAND_NESTED,"));// same package, nested class imported
    assertEquals("Cls.TestImport", elements[3].as(JavaPsiClassReferenceElement.class).getQualifiedName());
    assertTrue(weights.get(3)
                 .contains("explicitlyImported=CLASS_DECLARED_IN_SAME_PACKAGE_NESTED,"));// same package but nested class not imported
  }

  @NeedsIndex.SmartMode(reason = "Ordering requires smart mode")
  public void test_discourage_experimental() {
    myFixture.addClass("package org.jetbrains.annotations;public class ApiStatus{public @interface Experimental {}}");
    myFixture.addClass("class Cls {@org.jetbrains.annotations.ApiStatus.Experimental public void methodA() {} public void methodB() {}}");
    myFixture.configureByText("a.java", "class Test {void t(Cls cls) {cls.me<caret>}}");
    myFixture.completeBasic();
    assertEquals(myFixture.getLookupElementStrings(), List.of("methodB", "methodA"));
  }

  private static final String BASE_PATH = "/codeInsight/completion/normalSorting";
}
