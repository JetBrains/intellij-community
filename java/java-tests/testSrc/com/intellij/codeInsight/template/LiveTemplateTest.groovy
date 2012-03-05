/*
 * Copyright (c) 2005 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.codeInsight.template;


import com.intellij.JavaTestUtil
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.codeInsight.lookup.impl.LookupManagerImpl
import com.intellij.openapi.application.AccessToken
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.annotations.NotNull
import com.intellij.codeInsight.template.impl.*

/**
 * @author spleaner
 */
public class LiveTemplateTest extends LightCodeInsightFixtureTestCase {

  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/template/";
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    ((TemplateManagerImpl)TemplateManager.getInstance(getProject())).setTemplateTesting(true);
  }

  @Override
  protected void tearDown() throws Exception {
    ((TemplateManagerImpl)TemplateManager.getInstance(getProject())).setTemplateTesting(false);
    TemplateState state = TemplateManagerImpl.getTemplateState(myFixture.getEditor());
    if (state != null) {
      state.gotoEnd();
    }
    super.tearDown();
  }

  private void doTestTemplateWithArg(@NotNull String templateName,
                                   @NotNull String templateText,
                                   @NotNull String fileText,
                                   @NotNull String expected) throws IOException {
    configureFromFileText("dummy.java", fileText);
    final TemplateManager manager = TemplateManager.getInstance(getProject());
    String group = "user";
    final Template template = manager.createTemplate(templateName, group, templateText);
    template.addVariable("ARG", "", "", false);
    final TemplateContextType contextType =
      ContainerUtil.findInstance(TemplateContextType.EP_NAME.getExtensions(), JavaCodeContextType.class);
    ((TemplateImpl)template).getTemplateContext().setEnabled(contextType, true);
    TemplateSettings settings = TemplateSettings.getInstance();
    try {
      settings.addTemplate(template);
      final Editor editor = getEditor();

      manager.startTemplate(editor, settings.getDefaultShortcutChar());
      checkResultByText(expected);
    }
    finally {
      if (settings.getTemplate(template.getKey(), group) != null) {
        settings.removeTemplate(template);
      }
    }
  }

  public void testTemplateWithArg1() throws IOException {
    doTestTemplateWithArg("tst", 'wrap($ARG$)', "tst arg<caret>", "wrap(arg)");
  }

  public void testTemplateWithArg2() throws IOException {
    doTestTemplateWithArg("tst#", 'wrap($ARG$)', "tst#arg<caret>", "wrap(arg)");
  }

  public void testTemplateWithArg3() throws IOException {
    doTestTemplateWithArg("tst#", 'wrap($ARG$)', "tst# arg<caret>", "tst# arg");
  }

  public void testTemplateAtEndOfFile() throws Exception {
    configureFromFileText("empty.java", "");
    final TemplateManager manager = TemplateManager.getInstance(getProject());
    final Template template = manager.createTemplate("empty", "user", '$VAR$');
    template.addVariable("VAR", "", "", false);
    final Editor editor = getEditor();

    manager.startTemplate(editor, template);
    checkResultByText("");

  }

  public void testTemplateWithEnd() throws Exception {
    configureFromFileText("empty.java", "");
    final TemplateManager manager = TemplateManager.getInstance(getProject());
    final Template template = manager.createTemplate("empty", "user", '$VAR$$END$');
    template.addVariable("VAR", "bar", "bar", true);
    template.setToReformat(true);
    final Editor editor = getEditor();

    manager.startTemplate(editor, template);
    myFixture.type("foo");
    checkResultByText("foo");
  }

  private void checkResultByText(String text) {
    myFixture.checkResult(text);
  }

  private void configureFromFileText(String name, String text) {
    myFixture.configureByText(name, text);
  }

  public void testEndInTheMiddle() throws Exception {
    configure();
    final TemplateManager manager = TemplateManager.getInstance(getProject());
    final Template template = manager.createTemplate("frm", "user", "javax.swing.JFrame frame = new javax.swing.JFrame();\n" +
                                                                    '$END$\n' +
                                                                    "frame.setVisible(true);\n" +
                                                                    "frame.setDefaultCloseOperation(javax.swing.JFrame.EXIT_ON_CLOSE);\n" +
                                                                    "frame.pack();");
    template.setToShortenLongNames(false);
    template.setToReformat(true);
    manager.startTemplate(getEditor(), template);
    checkResult();
  }

  private Editor getEditor() {
    return myFixture.getEditor();
  }

  private void checkResult() {
    checkResultByFile(getTestName(false) + "-out.java");
  }

  private void checkResultByFile(String s) {
    myFixture.checkResultByFile(s);
  }

  public void testToar() throws Throwable {
    configure();
    TemplateManager.getInstance(getProject()).startTemplate(getEditor(), TemplateSettings.getInstance().getTemplate("toar", "other"));
    TemplateManagerImpl.getTemplateState(getEditor()).gotoEnd();
    checkResult();
  }

  private void configure() {
    myFixture.configureByFile(getTestName(false) + ".java");
  }

  public void testIter() throws Throwable {
    configure();
    TemplateManager.getInstance(getProject()).startTemplate(getEditor(), TemplateSettings.getInstance().getTemplate("iter", "iterations"));
    final TemplateState state = TemplateManagerImpl.getTemplateState(getEditor());
    state.nextTab();
    ((LookupImpl)LookupManagerImpl.getActiveLookup(getEditor())).finishLookup((char)0);
    checkResult();
  }

  public void testIter1() throws Throwable {
    configure();
    TemplateManager.getInstance(getProject()).startTemplate(getEditor(), TemplateSettings.getInstance().getTemplate("iter", "iterations"));
    final TemplateState state = TemplateManagerImpl.getTemplateState(getEditor());
    state.nextTab();
    checkResult();
  }

  public void _testIterForceBraces() {
    CodeStyleSettingsManager.getSettings(getProject()).IF_BRACE_FORCE = CommonCodeStyleSettings.FORCE_BRACES_ALWAYS;

    try {
      configure();
      TemplateManager.getInstance(getProject()).startTemplate(getEditor(), TemplateSettings.getInstance().getTemplate("iter", "iterations"));
      stripTrailingSpaces();
      checkResult();
    }
    finally {
      CodeStyleSettingsManager.getSettings(getProject()).IF_BRACE_FORCE = CommonCodeStyleSettings.DO_NOT_FORCE;
    }
  }

  private void stripTrailingSpaces() {
    DocumentImpl document = (DocumentImpl)getEditor().getDocument();
    document.setStripTrailingSpacesEnabled(true);
    document.stripTrailingSpaces();
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
  }

  public void testIterParameterizedInner() {
    configure();
    TemplateManager.getInstance(getProject()).startTemplate(getEditor(), TemplateSettings.getInstance().getTemplate("iter", "iterations"));
    stripTrailingSpaces();
    checkResult();
  }

  public void testVarargToar() {
    configure();
    TemplateManager.getInstance(getProject()).startTemplate(getEditor(), TemplateSettings.getInstance().getTemplate("toar", "other"));
    checkResult();
  }

  public void testSoutp() {
    configure();
    TemplateManager.getInstance(getProject()).startTemplate(getEditor(), TemplateSettings.getInstance().getTemplate("soutp", "output"));
    checkResult();
  }

  public void testJavaStatementContext() throws Exception {
    final TemplateImpl template = TemplateSettings.getInstance().getTemplate("inst", "other");
    assertFalse(isApplicable("class Foo {{ if (a inst<caret>) }}", template));
    assertTrue(isApplicable("class Foo {{ <caret>inst }}", template));
    assertTrue(isApplicable("class Foo {{ <caret>inst\n a=b; }}", template));
    assertFalse(isApplicable("class Foo {{ return (<caret>inst) }}", template));
    assertFalse(isApplicable("class Foo {{ return a <caret>inst) }}", template));
    assertTrue(isApplicable("class Foo {{ <caret>a.b(); ) }}", template));
    assertTrue(isApplicable("class Foo {{ <caret>a(); ) }}", template));
  }

  public void testJavaExpressionContext() throws Exception {
    final TemplateImpl template = TemplateSettings.getInstance().getTemplate("toar", "other");
    assertFalse(isApplicable("class Foo {{ if (a <caret>toar) }}", template));
    assertTrue(isApplicable("class Foo {{ <caret>toar }}", template));
    assertTrue(isApplicable("class Foo {{ return (<caret>toar) }}", template));
    assertFalse(isApplicable("class Foo {{ return (aaa <caret>toar) }}", template));
  }

  public void testJavaDeclarationContext() throws Exception {
    final TemplateImpl template = TemplateSettings.getInstance().getTemplate("psvm", "other");
    assertFalse(isApplicable("class Foo {{ <caret>xxx }}", template));
    assertFalse(isApplicable("class Foo {{ <caret>xxx }}", template));
    assertFalse(isApplicable("class Foo {{ if (a <caret>xxx) }}", template));
    assertFalse(isApplicable("class Foo {{ return (<caret>xxx) }}", template));
    assertTrue(isApplicable("class Foo { <caret>xxx }", template));
    assertFalse(isApplicable("class Foo { int <caret>xxx }", template));
    assertTrue(isApplicable("class Foo {} <caret>xxx", template));

    assertTrue(isApplicable("class Foo { void foo(<caret>xxx) {} }", template));
    assertTrue(isApplicable("class Foo { void foo(<caret>xxx String bar ) {} }", template));
    assertTrue(isApplicable("class Foo { void foo(<caret>xxx String bar, int goo ) {} }", template));
    assertTrue(isApplicable("class Foo { void foo(String bar, <caret>xxx int goo ) {} }", template));
    assertTrue(isApplicable("class Foo { void foo(String bar, <caret>xxx goo ) {} }", template));
    assertTrue(isApplicable("class Foo { <caret>xxx void foo(String bar, xxx goo ) {} }", template));
    assertTrue(isApplicable("class Foo { void foo(<caret>String[] bar) {} }", template));
    assertTrue(isApplicable("class Foo { <caret>xxx String[] foo(String[] bar) {} }", template));
  }

  public void testOtherContext() throws IOException {
    configureFromFileText("a.java", "class Foo { <caret>xxx }");
    assertInstanceOf(assertOneElement(TemplateManagerImpl.getApplicableContextTypes(myFixture.getFile(), getEditor().getCaretModel().getOffset())), JavaCodeContextType.Declaration.class);

    configureFromFileText("a.txt", "class Foo { <caret>xxx }");
    assertInstanceOf(assertOneElement(TemplateManagerImpl.getApplicableContextTypes(myFixture.getFile(), getEditor().getCaretModel().getOffset())), EverywhereContextType.class);
  }

  private boolean isApplicable(String text, TemplateImpl inst) throws IOException {
    configureFromFileText("a.java", text);
    return TemplateManagerImpl.isApplicable(myFixture.getFile(), getEditor().getCaretModel().getOffset(), inst);
  }

  @Override
  protected void invokeTestRunnable(final Runnable runnable) throws Exception {
    if (name in ["testNavigationActionsDontTerminateTemplate", "testTemplateWithEnd", "testDisappearingVar"]) {
      runnable.run();
      return;
    }

    writeCommand(runnable)
  }

  private writeCommand(Runnable runnable) {
    CommandProcessor.instance.executeCommand(project, {
      AccessToken token = WriteAction.start()
      try {
        runnable.run()
      }
      finally {
        token.finish()
      }
    }, null, null)
  }

  public void testSearchByDescriptionWhenTemplatesListed() {
    myFixture.configureByText("a.java", "class A {{ <caret> }}")

    new ListTemplatesHandler().invoke(project, editor, myFixture.file);
    myFixture.type('array')
    assert 'itar' in myFixture.lookupElementStrings
  }

  public void testListTemplatesAction() {
    myFixture.configureByText("a.java", "class A {{ <caret> }}")

    new ListTemplatesHandler().invoke(project, editor, myFixture.file);
    assert myFixture.lookupElementStrings.containsAll(['iter', 'itco', 'toar'])
    
    myFixture.type('it')
    assert myFixture.lookupElementStrings[0].startsWith('it')
    LookupManager.getInstance(project).hideActiveLookup()

    myFixture.type('\b')
    new ListTemplatesHandler().invoke(project, editor, myFixture.file);
    assert myFixture.lookupElementStrings.containsAll(['iter', 'itco'])
    assert !('toar' in myFixture.lookupElementStrings)
    LookupManager.getInstance(project).hideActiveLookup()

    myFixture.type('xxxxx')
    new ListTemplatesHandler().invoke(project, editor, myFixture.file);
    assert myFixture.lookupElementStrings.containsAll(['iter', 'itco', 'toar'])
    LookupManager.getInstance(project).hideActiveLookup()
  }

  public void testSelectionFromLookupBySpace() {
    myFixture.configureByText("a.java", "class A {{ itc<caret> }}")

    new ListTemplatesHandler().invoke(project, editor, myFixture.file);
    myFixture.type ' '
    myFixture.checkResult "import java.util.Iterator;\n" +
                          "\n" +
                          "class A {{\n" +
                          "    for (Iterator <selection>iterator</selection> = collection.iterator(); iterator.hasNext(); ) {\n" +
                          "        Object next =  iterator.next();\n" +
                          "        \n" +
                          "    } }}"
  }

  public void testNavigationActionsDontTerminateTemplate() throws Throwable {
    configureFromFileText("a.txt", "")

    final TemplateManager manager = TemplateManager.getInstance(getProject());
    final Template template = manager.createTemplate("vn", "user", 'Hello $V1$ World $V1$\nHello $V2$ World $V2$\nHello $V3$ World $V3$');
    template.addVariable("V1", "", "", true);
    template.addVariable("V2", "", "", true);
    template.addVariable("V3", "", "", true);
    final Editor editor = getEditor();

    writeCommand { manager.startTemplate(editor, template) }

    final TemplateState state = TemplateManagerImpl.getTemplateState(getEditor());

    for (int i = 0; i < 3; i++) {
      assertFalse(String.valueOf(i), state.isFinished());
      myFixture.type('H');
      final String docText = editor.getDocument().getText();
      assertTrue(docText, docText.startsWith("Hello H World H\n"));
      final int offset = editor.getCaretModel().getOffset();

      moveCaret(offset + 1);
      moveCaret(offset);

      myFixture.completeBasic()
      myFixture.type(' ');

      assertEquals(offset + 1, editor.getCaretModel().getOffset());
      assertFalse(state.isFinished());

      myFixture.type('\b');
      assertFalse(state.isFinished());
      writeCommand { state.nextTab() }
    }
    assertTrue(state.isFinished());
    checkResultByFile(getTestName(false) + "-out.txt");
  }

  private void moveCaret(final int offset) {
    edt {
      getEditor().getCaretModel().moveToOffset(offset);
    }
  }

  public void testUseDefaultValueForQuickResultCalculation() {
    myFixture.configureByText 'a.txt', '<caret>'

    final TemplateManager manager = TemplateManager.getInstance(getProject());
    final Template template = manager.createTemplate("vn", "user", '$V1$ var = $V2$;');
    template.addVariable("V1", "", "", true);
    template.addVariable("V2", "", '"239"', true);

    writeCommand { manager.startTemplate(editor, template) }

    myFixture.checkResult '<caret> var = 239;'

    myFixture.type 'O'
    myFixture.checkResult 'O<caret> var = 239;'

    myFixture.type '\t'
    myFixture.checkResult 'O var = <selection>239</selection>;'
  }

}
