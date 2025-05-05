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
import com.intellij.codeInsight.template.*;
import com.intellij.codeInsight.template.impl.*;
import com.intellij.codeInsight.template.macro.CompleteMacro;
import com.intellij.codeInsight.template.macro.ConcatMacro;
import com.intellij.codeInsight.template.macro.FilePathMacroBase;
import com.intellij.codeInsight.template.macro.SplitWordsMacro;
import com.intellij.ide.DataManager;
import com.intellij.internal.statistic.FUCollectorTestCase;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.testFramework.DumbModeTestUtils;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;
import com.intellij.testFramework.fixtures.CodeInsightTestUtil;
import com.intellij.util.DocumentUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import com.jetbrains.fus.reporting.model.lion3.LogEvent;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@SuppressWarnings("SpellCheckingInspection")
public class LiveTemplateTest extends LiveTemplateTestCase {
  private void doTestTemplateWithArg(@NotNull String templateName,
                                     @NotNull String templateText,
                                     @NotNull String fileText,
                                     @NotNull String expected) {
    configureFromFileText("dummy.java", fileText);
    final TemplateManager manager = TemplateManager.getInstance(getProject());
    String group = "user";
    final Template template = manager.createTemplate(templateName, group, templateText);
    template.addVariable("ARG", "", "", false);
    TemplateContextType contextType = contextType(JavaCodeContextType.Generic.class);
    ((TemplateImpl)template).getTemplateContext().setEnabled(contextType, true);
    CodeInsightTestUtil.addTemplate(template, myFixture.getTestRootDisposable());

    writeCommand(() -> manager.startTemplate(getEditor(), '\t'));
    UIUtil.dispatchAllInvocationEvents();
    checkResultByText(expected);
  }

  public void testDependentSegmentsAtTheSamePosition() {
    configureFromFileText("dummy.java", "class A { void test() { <caret> } }");
    TemplateManager manager = TemplateManager.getInstance(getProject());
    final Template template = manager.createTemplate("test_template", "user_group", "$A$$B$ then \"$A$.$B$\"");
    template.addVariable("A", "", "", true);
    template.addVariable("B", "", "", true);
    startTemplate(template);
    myFixture.type("HELLO");
    myFixture.type("\t");
    myFixture.type("THERE");
    myFixture.type("\t");
    assertNull(getState());
    checkResultByText("class A { void test() { HELLOTHERE then \"HELLO.THERE\" } }");
  }

  public void testTemplateWithSegmentsAtTheSamePosition1() {
    doTestTemplateWithThreeVariables("", "", "", "class A { void test() { for(TestValue1TestValue2TestValue3) {} } }");
  }

  public void testTemplateWithSegmentsAtTheSamePosition2() {
    doTestTemplateWithThreeVariables("Def1", "Def2", "DefaultValue", "class A { void test() { for(Def1Def2DefaultValue) {} } }");
  }

  public void testTemplateWithSegmentsAtTheSamePosition3() {
    doTestTemplateWithThreeVariables("", "DefaultValue", "", "class A { void test() { for(TestValue1DefaultValueTestValue3) {} } }");
  }

  public void testTemplateWithSegmentsAtTheSamePosition4() {
    configureFromFileText("dummy.java", "class A { void test() { <caret> } }");
    TemplateManager manager = TemplateManager.getInstance(getProject());
    final Template template = manager.createTemplate("test_template", "user_group", "$A$$B$ then \"$A$$B$\"");
    template.addVariable("A", "", "\"Def1\"", true);
    template.addVariable("B", "", "\"Def2\"", true);
    startTemplate(template);
    checkResultByText("class A { void test() { Def1Def2 then \"Def1Def2\" } }");
  }

  private void doTestTemplateWithThreeVariables(String firstDefaultValue,
                                                String secondDefaultValue,
                                                String thirdDefaultValue,
                                                String expectedText) {
    configureFromFileText("dummy.java", "class A { void test() { <caret> } }");

    TemplateManager manager = TemplateManager.getInstance(getProject());
    String templateName = "tst_template";
    String templateGroup = "user";
    final Template template = manager.createTemplate(templateName, templateGroup, "for($TEST1$$TEST2$$TEST3$) {}");
    template.addVariable("TEST1", "", StringUtil.wrapWithDoubleQuote(firstDefaultValue), true);
    template.addVariable("TEST2", "", StringUtil.wrapWithDoubleQuote(secondDefaultValue), true);
    template.addVariable("TEST3", "", StringUtil.wrapWithDoubleQuote(thirdDefaultValue), true);
    startTemplate(template);

    if (firstDefaultValue.isEmpty()) myFixture.type("TestValue1");
    myFixture.type("\t");
    if (secondDefaultValue.isEmpty()) myFixture.type("TestValue2");
    myFixture.type("\t");
    if (thirdDefaultValue.isEmpty()) myFixture.type("TestValue3");
    myFixture.type("\t");
    assertNull(getState());
    checkResultByText(expectedText);
  }

  public void testTemplateWithArg1() {
    doTestTemplateWithArg("tst", "wrap($ARG$)", "tst arg<caret>", "wrap(arg)");
  }

  public void testTemplateWithArg2() {
    doTestTemplateWithArg("tst#", "wrap($ARG$)", "tst#arg<caret>", "wrap(arg)");
  }

