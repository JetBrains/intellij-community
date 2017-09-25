/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.java.codeInsight.completion;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.LightFixtureCompletionTestCase;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.lang.java.JavaDocumentationProvider;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiClass;
import com.intellij.testFramework.TestDataPath;

import java.io.IOException;

@TestDataPath("$CONTENT_ROOT/testData")
public class ClassNameCompletionTest extends LightFixtureCompletionTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    LanguageLevelProjectExtension.getInstance(getProject()).setLanguageLevel(LanguageLevel.JDK_1_7);
  }

  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/completion/className/";
  }

  public void testImportAfterNew() {
    createClass("package pack; public class AAClass {}");
    createClass("package pack; public class WithInnerAClass{\n" +
                "  public static class Inner{}\n" +
                "}");

    String path = "/importAfterNew";

    configureByFile(path + "/before1.java");
    checkResultByFile(path + "/after1.java");

    configureByFile(path + "/before2.java");
    selectItem(myItems[0]);
    checkResultByFile(path + "/after2.java");
  }

  public void testDocAfterNew() {
    createClass("public class Time { Time() {} Time(long time) {} }");

    String path = "/docAfterNew";

    configureByFile(path + "/before1.java");
    assertTrue(myItems != null && myItems.length >= 1);
    String doc = new JavaDocumentationProvider().generateDoc(
      (PsiClass)myItems[0].getObject(),
      myFixture.getFile().findElementAt(myFixture.getEditor().getCaretModel().getOffset())
    );

    assertEquals(doc,
                 "<html>Candidates for new <b>Time</b>() are:<br>&nbsp;&nbsp;<a href=\"psi_element://Time#Time()\">Time()</a><br>&nbsp;" +
                 "&nbsp;<a href=\"psi_element://Time#Time(long)\">Time(long time)</a><br></html>");
  }

  public void testTypeParametersTemplate() {
    createClass("package pack; public interface Foo<T> {void foo(T t};");

    String path = "/template";

    TemplateManagerImpl.setTemplateTesting(getProject(), myFixture.getTestRootDisposable());
    configureByFile(path + "/before1.java");
    selectItem(myItems[0]);
    TemplateState state = TemplateManagerImpl.getTemplateState(myFixture.getEditor());
    type("String");
    assert state != null;
    state.gotoEnd(false);
    checkResultByFile(path + "/after1.java");

    configureByFile(path + "/before2.java");
    selectItem(myItems[0]);
    assert TemplateManagerImpl.getTemplateState(myFixture.getEditor()) == null;
    checkResultByFile(path +"/after2.java");

    configureByFile(path + "/before3.java");
    selectItem(myItems[0]);
    assert TemplateManagerImpl.getTemplateState(myFixture.getEditor()) == null;
    checkResultByFile(path +"/after3.java");
  }

  private void createClass(String text) {
    myFixture.addClass(text);
  }

  public void testAfterNewThrowable1() {
    addClassesForAfterNewThrowable();
    String path = "/afterNewThrowable";

    configureByFile(path + "/before1.java");
    myFixture.type('\n');
    checkResultByFile(path + "/after1.java");
  }

  private void addClassesForAfterNewThrowable() {
    createClass("public class OurException extends Throwable{}");
    createClass("public class OurNotException {\n" +
                "  public static class InnerException extends Throwable{}\n" +
                "  public static class InnerNonException{}\n" +
                "}");
  }

  public void testAfterNewThrowable2() {
    addClassesForAfterNewThrowable();
    String path = "/afterNewThrowable";

    configureByFile(path + "/before2.java");
    myFixture.type('\n');
    checkResultByFile(path + "/after2.java");
  }

  public void testExcessParensAfterNew() { doTest(); }

  public void testReuseParensAfterNew() { doTest(); }

  public void testBracesAfterNew() { doTest(); }

  public void testInPlainTextFile() {
    configureByFile(getTestName(false) + ".txt");
    checkResultByFile(getTestName(false) + "_after.txt");
  }

  public void testInPropertiesFile() {
    myFixture.configureByText("a.properties", "abc = StrinBui<caret>");
    complete();
    myFixture.checkResult("abc = java.lang.StringBuilder<caret>");
  }

  public void testDoubleStringBuffer() {
    createClass("package java.lang; public class StringBuffer {}");
    doTest();
    assertNull(myItems);
  }

  public void testReplaceReferenceExpressionWithTypeElement() {
    createClass("package foo.bar; public class ABCDEF {}");
    doTest();
  }

  public void testCamelHumpPrefix() {
    String path = "/java/";
    configureByFile(path + getTestName(false) + ".java");
    complete();
    assertEquals(2, myItems.length);
  }

  private void doTest() {
    String path = "/java/";
    configureByFile(path + getTestName(false) + ".java");
    checkResultByFile(path + getTestName(false) + "_after.java");
  }

  public void testNameCompletionJava() {
    String path = "/nameCompletion/java";
    configureByFile(path + "/test1-source.java");
    performAction();
    checkResultByFile(path + "/test1-result.java");
    configureByFile(path + "/test2-source.java");
    performAction();
    checkResultByFile(path + "/test2-result.java");
  }

  public void testImplementsFiltering1() {
    final String path = "/nameCompletion/java";
    configureByFile(path + "/test4-source.java");
    performAction();
    checkResultByFile(path + "/test4-result.java");
  }

  public void testImplementsFiltering2() {
    final String path = "/nameCompletion/java";
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

  public void testAnnotationFiltering() {
    createClass("@interface MyObjectType {}");

    final String path = "/nameCompletion/java";
    configureByFile(path + "/test8-source.java");
    performAction();
    checkResultByFile(path + "/test8-result.java");
    cleanupVfs();

    configureByFile(path + "/test9-source.java");
    performAction();
    checkResultByFile(path + "/test9-result.java");
    cleanupVfs();

    configureByFile(path + "/test9_2-source.java");
    performAction();
    checkResultByFile(path + "/test9_2-result.java");
    cleanupVfs();

    configureByFile(path + "/test9_3-source.java");
    performAction();
    checkResultByFile(path + "/test9_3-result.java");
    cleanupVfs();

    configureByFile(path + "/test11-source.java");
    performAction();
    checkResultByFile(path + "/test11-result.java");
    cleanupVfs();

    configureByFile(path + "/test10-source.java");
    performAction();
    checkResultByFile(path + "/test10-result.java");
    cleanupVfs();

    configureByFile(path + "/test12-source.java");
    performAction();
    checkResultByFile(path + "/test12-result.java");
    cleanupVfs();

    configureByFile(path + "/test13-source.java");
    performAction();
    checkResultByFile(path + "/test13-result.java");
  }

  private void cleanupVfs() {
    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      public void run() {
        FileDocumentManager.getInstance().saveAllDocuments();
        for (VirtualFile file : myFixture.getTempDirFixture().getFile("").getChildren()) {
          try {
            file.delete(this);
          }
          catch (IOException e) {
            throw new RuntimeException(e);
          }
        }
      }
    });
  }

  public void testInMethodCall() {
    final String path = "/nameCompletion/java";
    configureByFile(path + "/methodCall-source.java");
    performAction();
    checkResultByFile(path + "/methodCall-result.java");
  }

  public void testInMethodCallQualifier() {
    final String path = "/nameCompletion/java";
    configureByFile(path + "/methodCall1-source.java");
    performAction();
    checkResultByFile(path + "/methodCall1-result.java");
  }

  public void testInVariableDeclarationType() {
    final String path = "/nameCompletion/java";
    configureByFile(path + "/varType-source.java");
    performAction();
    checkResultByFile(path + "/varType-result.java");
  }

  public void testExtraSpace() { doJavaTest(); }

  public void testAnnotation() { doJavaTest(); }

  public void testInStaticImport() { doJavaTest(); }

  public void testInCommentWithPackagePrefix() { doJavaTest(); }

  private void doJavaTest() {
    final String path = "/nameCompletion/java";
    myFixture.configureByFile(path + "/" + getTestName(false) + "-source.java");
    performAction();
    checkResultByFile(path + "/" + getTestName(false) + "-result.java");
  }

  @Override
  protected void complete() {
    myItems = myFixture.complete(CompletionType.BASIC, 2);
  }

  private void performAction() {
    complete();
    if (LookupManager.getActiveLookup(myFixture.getEditor()) != null) {
      myFixture.type('\n');
    }
  }
}
