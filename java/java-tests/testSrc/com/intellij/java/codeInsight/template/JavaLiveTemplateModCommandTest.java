// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.template;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.template.Expression;
import com.intellij.codeInsight.template.ExpressionContext;
import com.intellij.codeInsight.template.Result;
import com.intellij.codeInsight.template.TextResult;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateSettings;
import com.intellij.codeInsight.template.impl.TextExpression;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandExecutor;
import com.intellij.modcommand.ModStartTemplate;
import com.intellij.openapi.application.ReadAction;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static com.intellij.java.codeInsight.template.LiveTemplateFixtureKt.liveTemplateFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class JavaLiveTemplateModCommandTest {
  private static final TestFixture<LightJavaProject> projectFixture =
    lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_LATEST_WITH_LATEST_JDK);
  private final TestFixture<LiveTemplateFixture> liveTemplate =
    liveTemplateFixture(projectFixture, JavaTestUtil.getJavaTestDataPath() + "/codeInsight/template/");

  @Test
  public void testModCommandSout() {
    LightJavaCodeInsightTestFixture myFixture = liveTemplate.get().getFixture();
    TemplateImpl template = TemplateSettings.getInstance().getTemplate("sout", "Java");
    myFixture.configureByText("Test.java", """
      class X {
          void test() {
              <caret>
          }
      }
      """);
    EdtTestUtil.runInEdtAndWait(() -> runModCommand(template));
    myFixture.checkResult("""
                            class X {
                                void test() {
                                    System.out.println(<caret>);
                                }
                            }
                            """);
  }

  @Test
  public void testModCommandSoutm() {
    LightJavaCodeInsightTestFixture myFixture = liveTemplate.get().getFixture();
    TemplateImpl template = TemplateSettings.getInstance().getTemplate("soutm", "Java");
    myFixture.configureByText("Test.java", """
      class X {
          void test() {
              <caret>
          }
      }
      """);
    EdtTestUtil.runInEdtAndWait(() -> runModCommand(template));
    myFixture.checkResult("""
                            class X {
                                void test() {
                                    System.out.println("X.test");<caret>
                                }
                            }
                            """);
  }

  @Test
  public void testModCommandSoutp() {
    LightJavaCodeInsightTestFixture myFixture = liveTemplate.get().getFixture();
    TemplateImpl template = TemplateSettings.getInstance().getTemplate("soutp", "Java");
    myFixture.configureByText("Test.java", """
      class X {
          void test(String[] arr, int[][] arr2, int p) {
              <caret>
          }
      }
      """);
    EdtTestUtil.runInEdtAndWait(() -> runModCommand(template));
    myFixture.checkResult("""
      import java.util.Arrays;

      class X {
          void test(String[] arr, int[][] arr2, int p) {
              System.out.println("arr = " + Arrays.toString(arr) + ", arr2 = " + Arrays.deepToString(arr2) + ", p = " + p);
          }
      }
      """);
  }

  @Test
  public void testModCommandMain() {
    LightJavaCodeInsightTestFixture myFixture = liveTemplate.get().getFixture();
    TemplateImpl template = TemplateSettings.getInstance().getTemplate("main", "Java");
    myFixture.configureByText("Test.java", """
      class X {
          <caret>
      }
      """);
    EdtTestUtil.runInEdtAndWait(() -> runModCommand(template));
    myFixture.checkResult("""
                            class X {
                                public static void main(String[] args) {
                                    <caret>
                                }
                            }
                            """);
  }

  @Test
  public void testModCommandFori() {
    LightJavaCodeInsightTestFixture myFixture = liveTemplate.get().getFixture();
    TemplateImpl template = TemplateSettings.getInstance().getTemplate("fori", "Java");
    myFixture.configureByText("Test.java", """
      class X {
          void test() {
              <caret>
          }
      }
      """);
    EdtTestUtil.runInEdtAndWait(() -> runModCommand(template));
    myFixture.checkResult("""
                            class X {
                                void test() {
                                    for (int <selection>i<caret></selection> = 0; i < ; i++) {
                                       \s
                                    }
                                }
                            }
                            """);
  }

  @Test
  public void testModCommandIter() {
    LightJavaCodeInsightTestFixture myFixture = liveTemplate.get().getFixture();
    TemplateImpl template = TemplateSettings.getInstance().getTemplate("iter", "Java");
    myFixture.configureByText("Test.java", """
      final class X {
          static void main(String[] args) {
              <caret>
          }
      }
      """);
    EdtTestUtil.runInEdtAndWait(() -> runModCommand(template));
    myFixture.checkResult("""
                            final class X {
                                static void main(String[] args) {
                                    for (String arg : <selection>args<caret></selection>) {
                                       \s
                                    }
                                }
                            }
                            """);
  }

  @Test
  public void testModCommandIterElementTypeIsDependent() {
    LightJavaCodeInsightTestFixture myFixture = liveTemplate.get().getFixture();
    TemplateImpl template = TemplateSettings.getInstance().getTemplate("iter", "Java");
    myFixture.configureByText("Test.java", """
      final class X {
          static void main(String[] args) {
              <caret>
          }
      }
      """);

    ModCommand cmd = ReadAction.computeBlocking(() -> {
      ActionContext context = myFixture.getActionContext();
      return ModCommand.psiUpdate(context, updater -> TemplateManagerImpl.updateTemplate(template, updater));
    });

    List<ModStartTemplate.TemplateField> fields = cmd.unpack().stream()
      .filter(ModStartTemplate.class::isInstance)
      .map(ModStartTemplate.class::cast)
      .flatMap(s -> s.fields().stream())
      .toList();

    // Editable fields in declaration order: ITERABLE_TYPE first, VAR second.
    List<String> editableOrder = fields.stream()
      .filter(ModStartTemplate.ExpressionField.class::isInstance)
      .map(f -> ((ModStartTemplate.ExpressionField) f).varName())
      .toList();
    assertEquals(List.of("ITERABLE_TYPE", "VAR"), editableOrder);

    // ELEMENT_TYPE is registered as DependantVariableField with the raw expression string so
    // the template engine re-evaluates the macro on ITERABLE_TYPE edits.
    ModStartTemplate.DependantVariableField elementTypeField = fields.stream()
      .filter(ModStartTemplate.DependantVariableField.class::isInstance)
      .map(f -> (ModStartTemplate.DependantVariableField) f)
      .filter(f -> "ELEMENT_TYPE".equals(f.varName()))
      .findFirst()
      .orElseThrow(() -> new AssertionError("ELEMENT_TYPE DependantVariableField missing"));
    assertEquals("iterableComponentType(ITERABLE_TYPE)", elementTypeField.dependantVariableName());
    assertFalse(elementTypeField.alwaysStopAt());
  }

  @Test
  public void testModCommandItcoPreservesGenericType() {
    LightJavaCodeInsightTestFixture myFixture = liveTemplate.get().getFixture();
    TemplateImpl template = TemplateSettings.getInstance().getTemplate("itco", "Java");
    myFixture.configureByText("Test.java", """
      import java.util.Collection;
      class X {
          void m(Collection<Integer> a2) {
              <caret>
          }
      }
      """);
    EdtTestUtil.runInEdtAndWait(() -> runModCommand(template));
    String result = myFixture.getEditor().getDocument().getText();
    assertTrue(result.contains("Iterator<Integer>"),
               "Expected ITER_TYPE to preserve the generic argument (Iterator<Integer>). Actual:\n" + result);
  }

  @Test
  public void testModCommandItco() {
    LightJavaCodeInsightTestFixture myFixture = liveTemplate.get().getFixture();
    TemplateImpl template = TemplateSettings.getInstance().getTemplate("itco", "Java");
    myFixture.configureByText("Test.java", """
      import java.util.ArrayList;
      import java.util.List;
      class X {
          void m() {
              List<Integer> a = new ArrayList<>();
              <caret>
          }
      }
      """);
    EdtTestUtil.runInEdtAndWait(() -> runModCommand(template));
    myFixture.checkResult("""
                            import java.util.ArrayList;
                            import java.util.Iterator;
                            import java.util.List;
                            class X {
                                void m() {
                                    List<Integer> a = new ArrayList<>();
                                    for (Iterator<Integer> <selection>iterator<caret></selection> = a.iterator(); iterator.hasNext(); ) {
                                        Integer next = iterator.next();
                                       \s
                                    }
                                }
                            }
                            """);
  }

  @Test
  public void testModCommandSoutv() {
    LightJavaCodeInsightTestFixture myFixture = liveTemplate.get().getFixture();
    TemplateImpl template = TemplateSettings.getInstance().getTemplate("soutv", "Java");
    myFixture.configureByText("Test.java", """
      class X {
          void m() {
              int ar = 1;
              <caret>
          }
      }
      """);
    EdtTestUtil.runInEdtAndWait(() -> runModCommand(template));
    myFixture.checkResult("""
                            class X {
                                void m() {
                                    int ar = 1;
                                    System.out.println("ar = " + <selection>ar<caret></selection>);
                                }
                            }
                            """);
  }

  @Test
  public void testModCommandTransformingExprIsNotMirrored() {
    LightJavaCodeInsightTestFixture myFixture = liveTemplate.get().getFixture();
    myFixture.configureByText("Test.java", """
      class X {
          void m() {
              <caret>
          }
      }
      """);
    TemplateImpl template = new TemplateImpl("test", "X = $X$ Y = $Y$", "user");
    template.addVariable("X", new TextExpression("FOO"), new TextExpression("FOO"), true);
    // Y's expression: uppercase of X's value. On input "FOO" the result is "FOO" (matches X),
    // but it's NOT a real mirror — the sentinel probe in detectDependantName must reject it.
    Expression upper = new Expression() {
      @Override
      public Result calculateResult(ExpressionContext c) {
        TextResult v = c.getVariableValue("X");
        return v == null ? null : new TextResult(v.toString().toUpperCase(Locale.ROOT));
      }

      @Override
      public LookupElement[] calculateLookupItems(ExpressionContext c) {
        return null;
      }
    };
    template.addVariable("Y", upper, upper, false);

    ModCommand cmd = ReadAction.computeBlocking(() -> {
      ActionContext context = myFixture.getActionContext();
      return ModCommand.psiUpdate(context, updater -> TemplateManagerImpl.updateTemplate(template, updater));
    });

    long mirrors = cmd.unpack().stream()
      .filter(ModStartTemplate.class::isInstance)
      .map(ModStartTemplate.class::cast)
      .flatMap(s -> s.fields().stream())
      .filter(ModStartTemplate.DependantVariableField.class::isInstance)
      .count();
    assertEquals(0L, mirrors, "Y must not be registered as a mirror because uppercase(sentinel) != sentinel");
  }

  @Test
  public void testModCommandRepeatedVariableAtSameOffset() {
    LightJavaCodeInsightTestFixture myFixture = liveTemplate.get().getFixture();
    myFixture.configureByText("Test.java", """
      class X {
          void m() {
              <caret>
          }
      }
      """);
    // Template text has $X$$X$ — two segments of the same variable at the same text offset.
    TemplateImpl template = new TemplateImpl("test", "($X$$X$)", "user");
    template.addVariable("X", new TextExpression("AB"), new TextExpression("AB"), true);

    EdtTestUtil.runInEdtAndWait(() -> runModCommand(template));
    myFixture.checkResult("""
                            class X {
                                void m() {
                                    (<selection>AB<caret></selection>AB)
                                }
                            }
                            """);
  }

  private void runModCommand(TemplateImpl template) {
    LightJavaCodeInsightTestFixture myFixture = liveTemplate.get().getFixture();
    ActionContext context = myFixture.getActionContext();
    ModCommandExecutor.executeInteractively(
      context, "ModCommand", myFixture.getEditor(),
      () -> ModCommand.psiUpdate(context, (updater) -> TemplateManagerImpl.updateTemplate(template, updater)));
  }

}
