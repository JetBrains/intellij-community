// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixTestCase;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.impl.preview.IntentionPreviewPopupUpdateProcessor;
import org.intellij.lang.regexp.inspection.DuplicateCharacterInClassInspection;

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
    String text = IntentionPreviewPopupUpdateProcessor.Companion.getPreviewText(getProject(), action, getFile(), getEditor());
    assertEquals("class Test {\n" +
                 "  public void test() {\n" +
                 "    int variable  ;variable = 2;\n" +
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
    String text = IntentionPreviewPopupUpdateProcessor.Companion.getPreviewText(getProject(), action, getFile(), getEditor());
    assertEquals("import static java.lang.Math.*;class Computer {\n" +
                 "    void f() {\n" +
                 "      double pi = PI;\n" +
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
    String text = IntentionPreviewPopupUpdateProcessor.Companion.getPreviewText(getProject(), action, getFile(), getEditor());
    assertEquals("[\"123]", text);
  }

  @Override
  protected void setupEditorForInjectedLanguage() {
    // we want to stay at host editor
  }
}
