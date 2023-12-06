package com.intellij.java.codeInsight.template;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.daemon.impl.quickfix.EmptyExpression;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.template.*;
import com.intellij.codeInsight.template.actions.SaveAsTemplateAction;
import com.intellij.codeInsight.template.impl.*;
import com.intellij.codeInsight.template.macro.*;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.util.containers.ContainerUtil;
import junit.framework.TestCase;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@SuppressWarnings("removal")
public class JavaLiveTemplateTest extends LiveTemplateTestCase {
  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_LATEST_WITH_LATEST_JDK;
  }

  public void test_not_to_go_to_next_tab_after_insert_if_element_is_a_psi_package() {
    myFixture.configureByText("a.java", "\n<caret>\n");
    Template template = getTemplateManager().createTemplate("imp", "user", "import $MODIFIER$ java.$NAME$;");
    template.addVariable("NAME", new MacroCallNode(new CompleteMacro(true)), new EmptyNode(), true);
    template.addVariable("MODIFIER", new EmptyExpression(), true);
    startTemplate(template);
    myFixture.type("uti\n");
    myFixture.checkResult("\nimport  java.util.<caret>;\n");
    assert !getState().isFinished();
  }

  public void test_not_to_go_to_next_tab_after_insert_if_element_has_call_arguments() {
    myFixture.configureByText("a.java",
                              """

                                import  java.util.*;
                                public class Main {
                                    List<String> getStringList(int i){
                                        List<String> ints = null;
                                        <caret>
                                        return new ArrayList<>(i);
                                    }
                                }
                                """);
    Template template =
      getTemplateManager().createTemplate("for", "user", """
        for ($ELEMENT_TYPE$ $VAR$ : $ITERABLE_TYPE$) {
        $END$;
        }""");
    template.addVariable("ITERABLE_TYPE", new MacroCallNode(new CompleteSmartMacro()), new EmptyNode(), true);
    template.addVariable("VAR", new TextExpression("item"), true);
    template.addVariable("ELEMENT_TYPE", new TextExpression("String"), true);
    template.setToReformat(true);
    startTemplate(template);
    myFixture.type("get\n");
    myFixture.checkResult(
      "\nimport  java.util.*;\npublic class Main {\n    List<String> getStringList(int i){\n        List<String> ints = null;\n        for (String item : getStringList(<caret>)) {\n            ;\n        }\n        return new ArrayList<>(i);\n    }\n}\n");
    assert !getState().isFinished();
  }

  public void test_go_to_next_tab_after_insert_if_element_does_not_have_call_arguments() {
    myFixture.configureByText("a.java",
                              """

                                import  java.util.*;
                                public class Main {
                                    List<String> getStringList(int i){
                                        List<String> ints = null;
                                        <caret>
                                        return new ArrayList<>(i);
                                    }
                                }
                                """);
    Template template =
      getTemplateManager().createTemplate("for", "user", """
        for ($ELEMENT_TYPE$ $VAR$ : $ITERABLE_TYPE$) {
        $END$;
        }""");
    template.addVariable("ITERABLE_TYPE", new MacroCallNode(new CompleteSmartMacro()), new EmptyNode(), true);
    template.addVariable("VAR", new TextExpression("item"), true);
    template.addVariable("ELEMENT_TYPE", new TextExpression("String"), true);
    template.setToReformat(true);
    startTemplate(template);
    myFixture.type("in\n");
    BaseCompleteMacro.waitForNextTab();
    myFixture.checkResult(
      """

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
        """);
    assert !getState().isFinished();
  }

  public void test_variableOfType_suggests_inner_static_classes() {
    myFixture.addClass("public interface MyCallback {}");
    myFixture.addClass("""
                         final class MyUtils {
                           public static void doSomethingWithCallback(MyCallback cb) { }
                         }
                         """);
    myFixture.configureByText("a.java",
                              "\nclass Outer {\n  static class Inner implements MyCallback {\n    void aMethod() {\n      <caret>\n    }\n  }\n}\n");

    Template template = getTemplateManager().createTemplate("myCbDo", "user", "MyUtils.doSomethingWithCallback($CB$)");

    MacroCallNode call = new MacroCallNode(new VariableOfTypeMacro());
    call.addParameter(new ConstantNode("MyCallback"));
    template.addVariable("CB", call, new EmptyNode(), false);
    startTemplate(template);

    myFixture.checkResult(
      """

        class Outer {
          static class Inner implements MyCallback {
            void aMethod() {
              MyUtils.doSomethingWithCallback(this)
            }
          }
        }
        """);
  }

  public void testToar() {
    configure();
    startTemplate("toar", "Java");
    getState().gotoEnd(false);
    checkResult();
  }

  public void testElseIf() {
    configure();
    startTemplate("else-if", "Java");
    WriteCommandAction.runWriteCommandAction(getProject(), () -> getState().gotoEnd(false));
    checkResult();
  }

  public void testElseIf2() {
    configure();
    startTemplate("else-if", "Java");
    WriteCommandAction.runWriteCommandAction(getProject(), () -> getState().gotoEnd(false));
    checkResult();
  }

  public void testElseIf3() {
    configure();
    startTemplate("else-if", "Java");
    WriteCommandAction.runWriteCommandAction(getProject(), () -> getState().gotoEnd(false));
    checkResult();
  }

  public void testIter() {
    configure();
    startTemplate("iter", "Java");
    WriteCommandAction.runWriteCommandAction(getProject(), () -> getState().nextTab());
    myFixture.finishLookup(Lookup.AUTO_INSERT_SELECT_CHAR);
    checkResult();
  }

  public void testIter1() {
    configure();
    startTemplate("iter", "Java");
    myFixture.performEditorAction("NextTemplateVariable");
    checkResult();
  }

  public void testIterParameterizedInner() {
    configure();
    startTemplate("iter", "Java");
    stripTrailingSpaces();
    checkResult();
  }

  public void testIterParameterizedInnerInMethod() {
    configure();
    startTemplate("iter", "Java");
    stripTrailingSpaces();
    checkResult();
  }

  public void testThrInSwitch() {
    configure();
    startTemplate("thr", "Java");
    stripTrailingSpaces();
    checkResult();
  }

  private void stripTrailingSpaces() {
    DocumentImpl document = (DocumentImpl)getEditor().getDocument();
    PsiDocumentManager manager = PsiDocumentManager.getInstance(getProject());
    manager.commitDocument(document);
    document.setStripTrailingSpacesEnabled(true);
    document.stripTrailingSpaces(getProject());
    manager.commitAllDocuments();
  }

  public void testAsListToar() {
    configure();
    startTemplate("toar", "Java");
    myFixture.type("\n\t");
    checkResult();
  }

  public void testVarargToar() {
    configure();
    startTemplate("toar", "Java");
    checkResult();
  }

  public void testSoutp() {
    configure();
    startTemplate("soutp", "Java");
    checkResult();
  }

  public void testItm() {
    configure();
    startTemplate("itm", "Java");
    WriteCommandAction.runWriteCommandAction(getProject(), () -> getState().gotoEnd(false));
    checkResult();
  }

  public void testInst() {
    configure();
    startTemplate("inst", "Java");
    myFixture.type("\n");
    myFixture.type("\n");
    myFixture.type("\n");
    checkResult();
  }

  public void testSoutConsumerApplicability() {
    for (String name : new ArrayList<>(Arrays.asList("soutc", "serrc"))) {
      TemplateImpl template = TemplateSettings.getInstance().getTemplate(name, "Java");
      assert !isApplicable("class Foo {void x(){ <caret>JUNK }}", template);
      assert !isApplicable("class Foo {void x(java.util.stream.IntStream is){ is.map(<caret>JUNK) }}", template);
      assert isApplicable("class Foo {void x(java.util.stream.IntStream is){ is.peek(<caret>JUNK) }}", template);
    }
  }

  public void testSoutConsumer() {
    configure();
    startTemplate("soutc", "Java");
    checkResult();
  }

  public void testSerrConsumerConflict() {
    configure();
    startTemplate("serrc", "Java");
    checkResult();
  }

  private boolean isApplicable(String text, TemplateImpl inst) {
    myFixture.configureByText("a.java", text);
    return TemplateManagerImpl.isApplicable(myFixture.getFile(), getEditor().getCaretModel().getOffset(), inst);
  }

  public void test_generic_type_argument_is_declaration_context() {
    myFixture.configureByText("a.java", "class Foo {{ List<Pair<X, <caret>Y>> l; }}");
    Set<TemplateContextType> contextTypeSet = TemplateManagerImpl
      .getApplicableContextTypes(TemplateActionContext.expanding(myFixture.getFile(), myFixture.getEditor()));
    List<Class<? extends TemplateContextType>> applicableContextTypesClasses = ContainerUtil.map(contextTypeSet, TemplateContextType::getClass);
    List<Class<JavaCodeContextType.Declaration>> declarationTypes = Arrays.asList(JavaCodeContextType.Declaration.class);

    assert applicableContextTypesClasses.equals(declarationTypes);
  }

  public void testJavaStatementContext() {
    final TemplateImpl template = TemplateSettings.getInstance().getTemplate("inst", "Java");
    TestCase.assertFalse(isApplicable("class Foo {{ if (a inst<caret>) }}", template));
    TestCase.assertTrue(isApplicable("class Foo {{ <caret>inst }}", template));
    TestCase.assertTrue(isApplicable("class Foo {{ <caret>inst\n a=b; }}", template));
    TestCase.assertFalse(isApplicable("class Foo {{ return (<caret>inst) }}", template));
    TestCase.assertFalse(isApplicable("class Foo {{ return a <caret>inst) }}", template));
    TestCase.assertFalse(isApplicable("class Foo {{ \"<caret>\" }}", template));
    TestCase.assertTrue(isApplicable("class Foo {{ <caret>a.b(); ) }}", template));
    TestCase.assertTrue(isApplicable("class Foo {{ <caret>a(); ) }}", template));
    TestCase.assertTrue(isApplicable("class Foo {{ Runnable r = () -> { <caret>System.out.println(\"foo\"); }; ) }}", template));
    TestCase.assertTrue(isApplicable("class Foo {{ Runnable r = () -> <caret>System.out.println(\"foo\"); ) }}", template));
  }

  public void testJavaExpressionContext() {
    final TemplateImpl template = TemplateSettings.getInstance().getTemplate("toar", "Java");
    assert !isApplicable("class Foo {{ if (a <caret>toar) }}", template);
    assert isApplicable("class Foo {{ <caret>toar }}", template);
    assert isApplicable("class Foo {{ return (<caret>toar) }}", template);
    assert !isApplicable("class Foo {{ return (aaa <caret>toar) }}", template);
    assert isApplicable("class Foo {{ Runnable r = () -> { <caret>System.out.println(\"foo\"); }; ) }}", template);
    assert isApplicable("class Foo {{ Runnable r = () -> <caret>System.out.println(\"foo\"); ) }}", template);
    assert !isApplicable("class Foo extends <caret>t {}", template);
    assert !isApplicable("record R(int i, <caret>toar) {}", template);
  }

  public void testJavaStringContext() {
    TemplateImpl template = (TemplateImpl)getTemplateManager().createTemplate("a", "b");
    template.getTemplateContext().setEnabled(TemplateContextTypes.getByClass(JavaStringContextType.class), true);
    assert !isApplicable("class Foo {{ <caret> }}", template);
    assert !isApplicable("class Foo {{ <caret>1 }}", template);
    assert isApplicable("class Foo {{ \"<caret>\" }}", template);
    assert isApplicable("class Foo {{ \"\"\"<caret>\"\"\" }}", template);
  }

  public void testJavaDeclarationContext() {
    final TemplateImpl template = TemplateSettings.getInstance().getTemplate("psvm", "Java");
    TestCase.assertFalse(isApplicable("class Foo {{ <caret>xxx }}", template));
    TestCase.assertFalse(isApplicable("class Foo {{ <caret>xxx }}", template));
    TestCase.assertFalse(isApplicable("class Foo {{ if (a <caret>xxx) }}", template));
    TestCase.assertFalse(isApplicable("class Foo {{ return (<caret>xxx) }}", template));
    TestCase.assertTrue(isApplicable("class Foo { <caret>xxx }", template));
    TestCase.assertFalse(isApplicable("class Foo { int <caret>xxx }", template));
    TestCase.assertTrue(isApplicable("class Foo {} <caret>xxx", template));

    TestCase.assertTrue(isApplicable("class Foo { void foo(<caret>xxx) {} }", template));
    TestCase.assertTrue(isApplicable("class Foo { void foo(<caret>xxx String bar ) {} }", template));
    TestCase.assertTrue(isApplicable("class Foo { void foo(<caret>xxx String bar, int goo ) {} }", template));
    TestCase.assertTrue(isApplicable("class Foo { void foo(String bar, <caret>xxx int goo ) {} }", template));
    TestCase.assertTrue(isApplicable("class Foo { void foo(String bar, <caret>xxx goo ) {} }", template));
    TestCase.assertTrue(isApplicable("class Foo { <caret>xxx void foo(String bar, xxx goo ) {} }", template));
    TestCase.assertTrue(isApplicable("class Foo { void foo(<caret>String[] bar) {} }", template));
    TestCase.assertTrue(isApplicable("class Foo { <caret>xxx String[] foo(String[] bar) {} }", template));

    TestCase.assertTrue(isApplicable("class Foo { /**\nfoo **/ <caret>xxx String[] foo(String[] bar) {} }", template));

    TestCase.assertTrue(isApplicable("<caret>xxx package foo; class Foo {}", template));
    TestCase.assertTrue(isApplicable("record R(<caret>xxx int i) {}", template));
    TestCase.assertTrue(isApplicable("record R(<caret>xxx) {}", template));
    TestCase.assertFalse(isApplicable("record R(int <caret>xxx)", template));
  }

  public void test_inner_class_name() {
    myFixture.configureByText("a.java",
                              "\nclass Outer {\n    class Inner {\n        void foo() {\n            soutm<caret>\n        }\n    }\n}");
    myFixture.type("\t");
    assert myFixture.getEditor().getDocument().getText().contains("\"Inner.foo");
  }

  public void test_do_not_strip_type_argument_containing_class() {
    myFixture.configureByText("a.java",
                              """

                                import java.util.*;
                                class Foo {
                                  List<Map.Entry<String, Integer>> foo() {
                                    <caret>
                                  }
                                }
                                """);

    Template template = getTemplateManager().createTemplate("result", "user", "$T$ result;");
    template.addVariable("T", new MacroCallNode(new MethodReturnTypeMacro()), new EmptyNode(), false);
    template.setToReformat(true);

    startTemplate(template);
    assert myFixture.getEditor().getDocument().getText().contains("List<Map.Entry<String, Integer>> result;");
  }

  public void test_method_name_in_annotation() {
    myFixture.configureByText("a.java", "\nclass Foo {\n  <caret>\n  void foo() {}\n}\n");

    Template template = getTemplateManager().createTemplate("result", "user", "@SuppressWarnings(\"$T$\")");
    template.addVariable("T", new MacroCallNode(new MethodNameMacro()), new EmptyNode(), false);
    template.setToReformat(true);

    startTemplate(template);
    assert myFixture.getEditor().getDocument().getText().contains("@SuppressWarnings(\"foo\")");
  }

  public void test_name_shadowing() {
    myFixture.configureByText("a.java",
                              """
                                class LiveTemplateVarSuggestion {
                                    private Object value;
                                    public void setValue(Object value, Object value1){
                                      inn<caret>
                                    }
                                }""");
    myFixture.type("\t");
    assert DefaultGroovyMethods.equals(myFixture.getLookupElementStrings(), new ArrayList<>(Arrays.asList("value", "value1")));
  }

  public void test_escape_string_characters_in_soutv() {
    myFixture.configureByText("a.java", "\nclass Foo {\n  {\n    soutv<caret>\n  }\n}\n");
    myFixture.type("\t\"a\"");
    myFixture.checkResult("""

                            class Foo {
                              {
                                  System.out.println("\\"a\\" = " + "a"<caret>);
                              }
                            }
                            """);
  }

  public void test_reuse_static_import() {
    myFixture.addClass(
      """
        package foo;
        public final class Bar {
          public static void someMethod() {}
          public static void someMethod(int a) {}
        }""");
    myFixture.configureByText("a.java", "\nimport static foo.Bar.someMethod;\n\nclass Foo {\n  {\n    <caret>\n  }\n}\n");
    Template template = getTemplateManager().createTemplate("xxx", "user", "foo.Bar.someMethod($END$)");
    template.setValue(TemplateImpl.Property.USE_STATIC_IMPORT_IF_POSSIBLE, true);

    startTemplate(template);
    myFixture.checkResult("""

                            import static foo.Bar.someMethod;

                            class Foo {
                              {
                                someMethod(<caret>)
                              }
                            }
                            """);
  }

  public void test_use_single_member_static_import_first() {
    myFixture.addClass(
      """
        package foo;
        public final class Bar {
          public static void someMethod() {}
          public static void someMethod(int a) {}
        }""");
    myFixture.configureByText("a.java", "\n\nclass Foo {\n  {\n    <caret>\n  }\n}\n");
    Template template = getTemplateManager().createTemplate("xxx", "user", "foo.Bar.someMethod($END$)");
    template.setValue(TemplateImpl.Property.USE_STATIC_IMPORT_IF_POSSIBLE, true);

    startTemplate(template);
    myFixture.checkResult("""
                            import static foo.Bar.someMethod;

                            class Foo {
                              {
                                someMethod(<caret>)
                              }
                            }
                            """);
  }

  public void test_two_static_imports() {
    myFixture.configureByText("a.java", "\n\nclass Foo {\n  {\n    <caret>\n  }\n}\n");
    Template template = getTemplateManager().createTemplate("xxx", "user", "java.lang.Math.abs(java.lang.Math.PI);");
    template.setValue(TemplateImpl.Property.USE_STATIC_IMPORT_IF_POSSIBLE, true);

    startTemplate(template);
    myFixture.checkResult(
      """
        import static java.lang.Math.PI;
        import static java.lang.Math.abs;

        class Foo {
          {
            abs(PI);<caret>
          }
        }
        """);
  }

  public void test_sout_template_in_expression_lambda() {
    myFixture.configureByText("a.java", "class Foo {{\n  strings.stream().forEach(o -> sout<caret>);\n}}\n");
    myFixture.type("\t");
    myFixture.checkResult("""
                            class Foo {{
                              strings.stream().forEach(o -> System.out.println(<caret>));
                            }}
                            """);
  }

  public void test_itar_template_in_expression_lambda() {
    myFixture.configureByText("a.java", "class Foo {\n  void test(int[] arr) {\n    Runnable r = () -> itar<caret>\n  }\n}\n");
    myFixture.type("\t");
    myFixture.checkResult(
      """
        class Foo {
          void test(int[] arr) {
            Runnable r = () -> {
                for (int i = 0; i < arr.length; i++) {
                    int i1 = arr[i];
                   \s
                }
            }
          }
        }
        """);
  }

  public void test_iterate_over_list_with_wildcard_component_type() {
    myFixture.configureByText("a.java", "class C {{\njava.util.List<? extends Integer> list;\n<caret>\n}}");
    myFixture.type("itli\t");
    myFixture.checkResult(
      """
        class C {{
        java.util.List<? extends Integer> list;
            for (int i = 0; i < list.size(); i++) {
                Integer integer =  list.get(i);
               \s
            }
        }}""");
  }

  private void configure() {
    myFixture.configureByFile(getTestName(false) + ".java");
  }

  private void checkResult() {
    myFixture.checkResultByFile(getTestName(false) + "-out.java");
  }

  public void test_suggest_foreach_parameter_name_based_on_the_called_method_name() {
    myFixture.configureByText("a.java", "class A { Iterable<String> getCreatedTags() { }\n{\n  iter<caret>\n}}");
    myFixture.type("\tgetCreatedTags()\n");
    myFixture.checkResult(
      """
        class A { Iterable<String> getCreatedTags() { }
        {
            for (String createdTag : getCreatedTags()) {
               \s
            }
        }}""");
  }

  public void test_overtyping_suggestion_with_a_quote() {
    CodeInsightSettings.getInstance().setSelectAutopopupSuggestionsByChars(true);

    myFixture.configureByText("a.java", "\nclass A {\n  {\n    String s;\n    <caret>s.toString();\n  }\n}");
    myFixture.doHighlighting();
    myFixture.launchAction(myFixture.findSingleIntention("Initialize variable"));
    myFixture.type("\"");
    myFixture.checkResult("""

                            class A {
                              {
                                String s = "null";
                                s.toString();
                              }
                            }""");
    assert !DefaultGroovyMethods.asBoolean(myFixture.getLookup());
  }

  public void test_save_as_live_template_for_annotation_values() {
    myFixture.addClass("package foo; public @interface Anno { String value(); }");
    myFixture.configureByText("a.java", "import foo.*; <selection>@Anno(\"\")</selection> class T {}");
    assert SaveAsTemplateAction.suggestTemplateText(myFixture.getEditor(), myFixture.getFile(), myFixture.getProject())
      .equals("@foo.Anno(\"\")");

    myFixture.configureByText("b.java", "import foo.*; <selection>@Anno(value=\"\")</selection> class T {}");
    assert SaveAsTemplateAction.suggestTemplateText(myFixture.getEditor(), myFixture.getFile(), myFixture.getProject())
      .equals("@foo.Anno(value=\"\")");
  }

  public void test_reformat_with_virtual_space() {
    myFixture.configureByText("a.java", "class C {\n    public static void main(String ...args) {\n        <caret>\n    }\n}");
    getEditor().getSettings().setVirtualSpace(true);
    myFixture.type("iter\t");
    myFixture.checkResult(
      """
        class C {
            public static void main(String ...args) {
                for (String arg : args) {
                   \s
                }
            }
        }""");
  }

  public void test_subtypes_macro_works_with_text_argument() {
    myFixture.configureByText("a.java",
                              "\n\nclass Foo {\n  {\n    <caret>\n  }\n}\n\nclass Bar1 extends Foo {}\nclass Bar2 extends Foo {}\n");
    Template template = getTemplateManager().createTemplate("xxx", "user", "$T$ var = new $ST$();");
    template.addVariable("T", new EmptyNode(), true);
    template.addVariable("ST", "subtypes(T)", "", true);

    startTemplate(template);

    myFixture.type("Foo");
    getState().nextTab();

    assert myFixture.getEditor().getDocument().getText().contains("Foo var = new Foo();");
    UsefulTestCase.assertSameElements(myFixture.getLookupElementStrings(), "Foo", "Bar1", "Bar2");
  }

  public void test_methodParameterTypes() {
    myFixture.configureByText("a.java", """

      class X {
        void test(int a, String b, double[] c) {
          <caret>
        }
      }
      """);
    Template template = getTemplateManager().createTemplate("xxx", "user", "System.out.println(\"$TYPES$\");");
    template.addVariable("TYPES", "methodParameterTypes()", "", true);

    startTemplate(template);

    myFixture.checkResult(
      "\nclass X {\n  void test(int a, String b, double[] c) {\n    System.out.println(\"[int, java.lang.String, double[]]\");\n  }\n}\n");
  }

  public void test_at_equals_token() {
    myFixture.configureByText("a.java", """

      class X {
        void test() {
          int i <selection>=</selection> 5;
        }
      }
      """);
    TemplateActionContext templateActionContext = TemplateActionContext.surrounding(getFile(), getEditor());
    List<TemplateImpl> templates = TemplateManagerImpl.listApplicableTemplates(templateActionContext);
    assert DefaultGroovyMethods.equals(templates, new ArrayList<>());
  }

  public void test_whole_line_selected() {
    myFixture.configureByText("a.java", "\nclass X {\n  int test() {\n<selection>    return 5;\n</selection>  }\n}\n");
    TemplateActionContext templateActionContext = TemplateActionContext.surrounding(getFile(), getEditor());
    List<TemplateImpl> templates = TemplateManagerImpl.listApplicableTemplates(templateActionContext);
    String result = templates.stream()
      .map(Object::toString)
      .collect(Collectors.joining(", "));

    assert result.equals("Java/C, Java/RL, Java/WL, Java/I");
  }

  public void test_generic_arguments_are_inserted() {
    myFixture.configureByText("a.java",
                              """

                                import java.util.*;
                                public class Main {
                                  List<String> getList(ArrayList<String> list) {
                                    <caret>
                                  }
                                }
                                """);
    Template template = getTemplateManager().createTemplate("rlazy", "user", "return $VAR$ == null ? $VAR$ = new $TYPE$($END$) : $VAR$;");
    template.addVariable("VAR", "methodParameterTypes()", "", true);
    template.addVariable("TYPE", "subtypes(typeOfVariable(VAR))", "", true);
    template.setToReformat(true);
    startTemplate(template);
    myFixture.type("list\n\n");
    myFixture.checkResult(
      """

        import java.util.*;
        public class Main {
          List<String> getList(ArrayList<String> list) {
              return list == null ? list = new ArrayList<String>() : list;
          }
        }
        """);
  }

  @Override
  public final String getBasePath() {
    return basePath;
  }

  private final String basePath = JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/template/";
}
