package com.intellij.codeInsight.completion;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.LookupManagerImpl;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.TestDataPath;

import java.io.File;

@TestDataPath("$CONTENT_ROOT/testData")
public class ClassNameCompletionTest extends CompletionTestCase {
  private static final String BASE_PATH = "/codeInsight/completion/className/";

  protected boolean myOldSetting;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    setType(CompletionType.CLASS_NAME);
    myOldSetting = CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CLASS_NAME_COMPLETION;
    CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CLASS_NAME_COMPLETION = true;
    LanguageLevelProjectExtension.getInstance(getProject()).setLanguageLevel(LanguageLevel.JDK_1_7);
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  @Override
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

  public void testExcessParensAfterNew() throws Exception { doTest(); }

  public void testReuseParensAfterNew() throws Exception { doTest(); }

  public void testBracesAfterNew() throws Exception { doTest(); }

  public void testInPlainTextFile() throws Exception {
    configureByFile(BASE_PATH + getTestName(false) + ".txt");
    checkResultByFile(BASE_PATH + getTestName(false) + "_after.txt");
  }

  public void testDoubleStringBuffer() throws Exception {
    createClass("package java.lang; public class StringBuffer {}");
    doTest();
    assertNull(myItems);
  }

  public void testReplaceReferenceExpressionWithTypeElement() throws Exception {
    createClass("package foo.bar; public class ABCDEF {}");
    doTest();
  }

  public void testCamelHumpPrefix() throws Exception {
    String path = BASE_PATH + "/java/";
    configureByFile(path + getTestName(false) + ".java");
    complete();
    checkResultByFile(path + getTestName(false) + "_after.java");
    assertEquals(2, myItems.length);
  }

  private void doTest() throws Exception {
    String path = BASE_PATH + "/java/";
    configureByFile(path + getTestName(false) + ".java");
    checkResultByFile(path + getTestName(false) + "_after.java");
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

  public void testImplementsFiltering1() throws Exception {
    final String path = BASE_PATH + "/nameCompletion/java";
    configureByFile(path + "/test4-source.java");
    performAction();
    checkResultByFile(path + "/test4-result.java");
  }

  public void testImplementsFiltering2() throws Exception {
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

  @Override
  protected boolean clearModelBeforeConfiguring() {
    return "testAnnotationFiltering".equals(getName());
  }

  public void testAnnotationFiltering() throws Exception {
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

  public void testInMethodCall() throws Exception {
    final String path = BASE_PATH + "/nameCompletion/java";
    configureByFile(path + "/methodCall-source.java");
    performAction();
    checkResultByFile(path + "/methodCall-result.java");
  }

  public void testInMethodCallQualifier() throws Exception {
    final String path = BASE_PATH + "/nameCompletion/java";
    configureByFile(path + "/methodCall1-source.java");
    performAction();
    checkResultByFile(path + "/methodCall1-result.java");
  }

  public void testInVariableDeclarationType() throws Exception {
    final String path = BASE_PATH + "/nameCompletion/java";
    configureByFile(path + "/varType-source.java");
    performAction();
    checkResultByFile(path + "/varType-result.java");
  }

  public void testExtraSpace() throws Exception { doJavaTest(); }

  public void testAnnotation() throws Exception { doJavaTest(); }

  public void testInStaticImport() throws Exception { doJavaTest(); }

  public void testInCommentWithPackagePrefix() throws Exception { doJavaTest(); }

  public void testQualifyNameOnSecondCompletion() throws Exception {
    new WriteCommandAction.Simple(getProject()) {
      @Override
      protected void run() throws Exception {
        final Module module = ModuleManager.getInstance(getProject()).newModule("second.iml", new JavaModuleType());
        createClass(module, "package foo.bar; class AxBxCxDxEx {}");
      }
    }.execute().throwException();

    configureByFileNoCompletion(BASE_PATH + "/nameCompletion/java/" + getTestName(false) + "-source.java");
    new CodeCompletionHandlerBase(CompletionType.CLASS_NAME).invokeCompletion(myProject, myEditor, 2, false);
    checkResultByFile(BASE_PATH + "/nameCompletion/java/" + getTestName(false) + "-result.java");
  }

  public void testInMultiCatchType1() throws Exception { doJavaTest(); }

  public void testInMultiCatchType2() throws Exception { doJavaTest(); }

  public void testInResourceList1() throws Exception { doJavaTest(); }

  public void testInResourceList2() throws Exception { doJavaTest(); }

  public void testInResourceList3() throws Exception { doJavaTest(); }

  private void doJavaTest() throws Exception {
    final String path = BASE_PATH + "/nameCompletion/java";
    configureByFileNoCompletion(path + "/" + getTestName(false) + "-source.java");
    performAction();
    checkResultByFile(path + "/" + getTestName(false) + "-result.java");
  }

  @Override
  protected void configureByFile(String filePath) throws Exception {
    final String path = getTestDataPath() + new File(filePath).getParent() + "/source";
    if (new File(path).exists()) {
      PsiTestUtil.createTestProjectStructure(myProject, myModule, path, myFilesToDelete);
    }
    super.configureByFile(filePath);
  }

  private void performAction() {
    CodeCompletionHandlerBase handler = new CodeCompletionHandlerBase(CompletionType.CLASS_NAME);
    handler.invokeCompletion(myProject, myEditor);
    final LookupManager instance = LookupManager.getInstance(myProject);
    if (instance instanceof LookupManagerImpl) {
      final LookupManagerImpl testLookupManager = ((LookupManagerImpl)instance);
      if (testLookupManager.getActiveLookup() != null) {
        testLookupManager.forceSelection(Lookup.NORMAL_SELECT_CHAR, 0);
      }
    }
  }
}
