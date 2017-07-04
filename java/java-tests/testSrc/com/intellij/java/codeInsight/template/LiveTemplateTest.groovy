/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.java.codeInsight.template

import com.intellij.JavaTestUtil
import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.daemon.impl.quickfix.EmptyExpression
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.template.*
import com.intellij.codeInsight.template.impl.*
import com.intellij.codeInsight.template.macro.*
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import com.intellij.testFramework.fixtures.CodeInsightTestUtil
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import com.intellij.util.JdomKt
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.ui.UIUtil
import org.jdom.Element
import org.jetbrains.annotations.NotNull

import static com.intellij.codeInsight.template.Template.Property.USE_STATIC_IMPORT_IF_POSSIBLE
import static com.intellij.testFramework.EdtTestUtil.runInEdtAndWait

/**
 * @author spleaner
 */
@SuppressWarnings("SpellCheckingInspection")
class LiveTemplateTest extends LightCodeInsightFixtureTestCase {
  final String basePath = JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/template/"

  @Override
  protected void setUp() {
    super.setUp()
    TemplateManagerImpl.setTemplateTesting(getProject(), myFixture.getTestRootDisposable())
  }

  @Override
  protected void tearDown() {
    CodeInsightSettings.instance.COMPLETION_CASE_SENSITIVE = CodeInsightSettings.FIRST_LETTER
    CodeInsightSettings.instance.SELECT_AUTOPOPUP_SUGGESTIONS_BY_CHARS = false
    if (state != null) {
      WriteCommandAction.runWriteCommandAction project, {
        state.gotoEnd()
      }
    }
    super.tearDown()
  }

  private void doTestTemplateWithArg(@NotNull String templateName,
                                     @NotNull String templateText,
                                     @NotNull String fileText,
                                     @NotNull String expected) throws IOException {
    configureFromFileText("dummy.java", fileText)
    final TemplateManager manager = TemplateManager.getInstance(getProject())
    String group = "user"
    final Template template = manager.createTemplate(templateName, group, templateText)
    template.addVariable("ARG", "", "", false)
    TemplateContextType contextType = contextType(JavaCodeContextType.class)
    ((TemplateImpl)template).getTemplateContext().setEnabled(contextType, true)
    CodeInsightTestUtil.addTemplate(template, myFixture.testRootDisposable)

    writeCommand { manager.startTemplate(editor, (char)'\t') }
    UIUtil.dispatchAllInvocationEvents()
    checkResultByText(expected)
  }

  void testDependentSegmentsAtTheSamePosition() {
    configureFromFileText("dummy.java", "class A { void test() { <caret> } }")
    TemplateManager manager = TemplateManager.getInstance(getProject())
    final Template template = manager.createTemplate("test_template", "user_group", '$A$$B$ then "$A$.$B$"')
    template.addVariable("A", "", "", true)
    template.addVariable("B", "", "", true)
    startTemplate(template)
    myFixture.type("HELLO")
    myFixture.type("\t")
    myFixture.type("THERE")
    myFixture.type("\t")
    assert state == null
    checkResultByText("class A { void test() { HELLOTHERE then \"HELLO.THERE\" } }")
  }

  void testTemplateWithSegmentsAtTheSamePosition_1() {
    doTestTemplateWithThreeVariables("", "", "", "class A { void test() { for(TestValue1TestValue2TestValue3) {} } }")
  }

  void testTemplateWithSegmentsAtTheSamePosition_2() {
    doTestTemplateWithThreeVariables("Def1", "Def2", "DefaultValue", "class A { void test() { for(Def1Def2DefaultValue) {} } }")
  }

  void testTemplateWithSegmentsAtTheSamePosition_3() {
    doTestTemplateWithThreeVariables("", "DefaultValue", "", "class A { void test() { for(TestValue1DefaultValueTestValue3) {} } }")
  }

  void testTemplateWithSegmentsAtTheSamePosition_4() {
    configureFromFileText("dummy.java", "class A { void test() { <caret> } }")
    TemplateManager manager = TemplateManager.getInstance(getProject())
    final Template template = manager.createTemplate("test_template", "user_group", '$A$$B$ then "$A$$B$"')
    template.addVariable("A", "", "\"Def1\"", true)
    template.addVariable("B", "", "\"Def2\"", true)
    startTemplate(template)
    checkResultByText("class A { void test() { Def1Def2 then \"Def1Def2\" } }")
  }

  private void doTestTemplateWithThreeVariables(String firstDefaultValue, String secondDefaultValue, String thirdDefaultValue, String expectedText) {
    configureFromFileText("dummy.java", "class A { void test() { <caret> } }")

    TemplateManager manager = TemplateManager.getInstance(getProject())
    def templateName = "tst_template"
    def templateGroup = "user"
    final Template template = manager.createTemplate(templateName, templateGroup, 'for($TEST1$$TEST2$$TEST3$) {}')
    template.addVariable("TEST1", "", StringUtil.wrapWithDoubleQuote(firstDefaultValue), true)
    template.addVariable("TEST2", "", StringUtil.wrapWithDoubleQuote(secondDefaultValue), true)
    template.addVariable("TEST3", "", StringUtil.wrapWithDoubleQuote(thirdDefaultValue), true)
    startTemplate(template)

    if (firstDefaultValue.empty) myFixture.type("TestValue1")
    myFixture.type("\t")
    if (secondDefaultValue.empty) myFixture.type("TestValue2")
    myFixture.type("\t")
    if (thirdDefaultValue.empty) myFixture.type("TestValue3")
    myFixture.type("\t")
    assert state == null
    checkResultByText(expectedText)
  }

  void testTemplateWithArg1() throws IOException {
    doTestTemplateWithArg("tst", 'wrap($ARG$)', "tst arg<caret>", "wrap(arg)")
  }

  void testTemplateWithArg2() throws IOException {
    doTestTemplateWithArg("tst#", 'wrap($ARG$)', "tst#arg<caret>", "wrap(arg)")
  }

  void testTemplateWithArg3() throws IOException {
    doTestTemplateWithArg("tst#", 'wrap($ARG$)', "tst# arg<caret>", "tst# arg")
  }

  void testTemplateAtEndOfFile() {
    configureFromFileText("empty.java", "")
    final TemplateManager manager = TemplateManager.getInstance(getProject())
    final Template template = manager.createTemplate("empty", "user", '$VAR$')
    template.addVariable("VAR", "", "", false)

    startTemplate(template)
    checkResultByText("")
  }

