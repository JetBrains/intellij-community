// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.template;

import com.intellij.JavaTestUtil;
import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.daemon.impl.quickfix.EmptyExpression;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.template.EverywhereContextType;
import com.intellij.codeInsight.template.Expression;
import com.intellij.codeInsight.template.ExpressionContext;
import com.intellij.codeInsight.template.JavaCodeContextType;
import com.intellij.codeInsight.template.JavaCommentContextType;
import com.intellij.codeInsight.template.JavaStringContextType;
import com.intellij.codeInsight.template.Macro;
import com.intellij.codeInsight.template.Result;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateActionContext;
import com.intellij.codeInsight.template.TemplateContextType;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.impl.ConstantNode;
import com.intellij.codeInsight.template.impl.EmptyNode;
import com.intellij.codeInsight.template.impl.InvokeTemplateAction;
import com.intellij.codeInsight.template.impl.ListTemplatesHandler;
import com.intellij.codeInsight.template.impl.LiveTemplateCompletionContributor;
import com.intellij.codeInsight.template.impl.MacroCallNode;
import com.intellij.codeInsight.template.impl.SurroundWithTemplateHandler;
import com.intellij.codeInsight.template.impl.TemplateContext;
import com.intellij.codeInsight.template.impl.TemplateContextTypes;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateSettings;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.codeInsight.template.impl.TextExpression;
import com.intellij.codeInsight.template.impl.VariableNode;
import com.intellij.codeInsight.template.macro.CompleteMacro;
import com.intellij.codeInsight.template.macro.ConcatMacro;
import com.intellij.codeInsight.template.macro.FilePathMacroBase;
import com.intellij.codeInsight.template.macro.SplitWordsMacro;
import com.intellij.ide.DataManager;
import com.intellij.internal.statistic.FUCollectorTestCase;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.testFramework.DumbModeTestUtils;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.testFramework.fixtures.CodeInsightTestUtil;
import com.intellij.util.DocumentUtil;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.fus.reporting.model.lion3.LogEvent;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.intellij.java.codeInsight.template.LiveTemplateFixtureKt.liveTemplateFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("SpellCheckingInspection")
@TestApplication
public class LiveTemplateTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LiveTemplateFixture> lt =
    liveTemplateFixture(projectFixture, JavaTestUtil.getJavaTestDataPath() + "/codeInsight/template/");

  private void doTestTemplateWithArg(@NotNull String templateName,
                                     @NotNull String templateText,
                                     @NotNull String fileText,
                                     @NotNull String expected) {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
    configureFromFileText("dummy.java", fileText);
    final TemplateManager manager = TemplateManager.getInstance(myFixture.getProject());
    String group = "user";
    final Template template = manager.createTemplate(templateName, group, templateText);
    template.addVariable("ARG", "", "", false);
    TemplateContextType contextType = contextType(JavaCodeContextType.Generic.class);
    ((TemplateImpl)template).getTemplateContext().setEnabled(contextType, true);
    CodeInsightTestUtil.addTemplate(template, myFixture.getTestRootDisposable());

    EdtTestUtil.runInEdtAndWait(() -> {
      writeCommand(() -> manager.startTemplate(myFixture.getEditor(), '\t'));
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
      checkResultByText(expected);
    });
  }

  @Test
  public void testDependentSegmentsAtTheSamePosition() {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
    configureFromFileText("dummy.java", "class A { void test() { <caret> } }");
    TemplateManager manager = TemplateManager.getInstance(myFixture.getProject());
    final Template template = manager.createTemplate("test_template", "user_group", "$A$$B$ then \"$A$.$B$\"");
    template.addVariable("A", "", "", true);
    template.addVariable("B", "", "", true);
    EdtTestUtil.runInEdtAndWait(() -> {
      lt.get().startTemplate(template);
      myFixture.type("HELLO");
      myFixture.type("\t");
      myFixture.type("THERE");
      myFixture.type("\t");
      assertNull(lt.get().getState());
      checkResultByText("class A { void test() { HELLOTHERE then \"HELLO.THERE\" } }");
    });
  }

  @Test
  public void testTemplateWithSegmentsAtTheSamePosition1() {
    doTestTemplateWithThreeVariables("", "", "", "class A { void test() { for(TestValue1TestValue2TestValue3) {} } }");
  }

  @Test
  public void testTemplateWithSegmentsAtTheSamePosition2() {
    doTestTemplateWithThreeVariables("Def1", "Def2", "DefaultValue", "class A { void test() { for(Def1Def2DefaultValue) {} } }");
  }

  @Test
  public void testTemplateWithSegmentsAtTheSamePosition3() {
    doTestTemplateWithThreeVariables("", "DefaultValue", "", "class A { void test() { for(TestValue1DefaultValueTestValue3) {} } }");
  }

  @Test
  public void testTemplateWithSegmentsAtTheSamePosition4() {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
    configureFromFileText("dummy.java", "class A { void test() { <caret> } }");
    TemplateManager manager = TemplateManager.getInstance(myFixture.getProject());
    final Template template = manager.createTemplate("test_template", "user_group", "$A$$B$ then \"$A$$B$\"");
    template.addVariable("A", "", "\"Def1\"", true);
    template.addVariable("B", "", "\"Def2\"", true);
    EdtTestUtil.runInEdtAndWait(() -> {
      lt.get().startTemplate(template);
      checkResultByText("class A { void test() { Def1Def2 then \"Def1Def2\" } }");
    });
  }

  private void doTestTemplateWithThreeVariables(String firstDefaultValue,
                                                String secondDefaultValue,
                                                String thirdDefaultValue,
                                                String expectedText) {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
    configureFromFileText("dummy.java", "class A { void test() { <caret> } }");

    TemplateManager manager = TemplateManager.getInstance(myFixture.getProject());
    String templateName = "tst_template";
    String templateGroup = "user";
    final Template template = manager.createTemplate(templateName, templateGroup, "for($TEST1$$TEST2$$TEST3$) {}");
    template.addVariable("TEST1", "", StringUtil.wrapWithDoubleQuote(firstDefaultValue), true);
    template.addVariable("TEST2", "", StringUtil.wrapWithDoubleQuote(secondDefaultValue), true);
    template.addVariable("TEST3", "", StringUtil.wrapWithDoubleQuote(thirdDefaultValue), true);
    EdtTestUtil.runInEdtAndWait(() -> {
      lt.get().startTemplate(template);

      if (firstDefaultValue.isEmpty()) myFixture.type("TestValue1");
      myFixture.type("\t");
      if (secondDefaultValue.isEmpty()) myFixture.type("TestValue2");
      myFixture.type("\t");
      if (thirdDefaultValue.isEmpty()) myFixture.type("TestValue3");
      myFixture.type("\t");
      assertNull(lt.get().getState());
      checkResultByText(expectedText);
    });
  }

  @Test
  public void testTemplateWithArg1() {
    doTestTemplateWithArg("tst", "wrap($ARG$)", "tst arg<caret>", "wrap(arg)");
  }

  @Test
  public void testTemplateWithArg2() {
    doTestTemplateWithArg("tst#", "wrap($ARG$)", "tst#arg<caret>", "wrap(arg)");
  }

  @Test
  public void testTemplateWithArg3() {
    doTestTemplateWithArg("tst#", "wrap($ARG$)", "tst# arg<caret>", "tst# arg");
  }

  @Test
  public void testTemplateAtEndOfFile() {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
    configureFromFileText("empty.java", "");
    final TemplateManager manager = TemplateManager.getInstance(myFixture.getProject());
    final Template template = manager.createTemplate("empty", "user", "$VAR$");
    template.addVariable("VAR", "", "", false);

    EdtTestUtil.runInEdtAndWait(() -> {
      lt.get().startTemplate(template);
      checkResultByText("");
    });
  }

  @Test
  public void testTemplateWithEnd() {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
    configureFromFileText("empty.java", "");
    final TemplateManager manager = TemplateManager.getInstance(myFixture.getProject());
    final Template template = manager.createTemplate("empty", "user", "$VAR$$END$");
    template.addVariable("VAR", "bar", "bar", true);
    template.setToReformat(true);

    EdtTestUtil.runInEdtAndWait(() -> {
      lt.get().startTemplate(template);
      myFixture.type("foo");
      checkResultByText("foo");
    });
  }

  @Test
  public void testTemplateWithIndentedEnd() {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
    configureFromFileText("empty.java", """
      class C {
        bar() {
          <caret>
        }
      }""");
    TemplateManager manager = TemplateManager.getInstance(myFixture.getProject());
    Template template = manager.createTemplate("empty", "user", """
      foo();
      int i = 0;    $END$
      foo()""");
    template.setToReformat(true);
    EdtTestUtil.runInEdtAndWait(() -> {
      lt.get().startTemplate(template);
      checkResultByText("""
                          class C {
                            bar() {
                                foo();
                                int i = 0;    <caret>
                                foo()
                            }
                          }""");
    });
  }

  @Test
  public void testTemplateWithEndOnEmptyLine() {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
    configureFromFileText("empty.java", """
      class C {
        bar() {
          <caret>
        }
      }""");
    TemplateManager manager = TemplateManager.getInstance(myFixture.getProject());
    Template template = manager.createTemplate("empty", "user", """
      foo()
        $END$
      foo()""");
    template.setToReformat(true);
    EdtTestUtil.runInEdtAndWait(() -> {
      lt.get().startTemplate(template);
      checkResultByText("""
                          class C {
                            bar() {
                                foo()
                                <caret>
                                foo()
                            }
                          }""");
    });
  }

  private void checkResultByText(String text) {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
    myFixture.checkResult(text);
  }

  private void configureFromFileText(String name, String text) {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
    myFixture.configureByText(name, text);
  }

  @Test
  public void testEndInTheMiddle() {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
    configure();
    final TemplateManager manager = TemplateManager.getInstance(myFixture.getProject());
    final Template template = manager.createTemplate("frm", "user", """
      javax.swing.JFrame frame = new javax.swing.JFrame();
      $END$
      frame.setVisible(true);
      frame.setDefaultCloseOperation(javax.swing.JFrame.EXIT_ON_CLOSE);
      frame.pack();""");
    template.setToShortenLongNames(false);
    template.setToReformat(true);
    EdtTestUtil.runInEdtAndWait(() -> {
      lt.get().startTemplate(template);
      checkResult();
    });
  }

  @Test
  public void testHonorCustomCompletionCaretPlacement() {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
    myFixture.configureByText("a.java", """
      class Foo {
        void foo(int a) {}
        { <caret> }
      }
      """);
    final TemplateManager manager = TemplateManager.getInstance(myFixture.getProject());
    final Template template = manager.createTemplate("frm", "user", "$VAR$");
    template.addVariable("VAR", new MacroCallNode(new CompleteMacro()), new EmptyNode(), true);
    EdtTestUtil.runInEdtAndWait(() -> {
      lt.get().startTemplate(template);
      myFixture.type("fo\n");
      myFixture.checkResult("""
                              class Foo {
                                void foo(int a) {}
                                { foo(<caret>); }
                              }
                              """);
      assertFalse(lt.get().getState().isFinished());
    });
  }

  @Test
  public void testCancelTemplateWhenCompletionPlacedCaretOutsideTheVariable() {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
    myFixture.configureByText("a.java", """
      class Foo {
        void foo(int a) {}
        { <caret>() }
      }
      """);
    final TemplateManager manager = TemplateManager.getInstance(myFixture.getProject());
    final Template template = manager.createTemplate("frm", "user", "$VAR$");
    template.addVariable("VAR", new MacroCallNode(new CompleteMacro()), new EmptyNode(), true);
    EdtTestUtil.runInEdtAndWait(() -> {
      lt.get().startTemplate(template);
      myFixture.type("fo\n");
      myFixture.checkResult("""
                              class Foo {
                                void foo(int a) {}
                                { foo(<caret>); }
                              }
                              """);
      assertNull(lt.get().getState());
    });
  }

  private void checkResult() {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
    checkResultByFile(myFixture.getTestName(false) + "-out.java");
  }

  private void checkResultByFile(String s) {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
    myFixture.checkResultByFile(s);
  }

  public void startTemplate(String name, char expandKey) {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
    myFixture.type(name);
    myFixture.type(expandKey);
  }

  private static <T extends TemplateContextType> T contextType(Class<T> clazz) {
    return TemplateContextTypes.getByClass(clazz);
  }

  private void configure() {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
    myFixture.configureByFile(myFixture.getTestName(false) + ".java");
  }

  @Test
  public void testPreferStartMatchesInLookups() {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
    configure();
    EdtTestUtil.runInEdtAndWait(() -> {
      lt.get().startTemplate("iter", "Java");
      myFixture.type("ese\n");//for entrySet
      assertEquals(List.of("barGooStringBuilderEntry", "gooStringBuilderEntry", "stringBuilderEntry", "builderEntry", "entry"),
                   myFixture.getLookupElementStrings());
      myFixture.type("e");
      assertEquals(List.of("entry", "barGooStringBuilderEntry", "gooStringBuilderEntry", "stringBuilderEntry", "builderEntry"),
                   myFixture.getLookupElementStrings());
      assertEquals("entry", LookupManager.getActiveLookup(myFixture.getEditor()).getCurrentItem().getLookupString());
    });
  }

  @Test
  public void testClassNameDotInTemplate() {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
    CodeInsightSettings.getInstance().setCompletionCaseSensitive(CodeInsightSettings.NONE);
    configure();
    EdtTestUtil.runInEdtAndWait(() -> {
      lt.get().startTemplate("soutv", "Java");
      myFixture.type("File");
      assertEquals(List.of("file"), myFixture.getLookupElementStrings());
      myFixture.type(".");
      checkResult();
      assertFalse(lt.get().getState().isFinished());
    });
  }

  @Test
  public void testFinishTemplateVariantWithDot() {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
    CodeInsightSettings.getInstance().setSelectAutopopupSuggestionsByChars(true);
    configure();
    EdtTestUtil.runInEdtAndWait(() -> {
      lt.get().startTemplate("soutv", "Java");
      myFixture.type("fil");
      assertEquals(List.of("file"), myFixture.getLookupElementStrings());
      myFixture.type(".");
      checkResult();
      assertFalse(lt.get().getState().isFinished());
    });
  }

  @Test
  public void testAllowTypingRandomExpressionsWithLookupOpen() {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
    configure();
    EdtTestUtil.runInEdtAndWait(() -> {
      lt.get().startTemplate("iter", "Java");
      myFixture.type("file.");
      checkResult();
      assertFalse(lt.get().getState().isFinished());
    });
  }

  public void _testIterForceBraces() {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
    CommonCodeStyleSettings settings = CodeStyle.getSettings(myFixture.getProject()).getCommonSettings(JavaLanguage.INSTANCE);
    settings.IF_BRACE_FORCE = CommonCodeStyleSettings.FORCE_BRACES_ALWAYS;

    try {
      configure();
      lt.get().startTemplate("iter", "Java");
      checkResult();
    }
    finally {
      settings.IF_BRACE_FORCE = CommonCodeStyleSettings.DO_NOT_FORCE;
    }
  }

  @Test
  public void testOtherContext() {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_21, () -> {

      configureFromFileText("a.java", "class Foo { <caret>xxx }");
      Set<TemplateContextType> types = ReadAction.computeBlocking(
        () -> TemplateManagerImpl.getApplicableContextTypes(TemplateActionContext.expanding(myFixture.getFile(), myFixture.getEditor())));
      assertEquals(2, types.size());
      assertTrue(types.contains(TemplateContextTypes.getByClass(JavaCodeContextType.Declaration.class)));
      assertTrue(types.contains(TemplateContextTypes.getByClass(JavaCodeContextType.NormalClassDeclarationBeforeShortMainMethod.class)));

      configureFromFileText("a.txt", "class Foo { <caret>xxx }");
      Set<TemplateContextType> textTypes = ReadAction.computeBlocking(
        () -> TemplateManagerImpl.getApplicableContextTypes(TemplateActionContext.expanding(myFixture.getFile(), myFixture.getEditor())));
      assertThat(textTypes).hasSize(1);
      assertInstanceOf(EverywhereContextType.class, textTypes.iterator().next());
    });
  }

  @Test
  public void testJavaOtherContext() {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
    JavaCodeContextType.Statement stmtContext = TemplateContextTypes.getByClass(JavaCodeContextType.Statement.class);

    configureFromFileText("a.java", "class Foo {{ iter<caret>  }}");

    TemplateImpl template = TemplateSettings.getInstance().getTemplate("iter", "Java");
    ReadAction.runBlocking(() -> {
      assertTrue(lt.get().getTemplateManager()
                   .findMatchingTemplates(myFixture.getFile(), myFixture.getEditor(), Lookup.REPLACE_SELECT_CHAR, TemplateSettings.getInstance())
                   .containsKey(template));

      assertTrue(template.getTemplateContext().getOwnValue(stmtContext));
      assertFalse(template.getTemplateContext().getOwnValue(stmtContext.getBaseContextType()));
      template.getTemplateContext().setEnabled(stmtContext, false);
      template.getTemplateContext().setEnabled(stmtContext.getBaseContextType(), true);
      try {
        assertFalse(lt.get().getTemplateManager()
                      .findMatchingTemplates(myFixture.getFile(), myFixture.getEditor(), Lookup.REPLACE_SELECT_CHAR, TemplateSettings.getInstance())
                      .containsKey(template));
      }
      finally {
        template.getTemplateContext().setEnabled(stmtContext, true);
        template.getTemplateContext().setEnabled(stmtContext.getBaseContextType(), false);
      }
    });
  }

  @Test
  public void testDontSaveDefaultContexts() throws IOException, JDOMException {
    Element defElement = JDOMUtil.load("""
                                         <context>
                                           <option name="JAVA_STATEMENT" value="false"/>
                                           <option name="JAVA_CODE" value="true"/>
                                         </context>""");
    TemplateContext defContext = new TemplateContext();
    defContext.readTemplateContext(defElement);

    assertFalse(defContext.isEnabled(TemplateContextTypes.getByClass(JavaCodeContextType.Statement.class)));
    assertTrue(defContext.isEnabled(TemplateContextTypes.getByClass(JavaCodeContextType.Declaration.class)));
    assertTrue(defContext.isEnabled(TemplateContextTypes.getByClass(JavaCodeContextType.Generic.class)));

    TemplateContext copy = defContext.createCopy();

    Element write = copy.writeTemplateContext(null);
    assertEquals(2, write.getChildren().size());

    copy.setEnabled(TemplateContextTypes.getByClass(JavaCommentContextType.class), false);

    write = copy.writeTemplateContext(null);
    assertEquals(3, write.getChildren().size());
  }

  @Test
  public void testUseDefaultContextWhenEmpty() {
    TemplateContext context = new TemplateContext();
    context.readTemplateContext(new Element("context"));

    TemplateContext defContext = new TemplateContext();
    JavaCommentContextType commentContext = TemplateContextTypes.getByClass(JavaCommentContextType.class);
    defContext.setEnabled(commentContext, true);

    context.setDefaultContext(defContext);
    assertTrue(context.isEnabled(commentContext));
    assertFalse(context.isEnabled(TemplateContextTypes.getByClass(JavaCodeContextType.Generic.class)));
  }

  @Test
  public void testAddingNewContextToOther() throws IOException, JDOMException {
    Element defElement = JDOMUtil.load("""
                                         <context>
                                           <option name="OTHER" value="true"/>
                                         </context>""");
    TemplateContext context = new TemplateContext();
    context.readTemplateContext(defElement);

    JavaCodeContextType.Generic javaContext = TemplateContextTypes.getByClass(JavaCodeContextType.Generic.class);
    context.setEnabled(javaContext, true);

    Element saved = context.writeTemplateContext(null);

    context = new TemplateContext();
    context.readTemplateContext(saved);

    assertTrue(context.isEnabled(javaContext));
    assertTrue(context.isEnabled(TemplateContextTypes.getByClass(EverywhereContextType.class)));
  }

  private static void writeCommand(Runnable runnable) {
    WriteCommandAction.runWriteCommandAction(null, runnable);
  }

  @Test
  public void testSearchByDescriptionWhenTemplatesListed() {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
    myFixture.configureByText("a.java", "class A {{ <caret> }}");

    EdtTestUtil.runInEdtAndWait(() -> {
      new ListTemplatesHandler().invoke(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile());
      myFixture.type("array");
      assertTrue(myFixture.getLookupElementStrings().contains("itar"));
    });
  }

  @Test
  public void testListTemplatesSearchesPrefixInDescription() {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_21, () -> {
      myFixture.configureByText("a.java", "class A { main<caret> }");

      EdtTestUtil.runInEdtAndWait(() -> {
        new ListTemplatesHandler().invoke(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile());
        assertEquals(List.of("main", "psvm"), myFixture.getLookupElementStrings());
      });
    });
  }

  @Test
  public void testListTemplatesAction() {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
    myFixture.configureByText("a.java", "class A {{ <caret> }}");

    EdtTestUtil.runInEdtAndWait(() -> {
      new ListTemplatesHandler().invoke(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile());
      assertTrue(myFixture.getLookupElementStrings().containsAll(List.of("iter", "itco", "toar")));

      myFixture.type("it");
      assertTrue(myFixture.getLookupElementStrings().get(0).startsWith("it"));
      assertEquals(LookupManager.getInstance(myFixture.getProject()).getActiveLookup().getCurrentItem(), myFixture.getLookupElements()[0]);

      myFixture.type("e");
      assertTrue(myFixture.getLookupElementStrings().get(0).startsWith("ite"));
      assertEquals(LookupManager.getInstance(myFixture.getProject()).getActiveLookup().getCurrentItem(), myFixture.getLookupElements()[0]);
      LookupManager.getInstance(myFixture.getProject()).hideActiveLookup();

      myFixture.type("\b\b");
      new ListTemplatesHandler().invoke(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile());
      assertTrue(myFixture.getLookupElementStrings().containsAll(List.of("iter", "itco")));
      LookupManager.getInstance(myFixture.getProject()).hideActiveLookup();

      myFixture.type("xxxxx");
      new ListTemplatesHandler().invoke(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile());
      assertTrue(myFixture.getLookupElementStrings().containsAll(List.of("iter", "itco", "toar")));
      LookupManager.getInstance(myFixture.getProject()).hideActiveLookup();
    });
  }

  @Test
  public void testSelectionFromLookupBySpace() {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
    myFixture.configureByText("a.java", "class A {{ itc<caret> }}");

    EdtTestUtil.runInEdtAndWait(() -> {
      new ListTemplatesHandler().invoke(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile());
      myFixture.type(" ");
      myFixture.checkResult("""
                              import java.util.Iterator;

                              class A {{
                                  for (Iterator <selection>iterator</selection> = collection.iterator(); iterator.hasNext(); ) {
                                      Object next =  iterator.next();
                                     \s
                                  }
                              }}""");
    });
  }

  @Test
  public void testNavigationActionsDontTerminateTemplate() {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
    configureFromFileText("a.txt", "");

    final TemplateManager manager = TemplateManager.getInstance(myFixture.getProject());
    final Template template = manager.createTemplate("vn", "user", "Hello $V1$ World $V1$\nHello $V2$ World $V2$\nHello $V3$ World $V3$");
    template.addVariable("V1", "", "", true);
    template.addVariable("V2", "", "", true);
    template.addVariable("V3", "", "", true);
    EdtTestUtil.runInEdtAndWait(() -> {
      final Editor editor = myFixture.getEditor();

      lt.get().startTemplate(template);

      final TemplateState state = lt.get().getState();

      for (int i = 0; i < 3; i++) {
        assertFalse(state.isFinished(), String.valueOf(i));
        myFixture.type("H");
        final String docText = editor.getDocument().getText();
        assertTrue(docText.startsWith("Hello H World H\n"), docText);
        final int offset = editor.getCaretModel().getOffset();

        moveCaret(offset + 1);
        moveCaret(offset);

        myFixture.completeBasic();
        myFixture.type(" ");

        assertEquals(offset + 1, editor.getCaretModel().getOffset());
        assertFalse(state.isFinished());

        myFixture.type("\b");
        assertFalse(state.isFinished());
        writeCommand(() -> state.nextTab());
      }

      assertTrue(state.isFinished());
      checkResultByFile(myFixture.getTestName(false) + "-out.txt");
    });
  }

  private void moveCaret(final int offset) {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> myFixture.getEditor().getCaretModel().moveToOffset(offset));
  }

  @Test
  public void testUseDefaultValueForQuickResultCalculation() {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
    myFixture.configureByText("a.txt", "<caret>");

    final TemplateManager manager = TemplateManager.getInstance(myFixture.getProject());
    final Template template = manager.createTemplate("vn", "user", "$V1$ var = $V2$;");
    template.addVariable("V1", "", "", true);
    template.addVariable("V2", "", "\"239\"", true);

    EdtTestUtil.runInEdtAndWait(() -> {
      lt.get().startTemplate(template);

      myFixture.checkResult("<caret> var = 239;");

      myFixture.type("O");
      myFixture.checkResult("O<caret> var = 239;");

      myFixture.type("\t");
      myFixture.checkResult("O var = <selection>239</selection>;");
    });
  }

  @Test
  public void testTemplateExpandingWithSelection() {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
    final TemplateManager manager = TemplateManager.getInstance(myFixture.getProject());
    final Template template = manager.createTemplate("tpl", "user", "expanded");
    final JavaStringContextType contextType = contextType(JavaStringContextType.class);
    ((TemplateImpl)template).getTemplateContext().setEnabled(contextType, true);

    myFixture.configureByText("a.java", "class A { void f() { Stri<selection>ng s = \"tpl</selection><caret>\"; } }");

    CodeInsightTestUtil.addTemplate(template, myFixture.getTestRootDisposable());
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.type("\t");
      myFixture.checkResult("    class A { void f() { String s = \"tpl\"; } }");
    });
  }

  @Test
  public void testExpandCurrentLiveTemplateOnNoSuggestionsInLookup() {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
    myFixture.configureByText("a.java", "class Foo {{ <caret> }}");
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.completeBasic();
      assertNotNull(myFixture.getLookup());
      myFixture.type("sout");
      //This assert fails sporadically
      //assert myFixture.lookup
      //assert myFixture.lookupElementStrings == []
      myFixture.type("\t");
      myFixture.checkResult("class Foo {{\n    System.out.println(<caret>);\n}}");
    });
  }

  @Test
  public void testInvokeSurroundTemplateByTab() {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
    myFixture.configureByText("a.java", "class A { public void B() { I<caret> } }");
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.type("\t");
      WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> {
        TemplateState templateState = TemplateManagerImpl.getTemplateState(myFixture.getEditor());
        assertNotNull(templateState);
        templateState.nextTab();// Object
        templateState.nextTab();// o
      });

      myFixture.checkResult("""
                              class A { public void B() {
                                  for (Object o :) {
                                     \s
                                  }
                              } }""");
    });
  }

  @Test
  public void testStopAtENDWhenInvokedSurroundTemplateByTab() {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
    myFixture.configureByText("a.txt", "<caret>");

    final TemplateManager manager = TemplateManager.getInstance(myFixture.getProject());
    final Template template = manager.createTemplate("xxx", "user", "foo $ARG$ bar $END$ goo $SELECTION$ after");
    template.addVariable("ARG", "", "", true);

    EdtTestUtil.runInEdtAndWait(() -> {
      lt.get().startTemplate(template);
      myFixture.type("arg");
      lt.get().getState().nextTab();
      assertNull(lt.get().getState());
      checkResultByText("foo arg bar <caret> goo  after");
    });
  }

  @Test
  public void testStopAtSELECTIONWhenInvokedSurroundTemplateByTabAndENDMissing() {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
    myFixture.configureByText("a.txt", "<caret>");

    final TemplateManager manager = TemplateManager.getInstance(myFixture.getProject());
    final Template template = manager.createTemplate("xxx", "user", "foo $ARG$ bar goo $SELECTION$ after");
    template.addVariable("ARG", "", "", true);

    EdtTestUtil.runInEdtAndWait(() -> {
      lt.get().startTemplate(template);
      myFixture.type("arg");
      lt.get().getState().nextTab();
      assertNull(lt.get().getState());
      checkResultByText("foo arg bar goo <caret> after");
    });
  }

  @Test
  public void testConcatMacro() {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
    final TemplateManager manager = TemplateManager.getInstance(myFixture.getProject());
    final Template template = manager.createTemplate("result", "user", "$A$ $B$ c");
    template.addVariable("A", new EmptyNode(), true);

    MacroCallNode macroCallNode = new MacroCallNode(new ConcatMacro());
    macroCallNode.addParameter(new VariableNode("A", null));
    macroCallNode.addParameter(new TextExpression("ID"));
    template.addVariable("B", macroCallNode, false);

    myFixture.configureByText("a.txt", "<caret>");
    EdtTestUtil.runInEdtAndWait(() -> {
      lt.get().startTemplate(template);
      myFixture.type("tableName");
      lt.get().getState().nextTab();
      assertNull(lt.get().getState());
      myFixture.checkResult("tableName tableNameID c");
    });
  }

  @Test
  public void testSubstringBeforeMacro() {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
    final TemplateManager manager = TemplateManager.getInstance(myFixture.getProject());
    final Template template = manager.createTemplate("result", "user", "$A$ $B$ $C$");
    template.addVariable("A", "substringBefore(\"hello.world\", \".\")", "\"empty\"", false);
    template.addVariable("B", "substringBefore(\"hello world\", \".\")", "\"empty\"", false);
    template.addVariable("C", "substringBefore(\"hello world\")", "\"empty\"", false);
    myFixture.configureByText("a.txt", "<caret>");
    EdtTestUtil.runInEdtAndWait(() -> {
      lt.get().startTemplate(template);
      lt.get().getState().nextTab();
      assertNull(lt.get().getState());
      myFixture.checkResult("hello empty empty");
    });
  }

  @Test
  public void testSnakeCaseShouldConvertHyphensToUnderscores() {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
    final TemplateManager manager = TemplateManager.getInstance(myFixture.getProject());
    final Template template = manager.createTemplate("result", "user", "$A$ $B$ c");
    template.addVariable("A", new EmptyNode(), true);

    MacroCallNode macroCallNode = new MacroCallNode(new SplitWordsMacro.SnakeCaseMacro());
    macroCallNode.addParameter(new VariableNode("A", null));
    template.addVariable("B", macroCallNode, false);

    myFixture.configureByText("a.txt", "<caret>");
    EdtTestUtil.runInEdtAndWait(() -> {
      lt.get().startTemplate(template);
      myFixture.type("-foo-bar_goo-");
      lt.get().getState().nextTab();
      assertNull(lt.get().getState());
      myFixture.checkResult("-foo-bar_goo- _foo_bar_goo_ c<caret>");
    });
  }

  @Test
  public void testDoNotReplaceMacroValueWithNullResult() {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
    myFixture.configureByText("a.java", """
      class Foo {
        {
          <caret>
        }
      }
      """);
    final TemplateManager manager = TemplateManager.getInstance(myFixture.getProject());
    final Template template = manager.createTemplate("xxx", "user", "$VAR1$ $VAR2$ $VAR1$");
    template.addVariable("VAR1", "", "", true);
    template.addVariable("VAR2", new MacroCallNode(new FilePathMacroBase.FileNameMacro()), new ConstantNode("default"), true);
    ((TemplateImpl)template).getTemplateContext().setEnabled(contextType(JavaCodeContextType.Generic.class), true);
    CodeInsightTestUtil.addTemplate(template, myFixture.getTestRootDisposable());

    EdtTestUtil.runInEdtAndWait(() -> {
      lt.get().startTemplate(template);
      myFixture.checkResult("""
                              class Foo {
                                {
                                  <caret> a.java\s
                                }
                              }
                              """);
      myFixture.type("test");

      myFixture.checkResult("""
                              class Foo {
                                {
                                  test<caret> a.java test
                                }
                              }
                              """);
    });
  }

  @Test
  public void testDoReplaceMacroValueWithEmptyResult() {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
    myFixture.configureByText("a.java", """
      class Foo {
        {
          <caret>
        }
      }
      """);
    final TemplateManager manager = TemplateManager.getInstance(myFixture.getProject());
    final Template template = manager.createTemplate("xxx", "user", "$VAR1$ $VAR2$");
    template.addVariable("VAR1", "", "", true);
    template.addVariable("VAR2", new MacroCallNode(new MyMirrorMacro("VAR1")), null, true);
    ((TemplateImpl)template).getTemplateContext().setEnabled(contextType(JavaCodeContextType.class), true);
    CodeInsightTestUtil.addTemplate(template, myFixture.getTestRootDisposable());

    EdtTestUtil.runInEdtAndWait(() -> {
      lt.get().startTemplate(template);
      myFixture.checkResult("""
                              class Foo {
                                {
                                  <caret>\s
                                }
                              }
                              """);
      myFixture.type("42");
      myFixture.checkResult("""
                              class Foo {
                                {
                                  42<caret> 42
                                }
                              }
                              """);

      myFixture.type("\b\b");
      myFixture.checkResult("""
                              class Foo {
                                {
                                  <caret>\s
                                }
                              }
                              """);
    });
  }

  @Test
  public void testFilePathMacros() {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
    final VirtualFile file = myFixture.addFileToProject("foo/bar.txt", "").getVirtualFile();
    myFixture.configureFromExistingVirtualFile(file);

    Template template = lt.get().getTemplateManager().createTemplate("xxx", "user", "$VAR1$ $VAR2$");
    template.addVariable("VAR1", "filePath()", "", false);
    template.addVariable("VAR2", "fileRelativePath()", "", false);
    EdtTestUtil.runInEdtAndWait(() -> {
      lt.get().getTemplateManager().startTemplate(myFixture.getEditor(), template);

      myFixture.checkResult(FileUtil.toSystemDependentName(file.getPath() + " foo/bar.txt"));
    });
  }

  @Test
  public void testEscapeShouldNotMoveCaretToTheEndMarker() {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
    myFixture.configureByText("a.java", """
      class Foo {{
        itar<caret>
      }}
      """);
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.type("\ta");
      myFixture.performEditorAction(IdeActions.ACTION_EDITOR_ESCAPE);
      myFixture.checkResult("""
                              class Foo {{
                                  for (int a<caret> = 0; a < array.length; a++) {
                                       = array[a];
                                     \s
                                  }
                              }}
                              """);
    });
  }

  @Test
  public void testAddNewLineOnEnterOutsideEditingVariable() {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
    myFixture.configureByText("a.java", """
      class Foo {{
        <caret>
      }}
      """);
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.type("soutv\tabc");
      myFixture.getEditor().getCaretModel().moveCaretRelatively(3, 0, false, false, false);
      myFixture.type("\n");
      myFixture.checkResult("""
                              class Foo {{
                                  System.out.println("abc = " + abc);
                                  <caret>
                              }}
                              """);
    });
  }

  @Test
  public void testTypeTabCharacterOnTabOutsideEditingVariable() {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
    myFixture.configureByText("a.java", """
      class Foo {{
        <caret>
      }}
      """);
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.type("soutv\tabc");
      myFixture.getEditor().getCaretModel().moveCaretRelatively(2, 0, false, false, false);
      myFixture.type("\t");
      myFixture.checkResult("""
                              class Foo {{
                                  System.out.println("abc = " + abc); <caret>
                              }}
                              """);
    });
  }

  @Test
  public void testNextTabIsNotIsEvaluatedOnLookupElementInsertIfTemplateIsFinishedOrBrokenOff() {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
    myFixture.configureByText("a.java", """
      <caret>
      """);
    final TemplateManager manager = TemplateManager.getInstance(myFixture.getProject());
    final Template template = manager.createTemplate("imp", "user", "import $PKG$");
    Expression expr = new EmptyExpression() {
      @Override
      public LookupElement[] calculateLookupItems(ExpressionContext context) {
        InsertHandler<LookupElement> finishTemplateHandler = new InsertHandler<>() {
          @Override
          public void handleInsert(@NotNull InsertionContext insertCtx, @NotNull LookupElement item) {
            TemplateState stateRef = TemplateManagerImpl.getTemplateState(insertCtx.getEditor());
            assertFalse(stateRef.isFinished());
            stateRef.nextTab();
            assertTrue(stateRef.isFinished());
            stateRef.considerNextTabOnLookupItemSelected(item);
          }
        };
        return new LookupElement[] { LookupElementBuilder.create("com").withInsertHandler(finishTemplateHandler) };
      }
    };
    template.addVariable("PKG", expr, true);
    EdtTestUtil.runInEdtAndWait(() -> {
      lt.get().startTemplate(template);
      assertNotNull(myFixture.getLookup());
      myFixture.type("\n");
      myFixture.checkResult("""
                              import com<caret>
                              """);
      assertNull(lt.get().getState());
    });
  }

  @Test
  public void testDeleteAtTheLastTemplatePosition() {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
    myFixture.configureByText("a.java", """
      class Foo {{
        <caret>
      }}
      """);
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.type("iter\t");
      LightPlatformCodeInsightTestCase.delete(myFixture.getEditor(), myFixture.getProject());
      myFixture.checkResult("""
                              class Foo {{
                                  for (Object o : <caret> {
                                     \s
                                  }
                              }}
                              """);
    });
  }

  @Test
  public void testDeleteAtTheLastTemplatePositionWithSelection() {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
    myFixture.configureByText("a.java", """
      class Foo {{
        <caret>
      }}
      """);
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.type("iter\t");
      int startOffset = myFixture.getEditor().getCaretModel().getOffset();
      myFixture.type("foo");
      myFixture.getEditor().getSelectionModel().setSelection(startOffset, myFixture.getEditor().getCaretModel().getOffset());
      LightPlatformCodeInsightTestCase.delete(myFixture.getEditor(), myFixture.getProject());
      assertNotNull(lt.get().getState());
    });
  }

  @Test
  public void testMulticaretExpandingWithSpace() {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
    myFixture.configureByText("a.java", """
      class Foo {
        {
          <caret>
          <caret>
          <caret>
        }
      }
      """);
    char defaultShortcutChar = TemplateSettings.getInstance().getDefaultShortcutChar();
    EdtTestUtil.runInEdtAndWait(() -> {
      try {
        TemplateSettings.getInstance().setDefaultShortcutChar(TemplateSettings.SPACE_CHAR);
        startTemplate("sout", TemplateSettings.SPACE_CHAR);
      }
      finally {
        TemplateSettings.getInstance().setDefaultShortcutChar(defaultShortcutChar);
      }

      myFixture.checkResult("""
                              class Foo {
                                {
                                    System.out.println();
                                    System.out.println();
                                    System.out.println();
                                }
                              }
                              """);
    });
  }

  @Test
  public void testMulticaretExpandingWithEnter() {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
    myFixture.configureByText("a.java", """
      class Foo {
        {
          <caret>
          <caret>
          <caret>
        }
      }
      """);
    char defaultShortcutChar = TemplateSettings.getInstance().getDefaultShortcutChar();
    EdtTestUtil.runInEdtAndWait(() -> {
      try {
        TemplateSettings.getInstance().setDefaultShortcutChar(TemplateSettings.ENTER_CHAR);
        startTemplate("sout", TemplateSettings.ENTER_CHAR);
      }
      finally {
        TemplateSettings.getInstance().setDefaultShortcutChar(defaultShortcutChar);
      }

      myFixture.checkResult("""
                              class Foo {
                                {
                                    System.out.println();
                                    System.out.println();
                                    System.out.println();
                                }
                              }
                              """);
    });
  }

  @Test
  public void testMulticaretExpandingWithTab() {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
    myFixture.configureByText("a.java", """
      class Foo {
        {
          <caret>
          <caret>
          <caret>
        }
      }
      """);
    char defaultShortcutChar = TemplateSettings.getInstance().getDefaultShortcutChar();
    EdtTestUtil.runInEdtAndWait(() -> {
      try {
        TemplateSettings.getInstance().setDefaultShortcutChar(TemplateSettings.TAB_CHAR);
        startTemplate("sout", TemplateSettings.TAB_CHAR);
      }
      finally {
        TemplateSettings.getInstance().setDefaultShortcutChar(defaultShortcutChar);
      }


      myFixture.checkResult("""
                              class Foo {
                                {
                                    System.out.println();
                                    System.out.println();
                                    System.out.println();
                                }
                              }
                              """);
    });
  }

  @Test
  public void testFinishTemplateOnMovingCaretByCompletionInsertHandler() {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
    myFixture.configureByText("a.html", "<selection><p></p></selection>");
    TemplateImpl template = TemplateSettings.getInstance().getTemplate("T", "HTML/XML");
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.testAction(new InvokeTemplateAction(template, myFixture.getEditor(), myFixture.getProject(), new HashSet<>()));
      myFixture.complete(CompletionType.BASIC);
      myFixture.type("nofra");
      myFixture.finishLookup(Lookup.REPLACE_SELECT_CHAR);
      myFixture.checkResult("<noframes><caret><p></p></noframes>");
      assertNull(lt.get().getTemplateManager().getActiveTemplate(myFixture.getEditor()));
    });
  }

  @Test
  public void testGotoNextVariableWhenCompletionInsertHandlerMovesTowardIt() {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
    myFixture.configureByText("a.java", "class C { void foo(C c, String s) {}; { <caret> } }");

    Template template = lt.get().getTemplateManager().createTemplate("empty", "user", "foo($CS$, \"$S$\");");
    template.addVariable("CS", "completeSmart()", "", true);
    template.addVariable("S", "", "\"\"", true);
    EdtTestUtil.runInEdtAndWait(() -> {
      lt.get().getTemplateManager().startTemplate(myFixture.getEditor(), template);
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();

      assertTrue(myFixture.getEditor().getDocument().getText().contains("foo(this, \"\");"));
      assertNotNull(lt.get().getState());

      myFixture.type("string\n");
      assertTrue(myFixture.getEditor().getDocument().getText().contains("foo(this, \"string\");"));
      assertNull(lt.get().getState());
    });
  }

  @Test
  public void testEscapeWithSelection() {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
    myFixture.configureByText("a.java", """
      class Foo {
        {
            soutv<caret>
        }
      }
      """);
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.type("\tfoo");
      myFixture.getEditor().getSelectionModel().setSelection(myFixture.getCaretOffset() - 3, myFixture.getCaretOffset());
      assertTrue(myFixture.getEditor().getSelectionModel().hasSelection());

      myFixture.performEditorAction(IdeActions.ACTION_EDITOR_ESCAPE);
      assertFalse(myFixture.getEditor().getSelectionModel().hasSelection());
      assertNotNull(lt.get().getTemplateManager().getActiveTemplate(myFixture.getEditor()));

      myFixture.performEditorAction(IdeActions.ACTION_EDITOR_ESCAPE);
      assertNull(lt.get().getTemplateManager().getActiveTemplate(myFixture.getEditor()));

      myFixture.checkResult("""
                              class Foo {
                                {
                                    System.out.println("foo = " + foo<caret>);
                                }
                              }
                              """);
    });
  }

  @Test
  public void testEscapeWithLookup() {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
    myFixture.configureByText("a.java", """
      class Foo {
        {
            int foo_1, foo_2;
            soutv<caret>
        }
      }
      """);
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.type("\t");
      assertNotNull(myFixture.getLookup());

      myFixture.performEditorAction(IdeActions.ACTION_EDITOR_ESCAPE);
      assertNull(myFixture.getLookup());
      assertNotNull(lt.get().getTemplateManager().getActiveTemplate(myFixture.getEditor()));

      myFixture.performEditorAction(IdeActions.ACTION_EDITOR_ESCAPE);
      assertNull(lt.get().getTemplateManager().getActiveTemplate(myFixture.getEditor()));

      myFixture.checkResult("""
                              class Foo {
                                {
                                    int foo_1, foo_2;
                                    System.out.println("foo_1 = " + foo_1);
                                }
                              }
                              """);
    });
  }

  @Test
  public void testEscapeWithLookupAndSelection() {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
    myFixture.configureByText("a.java", """
      class Foo {
        {
            int foo_1, foo_2;
            soutv<caret>
        }
      }
      """);
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.type("\tfoo");
      myFixture.getEditor().getSelectionModel().setSelection(myFixture.getCaretOffset() - 3, myFixture.getCaretOffset());
      myFixture.completeBasic();
      assertTrue(myFixture.getEditor().getSelectionModel().hasSelection());
      assertNotNull(myFixture.getLookup());

      myFixture.performEditorAction(IdeActions.ACTION_EDITOR_ESCAPE);
      assertFalse(myFixture.getEditor().getSelectionModel().hasSelection());
      assertNull(myFixture.getLookup());
      assertNotNull(lt.get().getTemplateManager().getActiveTemplate(myFixture.getEditor()));

      myFixture.performEditorAction(IdeActions.ACTION_EDITOR_ESCAPE);
      assertNull(lt.get().getTemplateManager().getActiveTemplate(myFixture.getEditor()));

      myFixture.checkResult("""
                              class Foo {
                                {
                                    int foo_1, foo_2;
                                    System.out.println("foo = " + foo<caret>);
                                }
                              }
                              """);
    });
  }

  @Test
  public void testEscapeWithLookupWithoutSelection() {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
    myFixture.configureByText("a.java", """                                                                                                                                                                                                                                                                                 
      class Foo {                                                                                                                                                                                                                                                                                                           
        {                                                                                                                                                                                                                                                                                                                   
            int foo_1, foo_2;                                                                                                                                                                                                                                                                                               
            soutv<caret>                                                                                                                                                                                                                                                                                                    
        }                                                                                                                                                                                                                                                                                                                   
      }                                                                                                                                                                                                                                                                                                                     
      """);
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.type("\tfoo");        // expand + type collapses the segment selection                                                                                                                                                                                                                                        
      myFixture.completeBasic();      // 2 matches -> lookup stays open with a current item                                                                                                                                                                                                                                   
      assertFalse(myFixture.getEditor().getSelectionModel().hasSelection());
      assertNotNull(myFixture.getLookup().getCurrentItem());

      myFixture.performEditorAction(IdeActions.ACTION_EDITOR_ESCAPE);
      assertNull(myFixture.getLookup());                                              // popup dismissed                                                                                                                                                                                                                      
      assertNotNull(lt.get().getTemplateManager().getActiveTemplate(myFixture.getEditor()));   // template survives                                                                                                                                                                                                                    

      myFixture.performEditorAction(IdeActions.ACTION_EDITOR_ESCAPE);
      assertNull(lt.get().getTemplateManager().getActiveTemplate(myFixture.getEditor()));      // 2nd Esc finishes                                                                                                                                                                                                                     
    });
  }

  @Test
  public void testEscapeWithEmptyLookup() {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
    myFixture.configureByText("a.java", """
      class Foo {
        {
            int foo_1, foo_2;
            soutv<caret>
        }
      }
      """);
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.type("\tfoobar");
      assertNotNull(myFixture.getLookup());
      assertNull(myFixture.getLookup().getCurrentItem());

      myFixture.performEditorAction(IdeActions.ACTION_EDITOR_ESCAPE);
      assertNull(myFixture.getLookup());
      assertNull(lt.get().getTemplateManager().getActiveTemplate(myFixture.getEditor()));

      myFixture.checkResult("""
                              class Foo {
                                {
                                    int foo_1, foo_2;
                                    System.out.println("foobar = " + foobar);
                                }
                              }
                              """);
    });
  }

  @Test
  public void testHomeEndGoOutsideTemplateFragmentsIfAlreadyOnTheirBounds() {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
    myFixture.configureByText("a.txt", " <caret> g");

    TemplateManager manager = TemplateManager.getInstance(myFixture.getProject());
    Template template = manager.createTemplate("empty", "user", "$VAR$");
    template.addVariable("VAR", "", "\"foo\"", true);
    EdtTestUtil.runInEdtAndWait(() -> {
      manager.startTemplate(myFixture.getEditor(), template);

      myFixture.checkResult(" <selection>foo<caret></selection> g");

      myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_LINE_START);
      myFixture.checkResult(" <caret>foo g");

      myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_LINE_START);
      myFixture.checkResult("<caret> foo g");

      myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT);
      myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_LINE_END);
      myFixture.checkResult(" foo<caret> g");

      myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_LINE_END);
      myFixture.checkResult(" foo g<caret>");
    });
  }

  @Test
  public void testComments() {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
    myFixture.configureByText("a.java", "<caret>");

    TemplateManager manager = TemplateManager.getInstance(myFixture.getProject());
    Template template = manager.createTemplate("empty", "user", "$V1$ line comment\n$V2$ block comment $V3$\n$V4$ any comment $V5$");
    template.addVariable("V1", "lineCommentStart()", "", false);
    template.addVariable("V2", "blockCommentStart()", "", false);
    template.addVariable("V3", "blockCommentEnd()", "", false);
    template.addVariable("V4", "commentStart()", "", false);
    template.addVariable("V5", "commentEnd()", "", false);

    EdtTestUtil.runInEdtAndWait(() -> {
      manager.startTemplate(myFixture.getEditor(), template);

      myFixture.checkResult("// line comment\n/* block comment */\n// any comment ");
    });
  }

  @Test
  public void testShowLookupWithGroovyScriptCollectionResult() {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
    myFixture.configureByText("a.java", "<caret>");

    TemplateManager manager = TemplateManager.getInstance(myFixture.getProject());
    Template template = manager.createTemplate("empty", "user", "$V$");
    template.addVariable("V", "groovyScript(\"[1, 2, true]\")", "", true);
    EdtTestUtil.runInEdtAndWait(() -> {
      manager.startTemplate(myFixture.getEditor(), template);

      assertEquals(List.of("1", "2", "true"), myFixture.getLookupElementStrings());
      myFixture.checkResult("1");
    });
  }

  @Test
  public void testUnrelatedCommandShouldNotFinishLiveTemplate() {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
    myFixture.configureByText("a.txt", "foo <caret>");

    TemplateManager manager = TemplateManager.getInstance(myFixture.getProject());
    Template template = manager.createTemplate("empty", "user", "$V$");
    template.addVariable("V", "\"Y\"", "", true);
    EdtTestUtil.runInEdtAndWait(() -> {
      manager.startTemplate(myFixture.getEditor(), template);

      // undo-transparent document change (e.g. auto-import on the fly) doesn't currently terminate template
      DocumentUtil.writeInRunUndoTransparentAction(() -> myFixture.getEditor().getDocument().insertString(0, "bar "));
      assertNotNull(lt.get().getState());

      myFixture.getEditor().getCaretModel().moveToOffset(0);
      // it's just caret outside template, we shouldn't yet cancel it
      assertNotNull(lt.get().getState());
      myFixture.checkResult("<caret>bar foo <selection>Y</selection>");

      // unrelated empty command should have no effect
      WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> {
      });
      assertNotNull(lt.get().getState());

      // undo-transparent change still doesn't terminate template
      DocumentUtil.writeInRunUndoTransparentAction(() -> myFixture.getEditor().getDocument().insertString(0, "bar "));
      assertNotNull(lt.get().getState());
      myFixture.checkResult("<caret>bar bar foo <selection>Y</selection>");

      // now we're really typing outside template, so it should be canceled
      myFixture.type("a");
      assertNull(lt.get().getState());
    });
  }

  @Test
  public void testStartTemplateThatBreakInjectionInsideInjection() {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
    myFixture.setCaresAboutInjection(false);
    PsiFile file = myFixture.configureByText("a.java", """
      public class Main {
          public static void main(String[] args) {
              //language=Java prefix="public class A {" suffix=}
              String s = "int a = <caret>;";
          }
      }
      """);

    TemplateManager manager = TemplateManager.getInstance(myFixture.getProject());
    Template template = manager.createTemplate("ttt", "user", "\" + $VAR$ + $END$\"");
    template.addVariable("VAR", "", "", true);

    EdtTestUtil.runInEdtAndWait(() -> {
      Editor injectionEditor = InjectedLanguageUtil.getEditorForInjectedLanguageNoCommit(myFixture.getEditor(), file);
      manager.startTemplate(injectionEditor, template);
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();

      assertNotNull(lt.get().getState());
      myFixture.type("123\t");

      assertNull(lt.get().getState());
      myFixture.checkResult("""
                              public class Main {
                                  public static void main(String[] args) {
                                      //language=Java prefix="public class A {" suffix=}
                                      String s = "int a = " + 123 + <caret>";";
                                  }
                              }
                              """);
    });
  }

  @Test
  public void testSurroundWithTemplateThatUsesSELECTIONOnlyInsideAnExpression() {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
    TemplateManager manager = TemplateManager.getInstance(myFixture.getProject());
    final TemplateImpl template = (TemplateImpl)manager.createTemplate("ttt", "user", "$VAR$+x");
    template.addVariable("VAR", "regularExpression(SELECTION, \"_\", \"\")", "", false);
    assertTrue(template.isSelectionTemplate());
    template.getTemplateContext().setEnabled(contextType(JavaCommentContextType.class), true);
    CodeInsightTestUtil.addTemplate(template, myFixture.getTestRootDisposable());

    myFixture.configureByText("a.java", "//a <selection><caret>foo_bar</selection> b");
    EdtTestUtil.runInEdtAndWait(() -> {
      List<AnAction> group = SurroundWithTemplateHandler.createActionGroup(myFixture.getEditor(), myFixture.getFile(), new HashSet<>());
      InvokeTemplateAction action =
        (InvokeTemplateAction)ContainerUtil.find(group, a -> a.getTemplatePresentation().getText().contains(template.getKey()));
      assertNotNull(action);
      action.perform();
      myFixture.checkResult("//a foobar+x b");
    });
  }

  @Test
  public void testCompletionInDumbMode() {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
    TemplateManager manager = TemplateManager.getInstance(myFixture.getProject());
    Template template = manager.createTemplate("helloWorld", "user", "\"Hello, World\"");
    TemplateContextType contextType = contextType(JavaCodeContextType.class);
    ((TemplateImpl)template).getTemplateContext().setEnabled(contextType, true);
    CodeInsightTestUtil.addTemplate(template, myFixture.getTestRootDisposable());

    myFixture.configureByText("a.java", "class Foo {{ System.out.println(helloW<caret>) }}");
    LiveTemplateCompletionContributor.setShowTemplatesInTests(true, myFixture.getTestRootDisposable());
    EdtTestUtil.runInEdtAndWait(() -> {
      DumbModeTestUtils.runInDumbModeSynchronously(myFixture.getProject(), () -> {
        RecursionManager.disableMissedCacheAssertions(myFixture.getTestRootDisposable());
        myFixture.completeBasic();
        assertNotNull(myFixture.getLookup());
        assertTrue(myFixture.getLookupElementStrings().contains("helloWorld"));
        myFixture.type("\t");
        myFixture.checkResult("class Foo {{ System.out.println(\"Hello, World\") }}");
      });
    });
  }

  @Test
  public void testLogLivetemplateStartedEvent() {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
    List<LogEvent> events = FUCollectorTestCase.INSTANCE.collectLogEvents(myFixture.getTestRootDisposable(), () -> {
      configureFromFileText("empty.java", "");
      TemplateManager manager = TemplateManager.getInstance(myFixture.getProject());
      Template template = manager.createTemplate("empty", "user", "$VAR$");
      template.addVariable("VAR", "", "", false);
      EdtTestUtil.runInEdtAndWait(() -> lt.get().startTemplate(template));
      return null;
    });
    LogEvent logEvent = ContainerUtil.find(events, event -> event.getGroup().getId().equals("live.templates"));
    assertNotNull(logEvent);
    assertEquals("started", logEvent.getEvent().getId());
  }

  @Test
  public void testAdditionalActions() {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
    TemplateManager manager = TemplateManager.getInstance(myFixture.getProject());
    TemplateImpl template = (TemplateImpl)manager.createTemplate("doubleparen", "user", "(($END$$SELECTION$))");
    TemplateContextType contextType = contextType(JavaCodeContextType.class);
    template.getTemplateContext().setEnabled(contextType, true);
    CodeInsightTestUtil.addTemplate(template, myFixture.getTestRootDisposable());
    myFixture.configureByText("a.java", "class X {{<selection>hello</selection>}}");
    EdtTestUtil.runInEdtAndWait(() -> {
      List<AnAction> group = SurroundWithTemplateHandler.createActionGroup(myFixture.getEditor(), myFixture.getFile(), new HashSet<>());
      AnAction action = ContainerUtil.find(group, a -> a.getTemplateText().equals("doubleparen"));
      assertTrue(action instanceof ActionGroup);
      final Presentation presentation = new Presentation();
      final DataContext context = DataManager.getInstance().getDataContext();
      final AnActionEvent event = new AnActionEvent(null, context, "", presentation, ActionManager.getInstance(), 0);
      assertFalse(presentation.isPerformGroup());
      assertFalse(presentation.isPopupGroup());
      action.update(event);
      assertTrue(presentation.isPerformGroup());
      assertTrue(presentation.isPopupGroup());
      AnAction[] children = ((ActionGroup)action).getChildren(event);
      assertEquals("Edit live template settings", children[0].getTemplateText());
      assertEquals("Disable 'doubleparen' template", children[1].getTemplateText());
      assertFalse(template.isDeactivated());
      children[1].actionPerformed(event);
      assertTrue(template.isDeactivated());
    });
  }

  @Test
  public void testEquality() {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
    TemplateManager manager = TemplateManager.getInstance(myFixture.getProject());
    TemplateImpl t1 = (TemplateImpl)manager.createTemplate("k", "g", "t");
    TemplateImpl t2 = (TemplateImpl)manager.createTemplate("k", "g", "t");
    assertEquals(t1, t2);
    t1.setValue(Template.Property.USE_STATIC_IMPORT_IF_POSSIBLE, true);
    //noinspection SimplifiableAssertion
    assertFalse(t1.equals(t2));
  }


  private static class MyMirrorMacro extends Macro {
    MyMirrorMacro(String variableName) {
      this.myVariableName = variableName;
    }

    @Override
    public String getName() {
      return "mirror";
    }

    @Override
    public String getPresentableName() {
      return getName();
    }

    @Override
    public Result calculateResult(Expression @NotNull [] params, ExpressionContext context) {
      return context.getVariableValue(myVariableName);
    }

    @Override
    public Result calculateQuickResult(Expression @NotNull [] params, ExpressionContext context) {
      return calculateResult(params, context);
    }

    private final String myVariableName;
  }
}