  public void testTemplateWithArg3() {
    doTestTemplateWithArg("tst#", "wrap($ARG$)", "tst# arg<caret>", "tst# arg");
  }

  public void testTemplateAtEndOfFile() {
    configureFromFileText("empty.java", "");
    final TemplateManager manager = TemplateManager.getInstance(getProject());
    final Template template = manager.createTemplate("empty", "user", "$VAR$");
    template.addVariable("VAR", "", "", false);

    startTemplate(template);
    checkResultByText("");
  }

  public void testTemplateWithEnd() {
    configureFromFileText("empty.java", "");
    final TemplateManager manager = TemplateManager.getInstance(getProject());
    final Template template = manager.createTemplate("empty", "user", "$VAR$$END$");
    template.addVariable("VAR", "bar", "bar", true);
    template.setToReformat(true);

    startTemplate(template);
    myFixture.type("foo");
    checkResultByText("foo");
  }

  public void testTemplateWithIndentedEnd() {
    configureFromFileText("empty.java", """
      class C {
        bar() {
          <caret>
        }
      }""");
    TemplateManager manager = TemplateManager.getInstance(getProject());
    Template template = manager.createTemplate("empty", "user", """
      foo();
      int i = 0;    $END$
      foo()""");
    template.setToReformat(true);
    startTemplate(template);
    checkResultByText("""
                        class C {
                          bar() {
                              foo();
                              int i = 0;    <caret>
                              foo()
                          }
                        }""");
  }

  public void testTemplateWithEndOnEmptyLine() {
    configureFromFileText("empty.java", """
      class C {
        bar() {
          <caret>
        }
      }""");
    TemplateManager manager = TemplateManager.getInstance(getProject());
    Template template = manager.createTemplate("empty", "user", """
      foo()
        $END$
      foo()""");
    template.setToReformat(true);
    startTemplate(template);
    checkResultByText("""
                        class C {
                          bar() {
                              foo()
                              <caret>
                              foo()
                          }
                        }""");
  }

  private void checkResultByText(String text) {
    myFixture.checkResult(text);
  }

  private void configureFromFileText(String name, String text) {
    myFixture.configureByText(name, text);
  }

  public void testEndInTheMiddle() {
    configure();
    final TemplateManager manager = TemplateManager.getInstance(getProject());
    final Template template = manager.createTemplate("frm", "user", """
      javax.swing.JFrame frame = new javax.swing.JFrame();
      $END$
      frame.setVisible(true);
      frame.setDefaultCloseOperation(javax.swing.JFrame.EXIT_ON_CLOSE);
      frame.pack();""");
    template.setToShortenLongNames(false);
    template.setToReformat(true);
    startTemplate(template);
    checkResult();
  }

  public void testHonorCustomCompletionCaretPlacement() {
    myFixture.configureByText("a.java", """
      class Foo {
        void foo(int a) {}
        { <caret> }
      }
      """);
    final TemplateManager manager = TemplateManager.getInstance(getProject());
    final Template template = manager.createTemplate("frm", "user", "$VAR$");
    template.addVariable("VAR", new MacroCallNode(new CompleteMacro()), new EmptyNode(), true);
    startTemplate(template);
    myFixture.type("fo\n");
    myFixture.checkResult("""
                            class Foo {
                              void foo(int a) {}
                              { foo(<caret>); }
                            }
                            """);
    assertFalse(getState().isFinished());
  }

  public void testCancelTemplateWhenCompletionPlacedCaretOutsideTheVariable() {
    myFixture.configureByText("a.java", """
      class Foo {
        void foo(int a) {}
        { <caret>() }
      }
      """);
    final TemplateManager manager = TemplateManager.getInstance(getProject());
    final Template template = manager.createTemplate("frm", "user", "$VAR$");
    template.addVariable("VAR", new MacroCallNode(new CompleteMacro()), new EmptyNode(), true);
    startTemplate(template);
    myFixture.type("fo\n");
    myFixture.checkResult("""
                            class Foo {
                              void foo(int a) {}
                              { foo(<caret>); }
                            }
                            """);
    assertNull(getState());
  }

  private void checkResult() {
    checkResultByFile(getTestName(false) + "-out.java");
  }

  private void checkResultByFile(String s) {
    myFixture.checkResultByFile(s);
  }

  public void startTemplate(String name, char expandKey) {
    myFixture.type(name);
    myFixture.type(expandKey);
  }

  private static <T extends TemplateContextType> T contextType(Class<T> clazz) {
    return TemplateContextTypes.getByClass(clazz);
  }

  private void configure() {
    myFixture.configureByFile(getTestName(false) + ".java");
  }

  public void testPreferStartMatchesInLookups() {
    configure();
    startTemplate("iter", "Java");
    myFixture.type("ese\n");//for entrySet
    assertEquals(List.of("barGooStringBuilderEntry", "gooStringBuilderEntry", "stringBuilderEntry", "builderEntry", "entry"),
                 myFixture.getLookupElementStrings());
    myFixture.type("e");
    assertEquals(List.of("entry", "barGooStringBuilderEntry", "gooStringBuilderEntry", "stringBuilderEntry", "builderEntry"),
                 myFixture.getLookupElementStrings());
    assertEquals("entry", LookupManager.getActiveLookup(getEditor()).getCurrentItem().getLookupString());
  }

