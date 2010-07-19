package com.intellij.codeInsight.completion;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.TestLookupManager;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.TestDataPath;

import java.io.File;

@TestDataPath("$CONTENT_ROOT/testData")
public class ClassNameCompletionTest extends CompletionTestCase {
  private static final String BASE_PATH = "/codeInsight/completion/className/";
  protected boolean myOldSetting;

  protected void setUp() throws Exception {
    super.setUp();
    setType(CompletionType.CLASS_NAME);
    myOldSetting = CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CLASS_NAME_COMPLETION;
    CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CLASS_NAME_COMPLETION = true;
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  protected Sdk getTestProjectJdk() {
    return JavaSdkImpl.getMockJdk17("java 1.5");
  }

  protected void tearDown() throws Exception {
    CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CLASS_NAME_COMPLETION = myOldSetting;
    super.tearDown();
  }

  public void testImportAfterNew() throws Exception {
    String path = BASE_PATH + "/importAfterNew";

    configureByFile(path + "/before1.java");
    checkResultByFile(path + "/after1.java");

    configureByFile(path + "/before2.java");
    checkResultByFile(path + "/after2.java");
  }

  public void testAfterNewThrowable1() throws Exception {
    String path = BASE_PATH + "/afterNewThrowable";

    configureByFile(path + "/before1.java");
    checkResultByFile(path + "/after1.java");
  }

  public void testAfterNewThrowable2() throws Exception {
    String path = BASE_PATH + "/afterNewThrowable";

    configureByFile(path + "/before2.java");
    checkResultByFile(path + "/after2.java");
  }

  public void testExcessParensAfterNew() throws Throwable { doTest(); }

  public void testReuseParensAfterNew() throws Throwable { doTest(); }

  public void testBracesAfterNew() throws Throwable { doTest(); }

  public void testInPlainTextFile() throws Throwable {
    configureByFile(BASE_PATH + getTestName(false) + ".txt");
    checkResultByFile(BASE_PATH +  getTestName(false) + "_after.txt");
  }

  public void testDoubleStringBuffer() throws Throwable {
    createClass("package java.lang; public class StringBuffer {}");
    doTest();
    assertNull(myItems);
  }

  public void testReplaceReferenceExpressionWithTypeElement() throws Throwable {
    createClass("package foo.bar; public class ABCDEF {}");
    doTest();
  }

  public void testCamelHumpPrefix() throws Throwable {
    String path = BASE_PATH + "/java/";
    configureByFile(path + getTestName(false) + ".java");
    complete();
    checkResultByFile(path + getTestName(false) + "_after.java");
    assertEquals(2, myItems.length);
  }

  private void doTest() throws Exception {
    String path = BASE_PATH + "/java/";
    configureByFile(path + getTestName(false) + ".java");
    checkResultByFile(path +  getTestName(false) + "_after.java");
  }

  public void testNameCompletionJava() throws Exception {
    String path = BASE_PATH + "/nameCompletion/java";
    configureByFile(path + "/test1-source.java");
    performAction();
    checkResultByFile(path + "/test1-result.java");
    configureByFile(path + "/test2-source.java");
    performAction();
    checkResultByFile(path + "/test2-result.java");
  }

  public void testImplementsFiltering1() throws Exception{
    final String path = BASE_PATH + "/nameCompletion/java";
    configureByFile(path + "/test4-source.java");
    performAction();
    checkResultByFile(path + "/test4-result.java");
  }

  public void testImplementsFiltering2() throws Exception{
    final String path = BASE_PATH + "/nameCompletion/java";
    configureByFile(path + "/test3-source.java");
    performAction();
    checkResultByFile(path + "/test3-result.java");

    configureByFile(path + "/implements2-source.java");
    performAction();
    checkResultByFile(path + "/implements2-result.java");

    configureByFile(path + "/implements3-source.java");
    performAction();
    checkResultByFile(path + "/implements3-result.java");
  }

  protected boolean clearModelBeforeConfiguring() {
    return "testAnnotationFiltering".equals(getName());
  }

  public void testAnnotationFiltering() throws Exception{
    final String path = BASE_PATH + "/nameCompletion/java";
    configureByFile(path + "/test7-source.java");
    performAction();
    checkResultByFile(path + "/test7-result.java");

    configureByFile(path + "/test8-source.java");
    performAction();
    checkResultByFile(path + "/test8-result.java");

    configureByFile(path + "/test9-source.java");
    performAction();
    checkResultByFile(path + "/test9-result.java");

    configureByFile(path + "/test9_2-source.java");
    performAction();
    checkResultByFile(path + "/test9_2-result.java");

    configureByFile(path + "/test9_3-source.java");
    performAction();
    checkResultByFile(path + "/test9_3-result.java");

    configureByFile(path + "/test11-source.java");
    performAction();
    checkResultByFile(path + "/test11-result.java");

    configureByFile(path + "/test10-source.java");
    performAction();
    checkResultByFile(path + "/test10-result.java");

    configureByFile(path + "/test12-source.java");
    performAction();
    checkResultByFile(path + "/test12-result.java");

    configureByFile(path + "/test13-source.java");
    performAction();
    checkResultByFile(path + "/test13-result.java");
  }

  public void testInMethodCall() throws Throwable {
    final String path = BASE_PATH + "/nameCompletion/java";
    configureByFile(path + "/methodCall-source.java");
    performAction();
    checkResultByFile(path + "/methodCall-result.java");
  }

  public void testInMethodCallQualifier() throws Throwable {
    final String path = BASE_PATH + "/nameCompletion/java";
    configureByFile(path + "/methodCall1-source.java");
    performAction();
    checkResultByFile(path + "/methodCall1-result.java");
  }

  public void testInVariableDeclarationType() throws Throwable {
    final String path = BASE_PATH + "/nameCompletion/java";
    configureByFile(path + "/varType-source.java");
    performAction();
    checkResultByFile(path + "/varType-result.java");
  }

  public void testExtraSpace() throws Throwable { doJavaTest(); }

  public void testAnnotation() throws Throwable { doJavaTest(); }

  public void testInStaticImport() throws Throwable { doJavaTest(); }

  public void testInCommentWithPackagePrefix() throws Throwable { doJavaTest(); }

  private void doJavaTest() throws Exception {
    final String path = BASE_PATH + "/nameCompletion/java";
    configureByFile(path + "/" + getTestName(false) + "-source.java");
    performAction();
    checkResultByFile(path + "/" + getTestName(false) + "-result.java");
  }

  protected void configureByFile(String filePath) throws Exception {
    final String path = getTestDataPath() + new File(filePath).getParent() + "/source";
    if (new File(path).exists()) {
      PsiTestUtil.createTestProjectStructure(myProject, myModule, path, myFilesToDelete);
    }
    super.configureByFile(filePath);
  }

  private void performAction() {
    CodeCompletionHandlerBase handler = new CodeCompletionHandlerBase(CompletionType.CLASS_NAME);
    handler.invoke(myProject, myEditor, myFile);
    final LookupManager instance = LookupManager.getInstance(myProject);
    if(instance instanceof TestLookupManager){
      final TestLookupManager testLookupManager = ((TestLookupManager)instance);
      if(testLookupManager.getActiveLookup() != null)
        testLookupManager.forceSelection(Lookup.NORMAL_SELECT_CHAR, 0);
    }
  }

  public void testQualifyNameOnSecondCompletion() throws Throwable {
    final Module module = ModuleManager.getInstance(getProject()).newModule("second.iml", new JavaModuleType());
    createClass(module, "package foo.bar; class AxBxCxDxEx {}");
    configureByFileNoCompletion(BASE_PATH + "/nameCompletion/java/" + getTestName(false) + "-source.java");
    new CodeCompletionHandlerBase(CompletionType.CLASS_NAME).invokeCompletion(myProject, myEditor, myFile, 2);
    checkResultByFile(BASE_PATH + "/nameCompletion/java/" + getTestName(false) + "-result.java");
  }
}
