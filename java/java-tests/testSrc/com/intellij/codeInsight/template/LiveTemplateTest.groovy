/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.template

import com.intellij.JavaTestUtil
import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.codeInsight.lookup.impl.LookupManagerImpl
import com.intellij.codeInsight.template.impl.*
import com.intellij.codeInsight.template.macro.ClassNameCompleteMacro
import com.intellij.codeInsight.template.macro.CompleteMacro
import com.intellij.codeInsight.template.macro.MethodReturnTypeMacro
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.AccessToken
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.annotations.NotNull

import static com.intellij.codeInsight.template.Template.Property.USE_STATIC_IMPORT_IF_POSSIBLE

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
    TemplateManagerImpl.setTemplateTesting(getProject(), getTestRootDisposable());
  }

  @Override
  protected void tearDown() throws Exception {
    CodeInsightSettings.instance.COMPLETION_CASE_SENSITIVE = CodeInsightSettings.FIRST_LETTER
    CodeInsightSettings.instance.SELECT_AUTOPOPUP_SUGGESTIONS_BY_CHARS = false
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
    addTemplate(template, testRootDisposable)
    final Editor editor = getEditor();

    manager.startTemplate(editor, (char)'\t');
    checkResultByText(expected);
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

  public void testTemplateWithEndOnEmptyLine() throws Exception {
    configureFromFileText("empty.java", "class C {\n" +
                                        "  bar() {\n" +
                                        "    <caret>\n" +
                                        "  }\n" +
                                        "}");
    TemplateManager manager = TemplateManager.getInstance(getProject());
    Template template = manager.createTemplate("empty", "user", 'foo()\n' +
                                                                '  $END$\n' +
                                                                'foo()');
    template.setToReformat(true);
    manager.startTemplate(getEditor(), template);
    checkResultByText("class C {\n" +
                      "  bar() {\n" +
                      "      foo()\n" +
                      "              <caret>\n" +
                      "      foo()\n" +
                      "  }\n" +
                      "}");
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

  public void "test honor custom completion caret placement"() {
    myFixture.configureByText 'a.java', '''
class Foo {
  void foo(int a) {}
  { <caret> }
}
'''
    final TemplateManager manager = TemplateManager.getInstance(getProject());
    final Template template = manager.createTemplate("frm", "user", '$VAR$');
    template.addVariable('VAR', new MacroCallNode(new CompleteMacro()), new EmptyNode(), true)
    manager.startTemplate(getEditor(), template);
    myFixture.type('fo\n')
    myFixture.checkResult '''
class Foo {
  void foo(int a) {}
  { foo(<caret>); }
}
'''
    assert !state.finished
  }

  public void "test cancel template when completion placed caret outside the variable"() {
    myFixture.configureByText 'a.java', '''
class Foo {
  void foo(int a) {}
  { <caret>() }
}
'''
    final TemplateManager manager = TemplateManager.getInstance(getProject());
    final Template template = manager.createTemplate("frm", "user", '$VAR$');
    template.addVariable('VAR', new MacroCallNode(new CompleteMacro()), new EmptyNode(), true)
    manager.startTemplate(getEditor(), template);
    myFixture.type('fo\n')
    myFixture.checkResult '''
class Foo {
  void foo(int a) {}
  { foo(<caret>); }
}
'''
    assert !state
  }

  public void "test non-imported classes in className macro"() {
    myFixture.addClass('package bar; public class Bar {}')
    myFixture.configureByText 'a.java', '''
class Foo {
  void foo(int a) {}
  { <caret> }
}
'''
    final TemplateManager manager = TemplateManager.getInstance(getProject());
    final Template template = manager.createTemplate("frm", "user", '$VAR$');
    template.addVariable('VAR', new MacroCallNode(new ClassNameCompleteMacro()), new EmptyNode(), true)
    manager.startTemplate(getEditor(), template);
    assert !state.finished
    assert 'Bar' in myFixture.lookupElementStrings
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
    startTemplate("toar", "other")
    state.gotoEnd();
    checkResult();
  }

  def startTemplate(String name, String group) {
    TemplateManager.getInstance(getProject()).startTemplate(getEditor(), TemplateSettings.getInstance().getTemplate(name, group));
  }

  private void configure() {
    myFixture.configureByFile(getTestName(false) + ".java");
  }

  public void testIter() throws Throwable {
    configure();
    startTemplate("iter", "iterations")
    state.nextTab();
    ((LookupImpl)LookupManagerImpl.getActiveLookup(getEditor())).finishLookup((char)0);
    checkResult();
  }

  public void testPreferStartMatchesInLookups() throws Throwable {
    configure();
    startTemplate("iter", "iterations")
    myFixture.type('ese\n') //for entrySet
    assert myFixture.lookupElementStrings == ['barGooStringBuilderEntry', 'gooStringBuilderEntry', 'stringBuilderEntry', 'builderEntry', 'entry']
    myFixture.type('e')
    assert myFixture.lookupElementStrings == ['entry', 'barGooStringBuilderEntry', 'gooStringBuilderEntry', 'stringBuilderEntry', 'builderEntry']
    assert LookupManager.getActiveLookup(editor).currentItem.lookupString == 'entry'
  }

  public void testClassNameDotInTemplate() {
    CodeInsightSettings.instance.COMPLETION_CASE_SENSITIVE = CodeInsightSettings.NONE
    configure();
    startTemplate("soutv", "output")
    myFixture.type('File')
    assert myFixture.lookupElementStrings == ['file']
    myFixture.type('.')
    checkResult()
    assert !state.finished
  }

  public void testFinishTemplateVariantWithDot() {
    CodeInsightSettings.instance.SELECT_AUTOPOPUP_SUGGESTIONS_BY_CHARS = true
    configure();
    startTemplate("soutv", "output")
    myFixture.type('fil')
    assert myFixture.lookupElementStrings == ['file']
    myFixture.type('.')
    checkResult()
    assert !state.finished
  }

  public void testAllowTypingRandomExpressionsWithLookupOpen() {
    configure();
    startTemplate("iter", "iterations")
    myFixture.type('file.')
    checkResult()
    assert !state.finished
  }

  private TemplateState getState() {
    TemplateManagerImpl.getTemplateState(getEditor())
  }

  public void testIter1() throws Throwable {
    configure();
    startTemplate("iter", "iterations")
    state.nextTab();
    checkResult();
  }

  public void _testIterForceBraces() {
    CodeStyleSettingsManager.getSettings(getProject()).IF_BRACE_FORCE = CommonCodeStyleSettings.FORCE_BRACES_ALWAYS;

    try {
      configure();
      startTemplate("iter", "iterations")
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
    document.stripTrailingSpaces(getProject());
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
  }

  public void testIterParameterizedInner() {
    configure();
    startTemplate("iter", "iterations")
    stripTrailingSpaces();
    checkResult();
  }

  public void testAsListToar() {
    configure();
    startTemplate("toar", "other")
    myFixture.type('\n\t')
    checkResult();
  }

  public void testVarargToar() {
    configure();
    startTemplate("toar", "other")
    checkResult();
  }

  public void testSoutp() {
    configure();
    startTemplate("soutp", "output")
    checkResult();
  }

  public void testJavaStatementContext() throws Exception {
    final TemplateImpl template = TemplateSettings.getInstance().getTemplate("inst", "other");
    assertFalse(isApplicable("class Foo {{ if (a inst<caret>) }}", template));
    assertTrue(isApplicable("class Foo {{ <caret>inst }}", template));
    assertTrue(isApplicable("class Foo {{ <caret>inst\n a=b; }}", template));
    assertFalse(isApplicable("class Foo {{ return (<caret>inst) }}", template));
    assertFalse(isApplicable("class Foo {{ return a <caret>inst) }}", template));
    assertFalse(isApplicable("class Foo {{ \"<caret>\" }}", template));
    assertTrue(isApplicable("class Foo {{ <caret>a.b(); ) }}", template));
    assertTrue(isApplicable("class Foo {{ <caret>a(); ) }}", template));
    assertTrue(isApplicable("class Foo {{ Runnable r = () -> { <caret>System.out.println(\"foo\"); }; ) }}", template));
    assertTrue(isApplicable("class Foo {{ Runnable r = () -> <caret>System.out.println(\"foo\"); ) }}", template));
  }

  public void testJavaExpressionContext() throws Exception {
    final TemplateImpl template = TemplateSettings.getInstance().getTemplate("toar", "other");
    assertFalse(isApplicable("class Foo {{ if (a <caret>toar) }}", template));
    assertTrue(isApplicable("class Foo {{ <caret>toar }}", template));
    assertTrue(isApplicable("class Foo {{ return (<caret>toar) }}", template));
    assertFalse(isApplicable("class Foo {{ return (aaa <caret>toar) }}", template));
    assertTrue(isApplicable("class Foo {{ Runnable r = () -> { <caret>System.out.println(\"foo\"); }; ) }}", template));
    assertTrue(isApplicable("class Foo {{ Runnable r = () -> <caret>System.out.println(\"foo\"); ) }}", template));
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
    
    assertTrue(isApplicable("<caret>xxx package foo; class Foo {}", template));
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
    if (name in ["testNavigationActionsDontTerminateTemplate", "testTemplateWithEnd", "testDisappearingVar", "test escape string characters in soutv"]) {
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

  public void testListTemplatesSearchesPrefixInDescription() {
    myFixture.configureByText("a.java", "class A { main<caret> }")

    new ListTemplatesHandler().invoke(project, editor, myFixture.file);
    assert myFixture.lookupElementStrings == ['psvm']
  }

  public void testListTemplatesAction() {
    myFixture.configureByText("a.java", "class A {{ <caret> }}")

    new ListTemplatesHandler().invoke(project, editor, myFixture.file);
    assert myFixture.lookupElementStrings.containsAll(['iter', 'itco', 'toar'])
    
    myFixture.type('it')
    assert myFixture.lookupElementStrings[0].startsWith('it')
    assert LookupManager.getInstance(project).activeLookup.currentItem == myFixture.getLookupElements()[0]

    myFixture.type('e')
    assert myFixture.lookupElementStrings[0].startsWith('ite')
    assert LookupManager.getInstance(project).activeLookup.currentItem == myFixture.getLookupElements()[0]
    LookupManager.getInstance(project).hideActiveLookup()

    myFixture.type('\b\b')
    new ListTemplatesHandler().invoke(project, editor, myFixture.file);
    assert myFixture.lookupElementStrings.containsAll(['iter', 'itco'])
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

    final TemplateState state = getState();

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

  public void testTemplateExpandingWithSelection() {
    final TemplateManager manager = TemplateManager.getInstance(getProject());
    final Template template = manager.createTemplate("tpl", "user", 'expanded');
    final JavaStringContextType contextType =
      ContainerUtil.findInstance(TemplateContextType.EP_NAME.getExtensions(), JavaStringContextType.class);
    ((TemplateImpl)template).getTemplateContext().setEnabled(contextType, true);

    myFixture.configureByText("a.java", "class A { void f() { Stri<selection>ng s = \"tpl</selection><caret>\"; } }")

    addTemplate(template, testRootDisposable)
    myFixture.type '\t'
    myFixture.checkResult 'class A { void f() { Stri    "; } }'
  }
  
  static void addTemplate(Template template, Disposable parentDisposable) {
    def settings = TemplateSettings.getInstance()
    settings.addTemplate(template);
    Disposer.register(parentDisposable, { settings.removeTemplate(template) } as Disposable)
  }

  public void "test expand current live template on no suggestions in lookup"() {
    myFixture.configureByText "a.java", "class Foo {{ <caret> }}"
    myFixture.completeBasic()
    assert myFixture.lookup
    myFixture.type("sout")
    assert myFixture.lookup
    assert myFixture.lookupElementStrings == []
    myFixture.type('\t')
    myFixture.checkResult "class Foo {{\n    System.out.println(<caret>); }}"
  }

  public void "_test multi-dimensional toar"() {
    myFixture.configureByText "a.java", '''
class Foo {{
  java.util.List<String[]> list;
  String[][] s = toar<caret>
}}'''
    myFixture.type('\t')
    //state.gotoEnd()
    myFixture.checkResult '''
class Foo {{
  java.util.List<String[]> list;
  String[][] s = list.toArray(new String[list.size()][])<caret>
}}''' 
  }

  public void "test inner class name"() {
    myFixture.configureByText "a.java", '''
class Outer {
    class Inner {
        void foo() {
            soutm<caret>
        }
    }
}'''
    myFixture.type('\t')
    assert myFixture.editor.document.text.contains("Outer.Inner.foo")
  }

  public void "test do not strip type argument containing class"() {
    myFixture.configureByText 'a.java', '''
import java.util.*;
class Foo {
  List<Map.Entry<String, Integer>> foo() { 
    <caret> 
  }
}
'''
    
    final TemplateManager manager = TemplateManager.getInstance(getProject());
    final Template template = manager.createTemplate("result", "user", '$T$ result;');
    template.addVariable('T', new MacroCallNode(new MethodReturnTypeMacro()), new EmptyNode(), false)
    template.toReformat = true
    
    manager.startTemplate(getEditor(), template);
    assert myFixture.editor.document.text.contains('List<Map.Entry<String, Integer>> result;')
  }

  public void "test name shadowing"() {
    myFixture.configureByText "a.java", """class LiveTemplateVarSuggestion {
    private Object value;
    public void setValue(Object value, Object value1){
      inn<caret>
    }
}"""
    myFixture.type('\t')
    assert myFixture.lookupElementStrings == ['value', 'value1']
  }

  public void "test invoke surround template by tab"() {
    myFixture.configureByText "a.txt", "B<caret>"
    myFixture.type('\t')
    myFixture.checkResult("{<caret>}")
  }

  public void "test escape string characters in soutv"() {
    myFixture.configureByText "a.java", """
class Foo {
  {
    soutv<caret>
  }
}
"""
    myFixture.type('\t"a"')
    myFixture.checkResult """
class Foo {
  {
      System.out.println("\\"a\\" = " + "a"<caret>);
  }
}
"""
  }

  public void "test stop at SELECTION when invoked surround template by tab"() {
    myFixture.configureByText "a.txt", "<caret>"
    
    final TemplateManager manager = TemplateManager.getInstance(getProject());
    final Template template = manager.createTemplate("xxx", "user", 'foo $ARG$ bar $END$ goo $SELECTION$ after');
    template.addVariable("ARG", "", "", true);
    
    manager.startTemplate(editor, template);
    myFixture.type('arg')
    state.nextTab()
    assert !state
    checkResultByText 'foo arg bar  goo <caret> after';
  }

  public void "test reuse static import"() {
    myFixture.addClass("""package foo; 
public class Bar { 
  public static void someMethod() {}
  public static void someMethod(int a) {}
}""")
    myFixture.configureByText "a.java", """
import static foo.Bar.someMethod;

class Foo {
  {
    <caret>
  }
}
"""
    final TemplateManager manager = TemplateManager.getInstance(getProject());
    final Template template = manager.createTemplate("xxx", "user", 'foo.Bar.someMethod($END$)');
    template.setValue(USE_STATIC_IMPORT_IF_POSSIBLE, true);

    manager.startTemplate(editor, template);
    myFixture.checkResult """
import static foo.Bar.someMethod;

class Foo {
  {
    someMethod(<caret>)
  }
}
"""

  }

}
