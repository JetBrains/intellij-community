package com.intellij.codeInsight.completion;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.completion.impl.CamelHumpMatcher;
import com.intellij.codeInsight.lookup.*;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.ThrowableRunnable;
import org.jetbrains.annotations.NonNls;

@SuppressWarnings({"ALL"})
public class SecondSmartTypeCompletionTest extends LightCompletionTestCase {
  private static final String BASE_PATH = "/codeInsight/completion/smartType/second";

  public SecondSmartTypeCompletionTest() {
    setType(CompletionType.SMART);
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    CamelHumpMatcher.forceStartMatching(getTestRootDisposable());
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void testMethodAsQualifier() throws Throwable { doTest(); }
  public void testFieldAsQualifier() throws Throwable { doTest(); }
  public void testArrayRefAsQualifier() throws Throwable { doTest(); }
  public void testMethodWithParams() throws Throwable { doTest(); }
  public void testMergeMethods() throws Throwable { doTest(); }

  public void testMethodDelegation() throws Throwable { doTest(); }

  public void testGenerics() throws Throwable { doTest(); }
  public void testQualifierMatters() throws Throwable { doTest(); }

  public void testDifferentQualifiers() throws Throwable {
    configure();
    assertStringItems("b.getGoo", "getBar().getGoo");
  }

  public void testSuggestArraysAsList() throws Throwable { doTest(); }
  public void testSuggestArraysAsListWildcard() throws Throwable { doTest(); }

  public void testSuggestToArrayWithNewEmptyArray() throws Throwable { doTest(); }
  public void testSuggestToArrayWithNewNonEmptyArray() throws Throwable { doTest(); }
  
  public void testSuggestToArrayWithExistingEmptyArray() throws Throwable {
    configure();
    assertStringItems("foos().toArray(EMPTY_ARRAY)", "foos().toArray(EMPTY_ARRAY2)");
    selectItem(myItems[0]);
    checkResult();
  }

  public void testToArrayGenericArrayCreation() throws Throwable { doTest(); }

  public void testToArrayFieldsQualifier() throws Throwable { doTest(); }
  public void testToArrayMethodQualifier() throws Throwable { doTest(); }

  public void testToListWithQualifier() throws Throwable { doTest(); }

  public void testSuggestToArrayWithExistingEmptyArrayFromAnotherClass() throws Throwable {
    configure();
    assertStringItems("foos().toArray(Bar.EMPTY_ARRAY)", "foos().toArray(Bar.EMPTY_ARRAY2)");
    selectItem(myItems[0]);
    checkResult();
  }

  public void testNonInitializedField() throws Throwable { doTest(); }
  public void testIgnoreToString() throws Throwable { doTest(); }
  public void testDontIgnoreToStringInsideIt() throws Throwable { doTest(); }
  public void testDontIgnoreToStringInStringBuilders() throws Throwable {
    configure();
    assertStringItems("bar.substring", "bar.substring", "bar.toString");
  }

  public void testNoObjectMethodsAsFirstPart() throws Throwable { doTest(); }
  public void testGetClassLoader() throws Throwable { doTest(); }
  public void testNewStaticProblem() throws Throwable { doTest(); }

  public void testChainingPerformance() throws Throwable {
    configureByFileNoComplete(BASE_PATH + "/" + getTestName(false) + ".java");
    PlatformTestUtil.startPerformanceTest(getTestName(false), 1000, new ThrowableRunnable() {
      @Override
      public void run() throws Exception {
        configure();
        assertNotNull(myItems);
        LookupManager.getInstance(getProject()).hideActiveLookup();
      }
    }).cpuBound().assertTiming();

  }

  public void testArrayMemberAccess() throws Throwable { doTest(); }
  public void testVarargMemberAccess() throws Throwable { doTest(); }
  public void testQualifiedArrayMemberAccess() throws Throwable { doTest(); }

  public void testPreferFieldAndGetterQualifiers() {
    configure();
    assertStringItems("localBar.getFoo", "bar.getFoo", "getBar().getFoo", "findBar().getFoo");
  }

  private void configure() {
    configureByFile(BASE_PATH + "/" + getTestName(false) + ".java");
  }

  public void testNoArraysAsListCommonPrefix() throws Throwable {
    configure();
    checkResultByFile(BASE_PATH + "/" + getTestName(false) + ".java");
    assertStringItems("bar()", "foo()");
    assertEquals("Arrays.asList(f.bar())", ((LookupItem)((LookupElementDecorator)myItems[0]).getDelegate()).getPresentableText());
    assertEquals("Arrays.asList(f.foo())", ((LookupItem)((LookupElementDecorator)myItems[1]).getDelegate()).getPresentableText());
    selectItem(myItems[1]);
    checkResult();
  }

  public void testRestoreInitialPrefix() throws Throwable {
    configureByFileNoComplete(BASE_PATH + "/" + getTestName(false) + ".java");
    complete(1);
    assertStringItems("MyEnum.Bar", "MyEnum.Foo");
    checkResult();
    complete(1);
    assertStringItems("my.getEnum", "MyEnum.Bar", "MyEnum.Foo");
  }

  public void testDontChainStringMethodsOnString() throws Throwable { doTest(); }
  public void testStringMethodsWhenNothingFound() throws Throwable { doTest(); }

  public void testDontSuggestTooGenericMethods() throws Throwable {
    configure();
    assertEquals("f.barAny", myItems[0].getLookupString());
    assertEquals("f.zipAny", myItems[1].getLookupString());
  }

  public void testNoUnqualifiedCastsInQualifiedContext() throws Throwable { doAntiTest(); }

  private void doAntiTest() throws Exception {
    configure();
    assertNull(myItems);
    checkResultByFile(BASE_PATH + "/" + getTestName(false) + ".java");
  }

  public void testCastInstanceofedQualifier() throws Throwable { doTest(); }

  public void testNoLeftRecursion() throws Throwable {
    final boolean old = CodeInsightSettings.getInstance().SHOW_STATIC_AFTER_INSTANCE;
    CodeInsightSettings.getInstance().SHOW_STATIC_AFTER_INSTANCE = true;
    try {
      doAntiTest();
    }
    finally {
      CodeInsightSettings.getInstance().SHOW_STATIC_AFTER_INSTANCE = old;
    }
  }

  public void testNoRedundantCasts() throws Throwable {
    configure();
    checkResultByFile(BASE_PATH + "/" + getTestName(false) + ".java");
    assertStringItems("o.gggg", "false", "true"); 
  }

  public void testEmptyListInMethodCall() throws Throwable {
    configure();
    selectItem(myItems[0]);
    checkResult();
  }

  private void checkResult() {
    checkResultByFile(BASE_PATH + "/" + getTestName(false) + "-out.java");
  }

  public void testSingletonMap() throws Throwable {
    configure();
    selectItem(myItems[0]);
    checkResult();
  }

  protected void checkResultByFile(@NonNls final String filePath)  {
    if (myItems != null) {
      //System.out.println("items = " + Arrays.asList(myItems));
    }
    super.checkResultByFile(filePath);
  }


  private void doTest() throws Exception {
    configure();
    checkResult();
  }

  public void testInheritorMembers() throws Throwable {
    configure();
    selectItem(myItems[0]);
    checkResult();
  }

  public void testInheritorEnumMembers() throws Throwable {
    configure();
    selectItem(myItems[0]);
    checkResult();
  }

  public void testEmptyMapPresentation() {
    configure();
    LookupElementPresentation presentation = new LookupElementPresentation();
    myItems[0].renderElement(presentation);
    assertEquals("Collections.<String, S...>emptyMap", presentation.getItemText());
  }

  public void testEmptyMapPresentation2() {
    configure();
    LookupElementPresentation presentation = new LookupElementPresentation();
    myItems[0].renderElement(presentation);
    assertEquals("Collections.emptyMap", presentation.getItemText());
  }

  @Override
  protected void complete() {
    complete(2);
    LookupImpl lookup = (LookupImpl)LookupManager.getActiveLookup(myEditor);
    myItems = lookup == null ? null : lookup.getItems().toArray(LookupElement.EMPTY_ARRAY);
  }

  protected void tearDown() throws Exception {
    LookupManager.getInstance(getProject()).hideActiveLookup();
    super.tearDown();
  }
}