  void testTemplateWithEnd() {
    configureFromFileText("empty.java", "")
    final TemplateManager manager = TemplateManager.getInstance(getProject())
    final Template template = manager.createTemplate("empty", "user", '$VAR$$END$')
    template.addVariable("VAR", "bar", "bar", true)
    template.setToReformat(true)

    startTemplate(template)
    myFixture.type("foo")
    checkResultByText("foo")
  }

  void testTemplateWithIndentedEnd() {
    configureFromFileText("empty.java", "class C {\n" +
                                        "  bar() {\n" +
                                        "    <caret>\n" +
                                        "  }\n" +
                                        "}")
    TemplateManager manager = TemplateManager.getInstance(getProject())
    Template template = manager.createTemplate("empty", "user", 'foo();\n' +
                                                                'int i = 0;    $END$\n' +
                                                                'foo()')
    template.setToReformat(true)
    startTemplate(template)
    checkResultByText("class C {\n" +
                      "  bar() {\n" +
                      "      foo();\n" +
                      "      int i = 0;    <caret>\n" +
                      "      foo()\n" +
                      "  }\n" +
                      "}")
  }


  void testTemplateWithEndOnEmptyLine() {
    configureFromFileText("empty.java", "class C {\n" +
                                        "  bar() {\n" +
                                        "    <caret>\n" +
                                        "  }\n" +
                                        "}")
    TemplateManager manager = TemplateManager.getInstance(getProject())
    Template template = manager.createTemplate("empty", "user", 'foo()\n' +
                                                                '  $END$\n' +
                                                                'foo()')
    template.setToReformat(true)
    startTemplate(template)
    checkResultByText("class C {\n" +
                      "  bar() {\n" +
                      "      foo()\n" +
                      "              <caret>\n" +
                      "      foo()\n" +
                      "  }\n" +
                      "}")
  }

  private void checkResultByText(String text) {
    myFixture.checkResult(text)
  }

  private void configureFromFileText(String name, String text) {
    myFixture.configureByText(name, text)
  }

  void testEndInTheMiddle() {
    configure()
    final TemplateManager manager = TemplateManager.getInstance(getProject())
    final Template template = manager.createTemplate("frm", "user", "javax.swing.JFrame frame = new javax.swing.JFrame();\n" +
                                                                    '$END$\n' +
                                                                    "frame.setVisible(true);\n" +
                                                                    "frame.setDefaultCloseOperation(javax.swing.JFrame.EXIT_ON_CLOSE);\n" +
                                                                    "frame.pack();")
    template.setToShortenLongNames(false)
    template.setToReformat(true)
    startTemplate(template)
    checkResult()
  }

  void "test honor custom completion caret placement"() {
    myFixture.configureByText 'a.java', '''
class Foo {
  void foo(int a) {}
  { <caret> }
}
'''
    final TemplateManager manager = TemplateManager.getInstance(getProject())
    final Template template = manager.createTemplate("frm", "user", '$VAR$')
    template.addVariable('VAR', new MacroCallNode(new CompleteMacro()), new EmptyNode(), true)
    startTemplate(template)
    myFixture.type('fo\n')
    myFixture.checkResult '''
class Foo {
  void foo(int a) {}
  { foo(<caret>); }
}
'''
    assert !state.finished
  }

  void "test cancel template when completion placed caret outside the variable"() {
    myFixture.configureByText 'a.java', '''
class Foo {
  void foo(int a) {}
  { <caret>() }
}
'''
    final TemplateManager manager = TemplateManager.getInstance(getProject())
    final Template template = manager.createTemplate("frm", "user", '$VAR$')
    template.addVariable('VAR', new MacroCallNode(new CompleteMacro()), new EmptyNode(), true)
    startTemplate(template)
    myFixture.type('fo\n')
    myFixture.checkResult '''
class Foo {
  void foo(int a) {}
  { foo(<caret>); }
}
'''
    assert !state
  }

  void "test not to go to next tab after insert if element is a psi package"() {
    myFixture.configureByText 'a.java', '''
<caret>
'''
    final TemplateManager manager = TemplateManager.getInstance(getProject())
    final Template template = manager.createTemplate("imp", "user", 'import $MODIFIER$ java.$NAME$;')
    template.addVariable('NAME', new MacroCallNode(new CompleteMacro(true)), new EmptyNode(), true)
    template.addVariable('MODIFIER', new EmptyExpression(), true)
    startTemplate(template)
    myFixture.type('uti\n')
    myFixture.checkResult '''
import  java.util.<caret>;
'''
    assert !state.finished
  }

  void "test not to go to next tab after insert if element has call arguments"() {
    myFixture.configureByText 'a.java', '''
import  java.util.*;
public class Main {
    List<String> getStringList(int i){
        List<String> ints = null;
        <caret>
        return new ArrayList<>(i);
    }
}
'''
    final TemplateManager manager = TemplateManager.getInstance(getProject())
    final Template template = manager.createTemplate("for", "user", 'for ($ELEMENT_TYPE$ $VAR$ : $ITERABLE_TYPE$) {\n' +
                                                                    '$END$;\n' +
                                                                    '}')
    template.addVariable('ITERABLE_TYPE', new MacroCallNode(new CompleteSmartMacro()), new EmptyNode(), true)
    template.addVariable('VAR', new TextExpression("item"), true)
    template.addVariable('ELEMENT_TYPE', new TextExpression("String"), true)
    template.setToReformat(true)
    startTemplate(template)
    myFixture.type('get\n')
    myFixture.checkResult """
import  java.util.*;
public class Main {
    List<String> getStringList(int i){
        List<String> ints = null;
        for (String item : getStringList(<caret>)) {
            ;
        }
        return new ArrayList<>(i);
    }
}
"""
    assert !state.finished
  }