  public void testClassNameDotInTemplate() {
    CodeInsightSettings.getInstance().setCompletionCaseSensitive(CodeInsightSettings.NONE);
    configure();
    startTemplate("soutv", "Java");
    myFixture.type("File");
    assertEquals(List.of("file"), myFixture.getLookupElementStrings());
    myFixture.type(".");
    checkResult();
    assertFalse(getState().isFinished());
  }

  public void testFinishTemplateVariantWithDot() {
    CodeInsightSettings.getInstance().setSelectAutopopupSuggestionsByChars(true);
    configure();
    startTemplate("soutv", "Java");
    myFixture.type("fil");
    assertEquals(List.of("file"), myFixture.getLookupElementStrings());
    myFixture.type(".");
    checkResult();
    assertFalse(getState().isFinished());
  }

  public void testAllowTypingRandomExpressionsWithLookupOpen() {
    configure();
    startTemplate("iter", "Java");
    myFixture.type("file.");
    checkResult();
    assertFalse(getState().isFinished());
  }

  public void _testIterForceBraces() {
    CommonCodeStyleSettings settings = CodeStyle.getSettings(getProject()).getCommonSettings(JavaLanguage.INSTANCE);
    settings.IF_BRACE_FORCE = CommonCodeStyleSettings.FORCE_BRACES_ALWAYS;

    try {
      configure();
      startTemplate("iter", "Java");
      checkResult();
    }
    finally {
      settings.IF_BRACE_FORCE = CommonCodeStyleSettings.DO_NOT_FORCE;
    }
  }

  public void testOtherContext() {
    configureFromFileText("a.java", "class Foo { <caret>xxx }");
    Set<TemplateContextType> types =
      TemplateManagerImpl.getApplicableContextTypes(TemplateActionContext.expanding(myFixture.getFile(), getEditor()));
    assertEquals(2, types.size());
    assertTrue(types.contains(TemplateContextTypes.getByClass(JavaCodeContextType.Declaration.class)));
    assertTrue(types.contains(TemplateContextTypes.getByClass(JavaCodeContextType.NormalClassDeclaration.class)));

    configureFromFileText("a.txt", "class Foo { <caret>xxx }");
    assertInstanceOf(assertOneElement(
                       TemplateManagerImpl.getApplicableContextTypes(TemplateActionContext.expanding(myFixture.getFile(), getEditor()))),
                     EverywhereContextType.class);
  }

  public void testJavaOtherContext() {
    JavaCodeContextType.Statement stmtContext = TemplateContextTypes.getByClass(JavaCodeContextType.Statement.class);

    configureFromFileText("a.java", "class Foo {{ iter<caret>  }}");

    TemplateImpl template = TemplateSettings.getInstance().getTemplate("iter", "Java");
    assertTrue(getTemplateManager()
                 .findMatchingTemplates(myFixture.getFile(), getEditor(), Lookup.REPLACE_SELECT_CHAR, TemplateSettings.getInstance())
                 .containsKey(template));

    assertTrue(template.getTemplateContext().getOwnValue(stmtContext));
    assertFalse(template.getTemplateContext().getOwnValue(stmtContext.getBaseContextType()));
    template.getTemplateContext().setEnabled(stmtContext, false);
    template.getTemplateContext().setEnabled(stmtContext.getBaseContextType(), true);
    try {
      assertFalse(getTemplateManager()
                    .findMatchingTemplates(myFixture.getFile(), getEditor(), Lookup.REPLACE_SELECT_CHAR, TemplateSettings.getInstance())
                    .containsKey(template));
    }
    finally {
      template.getTemplateContext().setEnabled(stmtContext, true);
      template.getTemplateContext().setEnabled(stmtContext.getBaseContextType(), false);
    }
  }

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

  public void testSearchByDescriptionWhenTemplatesListed() {
    myFixture.configureByText("a.java", "class A {{ <caret> }}");

    new ListTemplatesHandler().invoke(getProject(), getEditor(), myFixture.getFile());
    myFixture.type("array");
    assertTrue(myFixture.getLookupElementStrings().contains("itar"));
  }

  public void testListTemplatesSearchesPrefixInDescription() {
    myFixture.configureByText("a.java", "class A { main<caret> }");

    new ListTemplatesHandler().invoke(getProject(), getEditor(), myFixture.getFile());
    assertEquals(List.of("main", "psvm"), myFixture.getLookupElementStrings());
  }

