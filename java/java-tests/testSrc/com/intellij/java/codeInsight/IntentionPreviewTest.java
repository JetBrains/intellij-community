// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.xml.analysis.XmlAnalysisBundle;
import org.intellij.lang.annotations.Language;
import org.intellij.lang.regexp.inspection.DuplicateCharacterInClassInspection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class IntentionPreviewTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_11_ANNOTATED;
  }

  public void testIntentionPreview() {
    myFixture.configureByText("Test.java", """
      class Test {
        public void test() {
          int <caret>variable = 2;
        }
      }""");
    IntentionAction action = myFixture.findSingleIntention("Split into declaration and assignment");
    assertPreviewText(action, """
      class Test {
        public void test() {
          int variable;
            variable = 2;
        }
      }""");
  }

  public void testIntentionPreviewAfterFileChange() {
    myFixture.configureByText("Test.java", """
      class Test {
          void f(Iterable<String> it) {
            it<caret>;
          }
        }""");
    IntentionAction action = myFixture.findSingleIntention("Iterate over Iterable<String>");
    myFixture.type("\b\b");
    PsiDocumentManager.getInstance(getProject()).commitDocument(myFixture.getFile().getViewProvider().getDocument());
    // should not be available anymore
    assertPreviewText(action, null);
  }

  public void testStaticImportsIntentionPreview() {
    myFixture.configureByText("Test.java", """
      class Computer {
          void f() {
            double pi = Ma<caret>th.PI;
          }
        }""");
    IntentionAction action = myFixture.findSingleIntention("Add on-demand static import for 'java.lang.Math'");
    assertPreviewText(action, """
      import static java.lang.Math.*;

      class Computer {
          void f() {
            double pi = PI;
          }
        }""");
  }

  public void testIntentionPreviewWithTemplate() {
    // TEMPLATE_STARTED_TOPIC event should not fire for preview editor
    getProject().getMessageBus().connect(getTestRootDisposable())
      .subscribe(TemplateManager.TEMPLATE_STARTED_TOPIC, state -> fail());
    myFixture.configureByText("Test.java", """
      class Computer {
          void f() {
            int i;
            int j = <caret>i;
          }
        }""");
    IntentionAction action = myFixture.findSingleIntention("Initialize variable 'i'");
    assertPreviewText(action, """
      class Computer {
          void f() {
            int i = 0;
            int j = i;
          }
        }""");
  }

  public void testIntentionPreviewIterate() {
    myFixture.configureByText("Test.java", """
      class Test {
          void f(Iterable<String> it) {
            <caret>it;
          }
        }""");
    IntentionAction action = myFixture.findSingleIntention("Iterate over Iterable<String>");

    assertPreviewText(action, """
      class Test {
          void f(Iterable<String> it) {
              for (String s : it) {

              }

          }
        }""");
  }

  public void testIntentionPreviewInjection() {
    myFixture.setCaresAboutInjection(false);
    myFixture.configureByText("Test.java", """
      import java.util.regex.Pattern;

      class Test {
        Pattern p = Pattern.compile("[\\"123<caret>1]");
      }""");
    myFixture.enableInspections(new DuplicateCharacterInClassInspection());
    IntentionAction action = myFixture.findSingleIntention("Remove duplicate '1' from character class");
    assertEquals("[\"123]", myFixture.getIntentionPreviewText(action));
  }

  public void testBindFieldsFromParameters() {
    myFixture.configureByText("Test.java", """
      public    class    Test {
          Test(int <caret>a, String b) {

          }
      }
      """);
    IntentionAction action = myFixture.findSingleIntention("Bind constructor parameters to fields");
    assertPreviewText(action, """
      public    class    Test {
          private final int a;
          private final String b;

          Test(int a, String b) {

              this.a = a;
              this.b = b;
          }
      }
      """);
  }

  public void testAddRemoveException() {
    myFixture.configureByText("Test.java", """
      import java.io.IOException;

      public class A {
        String test() {
          return "";
        }
      }

      class B extends A {
        String test() throws <caret>IOException {
          return "";
        }

      }""");
    IntentionAction action = myFixture.findSingleIntention("Remove 'IOException' from 'test()' throws list");
    assertPreviewText(action, """
      import java.io.IOException;

      public class A {
        String test() {
          return "";
        }
      }

      class B extends A {
        String test() {
          return "";
        }

      }""");
    action = myFixture.findSingleIntention("Add 'IOException' to 'A.test()' throws list");
    assertPreviewText(action, """
      import java.io.IOException;

      public class A {
        String test() throws IOException {
          return "";
        }
      }

      class B extends A {
        String test() throws IOException {
          return "";
        }

      }""");
  }

  public void testDefineDefaultValues() {
    myFixture.configureByText("Test.java",
                              """
                                public class Test {
                                    void test(int <caret>a,  String b) {

                                    }
                                }
                                """);
    IntentionAction action = myFixture.findSingleIntention("Generate overloaded method with default parameter values");
    assertPreviewText(action, """
      public class Test {
          void test() {
              test(0, null);
          }

          void test(int a, String b) {

          }
      }
      """);
  }

  private void assertPreviewText(@NotNull IntentionAction action, @Language("JAVA") @Nullable String expectedText) {
    assertEquals(expectedText, myFixture.getIntentionPreviewText(action));
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

  public void testPreviewInjectionCaret() {
    myFixture.setCaresAboutInjection(false);
    myFixture.configureByText("Test.java",
                              """
                                class Test {
                                  // language=HTML
                                  String s = "<a><caret></a><b></b>");
                                }""");

    IntentionAction action = myFixture.findSingleIntention(XmlAnalysisBundle.message("xml.quickfix.remove.tag.family"));
    assertEquals("<b></b>", myFixture.getIntentionPreviewText(action));
  }
}
