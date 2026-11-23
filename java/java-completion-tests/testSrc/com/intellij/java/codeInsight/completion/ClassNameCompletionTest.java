// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.completion;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.LightCompletionFixture;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.lang.java.JavaDocumentationProvider;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.testFramework.junit5.codeInsight.indexing.IndexingModeTemplate;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiClass;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.NeedsIndex;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static com.intellij.codeInsight.completion.LightCompletionFixtureKt.lightCompletionFixture;
import static com.intellij.codeInsight.completion.LightCompletionFixtureKt.lightCompletionProjectFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestDataPath("$CONTENT_ROOT/testData")
@TestApplication
@IndexingModeTemplate
public class ClassNameCompletionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightCompletionProjectFixture();
  private final TestFixture<LightCompletionFixture> completion =
    lightCompletionFixture(projectFixture, JavaTestUtil.getJavaTestDataPath() + "/codeInsight/completion/className/");

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      IdeaTestUtil.setProjectLanguageLevel(myFixture.getProject(), LanguageLevel.JDK_1_7);
    });
  }

  @Test
  @NeedsIndex.Full
  public void testImportAfterNew() {
    EdtTestUtil.runInEdtAndWait(() -> {
      createClass("package pack; public class AAClass {}");
      createClass("""
                    package pack; public class WithInnerAClass{
                      public static class Inner{}
                    }""");

      String path = "/importAfterNew";

      configureByFile(path + "/before1.java");
      completion.get().checkResultByFile(path + "/after1.java");

      configureByFile(path + "/before2.java");
      completion.get().selectItem(completion.get().getItems()[0]);
      completion.get().checkResultByFile(path + "/after2.java");
    });
  }

  @Test
  public void testDocAfterNew() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      createClass("public class Time { Time() {} Time(long time) {} }");

      String path = "/docAfterNew";

      configureByFile(path + "/before1.java");
      assertTrue(completion.get().getItems() != null && completion.get().getItems().length >= 1);
      String doc = new JavaDocumentationProvider().generateDoc(
        (PsiClass)completion.get().getItems()[0].getObject(),
        myFixture.getFile().findElementAt(myFixture.getEditor().getCaretModel().getOffset())
      );

      assertEquals("<html>Candidates for new <b>Time</b>() are:<br>&nbsp;&nbsp;<a href=\"psi_element://Time#Time()\">Time()</a>" +
                   "<br>&nbsp;&nbsp;<a href=\"psi_element://Time#Time(long)\">Time(long time)</a><br></html>",
                   doc);
    });
  }

  @Test
  @NeedsIndex.SmartMode(reason = "For now ConstructorInsertHandler.createOverrideRunnable doesn't work in dumb mode")
  public void testTypeParametersTemplate() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      createClass("package pack; public interface Foo<T> {void foo(T t};");

      String path = "/template";

      TemplateManagerImpl.setTemplateTesting(myFixture.getTestRootDisposable());
      configureByFile(path + "/before1.java");
      completion.get().selectItem(completion.get().getItems()[0]);
      TemplateState state = TemplateManagerImpl.getTemplateState(myFixture.getEditor());
      completion.get().type("String");
      assert state != null;
      state.gotoEnd(false);
      NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
      completion.get().checkResultByFile(path + "/after1.java");

      configureByFile(path + "/before2.java");
      completion.get().selectItem(completion.get().getItems()[0]);
      assert TemplateManagerImpl.getTemplateState(myFixture.getEditor()) == null;
      NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
      completion.get().checkResultByFile(path +"/after2.java");

      configureByFile(path + "/before3.java");
      completion.get().selectItem(completion.get().getItems()[0]);
      assert TemplateManagerImpl.getTemplateState(myFixture.getEditor()) == null;
      NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
      completion.get().checkResultByFile(path +"/after3.java");
    });
  }

  private void createClass(@NotNull @Language("JAVA") String text) {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.addClass(text);
    });
  }

  @Test
  public void testAfterNewThrowable1() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      addClassesForAfterNewThrowable();
      String path = "/afterNewThrowable";

      configureByFile(path + "/before1.java");
      myFixture.type('\n');
      completion.get().checkResultByFile(path + "/after1.java");
    });
  }

  private void addClassesForAfterNewThrowable() {
    EdtTestUtil.runInEdtAndWait(() -> {
      createClass("public class OurException extends Throwable{}");
      createClass("""
                    public class OurNotException {
                      public static class InnerException extends Throwable{}
                      public static class InnerNonException{}
                    }""");
    });
  }

  @Test
  @NeedsIndex.Full
  public void testAfterNewThrowable2() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      addClassesForAfterNewThrowable();
      String path = "/afterNewThrowable";

      configureByFile(path + "/before2.java");
      myFixture.type('\n');
      completion.get().checkResultByFile(path + "/after2.java");
    });
  }

  @Test
  public void testExcessParensAfterNew() { doTest(); }

  @Test
  public void testReuseParensAfterNew() { doTest(); }

  @Test
  public void testBracesAfterNew() { doTest(); }

  @Test
  @NeedsIndex.SmartMode(reason = "Smart completion in dumb mode not supported for property files")
  public void testInPropertiesFile() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.properties", "abc = StrinBui<caret>");
      complete();
      myFixture.checkResult("abc = java.lang.StringBuilder<caret>");
    });
  }

  @Test
  public void testInsideForwardReferencingTypeBound() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java", "class F<T extends Zo<caret>o, Zoo> {}");
      complete();
      myFixture.assertPreferredCompletionItems(0, "Zoo");
    });
  }

  @Test
  public void testDoubleStringBuffer() {
    EdtTestUtil.runInEdtAndWait(() -> {
      createClass("package java.lang; public class StringBuffer {}");
      doTest();
      assertNull(completion.get().getItems());
    });
  }

  @Test
  @NeedsIndex.Full
  public void testReplaceReferenceExpressionWithTypeElement() {
    EdtTestUtil.runInEdtAndWait(() -> {
      createClass("package foo.bar; public class ABCDEF {}");
      doTest();
    });
  }

  @Test
  public void testCamelHumpPrefix() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    String path = "/java/";
    EdtTestUtil.runInEdtAndWait(() -> {
      configureByFile(path + myFixture.getTestName(false) + ".java");
      complete();
      assertEquals(2, completion.get().getItems().length);
    });
  }

  private void doTest() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    String path = "/java/";
    EdtTestUtil.runInEdtAndWait(() -> {
      configureByFile(path + myFixture.getTestName(false) + ".java");
      completion.get().checkResultByFile(path + myFixture.getTestName(false) + "_after.java");
    });
  }

  @Test
  public void testNameCompletionJava() {
    String path = "/nameCompletion/java";
    EdtTestUtil.runInEdtAndWait(() -> {
      configureByFile(path + "/test1-source.java");
      performAction();
      completion.get().checkResultByFile(path + "/test1-result.java");
      configureByFile(path + "/test2-source.java");
      performAction();
      completion.get().checkResultByFile(path + "/test2-result.java");
    });
  }

  @Test
  public void testImplementsFiltering1() {
    final String path = "/nameCompletion/java";
    EdtTestUtil.runInEdtAndWait(() -> {
      configureByFile(path + "/test4-source.java");
      performAction();
      completion.get().checkResultByFile(path + "/test4-result.java");
    });
  }

  @Test
  public void testImplementsFiltering2() {
    final String path = "/nameCompletion/java";
    EdtTestUtil.runInEdtAndWait(() -> {
      configureByFile(path + "/test3-source.java");
      performAction();
      completion.get().checkResultByFile(path + "/test3-result.java");

      configureByFile(path + "/implements2-source.java");
      performAction();
      completion.get().checkResultByFile(path + "/implements2-result.java");

      configureByFile(path + "/implements3-source.java");
      performAction();
      completion.get().checkResultByFile(path + "/implements3-result.java");
    });
  }

  @Test
  @NeedsIndex.Full
  public void testAnnotationFiltering() {
    EdtTestUtil.runInEdtAndWait(() -> {
      createClass("@interface MyObjectType {}");

      final String path = "/nameCompletion/java";
      configureByFile(path + "/test8-source.java");
      performAction();
      completion.get().checkResultByFile(path + "/test8-result.java");
      cleanupVfs();

      configureByFile(path + "/test9-source.java");
      performAction();
      completion.get().checkResultByFile(path + "/test9-result.java");
      cleanupVfs();

      configureByFile(path + "/test9_2-source.java");
      performAction();
      completion.get().checkResultByFile(path + "/test9_2-result.java");
      cleanupVfs();

      configureByFile(path + "/test9_3-source.java");
      performAction();
      completion.get().checkResultByFile(path + "/test9_3-result.java");
      cleanupVfs();

      configureByFile(path + "/test11-source.java");
      performAction();
      completion.get().checkResultByFile(path + "/test11-result.java");
      cleanupVfs();

      configureByFile(path + "/test10-source.java");
      performAction();
      completion.get().checkResultByFile(path + "/test10-result.java");
      cleanupVfs();

      configureByFile(path + "/test12-source.java");
      performAction();
      completion.get().checkResultByFile(path + "/test12-result.java");
      cleanupVfs();

      configureByFile(path + "/test13-source.java");
      performAction();
      completion.get().checkResultByFile(path + "/test13-result.java");
    });
  }

  private void cleanupVfs() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
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
    });
  }

  @Test
  public void testInMethodCall() {
    final String path = "/nameCompletion/java";
    EdtTestUtil.runInEdtAndWait(() -> {
      configureByFile(path + "/methodCall-source.java");
      performAction();
      completion.get().checkResultByFile(path + "/methodCall-result.java");
    });
  }

  @Test
  public void testInMethodCallQualifier() {
    final String path = "/nameCompletion/java";
    EdtTestUtil.runInEdtAndWait(() -> {
      configureByFile(path + "/methodCall1-source.java");
      performAction();
      completion.get().checkResultByFile(path + "/methodCall1-result.java");
    });
  }

  @Test
  public void testInVariableDeclarationType() {
    final String path = "/nameCompletion/java";
    EdtTestUtil.runInEdtAndWait(() -> {
      configureByFile(path + "/varType-source.java");
      performAction();
      completion.get().checkResultByFile(path + "/varType-result.java");
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testExtraSpace() { doJavaTest('\n'); }

  @Test
  public void testAnnotation() { doJavaTest('\n'); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testInStaticImport() { doJavaTest('\n'); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testInCommentWithPackagePrefix() { doJavaTest('\n'); }

  @Test
  public void testNestedAnonymousTab() { doJavaTest('\t');}

  @Test
  @NeedsIndex.Full
  public void testClassStartsWithUnderscore() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.addClass("package foo; public class _SomeClass {}");
      doJavaTest('\n');
    });
  }

  @Test
  public void testNoInnerInaccessibleClass() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.addClass("package foo; interface Intf { interface InnerInterface {} }");
      doAntiTest();
    });
  }

  @Test
  @NeedsIndex.Full
  public void testPublicClassInPrivateSuper() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
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
    });
  }

  @Test
  @NeedsIndex.Full
  public void testImplicitClassWithNestedClass() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_24, () -> {
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
    });
  }

  private void doJavaTest(char toType) {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    final String path = "/nameCompletion/java";
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByFile(path + "/" + myFixture.getTestName(false) + "-source.java");
      performAction(toType);
      completion.get().checkResultByFile(path + "/" + myFixture.getTestName(false) + "-result.java");
    });
  }

  private void complete() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().setItems(myFixture.complete(CompletionType.BASIC, 2));
    });
  }

  private void configureByFile(String path) {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureFromExistingVirtualFile(myFixture.copyFileToProject(path, StringUtil.getShortName(path, '/')));
      complete();
    });
  }

  private void configureByTestName() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      configureByFile("/" + myFixture.getTestName(false) + ".java");
    });
  }

  private void doAntiTest() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      configureByTestName();
      completion.get().checkResultByFile(myFixture.getTestName(false) + ".java");
      assertThat(completion.get().getItems()).isEmpty();
      assertNull(completion.get().getLookup());
    });
  }

  private void performAction() {
    performAction('\n');
  }
  private void performAction(char toType) {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      complete();
      if (LookupManager.getActiveLookup(myFixture.getEditor()) != null) {
        myFixture.type(toType);
      }
    });
  }
}