  public void testListTemplatesAction() {
    myFixture.configureByText("a.java", "class A {{ <caret> }}");

    new ListTemplatesHandler().invoke(getProject(), getEditor(), myFixture.getFile());
    assertTrue(myFixture.getLookupElementStrings().containsAll(List.of("iter", "itco", "toar")));

    myFixture.type("it");
    assertTrue(myFixture.getLookupElementStrings().get(0).startsWith("it"));
    assertEquals(LookupManager.getInstance(getProject()).getActiveLookup().getCurrentItem(), myFixture.getLookupElements()[0]);

    myFixture.type("e");
    assertTrue(myFixture.getLookupElementStrings().get(0).startsWith("ite"));
    assertEquals(LookupManager.getInstance(getProject()).getActiveLookup().getCurrentItem(), myFixture.getLookupElements()[0]);
    LookupManager.getInstance(getProject()).hideActiveLookup();

    myFixture.type("\b\b");
    new ListTemplatesHandler().invoke(getProject(), getEditor(), myFixture.getFile());
    assertTrue(myFixture.getLookupElementStrings().containsAll(List.of("iter", "itco")));
    LookupManager.getInstance(getProject()).hideActiveLookup();

    myFixture.type("xxxxx");
    new ListTemplatesHandler().invoke(getProject(), getEditor(), myFixture.getFile());
    assertTrue(myFixture.getLookupElementStrings().containsAll(List.of("iter", "itco", "toar")));
    LookupManager.getInstance(getProject()).hideActiveLookup();
  }

  public void testSelectionFromLookupBySpace() {
    myFixture.configureByText("a.java", "class A {{ itc<caret> }}");

    new ListTemplatesHandler().invoke(getProject(), getEditor(), myFixture.getFile());
    myFixture.type(" ");
    myFixture.checkResult("""
                            import java.util.Iterator;

                            class A {{
                                for (Iterator <selection>iterator</selection> = collection.iterator(); iterator.hasNext(); ) {
                                    Object next =  iterator.next();
                                   \s
                                }
                            }}""");
  }

