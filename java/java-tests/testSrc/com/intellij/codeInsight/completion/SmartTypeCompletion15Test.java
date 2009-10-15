package com.intellij.codeInsight.completion;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import org.jetbrains.annotations.NonNls;

public class SmartTypeCompletion15Test extends LightCompletionTestCase {
  @NonNls private static final String BASE_PATH = "/codeInsight/completion/smartType";
  private LanguageLevel myOldLanguageLevel;

  public SmartTypeCompletion15Test() {
    setType(CompletionType.SMART);
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }
  
  public void testAfterNewWithGenerics() throws Exception {
    doActionTest();
  }

  public void testClassLiteral() throws Exception {
    doActionTest();
    assertStringItems("String.class");
  }
  public void testNoClassLiteral() throws Exception {
    doActionTest();
    assertStringItems("Object.class", "getClass", "Class.forName", "Class.forName");
  }

  public void testClassLiteralInAnno2() throws Throwable {
    doActionItemTest();
  }

  public void testClassLiteralInheritors() throws Throwable {
    doActionItemTest();
  }

  public void testAfterNew15() throws Exception {
    doActionItemTest();
  }

  public void testInsertOverride() throws Exception {
    CodeStyleSettings styleSettings = CodeStyleSettingsManager.getSettings(getProject());
    styleSettings.INSERT_OVERRIDE_ANNOTATION = true;
    LanguageLevelProjectExtension.getInstance(getProject()).setLanguageLevel(LanguageLevel.JDK_1_6);
    doActionItemTest();
  }

  public void testForeach() throws Exception {
    doActionTest();
  }

  public void testIDEADEV2626() throws Exception {
    doActionTest();
  }

  public void testCastWith2TypeParameters() throws Throwable { doTest(); }

  public void testAnnotation() throws Exception {
    doTest();
    testByCount(8, "ElementType.ANNOTATION_TYPE", "ElementType.CONSTRUCTOR", "ElementType.FIELD", "ElementType.LOCAL_VARIABLE",
                "ElementType.METHOD", "ElementType.PACKAGE", "ElementType.PARAMETER", "ElementType.TYPE");
  }

  protected void checkResultByFile(@NonNls final String filePath) throws Exception {
    if (myItems != null) {
      //System.out.println("items = " + Arrays.asList(myItems));
    }
    super.checkResultByFile(filePath);
  }
  
  public void testAnnotation2() throws Exception {
    doTest();
    testByCount(3, "RetentionPolicy.CLASS", "RetentionPolicy.SOURCE", "RetentionPolicy.RUNTIME");
  }
  public void testAnnotation2_2() throws Exception {
    doTest();
    testByCount(3, "RetentionPolicy.CLASS", "RetentionPolicy.SOURCE", "RetentionPolicy.RUNTIME");
  }
  
  public void testAnnotation3() throws Exception {
    doTest();
  }
    
  public void testAnnotation3_2() throws Exception {
    doTest();
  }
  
  public void testAnnotation4() throws Exception {
    doTest();

    testByCount(2, "true","false");
  }
  
  public void testAnnotation5() throws Exception {
    doTest();

    testByCount(2, "CONNECTION","NO_CONNECTION");
  }

  public void testAnnotation6() throws Exception {
    doTest();

    testByCount(8, "ElementType.ANNOTATION_TYPE", "ElementType.CONSTRUCTOR", "ElementType.FIELD", "ElementType.LOCAL_VARIABLE",
                "ElementType.METHOD", "ElementType.PACKAGE", "ElementType.PARAMETER", "ElementType.TYPE");
  }

  public void testArrayClone() throws Exception {
    doTest();
  }

  public void testIDEADEV5150() throws Exception {
    doTest();
  }

  public void testIDEADEV7835() throws Exception {
    doTest();
  }

  public void testTypeArgs1() throws Exception {
    doTest();
  }

  public void testTypeArgs2() throws Exception {
    doTest();
  }

  public void testIDEADEV2668() throws Exception {
    doTest();
  }

  public void testExcessiveTail() throws Exception {
    doTest();
  }

  public void testExtendsInTypeCast() throws Exception {
    doTest();
  }

  public void testIDEADEV13148() throws Exception {
    configureByFile(BASE_PATH + "/IDEADEV13148.java");
    testByCount(0);
  }

  public void testOverloadedMethods() throws Throwable {
    doTest();
  }

  public void testEnumField() throws Throwable {
    doItemTest();
  }