  void "test go to next tab after insert if element does not have call arguments"() {
    myFixture.configureByText 'a.java', '''
import  java.util.*;
public class Main {
    List<String> getStringList(int i){
        List<String> ints = null;
        <caret>
        return new ArrayList<>(i);
    }
}
'''
    final TemplateManager manager = TemplateManager.getInstance(getProject())
    final Template template = manager.createTemplate("for", "user", 'for ($ELEMENT_TYPE$ $VAR$ : $ITERABLE_TYPE$) {\n' +
                                                                    '$END$;\n' +
                                                                    '}')
    template.addVariable('ITERABLE_TYPE', new MacroCallNode(new CompleteSmartMacro()), new EmptyNode(), true)
    template.addVariable('VAR', new TextExpression("item"), true)
    template.addVariable('ELEMENT_TYPE', new TextExpression("String"), true)
    template.setToReformat(true)
    startTemplate(template)
    myFixture.type('in\n')
    myFixture.checkResult """
import  java.util.*;
public class Main {
    List<String> getStringList(int i){
        List<String> ints = null;
        for (String <selection>item</selection> : ints) {
            ;
        }
        return new ArrayList<>(i);
    }
}
"""
    assert !state.finished
  }

  void "test non-imported classes in className macro"() {
    myFixture.addClass('package bar; public class Bar {}')
    myFixture.configureByText 'a.java', '''
class Foo {
  void foo(int a) {}
  { <caret> }
}
'''
    final TemplateManager manager = TemplateManager.getInstance(getProject())
    final Template template = manager.createTemplate("frm", "user", '$VAR$')
    template.addVariable('VAR', new MacroCallNode(new ClassNameCompleteMacro()), new EmptyNode(), true)
    startTemplate(template)
    assert !state.finished
    assert 'Bar' in myFixture.lookupElementStrings
  }

  void "test variableOfType suggests inner static classes"() {
    myFixture.addClass('public interface MyCallback {}')
    myFixture.addClass('''
class MyUtils {
  public static void doSomethingWithCallback(MyCallback cb) { }
}
''')
    myFixture.configureByText 'a.java', '''
class Outer {
  static class Inner implements MyCallback {
    void aMethod() {
      <caret>
    }
  }
}
'''

    TemplateManager manager = TemplateManager.getInstance(getProject())
    Template template = manager.createTemplate("myCbDo", "user", 'MyUtils.doSomethingWithCallback($CB$)')

    MacroCallNode call = new MacroCallNode(new VariableOfTypeMacro())
    call.addParameter(new ConstantNode("MyCallback"))
    template.addVariable('CB', call, new EmptyNode(), false)
    startTemplate(template)

    checkResultByText '''
class Outer {
  static class Inner implements MyCallback {
    void aMethod() {
      MyUtils.doSomethingWithCallback(this)
    }
  }
}
'''
  }

  private void checkResult() {
    checkResultByFile(getTestName(false) + "-out.java")
  }

  private void checkResultByFile(String s) {
    myFixture.checkResultByFile(s)
  }

  void testToar() throws Throwable {
    configure()
    startTemplate("toar", "other")
    state.gotoEnd(false)
    checkResult()
  }

  def startTemplate(String name, char expandKey) {
    myFixture.type(name)
    myFixture.type(expandKey)
  }

  def startTemplate(String name, String group) {
    startTemplate(TemplateSettings.getInstance().getTemplate(name, group))
  }

  def startTemplate(Template template) {
    TemplateManager.getInstance(getProject()).startTemplate(getEditor(), template)
    UIUtil.dispatchAllInvocationEvents()
  }

  private static <T extends TemplateContextType> T contextType(Class<T> clazz) {
    ContainerUtil.findInstance(TemplateContextType.EP_NAME.getExtensions(), clazz)
  }

  private void configure() {
    myFixture.configureByFile(getTestName(false) + ".java")
  }

  void testIter() throws Throwable {
    configure()
    startTemplate("iter", "iterations")
    writeCommand { state.nextTab() }
    myFixture.finishLookup(Lookup.AUTO_INSERT_SELECT_CHAR)
    checkResult()
  }

  void testPreferStartMatchesInLookups() throws Throwable {
    configure()
    startTemplate("iter", "iterations")
    myFixture.type('ese\n') //for entrySet
    assert myFixture.lookupElementStrings == ['barGooStringBuilderEntry', 'gooStringBuilderEntry', 'stringBuilderEntry', 'builderEntry', 'entry']
    myFixture.type('e')
    assert myFixture.lookupElementStrings == ['entry', 'barGooStringBuilderEntry', 'gooStringBuilderEntry', 'stringBuilderEntry', 'builderEntry']
    assert LookupManager.getActiveLookup(editor).currentItem.lookupString == 'entry'
  }

  void testClassNameDotInTemplate() {
    CodeInsightSettings.instance.COMPLETION_CASE_SENSITIVE = CodeInsightSettings.NONE
    configure()
    startTemplate("soutv", "output")
    myFixture.type('File')
    assert myFixture.lookupElementStrings == ['file']
    myFixture.type('.')
    checkResult()
    assert !state.finished
  }

  void testFinishTemplateVariantWithDot() {
    CodeInsightSettings.instance.SELECT_AUTOPOPUP_SUGGESTIONS_BY_CHARS = true
    configure()
    startTemplate("soutv", "output")
    myFixture.type('fil')
    assert myFixture.lookupElementStrings == ['file']
    myFixture.type('.')
    checkResult()
    assert !state.finished
  }

  void testAllowTypingRandomExpressionsWithLookupOpen() {
    configure()
    startTemplate("iter", "iterations")
    myFixture.type('file.')
    checkResult()
    assert !state.finished
  }

  private TemplateState getState() {
    editor?.with { TemplateManagerImpl.getTemplateState(it) }
  }

  void testIter1() throws Throwable {
    configure()
    startTemplate("iter", "iterations")
    myFixture.performEditorAction("NextTemplateVariable")
    checkResult()
  }

  void "_testIterForceBraces"() {
    CodeStyleSettingsManager.getSettings(getProject()).IF_BRACE_FORCE = CommonCodeStyleSettings.FORCE_BRACES_ALWAYS

    try {
      configure()
      startTemplate("iter", "iterations")
      stripTrailingSpaces()
      checkResult()
    }
    finally {
      CodeStyleSettingsManager.getSettings(getProject()).IF_BRACE_FORCE = CommonCodeStyleSettings.DO_NOT_FORCE
    }
  }

  private void stripTrailingSpaces() {
    DocumentImpl document = (DocumentImpl)getEditor().getDocument()
    document.setStripTrailingSpacesEnabled(true)
    document.stripTrailingSpaces(getProject())
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments()
  }

  void testIterParameterizedInner() {
    configure()
    startTemplate("iter", "iterations")
    stripTrailingSpaces()
    checkResult()
  }

