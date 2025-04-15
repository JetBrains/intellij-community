// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.daemon.quickFix.ActionHint;
import com.intellij.codeInsight.daemon.quickFix.LightQuickFixTestCase;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.impl.preview.IntentionPreviewPopupUpdateProcessor;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class CreateFieldFromUsageTest extends LightQuickFixTestCase {

  public void testSwitchCaseLabel() { doSingleTest(); }
  public void testAnonymousClass() { doSingleTest(); }
  public void testExpectedTypes() { doSingleTest(); }
  public void testInterface() { doSingleTest(); }
  public void testInterfaceField() { doSingleTest(); }
  public void testSuperInterfaceConstant() { doSingleTest(); }
  public void testMultipleTypes() { doSingleTest(); }
  public void testMultipleTypes2() { doSingleTest(); }
  public void testRecord() { doSingleTest(); }
  public void testExistingField() { doSingleTest(); }
  public void testParametericMethod() { doSingleTest(); }
  public void testQualifyInner() { doSingleTest(); }
  public void testTypeArgsFormatted() { doSingleTest(); }
  public void testInsideStaticInnerClass() { doSingleTest(); }
  public void testCreateFromEquals() { doSingleTest(); }
  public void testCreateFromNestedPolyadic() { doSingleTest(); }
  public void testCreateFromEqualsToPrimitiveType() { doSingleTest(); }
  public void testInsideInterface() { doSingleTest(); }
  public void testReferenceInCall() { doSingleTest(); }
  public void testReferenceLambdaType() { doSingleTest(); }
  public void testReferenceNull() { doSingleTest(); }
  public void testWithAlignment() {
    final CommonCodeStyleSettings settings = CodeStyle.getSettings(getProject()).getCommonSettings(JavaLanguage.INSTANCE);
    boolean old = settings.ALIGN_GROUP_FIELD_DECLARATIONS;
    try {
      settings.ALIGN_GROUP_FIELD_DECLARATIONS = true;
      doSingleTest();
    }
    finally {
      settings.ALIGN_GROUP_FIELD_DECLARATIONS = old;
    }
  }

  public void testSortByRelevance() throws IOException {
    WriteCommandAction.writeCommandAction(getProject()).run(() -> {
      VirtualFile foo = getSourceRoot().createChildDirectory(this, "foo").createChildData(this, "Foo.java");
      VfsUtil.saveText(foo, "package foo; public class Foo { public void put(Object key, Object value) {} }");
      PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    });
    PsiClass aClass = JavaPsiFacade.getInstance(getProject()).findClass("foo.Foo", GlobalSearchScope.allScope(getProject()));
    assertNotNull(aClass);
    doSingleTest();
  }

  public void testDependantConstant() { doSingleTest(); }
  public void testDependantConstant2() { doSingleTest(); }

  public void testArrayBraces() {
    doSingleTest();
  }

  public void testInnerGeneric() { doSingleTest(); }

  public void testInnerGenericArray() { doSingleTest(); }
  
  public void testFromUnfinishedAnonymousClass() { doSingleTest(); }

  public void testCreateFromAnnotationParameterIncorrectCode() { doSingleTest(); }

  public void testAbstractClassIncorrectCode() { doSingleTest(); }

  protected void doSingleTest() {
    doSingleTest(getTestName(false) + ".java");
  }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/createFieldFromUsage";
  }

  @Override
  protected ActionHint parseActionHintImpl(@NotNull PsiFile file, @NotNull String contents) {
    return ActionHint.parse(file, contents, false);
  }

  public static class PreviewTest extends LightJavaCodeInsightFixtureTestCase {
    public void testAnotherFile() {
      myFixture.addClass("class Another { }");
      myFixture.configureByText("Test.java", """
        class Test {
          void test() {
            Another.fo<caret>o;
          }
        }""");
      IntentionAction action = myFixture.findSingleIntention("Create field 'foo' in 'Another'");
      assertNotNull(action);
      String text = IntentionPreviewPopupUpdateProcessor.getPreviewText(getProject(), action, getFile(), getEditor());
      assertEquals("""
      class Another {
          public static Object foo;
      }""", text);
    }
  }
}
