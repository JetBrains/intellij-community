// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.completion;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.LightFixtureCompletionTestCase;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.lang.java.JavaDocumentationProvider;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiClass;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.NeedsIndex;
import com.intellij.testFramework.TestDataPath;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

@TestDataPath("$CONTENT_ROOT/testData")
public class ClassNameCompletionTest extends LightFixtureCompletionTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    IdeaTestUtil.setProjectLanguageLevel(getProject(), LanguageLevel.JDK_1_7);
  }

  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/completion/className/";
  }

  @NeedsIndex.Full
  public void testImportAfterNew() {
    createClass("package pack; public class AAClass {}");
    createClass("""
                  package pack; public class WithInnerAClass{
                    public static class Inner{}
                  }""");

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

    assertEquals("<html>Candidates for new <b>Time</b>() are:<br>&nbsp;&nbsp;<a href=\"psi_element://Time#Time()\">Time()</a>" +
                 "<br>&nbsp;&nbsp;<a href=\"psi_element://Time#Time(long)\">Time(long time)</a><br></html>",
                 doc);
  }

  @NeedsIndex.SmartMode(reason = "For now ConstructorInsertHandler.createOverrideRunnable doesn't work in dumb mode")
  public void testTypeParametersTemplate() {
    createClass("package pack; public interface Foo<T> {void foo(T t};");

    String path = "/template";

    TemplateManagerImpl.setTemplateTesting(myFixture.getTestRootDisposable());
    configureByFile(path + "/before1.java");
    selectItem(myItems[0]);
    TemplateState state = TemplateManagerImpl.getTemplateState(myFixture.getEditor());
    type("String");
    assert state != null;
    state.gotoEnd(false);
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
    checkResultByFile(path + "/after1.java");

    configureByFile(path + "/before2.java");
    selectItem(myItems[0]);
    assert TemplateManagerImpl.getTemplateState(myFixture.getEditor()) == null;
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
    checkResultByFile(path +"/after2.java");

    configureByFile(path + "/before3.java");
    selectItem(myItems[0]);
    assert TemplateManagerImpl.getTemplateState(myFixture.getEditor()) == null;
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
    checkResultByFile(path +"/after3.java");
  }

  private void createClass(@NotNull @Language("JAVA") String text) {
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
    createClass("""
                  public class OurNotException {
                    public static class InnerException extends Throwable{}
                    public static class InnerNonException{}
                  }""");
  }

  @NeedsIndex.Full
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

  @NeedsIndex.SmartMode(reason = "Smart completion in dumb mode not supported for property files")
  public void testInPropertiesFile() {
    myFixture.configureByText("a.properties", "abc = StrinBui<caret>");
    complete();
    myFixture.checkResult("abc = java.lang.StringBuilder<caret>");
  }

  public void testInsideForwardReferencingTypeBound() {
    myFixture.configureByText("a.java", "class F<T extends Zo<caret>o, Zoo> {}");
    complete();
    myFixture.assertPreferredCompletionItems(0, "Zoo");
  }

  public void testDoubleStringBuffer() {
    createClass("package java.lang; public class StringBuffer {}");
    doTest();
    assertNull(myItems);
  }

  @NeedsIndex.Full
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

  @NeedsIndex.Full
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
      @Override
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

  @NeedsIndex.ForStandardLibrary
  public void testExtraSpace() { doJavaTest('\n'); }

  public void testAnnotation() { doJavaTest('\n'); }

  @NeedsIndex.ForStandardLibrary
  public void testInStaticImport() { doJavaTest('\n'); }

  @NeedsIndex.ForStandardLibrary
  public void testInCommentWithPackagePrefix() { doJavaTest('\n'); }

  public void testNestedAnonymousTab() { doJavaTest('\t');}

  @NeedsIndex.Full
  public void testClassStartsWithUnderscore() {
    myFixture.addClass("package foo; public class _SomeClass {}");
    doJavaTest('\n');
  }

  public void testNoInnerInaccessibleClass() {
    myFixture.addClass("package foo; interface Intf { interface InnerInterface {} }");
    doAntiTest();
  }

  @NeedsIndex.Full
  public void testPublicClassInPrivateSuper() {
    myFixture.addClass("""
                         package pkg;
                         public class Sub extends Super {
                         }
                         class Super {
                           public static class Foo {
                           }
                         }""");
    myFixture.configureByText("Main.java",
                              """
                                import pkg.*;
                                public class Main {
                                  public static void main(String[] args) {
                                    Sub.F<caret>
                                  }
                                }""");
    myFixture.completeBasic();
    myFixture.checkResult("""
                            import pkg.*;
                            public class Main {
                              public static void main(String[] args) {
                                Sub.Foo
                              }
                            }""");
  }

  @NeedsIndex.Full
  public void testImplicitClassWithNestedClass() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_24, () -> {
      myFixture.configureByText("Main.java", """
        public static void main(String[] args){
        }
        
        private class AAAAAAAAB{
        
          void test(){
            call(AAAAAAAA<caret>)
          }
        
          void call(Class<?> a){
        
          }
        }
        """);
      myFixture.completeBasic();
      myFixture.checkResult("""
                              public static void main(String[] args){
                              }
                              
                              private class AAAAAAAAB{
                              
                                void test(){
                                  call(AAAAAAAAB)
                                }
                              
                                void call(Class<?> a){
                              
                                }
                              }
                              """);
    });
  }

  private void doJavaTest(char toType) {
    final String path = "/nameCompletion/java";
    myFixture.configureByFile(path + "/" + getTestName(false) + "-source.java");
    performAction(toType);
    checkResultByFile(path + "/" + getTestName(false) + "-result.java");
  }

  @Override
  protected void complete() {
    myItems = myFixture.complete(CompletionType.BASIC, 2);
  }

  private void performAction() {
    performAction('\n');
  }
  private void performAction(char toType) {
    complete();
    if (LookupManager.getActiveLookup(myFixture.getEditor()) != null) {
      myFixture.type(toType);
    }
  }
}