  void testIterParameterizedInnerInMethod() {
    configure()
    startTemplate("iter", "iterations")
    stripTrailingSpaces()
    checkResult()
  }

  void testAsListToar() {
    configure()
    startTemplate("toar", "other")
    myFixture.type('\n\t')
    checkResult()
  }

  void testVarargToar() {
    configure()
    startTemplate("toar", "other")
    checkResult()
  }

  void testSoutp() {
    configure()
    startTemplate("soutp", "output")
    checkResult()
  }

  void testJavaStatementContext() {
    final TemplateImpl template = TemplateSettings.getInstance().getTemplate("inst", "other")
    assertFalse(isApplicable("class Foo {{ if (a inst<caret>) }}", template))
    assertTrue(isApplicable("class Foo {{ <caret>inst }}", template))
    assertTrue(isApplicable("class Foo {{ <caret>inst\n a=b; }}", template))
    assertFalse(isApplicable("class Foo {{ return (<caret>inst) }}", template))
    assertFalse(isApplicable("class Foo {{ return a <caret>inst) }}", template))
    assertFalse(isApplicable("class Foo {{ \"<caret>\" }}", template))
    assertTrue(isApplicable("class Foo {{ <caret>a.b(); ) }}", template))
    assertTrue(isApplicable("class Foo {{ <caret>a(); ) }}", template))
    assertTrue(isApplicable("class Foo {{ Runnable r = () -> { <caret>System.out.println(\"foo\"); }; ) }}", template))
    assertTrue(isApplicable("class Foo {{ Runnable r = () -> <caret>System.out.println(\"foo\"); ) }}", template))
  }

  void testJavaExpressionContext() {
    final TemplateImpl template = TemplateSettings.getInstance().getTemplate("toar", "other")
    assert !isApplicable("class Foo {{ if (a <caret>toar) }}", template)
    assert isApplicable("class Foo {{ <caret>toar }}", template)
    assert isApplicable("class Foo {{ return (<caret>toar) }}", template)
    assert !isApplicable("class Foo {{ return (aaa <caret>toar) }}", template)
    assert isApplicable("class Foo {{ Runnable r = () -> { <caret>System.out.println(\"foo\"); }; ) }}", template)
    assert isApplicable("class Foo {{ Runnable r = () -> <caret>System.out.println(\"foo\"); ) }}", template)
    assert !isApplicable("class Foo extends <caret>t {}", template)
  }

  void testJavaDeclarationContext() {
    final TemplateImpl template = TemplateSettings.getInstance().getTemplate("psvm", "other")
    assertFalse(isApplicable("class Foo {{ <caret>xxx }}", template))
    assertFalse(isApplicable("class Foo {{ <caret>xxx }}", template))
    assertFalse(isApplicable("class Foo {{ if (a <caret>xxx) }}", template))
    assertFalse(isApplicable("class Foo {{ return (<caret>xxx) }}", template))
    assertTrue(isApplicable("class Foo { <caret>xxx }", template))
    assertFalse(isApplicable("class Foo { int <caret>xxx }", template))
    assertTrue(isApplicable("class Foo {} <caret>xxx", template))

    assertTrue(isApplicable("class Foo { void foo(<caret>xxx) {} }", template))
    assertTrue(isApplicable("class Foo { void foo(<caret>xxx String bar ) {} }", template))
    assertTrue(isApplicable("class Foo { void foo(<caret>xxx String bar, int goo ) {} }", template))
    assertTrue(isApplicable("class Foo { void foo(String bar, <caret>xxx int goo ) {} }", template))
    assertTrue(isApplicable("class Foo { void foo(String bar, <caret>xxx goo ) {} }", template))
    assertTrue(isApplicable("class Foo { <caret>xxx void foo(String bar, xxx goo ) {} }", template))
    assertTrue(isApplicable("class Foo { void foo(<caret>String[] bar) {} }", template))
    assertTrue(isApplicable("class Foo { <caret>xxx String[] foo(String[] bar) {} }", template))

    assertTrue(isApplicable("class Foo { /**\nfoo **/ <caret>xxx String[] foo(String[] bar) {} }", template))

    assertTrue(isApplicable("<caret>xxx package foo; class Foo {}", template))
  }

  void testOtherContext() throws IOException {
    configureFromFileText("a.java", "class Foo { <caret>xxx }")
    assertInstanceOf(
      assertOneElement(TemplateManagerImpl.getApplicableContextTypes(myFixture.getFile(), getEditor().getCaretModel().getOffset())),
      JavaCodeContextType.Declaration.class)

    configureFromFileText("a.txt", "class Foo { <caret>xxx }")
    assertInstanceOf(
      assertOneElement(TemplateManagerImpl.getApplicableContextTypes(myFixture.getFile(), getEditor().getCaretModel().getOffset())),
      EverywhereContextType.class)
  }

  void testJavaOtherContext() throws IOException {
    def manager = (TemplateManagerImpl)TemplateManager.getInstance(project)
    def stmtContext = TemplateContextType.EP_NAME.findExtension(JavaCodeContextType.Statement)

    configureFromFileText("a.java", "class Foo {{ iter<caret>  }}")

    TemplateImpl template = TemplateSettings.instance.getTemplate("iter", "iterations")
    assert (template in manager.findMatchingTemplates(myFixture.file, editor, Lookup.REPLACE_SELECT_CHAR, TemplateSettings.instance)?.keySet())

    assert template.templateContext.getOwnValue(stmtContext)
    assert !template.templateContext.getOwnValue(stmtContext.baseContextType)
    template.templateContext.setEnabled(stmtContext, false)
    template.templateContext.setEnabled(stmtContext.baseContextType, true)
    try {
      assert !(template in manager.findMatchingTemplates(myFixture.file, editor, Lookup.REPLACE_SELECT_CHAR, TemplateSettings.instance)?.keySet())
    } finally {
      template.templateContext.setEnabled(stmtContext, true)
      template.templateContext.setEnabled(stmtContext.baseContextType, false)
    }
  }

