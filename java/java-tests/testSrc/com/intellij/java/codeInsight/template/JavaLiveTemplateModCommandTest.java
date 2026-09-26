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
import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

public class JavaLiveTemplateModCommandTest extends LiveTemplateTestCase {
  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_LATEST_WITH_LATEST_JDK;
  }

  public void testModCommandSout() {
    TemplateImpl template = TemplateSettings.getInstance().getTemplate("sout", "Java");
    configureByJavaText("Test.java", """
      class X {
          void test() {
              <caret>
          }
      }
      """);
    runModCommand(template);
    myFixture.checkResult("""
                            class X {
                                void test() {
                                    System.out.println(<caret>);
                                }
                            }
                            """);
  }

  public void testModCommandSoutm() {
    TemplateImpl template = TemplateSettings.getInstance().getTemplate("soutm", "Java");
    configureByJavaText("Test.java", """
      class X {
          void test() {
              <caret>
          }
      }
      """);
    runModCommand(template);
    myFixture.checkResult("""
                            class X {
                                void test() {
                                    System.out.println("X.test");<caret>
                                }
                            }
                            """);
  }

  public void testModCommandSoutp() {
    TemplateImpl template = TemplateSettings.getInstance().getTemplate("soutp", "Java");
    configureByJavaText("Test.java", """
      class X {
          void test(String[] arr, int[][] arr2, int p) {
              <caret>
          }
      }
      """);
    runModCommand(template);
    myFixture.checkResult("""
      import java.util.Arrays;

      class X {
          void test(String[] arr, int[][] arr2, int p) {
              System.out.println("arr = " + Arrays.toString(arr) + ", arr2 = " + Arrays.deepToString(arr2) + ", p = " + p);
          }
      }
      """);
  }

  public void testModCommandMain() {
    TemplateImpl template = TemplateSettings.getInstance().getTemplate("main", "Java");
    configureByJavaText("Test.java", """
      class X {
          <caret>
      }
      """);
    runModCommand(template);
    myFixture.checkResult("""
                            class X {
                                public static void main(String[] args) {
                                    <caret>
                                }
                            }
                            """);
  }

  public void testModCommandFori() {
    TemplateImpl template = TemplateSettings.getInstance().getTemplate("fori", "Java");
    configureByJavaText("Test.java", """
      class X {
          void test() {
              <caret>
          }
      }
      """);
    runModCommand(template);
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

  public void testModCommandIter() {
    TemplateImpl template = TemplateSettings.getInstance().getTemplate("iter", "Java");
    configureByJavaText("Test.java", """
      final class X {
          static void main(String[] args) {
              <caret>
          }
      }
      """);
    runModCommand(template);
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

  public void testModCommandIterElementTypeIsDependent() {
    TemplateImpl template = TemplateSettings.getInstance().getTemplate("iter", "Java");
    configureByJavaText("Test.java", """
      final class X {
          static void main(String[] args) {
              <caret>
          }
      }
      """);

    ActionContext context = myFixture.getActionContext();
    ModCommand cmd = ModCommand.psiUpdate(context, updater -> TemplateManagerImpl.updateTemplate(template, updater));

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

  public void testModCommandItcoPreservesGenericType() {
    TemplateImpl template = TemplateSettings.getInstance().getTemplate("itco", "Java");
    configureByJavaText("Test.java", """
      import java.util.Collection;
      class X {
          void m(Collection<Integer> a2) {
              <caret>
          }
      }
      """);
    runModCommand(template);
    String result = myFixture.getEditor().getDocument().getText();
    assertTrue("Expected ITER_TYPE to preserve the generic argument (Iterator<Integer>). Actual:\n" + result,
               result.contains("Iterator<Integer>"));
  }

  public void testModCommandItco() {
    TemplateImpl template = TemplateSettings.getInstance().getTemplate("itco", "Java");
    configureByJavaText("Test.java", """
      import java.util.ArrayList;
      import java.util.List;
      class X {
          void m() {
              List<Integer> a = new ArrayList<>();
              <caret>
          }
      }
      """);
    runModCommand(template);
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

  public void testModCommandSoutv() {
    TemplateImpl template = TemplateSettings.getInstance().getTemplate("soutv", "Java");
    configureByJavaText("Test.java", """
      class X {
          void m() {
              int ar = 1;
              <caret>
          }
      }
      """);
    runModCommand(template);
    myFixture.checkResult("""
                            class X {
                                void m() {
                                    int ar = 1;
                                    System.out.println("ar = " + <selection>ar<caret></selection>);
                                }
                            }
                            """);
  }

  public void testModCommandTransformingExprIsNotMirrored() {
    configureByJavaText("Test.java", """
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

    ActionContext context = myFixture.getActionContext();
    ModCommand cmd = ModCommand.psiUpdate(context, updater -> TemplateManagerImpl.updateTemplate(template, updater));

    long mirrors = cmd.unpack().stream()
      .filter(ModStartTemplate.class::isInstance)
      .map(ModStartTemplate.class::cast)
      .flatMap(s -> s.fields().stream())
      .filter(ModStartTemplate.DependantVariableField.class::isInstance)
      .count();
    assertEquals("Y must not be registered as a mirror because uppercase(sentinel) != sentinel",
                 0L, mirrors);
  }

  public void testModCommandRepeatedVariableAtSameOffset() {
    configureByJavaText("Test.java", """
      class X {
          void m() {
              <caret>
          }
      }
      """);
    // Template text has $X$$X$ — two segments of the same variable at the same text offset.
    TemplateImpl template = new TemplateImpl("test", "($X$$X$)", "user");
    template.addVariable("X", new TextExpression("AB"), new TextExpression("AB"), true);

    runModCommand(template);
    myFixture.checkResult("""
                            class X {
                                void m() {
                                    (<selection>AB<caret></selection>AB)
                                }
                            }
                            """);
  }

  private void runModCommand(TemplateImpl template) {
    ActionContext context = myFixture.getActionContext();
    ModCommandExecutor.executeInteractively(
      context, "ModCommand", myFixture.getEditor(),
      () -> ModCommand.psiUpdate(context, (updater) -> TemplateManagerImpl.updateTemplate(template, updater)));
  }

  @Override
  public final String getBasePath() {
    return basePath;
  }

  private final String basePath = JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/template/";
}