  public void testNavigationActionsDontTerminateTemplate() {
    configureFromFileText("a.txt", "");

    final TemplateManager manager = TemplateManager.getInstance(getProject());
    final Template template = manager.createTemplate("vn", "user", "Hello $V1$ World $V1$\nHello $V2$ World $V2$\nHello $V3$ World $V3$");
    template.addVariable("V1", "", "", true);
    template.addVariable("V2", "", "", true);
    template.addVariable("V3", "", "", true);
    final Editor editor = getEditor();

    startTemplate(template);

    final TemplateState state = getState();

    for (int i = 0; i < 3; i++) {
      assertFalse(String.valueOf(i), state.isFinished());
      myFixture.type("H");
      final String docText = editor.getDocument().getText();
      assertTrue(docText, docText.startsWith("Hello H World H\n"));
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
    checkResultByFile(getTestName(false) + "-out.txt");
  }

  private void moveCaret(final int offset) {
    EdtTestUtil.runInEdtAndWait(() -> getEditor().getCaretModel().moveToOffset(offset));
  }

  public void testUseDefaultValueForQuickResultCalculation() {
    myFixture.configureByText("a.txt", "<caret>");

    final TemplateManager manager = TemplateManager.getInstance(getProject());
    final Template template = manager.createTemplate("vn", "user", "$V1$ var = $V2$;");
    template.addVariable("V1", "", "", true);
    template.addVariable("V2", "", "\"239\"", true);

    startTemplate(template);

    myFixture.checkResult("<caret> var = 239;");

    myFixture.type("O");
    myFixture.checkResult("O<caret> var = 239;");

    myFixture.type("\t");
    myFixture.checkResult("O var = <selection>239</selection>;");
  }

  public void testTemplateExpandingWithSelection() {
    final TemplateManager manager = TemplateManager.getInstance(getProject());
    final Template template = manager.createTemplate("tpl", "user", "expanded");
    final JavaStringContextType contextType = contextType(JavaStringContextType.class);
    ((TemplateImpl)template).getTemplateContext().setEnabled(contextType, true);

    myFixture.configureByText("a.java", "class A { void f() { Stri<selection>ng s = \"tpl</selection><caret>\"; } }");

    CodeInsightTestUtil.addTemplate(template, myFixture.getTestRootDisposable());
    myFixture.type("\t");
    myFixture.checkResult("    class A { void f() { String s = \"tpl\"; } }");
  }

  public void testExpandCurrentLiveTemplateOnNoSuggestionsInLookup() {
    myFixture.configureByText("a.java", "class Foo {{ <caret> }}");
    myFixture.completeBasic();
    assertNotNull(myFixture.getLookup());
    myFixture.type("sout");
    //This assert fails sporadically
    //assert myFixture.lookup
    //assert myFixture.lookupElementStrings == []
    myFixture.type("\t");
    myFixture.checkResult("class Foo {{\n    System.out.println(<caret>);\n}}");
  }

  public void testInvokeSurroundTemplateByTab() {
    myFixture.configureByText("a.java", "class A { public void B() { I<caret> } }");
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
  }

  public void testStopAtENDWhenInvokedSurroundTemplateByTab() {
    myFixture.configureByText("a.txt", "<caret>");

    final TemplateManager manager = TemplateManager.getInstance(getProject());
    final Template template = manager.createTemplate("xxx", "user", "foo $ARG$ bar $END$ goo $SELECTION$ after");
    template.addVariable("ARG", "", "", true);

    startTemplate(template);
    myFixture.type("arg");
    getState().nextTab();
    assertNull(getState());
    checkResultByText("foo arg bar <caret> goo  after");
  }

  public void testStopAtSELECTIONWhenInvokedSurroundTemplateByTabAndENDMissing() {
    myFixture.configureByText("a.txt", "<caret>");

    final TemplateManager manager = TemplateManager.getInstance(getProject());
    final Template template = manager.createTemplate("xxx", "user", "foo $ARG$ bar goo $SELECTION$ after");
    template.addVariable("ARG", "", "", true);

    startTemplate(template);
    myFixture.type("arg");
    getState().nextTab();
    assertNull(getState());
    checkResultByText("foo arg bar goo <caret> after");
  }

  public void testConcatMacro() {
    final TemplateManager manager = TemplateManager.getInstance(getProject());
    final Template template = manager.createTemplate("result", "user", "$A$ $B$ c");
    template.addVariable("A", new EmptyNode(), true);

    MacroCallNode macroCallNode = new MacroCallNode(new ConcatMacro());
    macroCallNode.addParameter(new VariableNode("A", null));
    macroCallNode.addParameter(new TextExpression("ID"));
    template.addVariable("B", macroCallNode, false);

    myFixture.configureByText("a.txt", "<caret>");
    startTemplate(template);
    myFixture.type("tableName");
    getState().nextTab();
    assertNull(getState());
    myFixture.checkResult("tableName tableNameID c");
  }

  public void testSubstringBeforeMacro() {
    final TemplateManager manager = TemplateManager.getInstance(getProject());
    final Template template = manager.createTemplate("result", "user", "$A$ $B$ $C$");
    template.addVariable("A", "substringBefore(\"hello.world\", \".\")", "\"empty\"", false);
    template.addVariable("B", "substringBefore(\"hello world\", \".\")", "\"empty\"", false);
    template.addVariable("C", "substringBefore(\"hello world\")", "\"empty\"", false);
    myFixture.configureByText("a.txt", "<caret>");
    startTemplate(template);
    getState().nextTab();
    assertNull(getState());
    myFixture.checkResult("hello empty empty");
  }

  public void testSnakeCaseShouldConvertHyphensToUnderscores() {
    final TemplateManager manager = TemplateManager.getInstance(getProject());
    final Template template = manager.createTemplate("result", "user", "$A$ $B$ c");
    template.addVariable("A", new EmptyNode(), true);

    MacroCallNode macroCallNode = new MacroCallNode(new SplitWordsMacro.SnakeCaseMacro());
    macroCallNode.addParameter(new VariableNode("A", null));
    template.addVariable("B", macroCallNode, false);

    myFixture.configureByText("a.txt", "<caret>");
    startTemplate(template);
    myFixture.type("-foo-bar_goo-");
    getState().nextTab();
    assertNull(getState());
    myFixture.checkResult("-foo-bar_goo- _foo_bar_goo_ c<caret>");
  }

  public void testDoNotReplaceMacroValueWithNullResult() {
    myFixture.configureByText("a.java", """
      class Foo {
        {
          <caret>
        }
      }
      """);
    final TemplateManager manager = TemplateManager.getInstance(getProject());
    final Template template = manager.createTemplate("xxx", "user", "$VAR1$ $VAR2$ $VAR1$");
    template.addVariable("VAR1", "", "", true);
    template.addVariable("VAR2", new MacroCallNode(new FilePathMacroBase.FileNameMacro()), new ConstantNode("default"), true);
    ((TemplateImpl)template).getTemplateContext().setEnabled(contextType(JavaCodeContextType.Generic.class), true);
    CodeInsightTestUtil.addTemplate(template, myFixture.getTestRootDisposable());

    startTemplate(template);
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
  }

  public void testDoReplaceMacroValueWithEmptyResult() {
    myFixture.configureByText("a.java", """
      class Foo {
        {
          <caret>
        }
      }
      """);
    final TemplateManager manager = TemplateManager.getInstance(getProject());
    final Template template = manager.createTemplate("xxx", "user", "$VAR1$ $VAR2$");
    template.addVariable("VAR1", "", "", true);
    template.addVariable("VAR2", new MacroCallNode(new MyMirrorMacro("VAR1")), null, true);
    ((TemplateImpl)template).getTemplateContext().setEnabled(contextType(JavaCodeContextType.class), true);
    CodeInsightTestUtil.addTemplate(template, myFixture.getTestRootDisposable());

    startTemplate(template);
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
  }

  public void testFilePathMacros() {
    final VirtualFile file = myFixture.addFileToProject("foo/bar.txt", "").getVirtualFile();
    myFixture.configureFromExistingVirtualFile(file);

    Template template = getTemplateManager().createTemplate("xxx", "user", "$VAR1$ $VAR2$");
    template.addVariable("VAR1", "filePath()", "", false);
    template.addVariable("VAR2", "fileRelativePath()", "", false);
    getTemplateManager().startTemplate(getEditor(), template);

    myFixture.checkResult(FileUtil.toSystemDependentName(file.getPath() + " foo/bar.txt"));
  }

  public void testEscapeShouldNotMoveCaretToTheEndMarker() {
    myFixture.configureByText("a.java", """
      class Foo {{
        itar<caret>
      }}
      """);
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
  }

  public void testAddNewLineOnEnterOutsideEditingVariable() {
    myFixture.configureByText("a.java", """
      class Foo {{
        <caret>
      }}
      """);
    myFixture.type("soutv\tabc");
    myFixture.getEditor().getCaretModel().moveCaretRelatively(3, 0, false, false, false);
    myFixture.type("\n");
    myFixture.checkResult("""
                            class Foo {{
                                System.out.println("abc = " + abc);
                                <caret>
                            }}
                            """);
  }

  public void testTypeTabCharacterOnTabOutsideEditingVariable() {
    myFixture.configureByText("a.java", """
      class Foo {{
        <caret>
      }}
      """);
    myFixture.type("soutv\tabc");
    myFixture.getEditor().getCaretModel().moveCaretRelatively(2, 0, false, false, false);
    myFixture.type("\t");
    myFixture.checkResult("""
                            class Foo {{
                                System.out.println("abc = " + abc); <caret>
                            }}
                            """);
  }

  public void testNextTabIsNotIsEvaluatedOnLookupElementInsertIfTemplateIsFinishedOrBrokenOff() {
    myFixture.configureByText("a.java", """
      <caret>
      """);
    final TemplateManager manager = TemplateManager.getInstance(getProject());
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
    startTemplate(template);
    assertNotNull(myFixture.getLookup());
    myFixture.type("\n");
    myFixture.checkResult("""
                            import com<caret>
                            """);
    assertNull(getState());
  }

  public void testDeleteAtTheLastTemplatePosition() {
    myFixture.configureByText("a.java", """
      class Foo {{
        <caret>
      }}
      """);
    myFixture.type("iter\t");
    LightPlatformCodeInsightTestCase.delete(myFixture.getEditor(), myFixture.getProject());
    myFixture.checkResult("""
                            class Foo {{
                                for (Object o : <caret> {
                                   \s
                                }
                            }}
                            """);
  }

  public void testDeleteAtTheLastTemplatePositionWithSelection() {
    myFixture.configureByText("a.java", """
      class Foo {{
        <caret>
      }}
      """);
    myFixture.type("iter\t");
    int startOffset = myFixture.getEditor().getCaretModel().getOffset();
    myFixture.type("foo");
    myFixture.getEditor().getSelectionModel().setSelection(startOffset, myFixture.getEditor().getCaretModel().getOffset());
    LightPlatformCodeInsightTestCase.delete(myFixture.getEditor(), myFixture.getProject());
    assertNotNull(getState());
  }

  public void testMulticaretExpandingWithSpace() {
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
  }

  public void testMulticaretExpandingWithEnter() {
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
  }

  public void testMulticaretExpandingWithTab() {
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
  }

  public void testFinishTemplateOnMovingCaretByCompletionInsertHandler() {
    myFixture.configureByText("a.html", "<selection><p></p></selection>");
    TemplateImpl template = TemplateSettings.getInstance().getTemplate("T", "HTML/XML");
    myFixture.testAction(new InvokeTemplateAction(template, myFixture.getEditor(), myFixture.getProject(), new HashSet<>()));
    myFixture.complete(CompletionType.BASIC);
    myFixture.type("nofra");
    myFixture.finishLookup(Lookup.REPLACE_SELECT_CHAR);
    myFixture.checkResult("<noframes><caret><p></p></noframes>");
    assertNull(getTemplateManager().getActiveTemplate(myFixture.getEditor()));
  }

  public void testGotoNextVariableWhenCompletionInsertHandlerMovesTowardIt() {
    myFixture.configureByText("a.java", "class C { void foo(C c, String s) {}; { <caret> } }");

    Template template = getTemplateManager().createTemplate("empty", "user", "foo($CS$, \"$S$\");");
    template.addVariable("CS", "completeSmart()", "", true);
    template.addVariable("S", "", "\"\"", true);
    getTemplateManager().startTemplate(myFixture.getEditor(), template);
    UIUtil.dispatchAllInvocationEvents();

    assertTrue(myFixture.getEditor().getDocument().getText().contains("foo(this, \"\");"));
    assertNotNull(getState());

    myFixture.type("string\n");
    assertTrue(myFixture.getEditor().getDocument().getText().contains("foo(this, \"string\");"));
    assertNull(getState());
  }

  public void testEscapeWithSelection() {
    myFixture.configureByText("a.java", """
      class Foo {
        {
            soutv<caret>
        }
      }
      """);
    myFixture.type("\tfoo");
    myFixture.getEditor().getSelectionModel().setSelection(myFixture.getCaretOffset() - 3, myFixture.getCaretOffset());
    assertTrue(myFixture.getEditor().getSelectionModel().hasSelection());

    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_ESCAPE);
    assertFalse(myFixture.getEditor().getSelectionModel().hasSelection());
    assertNotNull(getTemplateManager().getActiveTemplate(myFixture.getEditor()));

    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_ESCAPE);
    assertNull(getTemplateManager().getActiveTemplate(myFixture.getEditor()));