  void testDontSaveDefaultContexts() {
    def defElement = JdomKt.loadElement('''\
<context>
  <option name="JAVA_STATEMENT" value="false"/>
  <option name="JAVA_CODE" value="true"/>
</context>''')
    def defContext = new TemplateContext()
    defContext.readTemplateContext(defElement)

    assert !defContext.isEnabled(TemplateContextType.EP_NAME.findExtension(JavaCodeContextType.Statement))
    assert defContext.isEnabled(TemplateContextType.EP_NAME.findExtension(JavaCodeContextType.Declaration))
    assert defContext.isEnabled(TemplateContextType.EP_NAME.findExtension(JavaCodeContextType.Generic))

    def copy = defContext.createCopy()

    def write = copy.writeTemplateContext(null)
    assert write.children.size() == 2 : JDOMUtil.writeElement(write)

    copy.setEnabled(TemplateContextType.EP_NAME.findExtension(JavaCommentContextType), false)

    write = copy.writeTemplateContext(null)
    assert write.children.size() == 3 : JDOMUtil.writeElement(write)
  }

  void "test use default context when empty"() {
    def context = new TemplateContext()
    context.readTemplateContext(new Element("context"))

    def defContext = new TemplateContext()
    def commentContext = TemplateContextType.EP_NAME.findExtension(JavaCommentContextType)
    defContext.setEnabled(commentContext, true)

    context.setDefaultContext(defContext)
    assert context.isEnabled(commentContext)
    assert !context.isEnabled(TemplateContextType.EP_NAME.findExtension(JavaCodeContextType.Generic))
  }

  void "test adding new context to Other"() {
    def defElement = JdomKt.loadElement('''\
<context>
  <option name="OTHER" value="true"/>
</context>''')
    def context = new TemplateContext()
    context.readTemplateContext(defElement)

    def javaContext = TemplateContextType.EP_NAME.findExtension(JavaCodeContextType.Generic)
    context.setEnabled(javaContext, true)

    def saved = context.writeTemplateContext(null)

    context = new TemplateContext()
    context.readTemplateContext(saved)

    assert context.isEnabled(javaContext)
    assert context.isEnabled(TemplateContextType.EP_NAME.findExtension(EverywhereContextType))
  }

  private boolean isApplicable(String text, TemplateImpl inst) throws IOException {
    configureFromFileText("a.java", text)
    return TemplateManagerImpl.isApplicable(myFixture.getFile(), getEditor().getCaretModel().getOffset(), inst)
  }

  private static writeCommand(Runnable runnable) {
    WriteCommandAction.runWriteCommandAction(null, runnable)
  }

  void testSearchByDescriptionWhenTemplatesListed() {
    myFixture.configureByText("a.java", "class A {{ <caret> }}")

    new ListTemplatesHandler().invoke(project, editor, myFixture.file)
    myFixture.type('array')
    assert 'itar' in myFixture.lookupElementStrings
  }

  void testListTemplatesSearchesPrefixInDescription() {
    myFixture.configureByText("a.java", "class A { main<caret> }")

    new ListTemplatesHandler().invoke(project, editor, myFixture.file)
    assert myFixture.lookupElementStrings == ['psvm']
  }

  void testListTemplatesAction() {
    myFixture.configureByText("a.java", "class A {{ <caret> }}")

    new ListTemplatesHandler().invoke(project, editor, myFixture.file)
    assert myFixture.lookupElementStrings.containsAll(['iter', 'itco', 'toar'])

    myFixture.type('it')
    assert myFixture.lookupElementStrings[0].startsWith('it')
    assert LookupManager.getInstance(project).activeLookup.currentItem == myFixture.getLookupElements()[0]

    myFixture.type('e')
    assert myFixture.lookupElementStrings[0].startsWith('ite')
    assert LookupManager.getInstance(project).activeLookup.currentItem == myFixture.getLookupElements()[0]
    LookupManager.getInstance(project).hideActiveLookup()

    myFixture.type('\b\b')
    new ListTemplatesHandler().invoke(project, editor, myFixture.file)
    assert myFixture.lookupElementStrings.containsAll(['iter', 'itco'])
    LookupManager.getInstance(project).hideActiveLookup()

    myFixture.type('xxxxx')
    new ListTemplatesHandler().invoke(project, editor, myFixture.file)
    assert myFixture.lookupElementStrings.containsAll(['iter', 'itco', 'toar'])
    LookupManager.getInstance(project).hideActiveLookup()
  }

  void testSelectionFromLookupBySpace() {
    myFixture.configureByText("a.java", "class A {{ itc<caret> }}")

    new ListTemplatesHandler().invoke(project, editor, myFixture.file)
    myFixture.type ' '
    myFixture.checkResult """\
import java.util.Iterator;

class A {{
    for (Iterator <selection>iterator</selection> = collection.iterator(); iterator.hasNext(); ) {
        Object next =  iterator.next();
        \n\
    }
}}"""
  }

  void testNavigationActionsDontTerminateTemplate() throws Throwable {
    configureFromFileText("a.txt", "")

    final TemplateManager manager = TemplateManager.getInstance(getProject())
    final Template template = manager.createTemplate("vn", "user", 'Hello $V1$ World $V1$\nHello $V2$ World $V2$\nHello $V3$ World $V3$')
    template.addVariable("V1", "", "", true)
    template.addVariable("V2", "", "", true)
    template.addVariable("V3", "", "", true)
    final Editor editor = getEditor()

    startTemplate(template)

    final TemplateState state = getState()

    for (int i = 0; i < 3; i++) {
      assertFalse(String.valueOf(i), state.isFinished())
      myFixture.type('H')
      final String docText = editor.getDocument().getText()
      assertTrue(docText, docText.startsWith("Hello H World H\n"))
      final int offset = editor.getCaretModel().getOffset()

      moveCaret(offset + 1)
      moveCaret(offset)

      myFixture.completeBasic()
      myFixture.type(' ')

      assertEquals(offset + 1, editor.getCaretModel().getOffset())
      assertFalse(state.isFinished())

      myFixture.type('\b')
      assertFalse(state.isFinished())
      writeCommand { state.nextTab() }
    }
    assertTrue(state.isFinished())
    checkResultByFile(getTestName(false) + "-out.txt")
  }

  private void moveCaret(final int offset) {
    runInEdtAndWait {
      getEditor().getCaretModel().moveToOffset(offset)
    }
  }