  public void testEnumField1() throws Exception {
    doTest();
    assertEquals(4, myItems.length);
  }

  public void testInsertTypeParametersOnImporting() throws Throwable {
    doTest();
  }

  public void testEmptyListInReturn() throws Throwable {
    doItemTest();
  }

  public void testEmptyListInReturn2() throws Throwable {
    doTest();
  }

  public void testEmptyListInReturnTernary() throws Throwable {
    doItemTest();
  }

  public void testEmptyListBeforeSemicolon() throws Throwable {
    doItemTest();
  }

  public void testStaticallyImportedMagicMethod() throws Throwable {
    doItemTest();
  }

  public void _testCallVarargArgument() throws Throwable { doTest(); }

  public void testTabToReplaceClassKeyword() throws Throwable {
    configureByFile(BASE_PATH + "/" + getTestName(false) + ".java");
    selectItem(myItems[0], Lookup.REPLACE_SELECT_CHAR);
    checkResultByFile(BASE_PATH + "/" + getTestName(false) + "-out.java");
  }

  public void testNoTypeParametersForToArray() throws Throwable {
    doTest();
  }

  public void testInferFromCall() throws Throwable {
    doTest();
  }

  public void testInferFromCall1() throws Throwable {
    doTest();
  }

  public void testCastToParameterizedType() throws Throwable { doActionTest(); }

  public void testInnerEnumInMethod() throws Throwable {
    doActionItemTest();
  }

  public void testEnumAsDefaultAnnotationParam() throws Throwable { doTest(); }

  public void testFilterPrivateConstructors() throws Throwable { doTest(); }

  public void testExplicitMethodTypeParametersQualify() throws Throwable { doTest(); }

  public void testWildcardedInstanceof() throws Throwable { doTest(); }
  public void testWildcardedInstanceof2() throws Throwable { doTest(); }
  public void testWildcardedInstanceof3() throws Throwable { doTest(); }

  public void testTypeVariableInstanceOf() throws Throwable {
    configureByFile(BASE_PATH + "/" + getTestName(false) + ".java");
    performAction();
    assertStringItems("Bar", "Goo");
  }

  public void testCommonPrefixWithSelection() throws Throwable {
    doItemTest();
  }

  public void testInsideGenericClassLiteral() throws Throwable {
    configureByFile(BASE_PATH + "/" + getTestName(false) + ".java");
    testByCount(3, "String.class", "StringBuffer.class", "StringBuilder.class");
  }

  public void testArrayAnnoParameter() throws Throwable {
    doActionTest();
  }

  public void testCastWithGenerics() throws Throwable {
    doActionTest();
  }



  private void doTest(boolean performAction, boolean selectItem) throws Exception {
    configureByFile(BASE_PATH + "/" + getTestName(false) + ".java");
    if (performAction) {
      performAction();
    }
    if (selectItem) {
      selectItem(myItems[0]);
    }
    checkResultByFile(BASE_PATH + "/" + getTestName(false) + "-out.java");
  }
  private void doTest() throws Exception {
    doTest(false, false);
  }
  private void doActionTest() throws Exception {
    doTest(true, false);
  }
  private void doItemTest() throws Exception {
    doTest(false, true);
  }
  private void doActionItemTest() throws Exception {
    doTest(true, true);
  }

  private void performAction() {
    complete();
  }

  protected void setUp() throws Exception {
    super.setUp();
    myOldLanguageLevel = LanguageLevelProjectExtension.getInstance(getProject()).getLanguageLevel();
    LanguageLevelProjectExtension.getInstance(getProject()).setLanguageLevel(LanguageLevel.JDK_1_5);
    CodeStyleSettings styleSettings = CodeStyleSettingsManager.getSettings(getProject()).clone();
    styleSettings.INSERT_OVERRIDE_ANNOTATION = false;
    CodeStyleSettingsManager.getInstance(getProject()).setTemporarySettings(styleSettings);
  }

  protected void tearDown() throws Exception {
    LanguageLevelProjectExtension.getInstance(getProject()).setLanguageLevel(myOldLanguageLevel);
    LookupManager.getInstance(getProject()).hideActiveLookup();
    CodeStyleSettingsManager.getInstance(getProject()).dropTemporarySettings();
    super.tearDown();
  }

  @Override
  protected Sdk getProjectJDK() {
    return JavaSdkImpl.getMockJdk15("java 1.5");
  }
}
