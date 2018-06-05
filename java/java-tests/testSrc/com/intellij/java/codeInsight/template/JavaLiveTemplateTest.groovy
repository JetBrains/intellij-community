// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.template

import com.intellij.JavaTestUtil
import com.intellij.codeInsight.daemon.impl.quickfix.EmptyExpression
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.template.Template
import com.intellij.codeInsight.template.TemplateManager
import com.intellij.codeInsight.template.impl.ConstantNode
import com.intellij.codeInsight.template.impl.EmptyNode
import com.intellij.codeInsight.template.impl.MacroCallNode
import com.intellij.codeInsight.template.impl.TemplateImpl
import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.codeInsight.template.impl.TemplateSettings
import com.intellij.codeInsight.template.impl.TextExpression
import com.intellij.codeInsight.template.macro.ClassNameCompleteMacro
import com.intellij.codeInsight.template.macro.CompleteMacro
import com.intellij.codeInsight.template.macro.CompleteSmartMacro
import com.intellij.codeInsight.template.macro.MethodReturnTypeMacro
import com.intellij.codeInsight.template.macro.VariableOfTypeMacro
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.psi.PsiDocumentManager

import static com.intellij.codeInsight.template.Template.Property.USE_STATIC_IMPORT_IF_POSSIBLE

/**
 * @author peter
 */
class JavaLiveTemplateTest extends LiveTemplateTestCase {
  final String basePath = JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/template/"
  
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
    final Template template = manager.createTemplate("for", "user", 'for ($ELEMENT_TYPE$ $VAR$: $ITERABLE_TYPE$) {\n' +
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
    startTemplate("toar", "other")
    state.gotoEnd(false)
    checkResult()
  }

  void testIter() throws Throwable {
    configure()
    startTemplate("iter", "iterations")
    WriteCommandAction.runWriteCommandAction(project) { state.nextTab() }
    myFixture.finishLookup(Lookup.AUTO_INSERT_SELECT_CHAR)
    checkResult()
  }

  void testIter1() throws Throwable {
    configure()
    startTemplate("iter", "iterations")
    myFixture.performEditorAction("NextTemplateVariable")
    checkResult()
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

  private void stripTrailingSpaces() {
    DocumentImpl document = (DocumentImpl)getEditor().getDocument()
    document.setStripTrailingSpacesEnabled(true)
   document.stripTrailingSpaces(getProject())
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments()
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

  private boolean isApplicable(String text, TemplateImpl inst) throws IOException {
    myFixture.configureByText("a.java", text)
    return TemplateManagerImpl.isApplicable(myFixture.getFile(), getEditor().getCaretModel().getOffset(), inst)
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
}