  void testUseDefaultValueForQuickResultCalculation() {
    myFixture.configureByText 'a.txt', '<caret>'

    final TemplateManager manager = TemplateManager.getInstance(getProject())
    final Template template = manager.createTemplate("vn", "user", '$V1$ var = $V2$;')
    template.addVariable("V1", "", "", true)
    template.addVariable("V2", "", '"239"', true)

    startTemplate(template)

    myFixture.checkResult '<caret> var = 239;'

    myFixture.type 'O'
    myFixture.checkResult 'O<caret> var = 239;'

    myFixture.type '\t'
    myFixture.checkResult 'O var = <selection>239</selection>;'
  }

  void testTemplateExpandingWithSelection() {
    final TemplateManager manager = TemplateManager.getInstance(getProject())
    final Template template = manager.createTemplate("tpl", "user", 'expanded')
    final JavaStringContextType contextType = contextType(JavaStringContextType.class)
    ((TemplateImpl)template).getTemplateContext().setEnabled(contextType, true)

    myFixture.configureByText("a.java", "class A { void f() { Stri<selection>ng s = \"tpl</selection><caret>\"; } }")

    CodeInsightTestUtil.addTemplate(template, myFixture.testRootDisposable)
    myFixture.type '\t'
    myFixture.checkResult 'class A { void f() { Stri   "; } }'
  }

  void "test expand current live template on no suggestions in lookup"() {
    myFixture.configureByText "a.java", "class Foo {{ <caret> }}"
    myFixture.completeBasic()
    assert myFixture.lookup
    myFixture.type("sout")
    assert myFixture.lookup
    assert myFixture.lookupElementStrings == []
    myFixture.type('\t')
    myFixture.checkResult "class Foo {{\n    System.out.println(<caret>);\n}}"
  }

