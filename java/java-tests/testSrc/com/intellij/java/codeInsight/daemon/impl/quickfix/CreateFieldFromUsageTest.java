// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.impl.quickfix;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture;
import com.intellij.codeInsight.daemon.LightDaemonAnalyzerOptions;
import com.intellij.codeInsight.daemon.quickFix.LightQuickFixOptions;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.impl.preview.IntentionPreviewPopupUpdateProcessor;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture.lightDaemonAnalyzerFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;

@TestApplication
public class CreateFieldFromUsageTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor());
  private final TestFixture<LightDaemonAnalyzerFixture> daemonFixture = lightDaemonAnalyzerFixture(
    projectFixture, List::of,
    LightDaemonAnalyzerOptions.DEFAULT.withBasePath("/java/java-tests/testData/codeInsight/daemonCodeAnalyzer/quickFix/createFieldFromUsage"));

  @Test public void testSwitchCaseLabel() { doSingleTest(); }
  @Test public void testAnonymousClass() { doSingleTest(); }
  @Test public void testExpectedTypes() { doSingleTest(); }
  @Test public void testInterface() { doSingleTest(); }
  @Test public void testInterfaceField() { doSingleTest(); }
  @Test public void testSuperInterfaceConstant() { doSingleTest(); }
  @Test public void testMultipleTypes() { doSingleTest(); }
  @Test public void testMultipleTypes2() { doSingleTest(); }
  @Test public void testRecord() { doSingleTest(); }
  @Test public void testExistingField() { doSingleTest(); }
  @Test public void testParametericMethod() { doSingleTest(); }
  @Test public void testQualifyInner() { doSingleTest(); }
  @Test public void testTypeArgsFormatted() { doSingleTest(); }
  @Test public void testInsideStaticInnerClass() { doSingleTest(); }
  @Test public void testCreateFromEquals() { doSingleTest(); }
  @Test public void testCreateFromNestedPolyadic() { doSingleTest(); }
  @Test public void testCreateFromEqualsToPrimitiveType() { doSingleTest(); }
  @Test public void testInsideInterface() { doSingleTest(); }
  @Test public void testReferenceInCall() { doSingleTest(); }
  @Test public void testReferenceLambdaType() { doSingleTest(); }
  @Test public void testReferenceNull() { doSingleTest(); }

  @Test
  public void testWithAlignment() {
    final CommonCodeStyleSettings settings =
      CodeStyle.getSettings(daemonFixture.get().getFixture().getProject()).getCommonSettings(JavaLanguage.INSTANCE);
    boolean old = settings.ALIGN_GROUP_FIELD_DECLARATIONS;
    try {
      settings.ALIGN_GROUP_FIELD_DECLARATIONS = true;
      doSingleTest();
    }
    finally {
      settings.ALIGN_GROUP_FIELD_DECLARATIONS = old;
    }
  }

  @Test
  public void testSortByRelevance() throws IOException {
    Project project = daemonFixture.get().getFixture().getProject();
    WriteCommandAction.writeCommandAction(project).run(() -> {
      VirtualFile foo = projectFixture.get().getSourceRoot().createChildDirectory(this, "foo").createChildData(this, "Foo.java");
      VfsUtil.saveText(foo, "package foo; public class Foo { public void put(Object key, Object value) {} }");
      PsiDocumentManager.getInstance(project).commitAllDocuments();
    });
    PsiClass aClass = ReadAction.computeBlocking(() -> JavaPsiFacade.getInstance(project).findClass("foo.Foo", GlobalSearchScope.allScope(project)));
    Assertions.assertNotNull(aClass);
    doSingleTest();
  }

  @Test public void testDependantConstant() { doSingleTest(); }
  @Test public void testDependantConstant2() { doSingleTest(); }
  @Test public void testMethodReference() { doSingleTest(); }
  @Test public void testArrayBraces() { doSingleTest(); }
  @Test public void testInnerGeneric() { doSingleTest(); }
  @Test public void testInnerGenericArray() { doSingleTest(); }
  @Test public void testFromUnfinishedAnonymousClass() { doSingleTest(); }
  @Test public void testCreateFromAnnotationParameterIncorrectCode() { doSingleTest(); }
  @Test public void testAbstractClassIncorrectCode() { doSingleTest(); }
  @Test public void testBrokenCode() { doSingleTest(); }

  private void doSingleTest() {
    LightDaemonAnalyzerFixture daemon = daemonFixture.get();
    daemon.quickFixFixture(LightQuickFixOptions.DEFAULT.withPrefixActionHintMatch()).doTest(daemon.getFixture().getTestName(false) + ".java");
  }

  @TestApplication
  public static class PreviewTest {
    private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
    private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

    @Test
    public void testAnotherFile() {
      LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
      myFixture.addClass("class Another { }");
      myFixture.configureByText("Test.java", """
        class Test {
          void test() {
            Another.fo<caret>o;
          }
        }""");
      IntentionAction action = myFixture.findSingleIntention("Create field 'foo' in 'Another'");
      Assertions.assertNotNull(action);
      String text = ReadAction.computeBlocking(() -> IntentionPreviewPopupUpdateProcessor.getPreviewText(myFixture.getProject(), action, myFixture.getFile(), myFixture.getEditor()));
      Assertions.assertEquals("""
      class Another {
          public static Object foo;
      }""", text);
    }
  }
}