    myFixture.checkResult("""
                            class Foo {
                              {
                                  System.out.println("foo = " + foo<caret>);
                              }
                            }
                            """);
  }

  public void testEscapeWithLookup() {
    myFixture.configureByText("a.java", """
      class Foo {
        {
            int foo_1, foo_2;
            soutv<caret>
        }
      }
      """);
    myFixture.type("\t");
    assertNotNull(myFixture.getLookup());

    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_ESCAPE);
    assertNull(myFixture.getLookup());
    assertNotNull(getTemplateManager().getActiveTemplate(myFixture.getEditor()));

    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_ESCAPE);
    assertNull(getTemplateManager().getActiveTemplate(myFixture.getEditor()));

    myFixture.checkResult("""
                            class Foo {
                              {
                                  int foo_1, foo_2;
                                  System.out.println("foo_1 = " + foo_1);
                              }
                            }
                            """);
  }

  public void testEscapeWithLookupAndSelection() {
    myFixture.configureByText("a.java", """
      class Foo {
        {
            int foo_1, foo_2;
            soutv<caret>
        }
      }
      """);
    myFixture.type("\tfoo");
    myFixture.getEditor().getSelectionModel().setSelection(myFixture.getCaretOffset() - 3, myFixture.getCaretOffset());
    myFixture.completeBasic();
    assertTrue(myFixture.getEditor().getSelectionModel().hasSelection());
    assertNotNull(myFixture.getLookup());

    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_ESCAPE);
    assertFalse(myFixture.getEditor().getSelectionModel().hasSelection());
    assertNull(myFixture.getLookup());
    assertNotNull(getTemplateManager().getActiveTemplate(myFixture.getEditor()));

    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_ESCAPE);
    assertNull(getTemplateManager().getActiveTemplate(myFixture.getEditor()));

    myFixture.checkResult("""
                            class Foo {
                              {
                                  int foo_1, foo_2;
                                  System.out.println("foo = " + foo<caret>);
                              }
                            }
                            """);
  }

  public void testEscapeWithEmptyLookup() {
    myFixture.configureByText("a.java", """
      class Foo {
        {
            int foo_1, foo_2;
            soutv<caret>
        }
      }
      """);
    myFixture.type("\tfoobar");
    assertNotNull(myFixture.getLookup());
    assertNull(myFixture.getLookup().getCurrentItem());

    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_ESCAPE);
    assertNull(myFixture.getLookup());
    assertNull(getTemplateManager().getActiveTemplate(myFixture.getEditor()));

    myFixture.checkResult("""
                            class Foo {
                              {
                                  int foo_1, foo_2;
                                  System.out.println("foobar = " + foobar);
                              }
                            }
                            """);
  }

  public void testHomeEndGoOutsideTemplateFragmentsIfAlreadyOnTheirBounds() {
    myFixture.configureByText("a.txt", " <caret> g");

    TemplateManager manager = TemplateManager.getInstance(getProject());
    Template template = manager.createTemplate("empty", "user", "$VAR$");
    template.addVariable("VAR", "", "\"foo\"", true);
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
  }

  public void testComments() {
    myFixture.configureByText("a.java", "<caret>");

    TemplateManager manager = TemplateManager.getInstance(getProject());
    Template template = manager.createTemplate("empty", "user", "$V1$ line comment\n$V2$ block comment $V3$\n$V4$ any comment $V5$");
    template.addVariable("V1", "lineCommentStart()", "", false);
    template.addVariable("V2", "blockCommentStart()", "", false);
    template.addVariable("V3", "blockCommentEnd()", "", false);
    template.addVariable("V4", "commentStart()", "", false);
    template.addVariable("V5", "commentEnd()", "", false);

    manager.startTemplate(myFixture.getEditor(), template);

    myFixture.checkResult("// line comment\n/* block comment */\n// any comment ");
  }

  public void testShowLookupWithGroovyScriptCollectionResult() {
    myFixture.configureByText("a.java", "<caret>");

    TemplateManager manager = TemplateManager.getInstance(getProject());
    Template template = manager.createTemplate("empty", "user", "$V$");
    template.addVariable("V", "groovyScript(\"[1, 2, true]\")", "", true);
    manager.startTemplate(myFixture.getEditor(), template);

    assertEquals(List.of("1", "2", "true"), myFixture.getLookupElementStrings());
    myFixture.checkResult("1");
  }

  public void testUnrelatedCommandShouldNotFinishLiveTemplate() {
    myFixture.configureByText("a.txt", "foo <caret>");

    TemplateManager manager = TemplateManager.getInstance(getProject());
    Template template = manager.createTemplate("empty", "user", "$V$");
    template.addVariable("V", "\"Y\"", "", true);
    manager.startTemplate(myFixture.getEditor(), template);

    // undo-transparent document change (e.g. auto-import on the fly) doesn't currently terminate template
    DocumentUtil.writeInRunUndoTransparentAction(() -> myFixture.getEditor().getDocument().insertString(0, "bar "));
    assertNotNull(getState());

    myFixture.getEditor().getCaretModel().moveToOffset(0);
    // it's just caret outside template, we shouldn't yet cancel it
    assertNotNull(getState());
    myFixture.checkResult("<caret>bar foo <selection>Y</selection>");

    // unrelated empty command should have no effect
    WriteCommandAction.runWriteCommandAction(getProject(), () -> {
    });
    assertNotNull(getState());

    // undo-transparent change still doesn't terminate template
    DocumentUtil.writeInRunUndoTransparentAction(() -> myFixture.getEditor().getDocument().insertString(0, "bar "));
    assertNotNull(getState());
    myFixture.checkResult("<caret>bar bar foo <selection>Y</selection>");

    // now we're really typing outside template, so it should be canceled
    myFixture.type("a");
    assertNull(getState());
  }

  public void testStartTemplateThatBreakInjectionInsideInjection() {
    myFixture.setCaresAboutInjection(false);
    PsiFile file = myFixture.configureByText("a.java", """
      public class Main {
          public static void main(String[] args) {
              //language=Java prefix="public class A {" suffix=}
              String s = "int a = <caret>;";
          }
      }
      """);

    TemplateManager manager = TemplateManager.getInstance(getProject());
    Template template = manager.createTemplate("ttt", "user", "\" + $VAR$ + $END$\"");
    template.addVariable("VAR", "", "", true);

    Editor injectionEditor = InjectedLanguageUtil.getEditorForInjectedLanguageNoCommit(myFixture.getEditor(), file);
    manager.startTemplate(injectionEditor, template);
    UIUtil.dispatchAllInvocationEvents();

    assertNotNull(getState());
    myFixture.type("123\t");

    assertNull(getState());
    myFixture.checkResult("""
                            public class Main {
                                public static void main(String[] args) {
                                    //language=Java prefix="public class A {" suffix=}
                                    String s = "int a = " + 123 + <caret>";";
                                }
                            }
                            """);
  }

  public void testSurroundWithTemplateThatUsesSELECTIONOnlyInsideAnExpression() {
    TemplateManager manager = TemplateManager.getInstance(getProject());
    final TemplateImpl template = (TemplateImpl)manager.createTemplate("ttt", "user", "$VAR$+x");
    template.addVariable("VAR", "regularExpression(SELECTION, \"_\", \"\")", "", false);
    assertTrue(template.isSelectionTemplate());
    template.getTemplateContext().setEnabled(contextType(JavaCommentContextType.class), true);
    CodeInsightTestUtil.addTemplate(template, getTestRootDisposable());

    myFixture.configureByText("a.java", "//a <selection><caret>foo_bar</selection> b");
    List<AnAction> group = SurroundWithTemplateHandler.createActionGroup(getEditor(), myFixture.getFile(), new HashSet<>());
    InvokeTemplateAction action =
      (InvokeTemplateAction)ContainerUtil.find(group, a -> a.getTemplatePresentation().getText().contains(template.getKey()));
    assertNotNull(action);
    action.perform();
    myFixture.checkResult("//a foobar+x b");
  }

  public void testCompletionInDumbMode() {
    TemplateManager manager = TemplateManager.getInstance(getProject());
    Template template = manager.createTemplate("helloWorld", "user", "\"Hello, World\"");
    TemplateContextType contextType = contextType(JavaCodeContextType.class);
    ((TemplateImpl)template).getTemplateContext().setEnabled(contextType, true);
    CodeInsightTestUtil.addTemplate(template, myFixture.getTestRootDisposable());

    myFixture.configureByText("a.java", "class Foo {{ System.out.println(helloW<caret>) }}");
    LiveTemplateCompletionContributor.setShowTemplatesInTests(true, myFixture.getTestRootDisposable());
    DumbModeTestUtils.runInDumbModeSynchronously(getProject(), () -> {
      RecursionManager.disableMissedCacheAssertions(getTestRootDisposable());
      myFixture.completeBasic();
      assertNotNull(myFixture.getLookup());
      assertTrue(myFixture.getLookupElementStrings().contains("helloWorld"));
      myFixture.type("\t");
      myFixture.checkResult("class Foo {{ System.out.println(\"Hello, World\") }}");
    });
  }

  public void testLogLivetemplateStartedEvent() {
    List<LogEvent> events = FUCollectorTestCase.INSTANCE.collectLogEvents(getTestRootDisposable(), () -> {
      configureFromFileText("empty.java", "");
      TemplateManager manager = TemplateManager.getInstance(getProject());
      Template template = manager.createTemplate("empty", "user", "$VAR$");
      template.addVariable("VAR", "", "", false);
      startTemplate(template);
      return null;
    });
    LogEvent logEvent = ContainerUtil.find(events, event -> event.getGroup().getId().equals("live.templates"));
    assertNotNull(logEvent);
    assertEquals("started", logEvent.getEvent().getId());
  }

  public void testAdditionalActions() {
    TemplateManager manager = TemplateManager.getInstance(getProject());
    TemplateImpl template = (TemplateImpl)manager.createTemplate("doubleparen", "user", "(($END$$SELECTION$))");
    TemplateContextType contextType = contextType(JavaCodeContextType.class);
    template.getTemplateContext().setEnabled(contextType, true);
    CodeInsightTestUtil.addTemplate(template, myFixture.getTestRootDisposable());
    myFixture.configureByText("a.java", "class X {{<selection>hello</selection>}}");
    List<AnAction> group = SurroundWithTemplateHandler.createActionGroup(getEditor(), myFixture.getFile(), new HashSet<>());
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
  }

  public void testEquality() {
    TemplateManager manager = TemplateManager.getInstance(getProject());
    TemplateImpl t1 = (TemplateImpl)manager.createTemplate("k", "g", "t");
    TemplateImpl t2 = (TemplateImpl)manager.createTemplate("k", "g", "t");
    assertEquals(t1, t2);
    t1.setValue(Template.Property.USE_STATIC_IMPORT_IF_POSSIBLE, true);
    //noinspection SimplifiableAssertion
    assertFalse(t1.equals(t2));
  }

  @Override
  public final String getBasePath() {
    return basePath;
  }

  private final String basePath = JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/template/";

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
