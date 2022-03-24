// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixTestCase;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.impl.preview.IntentionPreviewPopupUpdateProcessor;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.intellij.lang.regexp.inspection.DuplicateCharacterInClassInspection;

import java.util.concurrent.ExecutionException;

public class IntentionPreviewTest extends LightQuickFixTestCase {
  public void testIntentionPreview() {
    configureFromFileText("Test.java",
                          "class Test {\n" +
                          "  public void test() {\n" +
                          "    int <caret>variable = 2;\n" +
                          "  }\n" +
                          "}");
    IntentionAction action = findActionWithText("Split into declaration and assignment");
    assertNotNull(action);
    String text = IntentionPreviewPopupUpdateProcessor.getPreviewText(getProject(), action, getFile(), getEditor());
    assertEquals("class Test {\n" +
                 "  public void test() {\n" +
                 "    int variable  ;\n" +
                 "      variable = 2;\n" +
                 "  }\n" +
                 "}", text);
  }

  public void testStaticImportsIntentionPreview() {
    configureFromFileText("Test.java",
                          "class Computer {\n" +
                          "    void f() {\n" +
                          "      double pi = Ma<caret>th.PI;\n" +
                          "    }\n" +
                          "  }");
    IntentionAction action = findActionWithText("Add on-demand static import for 'java.lang.Math'");
    assertNotNull(action);
    String text = IntentionPreviewPopupUpdateProcessor.getPreviewText(getProject(), action, getFile(), getEditor());
    assertEquals("import static java.lang.Math.*;\n" +
                 "\n" +
                 "class Computer {\n" +
                 "    void f() {\n" +
                 "      double pi = PI;\n" +
                 "    }\n" +
                 "  }", text);
  }

  public void testIntentionPreviewWithTemplate() {
    // TEMPLATE_STARTED_TOPIC event should not fire for preview editor
    getProject().getMessageBus().connect(getTestRootDisposable())
        .subscribe(TemplateManager.TEMPLATE_STARTED_TOPIC, state -> fail());
    configureFromFileText("Test.java",
                          "class Computer {\n" +
                          "    void f() {\n" +
                          "      int i;\n" +
                          "      int j = <caret>i;\n" +
                          "    }\n" +
                          "  }");
    IntentionAction action = findActionWithText("Initialize variable 'i'");
    assertNotNull(action);
    String text = getPreviewText(action);
    assertEquals("class Computer {\n" +
                 "    void f() {\n" +
                 "      int i = 0;\n" +
                 "      int j = i;\n" +
                 "    }\n" +
                 "  }", text);
  }

  public void testIntentionPreviewInjection() {
    configureFromFileText("Test.java",
                          "import java.util.regex.Pattern;\n" +
                          "\n" +
                          "class Test {\n" +
                          "  Pattern p = Pattern.compile(\"[\\\"123<caret>1]\");\n" +
                          "}");
    enableInspectionTool(new DuplicateCharacterInClassInspection());
    IntentionAction action = findActionWithText("Remove duplicate '1' from character class");
    assertNotNull(action);
    String text = getPreviewText(action);
    assertEquals("[\"123]", text);
  }

  public void testBindFieldsFromParameters() {
    configureFromFileText("Test.java",
                          "public    class    Test {\n" +
                          "    Test(int <caret>a, String b) {\n" +
                          "\n" +
                          "    }\n" +
                          "}\n");
    IntentionAction action = findActionWithText("Bind constructor parameters to fields");
    assertNotNull(action);
    String text = getPreviewText(action);
    assertEquals("public    class    Test {\n" +
                 "    private final int a;\n" +
                 "    private final String b;\n" +
                 "\n" +
                 "    Test(int a, String b) {\n" +
                 "\n" +
                 "    this.a = a;\n" +
                 "        this.b = b;\n" +
                 "    }\n" +
                 "}\n", text);
  }

  public void testDefineDefaultValues() {
    configureFromFileText("Test.java",
                          "public class Test {\n" +
                          "    void test(int <caret>a,  String b) {\n" +
                          "\n" +
                          "    }\n" +
                          "}\n");
    IntentionAction action = findActionWithText("Generate overloaded method with default parameter values");
    assertNotNull(action);
    String text = getPreviewText(action);
    assertEquals("public class Test {\n" +
                 "    void test() {\n" +
                 "        test(0, null);\n" +
                 "    }\n" +
                 "\n" +
                 "    void test(int a, String b) {\n" +
                 "\n" +
                 "    }\n" +
                 "}\n", text);
  }

  public void testRenameFile() {
    configureFromFileText("Test.java", "public class <caret>Best {}");
    IntentionAction action = findActionWithText("Rename File");
    assertNotNull(action);
    IntentionPreviewInfo info = IntentionPreviewPopupUpdateProcessor.getPreviewInfo(getProject(), action, getFile(), getEditor());
    assertTrue(info instanceof IntentionPreviewInfo.Html);
    HtmlChunk content = ((IntentionPreviewInfo.Html)info).content();
    assertEquals("<icon src=\"file\"/>&nbsp;Test.java &rarr; <icon src=\"file\"/>&nbsp;Best.java",
                 content.toString());
    assertNotNull(((IntentionPreviewInfo.Html)info).icon("file"));
  }

  @Override
  protected void setupEditorForInjectedLanguage() {
    // we want to stay at host editor
  }

  private String getPreviewText(IntentionAction action) {
    // Run in background thread to catch accidental write-actions during preview generation
    try {
      return ReadAction.nonBlocking(() -> IntentionPreviewPopupUpdateProcessor.getPreviewText(getProject(), action, getFile(), getEditor()))
        .submit(AppExecutorUtil.getAppExecutorService()).get();
    }
    catch (InterruptedException | ExecutionException e) {
      throw new RuntimeException(e);
    }
  }
}