  void "_test multi-dimensional toar"() {
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

  void "test inner class name"() {
    myFixture.configureByText "a.java", '''
class Outer {
    class Inner {
        void foo() {
            soutm<caret>
        }
    }
}'''
    myFixture.type('\t')
    assert myFixture.editor.document.text.contains("\"Inner.foo")
  }

  void "test do not strip type argument containing class"() {
    myFixture.configureByText 'a.java', '''
import java.util.*;
class Foo {
  List<Map.Entry<String, Integer>> foo() {
    <caret>
  }
}
'''

    final TemplateManager manager = TemplateManager.getInstance(getProject())
    final Template template = manager.createTemplate("result", "user", '$T$ result;')
    template.addVariable('T', new MacroCallNode(new MethodReturnTypeMacro()), new EmptyNode(), false)
    template.toReformat = true

    startTemplate(template)
    assert myFixture.editor.document.text.contains('List<Map.Entry<String, Integer>> result;')
  }

  void "test name shadowing"() {
    myFixture.configureByText "a.java", """class LiveTemplateVarSuggestion {
    private Object value;
    public void setValue(Object value, Object value1){
      inn<caret>
    }
}"""
    myFixture.type('\t')
    assert myFixture.lookupElementStrings == ['value', 'value1']
  }

  void "test invoke surround template by tab"() {
    myFixture.configureByText "a.java", "class A { public void B() { I<caret> } }"
    myFixture.type('\t')
    myFixture.checkResult("class A { public void B() {\n" +
                          "    for (Object o :) {\n" +
                          "        \n" +
                          "    }\n" +
                          "} }")
  }

  void "test escape string characters in soutv"() {
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

  void "test stop at SELECTION when invoked surround template by tab"() {
    myFixture.configureByText "a.txt", "<caret>"

    final TemplateManager manager = TemplateManager.getInstance(getProject())
    final Template template = manager.createTemplate("xxx", "user", 'foo $ARG$ bar $END$ goo $SELECTION$ after')
    template.addVariable("ARG", "", "", true)

    startTemplate(template)
    myFixture.type('arg')
    state.nextTab()
    assert !state
    checkResultByText 'foo arg bar  goo <caret> after'
  }

  void "test reuse static import"() {
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
    final TemplateManager manager = TemplateManager.getInstance(getProject())
    final Template template = manager.createTemplate("xxx", "user", 'foo.Bar.someMethod($END$)')
    template.setValue(USE_STATIC_IMPORT_IF_POSSIBLE, true)

    startTemplate(template)
    myFixture.checkResult """
import static foo.Bar.someMethod;

class Foo {
  {
    someMethod(<caret>)
  }
}
"""
  }

  void "test concat macro"() {
    final TemplateManager manager = TemplateManager.getInstance(getProject())
    final Template template = manager.createTemplate("result", "user", '$A$ $B$ c')
    template.addVariable('A', new EmptyNode(), true)
    
    def macroCallNode = new MacroCallNode(new ConcatMacro())
    macroCallNode.addParameter(new VariableNode('A', null))
    macroCallNode.addParameter(new TextExpression("ID"))
    template.addVariable('B', macroCallNode, false)

    myFixture.configureByText "a.txt", "<caret>"
    startTemplate(template)
    myFixture.type('tableName')
    state.nextTab()
    assert !state
    myFixture.checkResult('tableName tableNameID c')
  }

  void "test snakeCase should convert hyphens to underscores"() {
    final TemplateManager manager = TemplateManager.getInstance(getProject())
    final Template template = manager.createTemplate("result", "user", '$A$ $B$ c')
    template.addVariable('A', new EmptyNode(), true)

    def macroCallNode = new MacroCallNode(new SplitWordsMacro.SnakeCaseMacro())
    macroCallNode.addParameter(new VariableNode('A', null))
    template.addVariable('B', macroCallNode, false)

    myFixture.configureByText "a.txt", "<caret>"
    startTemplate(template)
    myFixture.type('-foo-bar_goo-')
    state.nextTab()
    assert !state
    myFixture.checkResult('-foo-bar_goo- _foo_bar_goo_ c<caret>')
  }

  void "test use single member static import first"() {
    myFixture.addClass("""package foo;
public class Bar {
  public static void someMethod() {}
  public static void someMethod(int a) {}
}""")
    myFixture.configureByText "a.java", """

class Foo {
  {
    <caret>
  }
}
"""
    final TemplateManager manager = TemplateManager.getInstance(getProject())
    final Template template = manager.createTemplate("xxx", "user", 'foo.Bar.someMethod($END$)')
    template.setValue(USE_STATIC_IMPORT_IF_POSSIBLE, true)

    startTemplate(template)
    myFixture.checkResult """import static foo.Bar.someMethod;

class Foo {
  {
    someMethod(<caret>)
  }
}
"""
  }

  void "test two static imports"() {
    myFixture.configureByText "a.java", """

class Foo {
  {
    <caret>
  }
}
"""
    final TemplateManager manager = TemplateManager.getInstance(getProject())
    final Template template = manager.createTemplate("xxx", "user", 'java.lang.Math.abs(java.lang.Math.PI);')
    template.setValue(USE_STATIC_IMPORT_IF_POSSIBLE, true)

    startTemplate(template)
    myFixture.checkResult """\
import static java.lang.Math.PI;
import static java.lang.Math.abs;

class Foo {
  {
    abs(PI);<caret>
  }
}
"""
  }

  void "test do not replace macro value with null result"() {
    myFixture.configureByText "a.java", """\
class Foo {
  {
    <caret>
  }
}
"""
    final TemplateManager manager = TemplateManager.getInstance(getProject())
    final Template template = manager.createTemplate("xxx", "user", '$VAR1$ $VAR2$ $VAR1$')
    template.addVariable("VAR1", "", "", true)
    template.addVariable("VAR2", new MacroCallNode(new FileNameMacro()), new ConstantNode("default"), true)
    ((TemplateImpl)template).templateContext.setEnabled(contextType(JavaCodeContextType.class), true)
    CodeInsightTestUtil.addTemplate(template, myFixture.testRootDisposable)

    startTemplate(template)
    myFixture.checkResult """\
class Foo {
  {
    <caret> a.java """ + """
  }
}
"""
    myFixture.type 'test'

    myFixture.checkResult """\
class Foo {
  {
    test<caret> a.java test
  }
}
"""
  }

  void "test do replace macro value with empty result"() {
    myFixture.configureByText "a.java", """\
class Foo {
  {
    <caret>
  }
}
"""
    final TemplateManager manager = TemplateManager.getInstance(getProject())
    final Template template = manager.createTemplate("xxx", "user", '$VAR1$ $VAR2$')
    template.addVariable("VAR1", "", "", true)
    template.addVariable("VAR2", new MacroCallNode(new MyMirrorMacro("VAR1")), null, true)
    ((TemplateImpl)template).templateContext.setEnabled(contextType(JavaCodeContextType.class), true)
    CodeInsightTestUtil.addTemplate(template, myFixture.testRootDisposable)

    startTemplate(template)
    myFixture.checkResult """\
class Foo {
  {
    <caret> """ + """
  }
}
"""
    myFixture.type '42'
    myFixture.checkResult """\
class Foo {
  {
    42<caret> 42
  }
}
"""

    myFixture.type '\b\b'
    myFixture.checkResult """\
class Foo {
  {
    <caret> """ + """
  }
}
"""
  }

  private static class MyMirrorMacro extends Macro {
    private final String myVariableName

    MyMirrorMacro(String variableName) {
      this.myVariableName = variableName
    }

    @Override
    String getName() {
      return "mirror"
    }

    @Override
    String getPresentableName() {
      return getName()
    }

    @Override
    Result calculateResult(@NotNull Expression[] params, ExpressionContext context) {
      def state = TemplateManagerImpl.getTemplateState(context.editor)
      return state != null ? state.getVariableValue(myVariableName) : null
    }

    @Override
    Result calculateQuickResult(@NotNull Expression[] params, ExpressionContext context) {
      return calculateResult(params, context)
    }
  }

  void "test escape shouldn't move caret to the end marker"() {
    myFixture.configureByText 'a.java', """
class Foo {{
  itar<caret>
}}
"""
    myFixture.type '\ta'
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_ESCAPE)
    myFixture.checkResult """
class Foo {{
    for (int a<caret> = 0; a < array.length; a++) {
         = array[a];
        
    }
}}
"""
  }

  void "test add new line on enter outside editing variable"() {
    myFixture.configureByText 'a.java', """
class Foo {{
  <caret>
}}
"""
    myFixture.type 'soutv\tabc'
    myFixture.editor.caretModel.moveCaretRelatively(3, 0, false, false, false)
    myFixture.type '\n'
    myFixture.checkResult """
class Foo {{
    System.out.println("abc = " + abc);
    <caret>
}}
"""
  }

  void "test type tab character on tab outside editing variable"() {
    myFixture.configureByText 'a.java', """
class Foo {{
  <caret>
}}
"""
    myFixture.type 'soutv\tabc'
    myFixture.editor.caretModel.moveCaretRelatively(2, 0, false, false, false)
    myFixture.type '\t'
    myFixture.checkResult """
class Foo {{
    System.out.println("abc = " + abc); <caret>
}}
"""
  }

  void "test delete at the last template position"() {
    myFixture.configureByText 'a.java', """
class Foo {{
  <caret>
}}
"""
    myFixture.type 'iter\t'
    LightPlatformCodeInsightTestCase.delete(myFixture.editor, myFixture.project)
    myFixture.checkResult """
class Foo {{
    for (Object o : <caret> {
        
    }
}}
"""
  }

  void "test delete at the last template position with selection"() {
    myFixture.configureByText 'a.java', """
class Foo {{
  <caret>
}}
"""
    myFixture.type 'iter\t'
    def startOffset = myFixture.editor.caretModel.offset
    myFixture.type "foo"
    myFixture.editor.getSelectionModel().setSelection(startOffset, myFixture.editor.caretModel.offset)
    LightPlatformCodeInsightTestCase.delete(myFixture.editor, myFixture.project)
    assertNotNull(state)
  }



  void "test multicaret expanding with space"() {
    myFixture.configureByText "a.java", """\
class Foo {
  {
    <caret>
    <caret>
    <caret>
  }
}
"""
    def defaultShortcutChar = TemplateSettings.instance.defaultShortcutChar
    try {
      TemplateSettings.instance.defaultShortcutChar = TemplateSettings.SPACE_CHAR
      startTemplate("sout", TemplateSettings.SPACE_CHAR)
    }
    finally {
      TemplateSettings.instance.defaultShortcutChar = defaultShortcutChar
    }
    myFixture.checkResult("""\
class Foo {
  {
      System.out.println();
      System.out.println();
      System.out.println();
  }
}
""")
  }

  void "test multicaret expanding with enter"() {
    myFixture.configureByText "a.java", """\
class Foo {
  {
    <caret>
    <caret>
    <caret>
  }
}
"""
    def defaultShortcutChar = TemplateSettings.instance.defaultShortcutChar
    try {
      TemplateSettings.instance.defaultShortcutChar = TemplateSettings.ENTER_CHAR
      startTemplate("sout", TemplateSettings.ENTER_CHAR)
    }
    finally {
      TemplateSettings.instance.defaultShortcutChar = defaultShortcutChar
    }
    myFixture.checkResult("""\
class Foo {
  {
      System.out.println();
      System.out.println();
      System.out.println();
  }
}
""")
  }

  void "test multicaret expanding with tab"() {
    myFixture.configureByText "a.java", """\
class Foo {
  {
    <caret>
    <caret>
    <caret>
  }
}
"""
    def defaultShortcutChar = TemplateSettings.instance.defaultShortcutChar
    try {
      TemplateSettings.instance.defaultShortcutChar = TemplateSettings.TAB_CHAR
      startTemplate("sout", TemplateSettings.TAB_CHAR)
    }
    finally {
      TemplateSettings.instance.defaultShortcutChar = defaultShortcutChar
    }

    myFixture.checkResult("""\
class Foo {
  {
      System.out.println();
      System.out.println();
      System.out.println();
  }
}
""")
  }

  void "test sout template in expression lambda"() {
    myFixture.configureByText 'a.java', '''class Foo {{
  strings.stream().forEach(o -> sout<caret>);
}}
'''
    myFixture.type('\t')
    myFixture.checkResult '''class Foo {{
  strings.stream().forEach(o -> System.out.println(<caret>));
}}
'''
  }

  void "test finish template on moving caret by completion insert handler"() {
    TemplateManagerImpl templateManager = TemplateManager.getInstance(project) as TemplateManagerImpl
    myFixture.configureByText('a.html', '<selection><p></p></selection>')
    def template = TemplateSettings.instance.getTemplate("T2", "html/xml")
    myFixture.testAction(new InvokeTemplateAction(template, myFixture.editor, myFixture.project, ContainerUtil.newHashSet()))
    myFixture.complete(CompletionType.BASIC)
    myFixture.type("nofra")
    myFixture.finishLookup(Lookup.REPLACE_SELECT_CHAR)
    myFixture.checkResult("<noframes><caret><p></p></noframes>")
    assertNull(templateManager.getActiveTemplate(myFixture.editor))
  }

  void "test escape with selection"() {
    myFixture.configureByText "a.java", """
class Foo {
  {
      soutv<caret>
  }
}
"""
    myFixture.type('\tfoo')
    myFixture.editor.selectionModel.setSelection(myFixture.caretOffset - 3, myFixture.caretOffset)
    assert myFixture.editor.selectionModel.hasSelection()

    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_ESCAPE)
    assert !myFixture.editor.selectionModel.hasSelection()
    assert TemplateManager.getInstance(project).getActiveTemplate(myFixture.editor)

    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_ESCAPE)
    assert !TemplateManager.getInstance(project).getActiveTemplate(myFixture.editor)

