// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.xml.analysis.XmlAnalysisBundle;
import org.intellij.lang.annotations.Language;
import org.intellij.lang.regexp.inspection.DuplicateCharacterInClassInspection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

@TestApplication
public class IntentionPreviewTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_11_ANNOTATED);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testIntentionPreview() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
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

  @Test
  public void testIntentionPreviewAfterFileChange() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("Test.java", """
      class Test {
          void f(Iterable<String> it) {
            it<caret>;
          }
        }""");
    IntentionAction action = myFixture.findSingleIntention("Iterate over Iterable<String>");
    myFixture.type("\b\b");
    EdtTestUtil.runInEdtAndWait(() -> PsiDocumentManager.getInstance(myFixture.getProject()).commitDocument(myFixture.getFile().getViewProvider().getDocument()));
    // should not be available anymore
    assertPreviewText(action, null);
  }

  @Test
  public void testStaticImportsIntentionPreview() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
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

  @Test
  public void testIntentionPreviewWithTemplate() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    // TEMPLATE_STARTED_TOPIC event should not fire for preview editor
    myFixture.getProject().getMessageBus().connect(myFixture.getTestRootDisposable())
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

  @Test
  public void testIntentionPreviewIterate() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
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

  @Test
  public void testIntentionPreviewInjection() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.setCaresAboutInjection(false);
    myFixture.configureByText("Test.java", """
      import java.util.regex.Pattern;

      class Test {
        Pattern p = Pattern.compile("[\\"123<caret>1]");
      }""");
    myFixture.enableInspections(new DuplicateCharacterInClassInspection());
    IntentionAction action = myFixture.findSingleIntention("Remove duplicate '1' from character class");
    assertEquals("""
                   import java.util.regex.Pattern;
                                      
                   class Test {
                     Pattern p = Pattern.compile("[\\"123]");
                   }""", myFixture.getIntentionPreviewText(action));
  }

  @Test
  public void testBindFieldsFromParameters() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
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

  @Test
  public void testAddRemoveException() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
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

  @Test
  public void testDefineDefaultValues() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
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
          void test(String b) {
              test(0, b);
          }
      
          void test(int a, String b) {
      
          }
      }
      """);
  }

  private void assertPreviewText(@NotNull IntentionAction action, @Language("JAVA") @Nullable String expectedText) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    assertEquals(expectedText, myFixture.getIntentionPreviewText(action));
  }

  @Test
  public void testRenameFile() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("Test.java", "public class <caret>Best {}");
    IntentionAction action = myFixture.findSingleIntention("Rename File");
    myFixture.checkIntentionPreviewHtml(action, "<p><icon src=\"file\"/>&nbsp;Test.java &rarr; <icon src=\"file\"/>&nbsp;Best.java</p>");
  }

  @Test
  public void testNavigate() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("Test.java", "public class Test {} class <caret>Test {}");
    IntentionAction action = myFixture.findSingleIntention("Navigate to duplicate class");
    myFixture.checkIntentionPreviewHtml(action, "<p>&rarr; <icon src=\"icon\"/>&nbsp;Test.java, line #1</p>");
  }

  @Test
  public void testPreviewInjectionCaret() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
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

  @Test
  public void testModCommandFixPreviewWithCaretInInjection() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.setCaresAboutInjection(false);
    myFixture.enableInspections(new ReplaceLiteralInspection());
    myFixture.configureByText("Test.java",
                              """
                                class Test {
                                  // language=HTML
                                  String s = "<a><caret></a>";
                                }""");

    IntentionAction action = myFixture.findSingleIntention("Replace literal");
    assertPreviewText(action, """
      class Test {
        // language=HTML
        String s = "replaced";
      }""");
  }

  @Test
  public void testCaretOutsideOfProblem() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("Test.java", "class Test { int foo() { return 1 } }<caret>");
    String expected = "class Test { int foo() { return 1; } }";
    EdtTestUtil.runInEdtAndWait(() -> {
      assertEquals(expected, myFixture.getIntentionPreviewText("Insert"));
      myFixture.launchAction("Insert");
      myFixture.checkResult(expected);
    });
  }

  private static final class ReplaceLiteralInspection extends LocalInspectionTool {
    @Override
    public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
      return new JavaElementVisitor() {
        @Override
        public void visitLiteralExpression(@NotNull PsiLiteralExpression expression) {
          if (!(expression.getValue() instanceof String)) return;
          holder.problem(expression, "Literal can be replaced").fix(new ReplaceLiteralFix(expression)).register();
        }
      };
    }
  }

  private static final class ReplaceLiteralFix extends PsiUpdateModCommandAction<PsiLiteralExpression> {
    private ReplaceLiteralFix(@NotNull PsiLiteralExpression element) {
      super(element);
    }

    @Override
    public @NotNull String getFamilyName() {
      return "Replace literal";
    }

    @Override
    protected void invoke(@NotNull ActionContext context, @NotNull PsiLiteralExpression literal, @NotNull ModPsiUpdater updater) {
      literal.replace(JavaPsiFacade.getElementFactory(context.project()).createExpressionFromText("\"replaced\"", literal));
    }
  }
}
