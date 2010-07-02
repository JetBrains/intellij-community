package com.intellij.codeInsight.completion;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementDecorator;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import org.jetbrains.annotations.NonNls;

import java.util.Arrays;

@SuppressWarnings({"ALL"})
public class SecondSmartTypeCompletionTest extends LightCompletionTestCase {
  private static final String BASE_PATH = "/codeInsight/completion/smartType/second";

  public SecondSmartTypeCompletionTest() {
    setType(CompletionType.SMART);
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void testMethodAsQualifier() throws Throwable { doTest(); }
  public void testFieldAsQualifier() throws Throwable { doTest(); }
  public void testMethodWithParams() throws Throwable { doTest(); }
  public void testMergeMethods() throws Throwable { doTest(); }

  public void testMethodDelegation() throws Throwable { doTest(); }

  public void testGenerics() throws Throwable { doTest(); }
  public void testQualifierMatters() throws Throwable { doTest(); }

  public void testDifferentQualifiers() throws Throwable {
    configureByFile(BASE_PATH + "/" + getTestName(false) + ".java");
    assertStringItems("b.getGoo", "getBar().getGoo");
  }

  public void testSuggestArraysAsList() throws Throwable { doTest(); }
  public void testSuggestArraysAsListWildcard() throws Throwable { doTest(); }

  public void testSuggestToArrayWithNewEmptyArray() throws Throwable { doTest(); }
  public void testSuggestToArrayWithNewNonEmptyArray() throws Throwable { doTest(); }
  
  public void testSuggestToArrayWithExistingEmptyArray() throws Throwable {
    configureByFile(BASE_PATH + "/" + getTestName(false) + ".java");
    assertStringItems("foos().toArray(EMPTY_ARRAY)", "foos().toArray(EMPTY_ARRAY2)");
    selectItem(myItems[0]);
    checkResultByFile(BASE_PATH + "/" + getTestName(false) + "-out.java");
  }

  public void testToArrayGenericArrayCreation() throws Throwable { doTest(); }

  public void testToArrayFieldsQualifier() throws Throwable { doTest(); }
  public void testToArrayMethodQualifier() throws Throwable { doTest(); }

  public void testToListWithQualifier() throws Throwable { doTest(); }

  public void testSuggestToArrayWithExistingEmptyArrayFromAnotherClass() throws Throwable {
    configureByFile(BASE_PATH + "/" + getTestName(false) + ".java");
    assertStringItems("foos().toArray(Bar.EMPTY_ARRAY)", "foos().toArray(Bar.EMPTY_ARRAY2)");
    selectItem(myItems[0]);
    checkResultByFile(BASE_PATH + "/" + getTestName(false) + "-out.java");
  }

  public void testIgnoreToString() throws Throwable { doTest(); }
  public void testDontIgnoreToStringInsideIt() throws Throwable { doTest(); }
  public void testDontIgnoreToStringInStringBuilders() throws Throwable {
    configureByFile(BASE_PATH + "/" + getTestName(false) + ".java");
    assertStringItems("bar.substring", "bar.substring", "bar.toString");
  }

  public void testNoObjectMethodsAsFirstPart() throws Throwable { doTest(); }
  public void testGetClassLoader() throws Throwable { doTest(); }

  public void testChainingPerformance() throws Throwable {
    long time = System.currentTimeMillis();
    configureByFile(BASE_PATH + "/" + getTestName(false) + ".java");
    IdeaTestUtil.assertTiming("", 3000, System.currentTimeMillis() - time);
    assertNotNull(myItems);
  }

  public void testArrayMemberAccess() throws Throwable { doTest(); }
  public void testVarargMemberAccess() throws Throwable { doTest(); }
  public void testQualifiedArrayMemberAccess() throws Throwable { doTest(); }

  public void testNoArraysAsListCommonPrefix() throws Throwable {
    configureByFile(BASE_PATH + "/" + getTestName(false) + ".java");
    checkResultByFile(BASE_PATH + "/" + getTestName(false) + ".java");
    assertStringItems("bar()", "foo()");
    assertEquals("Arrays.asList(f.bar())", ((LookupItem)((LookupElementDecorator)myItems[0]).getDelegate()).getPresentableText());
    assertEquals("Arrays.asList(f.foo())", ((LookupItem)((LookupElementDecorator)myItems[1]).getDelegate()).getPresentableText());
    selectItem(myItems[1]);
    checkResultByFile(BASE_PATH + "/" + getTestName(false) + "-out.java");
  }

  public void testRestoreInitialPrefix() throws Throwable {
    configureByFileNoComplete(BASE_PATH + "/" + getTestName(false) + ".java");
    complete(1);
    assertStringItems("MyEnum.Bar", "MyEnum.Foo");
    checkResultByFile(BASE_PATH + "/" + getTestName(false) + "-out.java");
    complete(1);
    assertStringItems("MyEnum.Bar", "MyEnum.Foo", "my.getEnum");
  }

  public void testDontChainStringMethodsOnString() throws Throwable { doTest(); }

  public void testDontSuggestTooGenericMethods() throws Throwable {
    configureByFile(BASE_PATH + "/" + getTestName(false) + ".java");
    assertEquals("f.barAny", myItems[0].getLookupString());
    assertEquals("f.zipAny", myItems[1].getLookupString());
  }

  public void testNoUnqualifiedCastsInQualifiedContext() throws Throwable { doAntiTest(); }

  private void doAntiTest() throws Exception {
    configureByFile(BASE_PATH + "/" + getTestName(false) + ".java");
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
    configureByFile(BASE_PATH + "/" + getTestName(false) + ".java");
    checkResultByFile(BASE_PATH + "/" + getTestName(false) + ".java");
    assertStringItems("o.gggg", "false", "true"); 
  }

  public void testEmptyListInMethodCall() throws Throwable {
    configureByFile(BASE_PATH + "/" + getTestName(false) + ".java");
    selectItem(myItems[0]);
    checkResultByFile(BASE_PATH + "/" + getTestName(false) + "-out.java");
  }

  public void testSingletonMap() throws Throwable {
    configureByFile(BASE_PATH + "/" + getTestName(false) + ".java");
    selectItem(myItems[0]);
    checkResultByFile(BASE_PATH + "/" + getTestName(false) + "-out.java");
  }

  protected void checkResultByFile(@NonNls final String filePath) throws Exception {
    if (myItems != null) {
      System.out.println("items = " + Arrays.asList(myItems));
    }
    super.checkResultByFile(filePath);
  }


  private void doTest() throws Exception {
    configureByFile(BASE_PATH + "/" + getTestName(false) + ".java");
    checkResultByFile(BASE_PATH + "/" + getTestName(false) + "-out.java");
  }

  @Override
  protected void complete() {
    complete(2);
    LookupImpl lookup = (LookupImpl)LookupManager.getActiveLookup(myEditor);
    myItems = lookup == null ? null : lookup.getItems().toArray(LookupElement.EMPTY_ARRAY);
  }

  protected void setUp() throws Exception {
    super.setUp();
    LanguageLevelProjectExtension.getInstance(getProject()).setLanguageLevel(LanguageLevel.JDK_1_5);
  }

  protected void tearDown() throws Exception {
    LookupManager.getInstance(getProject()).hideActiveLookup();
    super.tearDown();
  }

  @Override
  protected Sdk getProjectJDK() {
    return JavaSdkImpl.getMockJdk17("java 1.5");
  }
}