    myFixture.checkResult """
class Foo {
  {
      System.out.println("foo = " + foo<caret>);
  }
}
"""
  }

  void "test escape with lookup"() {
    myFixture.configureByText "a.java", """
class Foo {
  {
      int foo_1, foo_2;
      soutv<caret>
  }
}
"""
    myFixture.type('\t')
    assert myFixture.lookup

    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_ESCAPE)
    assert !myFixture.lookup
    assert TemplateManager.getInstance(project).getActiveTemplate(myFixture.editor)

    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_ESCAPE)
    assert !TemplateManager.getInstance(project).getActiveTemplate(myFixture.editor)

    myFixture.checkResult """
class Foo {
  {
      int foo_1, foo_2;
      System.out.println("foo_1 = " + foo_1);
  }
}
"""
  }

  void "test escape with lookup and selection"() {
    myFixture.configureByText "a.java", """
class Foo {
  {
      int foo_1, foo_2;
      soutv<caret>
  }
}
"""
    myFixture.type('\tfoo')
    myFixture.editor.selectionModel.setSelection(myFixture.caretOffset - 3, myFixture.caretOffset)
    myFixture.completeBasic()
    assert myFixture.editor.selectionModel.hasSelection()
    assert myFixture.lookup

    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_ESCAPE)
    assert !myFixture.editor.selectionModel.hasSelection()
    assert !myFixture.lookup
    assert TemplateManager.getInstance(project).getActiveTemplate(myFixture.editor)

    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_ESCAPE)
    assert !TemplateManager.getInstance(project).getActiveTemplate(myFixture.editor)

    myFixture.checkResult """
class Foo {
  {
      int foo_1, foo_2;
      System.out.println("foo = " + foo<caret>);
  }
}
"""
  }

  void "test escape with empty lookup"() {
    myFixture.configureByText "a.java", """
class Foo {
  {
      int foo_1, foo_2;
      soutv<caret>
  }
}
"""
    myFixture.type('\tfoobar')
    assert myFixture.lookup
    assert !myFixture.lookup.currentItem

    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_ESCAPE)
    assert !myFixture.lookup
    assert !TemplateManager.getInstance(project).getActiveTemplate(myFixture.editor)

    myFixture.checkResult """
class Foo {
  {
      int foo_1, foo_2;
      System.out.println("foobar = " + foobar);
  }
}
"""
  }

  void "test iterate over list with wildcard component type"() {
    myFixture.configureByText 'a.java', '''class C {{
java.util.List<? extends Integer> list;
<caret>
}}'''
    myFixture.type('itli\t')
    myFixture.checkResult '''class C {{
java.util.List<? extends Integer> list;
    for (int i = 0; i < list.size(); i++) {
        Integer integer =  list.get(i);
        
    }
}}'''
  }

  void "test home end go outside template fragments if already on their bounds"() {
    myFixture.configureByText 'a.txt', ' <caret> g'

    TemplateManager manager = TemplateManager.getInstance(getProject())
    Template template = manager.createTemplate("empty", "user", '$VAR$')
    template.addVariable("VAR", "", '"foo"', true)
    manager.startTemplate(myFixture.editor, template)

    myFixture.checkResult ' <selection>foo<caret></selection> g'

    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_LINE_START)
    myFixture.checkResult ' <caret>foo g'

    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_LINE_START)
    myFixture.checkResult '<caret> foo g'

    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT)
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_LINE_END)
    myFixture.checkResult ' foo<caret> g'

    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_LINE_END)
    myFixture.checkResult ' foo g<caret>'
  }
}