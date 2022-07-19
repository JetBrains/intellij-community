// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.intellij.lang.regexp.inspection.DuplicateCharacterInClassInspection;
import org.jetbrains.annotations.NotNull;

public class IntentionPreviewTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_11_ANNOTATED;
  }

  public void testIntentionPreview() {
    myFixture.configureByText("Test.java",
                          "class Test {\n" +
                          "  public void test() {\n" +
                          "    int <caret>variable = 2;\n" +
                          "  }\n" +
                          "}");
    IntentionAction action = myFixture.findSingleIntention("Split into declaration and assignment");
    String text = myFixture.getIntentionPreviewText(action);
    assertEquals("class Test {\n" +
                 "  public void test() {\n" +
                 "    int variable;\n" +
                 "      variable = 2;\n" +
                 "  }\n" +
                 "}", text);
  }

  public void testStaticImportsIntentionPreview() {
    myFixture.configureByText("Test.java",
                          "class Computer {\n" +
                          "    void f() {\n" +
                          "      double pi = Ma<caret>th.PI;\n" +
                          "    }\n" +
                          "  }");
    IntentionAction action = myFixture.findSingleIntention("Add on-demand static import for 'java.lang.Math'");
    String text = myFixture.getIntentionPreviewText(action);
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
    myFixture.configureByText("Test.java",
                          "class Computer {\n" +
                          "    void f() {\n" +
                          "      int i;\n" +
                          "      int j = <caret>i;\n" +
                          "    }\n" +
                          "  }");
    IntentionAction action = myFixture.findSingleIntention("Initialize variable 'i'");
    String text = myFixture.getIntentionPreviewText(action);
    assertEquals("class Computer {\n" +
                 "    void f() {\n" +
                 "      int i = 0;\n" +
                 "      int j = i;\n" +
                 "    }\n" +
                 "  }", text);
  }

  public void testIntentionPreviewIterate() {
    myFixture.configureByText("Test.java",
                          "class Test {\n" +
                          "    void f(Iterable<String> it) {\n" +
                          "      <caret>it;\n" +
                          "    }\n" +
                          "  }");
    IntentionAction action = myFixture.findSingleIntention("Iterate over Iterable<String>");

    String text = myFixture.getIntentionPreviewText(action);
    assertEquals("class Test {\n" +
                 "    void f(Iterable<String> it) {\n" +
                 "        for (String s : it) {\n" +
                 "\n" +
                 "        }\n" +
                 "\n" +
                 "    }\n" +
                 "  }", text);
  }

  public void testIntentionPreviewInjection() {
    myFixture.setCaresAboutInjection(false);
    myFixture.configureByText("Test.java",
                          "import java.util.regex.Pattern;\n" +
                          "\n" +
                          "class Test {\n" +
                          "  Pattern p = Pattern.compile(\"[\\\"123<caret>1]\");\n" +
                          "}");
    myFixture.enableInspections(new DuplicateCharacterInClassInspection());
    IntentionAction action = myFixture.findSingleIntention("Remove duplicate '1' from character class");
    String text = myFixture.getIntentionPreviewText(action);
    assertEquals("[\"123]", text);
  }

  public void testBindFieldsFromParameters() {
    myFixture.configureByText("Test.java",
                          "public    class    Test {\n" +
                          "    Test(int <caret>a, String b) {\n" +
                          "\n" +
                          "    }\n" +
                          "}\n");
    IntentionAction action = myFixture.findSingleIntention("Bind constructor parameters to fields");
    String text = myFixture.getIntentionPreviewText(action);
    assertEquals("public    class    Test {\n" +
                 "    private final int a;\n" +
                 "    private final String b;\n" +
                 "\n" +
                 "    Test(int a, String b) {\n" +
                 "\n" +
                 "        this.a = a;\n" +
                 "        this.b = b;\n" +
                 "    }\n" +
                 "}\n", text);
  }

  public void testAddRemoveException() {
    myFixture.configureByText("Test.java",
                          "import java.io.IOException;\n" +
                          "\n" +
                          "public class A {\n" +
                          "  String test() {\n" +
                          "    return \"\";\n" +
                          "  }\n" +
                          "}\n" +
                          "\n" +
                          "class B extends A {\n" +
                          "  String test() throws <caret>IOException {\n" +
                          "    return \"\";\n" +
                          "  }\n" +
                          "\n" +
                          "}");
    IntentionAction action = myFixture.findSingleIntention("Remove 'IOException' from 'test' throws list");
    assertEquals("import java.io.IOException;\n" +
                 "\n" +
                 "public class A {\n" +
                 "  String test() {\n" +
                 "    return \"\";\n" +
                 "  }\n" +
                 "}\n" +
                 "\n" +
                 "class B extends A {\n" +
                 "  String test() {\n" +
                 "    return \"\";\n" +
                 "  }\n" +
                 "\n" +
                 "}", myFixture.getIntentionPreviewText(action));
    action = myFixture.findSingleIntention("Add 'IOException' to 'A.test' throws list");
    assertEquals("import java.io.IOException;\n" +
                 "\n" +
                 "public class A {\n" +
                 "  String test() throws IOException {\n" +
                 "    return \"\";\n" +
                 "  }\n" +
                 "}\n" +
                 "\n" +
                 "class B extends A {\n" +
                 "  String test() throws IOException {\n" +
                 "    return \"\";\n" +
                 "  }\n" +
                 "\n" +
                 "}", myFixture.getIntentionPreviewText(action));
  }

  public void testDefineDefaultValues() {
    myFixture.configureByText("Test.java",
                          "public class Test {\n" +
                          "    void test(int <caret>a,  String b) {\n" +
                          "\n" +
                          "    }\n" +
                          "}\n");
    IntentionAction action = myFixture.findSingleIntention("Generate overloaded method with default parameter values");
    String text = myFixture.getIntentionPreviewText(action);
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
    myFixture.configureByText("Test.java", "public class <caret>Best {}");
    IntentionAction action = myFixture.findSingleIntention("Rename File");
    myFixture.checkIntentionPreviewHtml(action, "<p><icon src=\"file\"/>&nbsp;Test.java &rarr; <icon src=\"file\"/>&nbsp;Best.java</p>");
  }

  public void testMoveMemberIntoClass() {
    myFixture.configureByText("Test.java", "public class Test {} void <caret>method() {}");
    IntentionAction action = myFixture.findSingleIntention("Move member into class");
    myFixture.checkIntentionPreviewHtml(action,
                            "<p><icon src=\"source\"/>&nbsp;method &rarr; <icon src=\"target\"/>&nbsp;Test</p>");
  }

  public void testNavigate() {
    myFixture.configureByText("Test.java", "public class Test {} class <caret>Test {}");
    IntentionAction action = myFixture.findSingleIntention("Navigate to duplicate class");
    myFixture.checkIntentionPreviewHtml(action, "<p>&rarr; <icon src=\"icon\"/>&nbsp;Test.java, line #1</p>");
  }
}
