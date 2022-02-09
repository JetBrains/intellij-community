// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.template

import com.intellij.JavaTestUtil
import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.daemon.impl.quickfix.EmptyExpression
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.template.*
import com.intellij.codeInsight.template.actions.SaveAsTemplateAction
import com.intellij.codeInsight.template.impl.*
import com.intellij.codeInsight.template.macro.*
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.LightProjectDescriptor
import groovy.transform.CompileStatic

import static com.intellij.codeInsight.template.Template.Property.USE_STATIC_IMPORT_IF_POSSIBLE

/**
 * @author peter
 */
@CompileStatic
class JavaLiveTemplateTest extends LiveTemplateTestCase {

  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_16
  }

  final String basePath = JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/template/"
  
  void "test not to go to next tab after insert if element is a psi package"() {
    myFixture.configureByText 'a.java', '''
<caret>
'''
    Template template = templateManager.createTemplate("imp", "user", 'import $MODIFIER$ java.$NAME$;')
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
    Template template = templateManager.createTemplate("for", "user", 'for ($ELEMENT_TYPE$ $VAR$ : $ITERABLE_TYPE$) {\n' +
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
    Template template = templateManager.createTemplate("for", "user", 'for ($ELEMENT_TYPE$ $VAR$ : $ITERABLE_TYPE$) {\n' +
                                                                    '$END$;\n' +
                                                                    '}')
    template.addVariable('ITERABLE_TYPE', new MacroCallNode(new CompleteSmartMacro()), new EmptyNode(), true)
    template.addVariable('VAR', new TextExpression("item"), true)
    template.addVariable('ELEMENT_TYPE', new TextExpression("String"), true)
    template.setToReformat(true)
    startTemplate(template)
    myFixture.type('in\n')
    BaseCompleteMacro.waitForNextTab()
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

    Template template = templateManager.createTemplate("myCbDo", "user", 'MyUtils.doSomethingWithCallback($CB$)')

    MacroCallNode call = new MacroCallNode(new VariableOfTypeMacro())
    call.addParameter(new ConstantNode("MyCallback"))
    template.addVariable('CB', call, new EmptyNode(), false)
    startTemplate(template)

    myFixture.checkResult '''
class Outer {
  static class Inner implements MyCallback {
    void aMethod() {
      MyUtils.doSomethingWithCallback(this)
    }
  }
}
'''
  }

  void testToar() throws Throwable {
    configure()
    startTemplate("toar", "Java")
    state.gotoEnd(false)
    checkResult()
  }

  void testElseIf() throws Throwable {
    configure()
    startTemplate("else-if", "Java")
    WriteCommandAction.runWriteCommandAction(project) { state.gotoEnd(false) }
    checkResult()
  }

  void testElseIf2() throws Throwable {
    configure()
    startTemplate("else-if", "Java")
    WriteCommandAction.runWriteCommandAction(project) { state.gotoEnd(false) }
    checkResult()
  }

  void testElseIf3() throws Throwable {
    configure()
    startTemplate("else-if", "Java")
    WriteCommandAction.runWriteCommandAction(project) { state.gotoEnd(false) }
    checkResult()
  }

  void testIter() throws Throwable {
    configure()
    startTemplate("iter", "Java")
    WriteCommandAction.runWriteCommandAction(project) { state.nextTab() }
    myFixture.finishLookup(Lookup.AUTO_INSERT_SELECT_CHAR)
    checkResult()
  }

  void testIter1() throws Throwable {
    configure()
    startTemplate("iter", "Java")
    myFixture.performEditorAction("NextTemplateVariable")
    checkResult()
  }

  void testIterParameterizedInner() {
    configure()
    startTemplate("iter", "Java")
    stripTrailingSpaces()
    checkResult()
  }

  void testIterParameterizedInnerInMethod() {
    configure()
    startTemplate("iter", "Java")
    stripTrailingSpaces()
    checkResult()
  }

  private void stripTrailingSpaces() {
    DocumentImpl document = (DocumentImpl)getEditor().getDocument()
    document.setStripTrailingSpacesEnabled(true)
   document.stripTrailingSpaces(getProject())
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments()
  }

  void testAsListToar() {
    configure()
    startTemplate("toar", "Java")
    myFixture.type('\n\t')
    checkResult()
  }

  void testVarargToar() {
    configure()
    startTemplate("toar", "Java")
    checkResult()
  }

  void testSoutp() {
    configure()
    startTemplate("soutp", "Java")
    checkResult()
  }
  
  void testSoutConsumerApplicability() {
    for (String name : ["soutc", "serrc"]) {
      TemplateImpl template = (TemplateImpl)TemplateSettings.getInstance().getTemplate(name, "Java")
      assert !isApplicable('class Foo {void x(){ <caret>JUNK }}', template)
      assert !isApplicable('class Foo {void x(java.util.stream.IntStream is){ is.map(<caret>JUNK) }}', template)
      assert isApplicable('class Foo {void x(java.util.stream.IntStream is){ is.peek(<caret>JUNK) }}', template)
    }
  }

  void testSoutConsumer() {
    configure()
    startTemplate("soutc", "Java")
    checkResult()
  }

  void testSerrConsumerConflict() {
    configure()
    startTemplate("serrc", "Java")
    checkResult()
  }

  private boolean isApplicable(String text, TemplateImpl inst) throws IOException {
    myFixture.configureByText("a.java", text)
    return TemplateManagerImpl.isApplicable(myFixture.getFile(), getEditor().getCaretModel().getOffset(), inst)
  }

  void 'test generic type argument is declaration context'() {
    myFixture.configureByText "a.java", "class Foo {{ List<Pair<X, <caret>Y>> l; }}"
    assert TemplateManagerImpl.getApplicableContextTypes(TemplateActionContext.expanding(myFixture.file, myFixture.editor)).
      collect { it.class } == [JavaCodeContextType.Declaration]
  }

  void testJavaStatementContext() {
    final TemplateImpl template = TemplateSettings.getInstance().getTemplate("inst", "Java")
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
    final TemplateImpl template = TemplateSettings.getInstance().getTemplate("toar", "Java")
    assert !isApplicable("class Foo {{ if (a <caret>toar) }}", template)
    assert isApplicable("class Foo {{ <caret>toar }}", template)
    assert isApplicable("class Foo {{ return (<caret>toar) }}", template)
    assert !isApplicable("class Foo {{ return (aaa <caret>toar) }}", template)
    assert isApplicable("class Foo {{ Runnable r = () -> { <caret>System.out.println(\"foo\"); }; ) }}", template)
    assert isApplicable("class Foo {{ Runnable r = () -> <caret>System.out.println(\"foo\"); ) }}", template)
    assert !isApplicable("class Foo extends <caret>t {}", template)
  }

  void testJavaStringContext() {
    TemplateImpl template = (TemplateImpl)templateManager.createTemplate("a", "b")
    template.templateContext.setEnabled(TemplateContextType.EP_NAME.findExtension(JavaStringContextType), true)
    assert !isApplicable('class Foo {{ <caret> }}', template)
    assert !isApplicable('class Foo {{ <caret>1 }}', template)
    assert isApplicable('class Foo {{ "<caret>" }}', template)
    assert isApplicable('class Foo {{ """<caret>""" }}', template)
  }

  void testJavaDeclarationContext() {
    final TemplateImpl template = TemplateSettings.getInstance().getTemplate("psvm", "Java")
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

    Template template = templateManager.createTemplate("result", "user", '$T$ result;')
    template.addVariable('T', new MacroCallNode(new MethodReturnTypeMacro()), new EmptyNode(), false)
    template.toReformat = true

    startTemplate(template)
    assert myFixture.editor.document.text.contains('List<Map.Entry<String, Integer>> result;')
  }

  void "test method name in annotation"() {
    myFixture.configureByText 'a.java', '''
class Foo {
  <caret>
  void foo() {}
}
'''

    Template template = templateManager.createTemplate("result", "user", '@SuppressWarnings("$T$")')
    template.addVariable('T', new MacroCallNode(new MethodNameMacro()), new EmptyNode(), false)
    template.toReformat = true

    startTemplate(template)
    assert myFixture.editor.document.text.contains('@SuppressWarnings("foo")')
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
    Template template = templateManager.createTemplate("xxx", "user", 'foo.Bar.someMethod($END$)')
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
    Template template = templateManager.createTemplate("xxx", "user", 'foo.Bar.someMethod($END$)')
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
    Template template = templateManager.createTemplate("xxx", "user", 'java.lang.Math.abs(java.lang.Math.PI);')
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

  void "test ritar template in expression lambda"() {
    myFixture.configureByText 'a.java', '''class Foo {
  void test(int[] arr) {
    Runnable r = () -> itar<caret>
  }
}
'''
    myFixture.type('\t')
    myFixture.checkResult '''class Foo {
  void test(int[] arr) {
    Runnable r = () -> {
        for (int i = 0; i < arr.length; i++) {
            int i1 = arr[i];
            
        }
    }
  }
}
'''
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

  private void configure() {
    myFixture.configureByFile(getTestName(false) + ".java")
  }

  private void checkResult() {
    myFixture.checkResultByFile(getTestName(false) + "-out.java")
  }

  void "test suggest foreach parameter name based on the called method name"() {
    myFixture.configureByText 'a.java', '''class A { Iterable<String> getCreatedTags() { }
{
  iter<caret>
}}'''
    myFixture.type('\tgetCreatedTags()\n')
    myFixture.checkResult '''class A { Iterable<String> getCreatedTags() { }
{
    for (String createdTag : getCreatedTags()) {
        
    }
}}'''
  }

  void "test overtyping suggestion with a quote"() {
    CodeInsightSettings.instance.selectAutopopupSuggestionsByChars = true

    myFixture.configureByText 'a.java', '''
class A {
  {
    String s;
    <caret>s.toString();
  }
}'''
    myFixture.doHighlighting()
    myFixture.launchAction(myFixture.findSingleIntention('Initialize variable'))
    myFixture.type('"')
    myFixture.checkResult '''
class A {
  {
    String s = "null";
    s.toString();
  }
}'''
    assert !myFixture.lookup
  }

  void "test save as live template for annotation values"() {
    myFixture.addClass("package foo; public @interface Anno { String value(); }")
    myFixture.configureByText "a.java", 'import foo.*; <selection>@Anno("")</selection> class T {}'
    assert SaveAsTemplateAction.suggestTemplateText(myFixture.editor, myFixture.file) == '@foo.Anno("")'

    myFixture.configureByText "b.java", 'import foo.*; <selection>@Anno(value="")</selection> class T {}'
    assert SaveAsTemplateAction.suggestTemplateText(myFixture.editor, myFixture.file) == '@foo.Anno(value="")'
  }

  void "test reformat with virtual space"() {
    myFixture.configureByText 'a.java', '''class C {
    public static void main(String ...args) {
        <caret>
    }
}'''
    getEditor().getSettings().setVirtualSpace(true)
    myFixture.type('iter\t')
    myFixture.checkResult '''class C {
    public static void main(String ...args) {
        for (String arg : args) {
            
        }
    }
}'''
  }

  void "test subtypes macro works with text argument"() {
    myFixture.configureByText "a.java", """

class Foo {
  {
    <caret>
  }
}

class Bar1 extends Foo {}
class Bar2 extends Foo {}
"""
    Template template = templateManager.createTemplate("xxx", "user", '$T$ var = new $ST$();')
    template.addVariable('T', new EmptyNode(), true)
    template.addVariable('ST', 'subtypes(T)', '', true)

    startTemplate(template)

    myFixture.type('Foo')
    state.nextTab()

    assert myFixture.editor.document.text.contains('Foo var = new Foo();')
    assertSameElements(myFixture.lookupElementStrings, 'Foo', 'Bar1', 'Bar2')
  }

  void "test methodParameterTypes"() {
    myFixture.configureByText "a.java", """
class X {
  void test(int a, String b, double[] c) {
    <caret>
  }
}
"""
    Template template = templateManager.createTemplate("xxx", "user", 'System.out.println("$TYPES$");')
    template.addVariable('TYPES', 'methodParameterTypes()', '', true)

    startTemplate(template)

    myFixture.checkResult("""
class X {
  void test(int a, String b, double[] c) {
    System.out.println("[int, java.lang.String, double[]]");
  }
}
""")
  }

  void "test at equals token"() {
    myFixture.configureByText "a.java", """
class X {
  void test() {
    int i <selection>=</selection> 5;
  }
}
"""
    TemplateActionContext templateActionContext = TemplateActionContext.surrounding(file, editor);
    List<TemplateImpl> templates = TemplateManagerImpl.listApplicableTemplates(templateActionContext);
    assert templates == []
  }

  void "test whole line selected"() {
    myFixture.configureByText "a.java", """
class X {
  int test() {
<selection>    return 5;
</selection>  }
}
"""
    TemplateActionContext templateActionContext = TemplateActionContext.surrounding(file, editor);
    List<TemplateImpl> templates = TemplateManagerImpl.listApplicableTemplates(templateActionContext);
    assert templates.join(", ") == "Java/C, Java/RL, Java/WL, Java/I"
  }

  void "test generic arguments are inserted"() {
    myFixture.configureByText 'a.java', '''
import java.util.*;
public class Main {
  List<String> getList(ArrayList<String> list) {
    <caret>
  }
}
'''
    Template template = templateManager.createTemplate("rlazy", "user", 'return $VAR$ == null ? $VAR$ = new $TYPE$($END$) : $VAR$;')
    template.addVariable('VAR', 'methodParameterTypes()', '', true)
    template.addVariable('TYPE', 'subtypes(typeOfVariable(VAR))', '', true)
    template.setToReformat(true)
    startTemplate(template)
    myFixture.type('list\n\n')
    myFixture.checkResult """
import java.util.*;
public class Main {
  List<String> getList(ArrayList<String> list) {
      return list == null ? list = new ArrayList<String>() : list;
  }
}
"""
  }
}
