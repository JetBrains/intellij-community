// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.template;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.daemon.impl.quickfix.EmptyExpression;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.template.*;
import com.intellij.codeInsight.template.actions.SaveAsTemplateAction;
import com.intellij.codeInsight.template.impl.*;
import com.intellij.codeInsight.template.macro.*;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.pom.java.JavaFeature;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.util.containers.ContainerUtil;
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

  public void testNotToGoToNextTabAfterInsertIfElementIsPsiPackage() {
    myFixture.configureByText("a.java", "\n<caret>\n");
    Template template = getTemplateManager().createTemplate("imp", "user", "import $MODIFIER$ java.$NAME$;");
    template.addVariable("NAME", new MacroCallNode(new CompleteMacro(true)), new EmptyNode(), true);
    template.addVariable("MODIFIER", new EmptyExpression(), true);
    startTemplate(template);
    myFixture.type("uti\n");
    myFixture.checkResult("\nimport  java.util.<caret>;\n");
    assertFalse(getState().isFinished());
  }

  public void testNotToGoToNextTabAfterInsertIfElementHasCallArguments() {
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
      """
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
        """);
    assertFalse(getState().isFinished());
  }

  public void testGoToNextTabAfterInsertIfElementDoesNotHaveCallArguments() {
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
    assertFalse(getState().isFinished());
  }

  public void testVariableOfTypeSuggestsInnerStaticClasses() {
    myFixture.addClass("public interface MyCallback {}");
    myFixture.addClass("""
                         final class MyUtils {
                           public static void doSomethingWithCallback(MyCallback cb) { }
                         }
                         """);
    myFixture.configureByText("a.java",
                              """
                                class Outer {
                                  static class Inner implements MyCallback {
                                    void aMethod() {
                                      <caret>
                                    }
                                  }
                                }
                                """);

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
      assertFalse(isApplicable("class Foo {void x(){ <caret>JUNK }}", template));
      assertFalse(isApplicable("class Foo {void x(java.util.stream.IntStream is){ is.map(<caret>JUNK) }}", template));
      assertTrue(isApplicable("class Foo {void x(java.util.stream.IntStream is){ is.peek(<caret>JUNK) }}", template));
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

  public void testGenericTypeArgumentIsDeclarationContext() {
    myFixture.configureByText("a.java", "class Foo {{ List<Pair<X, <caret>Y>> l; }}");
    Set<TemplateContextType> contextTypeSet = TemplateManagerImpl
      .getApplicableContextTypes(TemplateActionContext.expanding(myFixture.getFile(), myFixture.getEditor()));
    List<Class<? extends TemplateContextType>> applicableContextTypesClasses = ContainerUtil.map(contextTypeSet, TemplateContextType::getClass);
    List<Class<? extends JavaCodeContextType>> declarationTypes = Arrays.asList(JavaCodeContextType.Declaration.class, JavaCodeContextType.NormalClassDeclarationBeforeShortMainMethod.class);

    assertEquals(applicableContextTypesClasses, declarationTypes);
  }

  public void testJavaStatementContext() {
    final TemplateImpl template = TemplateSettings.getInstance().getTemplate("inst", "Java");
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

  public void testJavaExpressionContext() {
    final TemplateImpl template = TemplateSettings.getInstance().getTemplate("toar", "Java");
    assertFalse(isApplicable("class Foo {{ if (a <caret>toar) }}", template));
    assertTrue(isApplicable("class Foo {{ <caret>toar }}", template));
    assertTrue(isApplicable("class Foo {{ return (<caret>toar) }}", template));
    assertFalse(isApplicable("class Foo {{ return (aaa <caret>toar) }}", template));
    assertTrue(isApplicable("class Foo {{ Runnable r = () -> { <caret>System.out.println(\"foo\"); }; ) }}", template));
    assertTrue(isApplicable("class Foo {{ Runnable r = () -> <caret>System.out.println(\"foo\"); ) }}", template));
    assertFalse(isApplicable("class Foo extends <caret>t {}", template));
    assertFalse(isApplicable("record R(int i, <caret>toar) {}", template));
  }

  public void testJavaStringContext() {
    TemplateImpl template = (TemplateImpl)getTemplateManager().createTemplate("a", "b");
    template.getTemplateContext().setEnabled(TemplateContextTypes.getByClass(JavaStringContextType.class), true);
    assertFalse(isApplicable("class Foo {{ <caret> }}", template));
    assertFalse(isApplicable("class Foo {{ <caret>1 }}", template));
    assertTrue(isApplicable("class Foo {{ \"<caret>\" }}", template));
    assertTrue(isApplicable("class Foo {{ \"\"\"<caret>\"\"\" }}", template));
  }

  public void testJavaNormalClassDeclarationContextWithInstanceMethod() {
    final TemplateImpl template = TemplateSettings.getInstance().getTemplate("psvm", "Java//Instance 'main' methods for normal classes");
    IdeaTestUtil.withLevel(getModule(), JavaFeature.IMPLICIT_CLASSES.getMinimumLevel(), () -> {
      assertTrue(isApplicable("class Foo { <caret>xxx }", template));
      assertTrue(isApplicable("class Foo { <caret>xxx String[] foo(String[] bar) {} }", template));
      assertFalse(isApplicable("<caret>", template));
      assertFalse(isApplicable("int a = 1; <caret>", template));
    });
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_1_8, () -> {
      assertFalse(isApplicable("class Foo { <caret>xxx }", template));
    });
  }

  public void testJavaNormalClassDeclarationContextWithoutInstanceMethod() {
    final TemplateImpl template = TemplateSettings.getInstance().getTemplate("psvm", "Java");
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_1_8, () -> {
      assertTrue(isApplicable("class Foo { <caret>xxx }", template));
      assertTrue(isApplicable("class Foo { <caret>xxx String[] foo(String[] bar) {} }", template));
      assertFalse(isApplicable("<caret>", template));
      assertFalse(isApplicable("int a = 1; <caret>", template));
    });
    IdeaTestUtil.withLevel(getModule(), JavaFeature.IMPLICIT_CLASSES.getMinimumLevel(), () -> {
      assertFalse(isApplicable("class Foo { <caret>xxx }", template));
    });
  }

  public void testImplicitClassDeclarationContext() {
    final TemplateImpl template = TemplateSettings.getInstance().getTemplate("psvm", "Java//Instance 'main' methods for implicitly declared classes");
    IdeaTestUtil.withLevel(getModule(), JavaFeature.IMPLICIT_CLASSES.getMinimumLevel(), ()->{
      assertFalse(isApplicable("class Foo { <caret>xxx }", template));
      assertFalse(isApplicable("class Foo { <caret>xxx String[] foo(String[] bar) {} }", template));
      assertTrue(isApplicable("<caret>xxx", template));
      assertTrue(isApplicable("int a = 1; <caret>xxx", template));
    });
  }

  public void testJavaDeclarationContext() {
    final TemplateImpl template = TemplateSettings.getInstance().getTemplate("psf", "Java");
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

    assertTrue(isApplicable("class Foo { /**\nfoo **/ <caret>xxx String[] foo(String[] bar) {} }", template));

    assertTrue(isApplicable("<caret>xxx package foo; class Foo {}", template));
    assertTrue(isApplicable("record R(<caret>xxx int i) {}", template));
    assertTrue(isApplicable("record R(<caret>xxx) {}", template));
    assertTrue(isApplicable("record Foo(nu<caret>String bar)", template));
    assertFalse(isApplicable("record R(int <caret>xxx)", template));
  }

  public void testInnerClassName() {
    myFixture.configureByText("a.java",
                              """
                                class Outer {
                                    class Inner {
                                        void foo() {
                                            soutm<caret>
                                        }
                                    }
                                }""");
    myFixture.type("\t");
    assertTrue(myFixture.getEditor().getDocument().getText().contains("\"Inner.foo"));
  }

  public void testDoNotStripTypeArgumentContainingClass() {
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
    assertTrue(myFixture.getEditor().getDocument().getText().contains("List<Map.Entry<String, Integer>> result;"));
  }

  public void testMethodNameInAnnotation() {
    myFixture.configureByText("a.java", """
      class Foo {
        <caret>
        void foo() {}
      }
      """);

    Template template = getTemplateManager().createTemplate("result", "user", "@SuppressWarnings(\"$T$\")");
    template.addVariable("T", new MacroCallNode(new MethodNameMacro()), new EmptyNode(), false);
    template.setToReformat(true);

    startTemplate(template);
    assertTrue(myFixture.getEditor().getDocument().getText().contains("@SuppressWarnings(\"foo\")"));
  }

  public void testNameShadowing() {
    myFixture.configureByText("a.java",
                              """
                                class LiveTemplateVarSuggestion {
                                    private Object value;
                                    public void setValue(Object value, Object value1){
                                      inn<caret>
                                    }
                                }""");
    myFixture.type("\t");
    assertEquals(myFixture.getLookupElementStrings(), List.of("value", "value1"));
  }

  public void testEscapeStringCharactersInSoutv() {
    myFixture.configureByText("a.java", """
      class Foo {
        {
          soutv<caret>
        }
      }
      """);
    myFixture.type("\t\"a\"");
    myFixture.checkResult("""
                            class Foo {
                              {
                                  System.out.println("\\"a\\" = " + "a"<caret>);
                              }
                            }
                            """);
  }

  public void testReuseStaticImport() {
    myFixture.addClass(
      """
        package foo;
        public final class Bar {
          public static void someMethod() {}
          public static void someMethod(int a) {}
        }""");
    myFixture.configureByText("a.java", """
      import static foo.Bar.someMethod;

      class Foo {
        {
          <caret>
        }
      }
      """);
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

  public void testUseSingleMemberStaticImportFirst() {
    myFixture.addClass(
      """
        package foo;
        public final class Bar {
          public static void someMethod() {}
          public static void someMethod(int a) {}
        }""");
    myFixture.configureByText("a.java", """
      class Foo {
        {
          <caret>
        }
      }
      """);
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

  public void testTwoStaticImports() {
    myFixture.configureByText("a.java", """
      class Foo {
        {
          <caret>
        }
      }
      """);
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

  public void testSoutTemplateInExpressionLambda() {
    myFixture.configureByText("a.java", """
      class Foo {{
        strings.stream().forEach(o -> sout<caret>);
      }}
      """);
    myFixture.type("\t");
    myFixture.checkResult("""
                            class Foo {{
                              strings.stream().forEach(o -> System.out.println(<caret>));
                            }}
                            """);
  }

  public void testItarTemplateInExpressionLambda() {
    myFixture.configureByText("a.java", """
      class Foo {
        void test(int[] arr) {
          Runnable r = () -> itar<caret>
        }
      }
      """);
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

  public void testIterateOverListWithWildcardComponentType() {
    myFixture.configureByText("a.java", """
      class C {{
      java.util.List<? extends Integer> list;
      <caret>
      }}""");
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

  public void testSuggestForeachParameterNameBasedOnTheCalledMethodName() {
    myFixture.configureByText("a.java", """
      class A { Iterable<String> getCreatedTags() { }
      {
        iter<caret>
      }}""");
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

  public void testOvertypingSuggestionWithQuote() {
    CodeInsightSettings.getInstance().setSelectAutopopupSuggestionsByChars(true);

    myFixture.configureByText("a.java", """
      class A {
        {
          String s;
          <caret>s.toString();
        }
      }""");
    myFixture.doHighlighting();
    myFixture.launchAction(myFixture.findSingleIntention("Initialize variable"));
    myFixture.type("\"");
    myFixture.checkResult("""
                            class A {
                              {
                                String s = "";
                                s.toString();
                              }
                            }""");
    assertNull(myFixture.getLookup());
  }

  public void testSaveAsLiveTemplateForAnnotationValues() {
    myFixture.addClass("package foo; public @interface Anno { String value(); }");
    myFixture.configureByText("a.java", "import foo.*; <selection>@Anno(\"\")</selection> class T {}");
    assert SaveAsTemplateAction.suggestTemplateText(myFixture.getEditor(), myFixture.getFile(), myFixture.getProject())
      .equals("@foo.Anno(\"\")");

    myFixture.configureByText("b.java", "import foo.*; <selection>@Anno(value=\"\")</selection> class T {}");
    assert SaveAsTemplateAction.suggestTemplateText(myFixture.getEditor(), myFixture.getFile(), myFixture.getProject())
      .equals("@foo.Anno(value=\"\")");
  }

  public void testReformatWithVirtualSpace() {
    myFixture.configureByText("a.java", """
      class C {
          public static void main(String ...args) {
              <caret>
          }
      }""");
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

  public void testSubtypesMacroWorksWithTextArgument() {
    myFixture.configureByText("a.java",
                              """
                                class Foo {
                                  {
                                    <caret>
                                  }
                                }

                                class Bar1 extends Foo {}
                                class Bar2 extends Foo {}
                                """);
    Template template = getTemplateManager().createTemplate("xxx", "user", "$T$ var = new $ST$();");
    template.addVariable("T", new EmptyNode(), true);
    template.addVariable("ST", "subtypes(T)", "", true);

    startTemplate(template);

    myFixture.type("Foo");
    getState().nextTab();

    assertTrue(myFixture.getEditor().getDocument().getText().contains("Foo var = new Foo();"));
    assertSameElements(myFixture.getLookupElementStrings(), "Foo", "Bar1", "Bar2");
  }

  public void testMethodParameterTypes() {
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
      """
        class X {
          void test(int a, String b, double[] c) {
            System.out.println("[int, java.lang.String, double[]]");
          }
        }
        """);
  }

  public void testAtEqualsToken() {
    myFixture.configureByText("a.java", """
      class X {
        void test() {
          int i <selection>=</selection> 5;
        }
      }
      """);
    TemplateActionContext templateActionContext = TemplateActionContext.surrounding(getFile(), getEditor());
    List<TemplateImpl> templates = TemplateManagerImpl.listApplicableTemplates(templateActionContext);
    assertEquals(templates, List.of());
  }

  public void testWholeLineSelected() {
    myFixture.configureByText("a.java", """
      class X {
        int test() {
      <selection>    return 5;
      </selection>  }
      }
      """);
    TemplateActionContext templateActionContext = TemplateActionContext.surrounding(getFile(), getEditor());
    List<TemplateImpl> templates = TemplateManagerImpl.listApplicableTemplates(templateActionContext);
    String result = templates.stream()
      .map(Object::toString)
      .collect(Collectors.joining(", "));

    assertEquals("Java/C, Java/RL, Java/WL, Java/I", result);
  }

  public void testGenericArgumentsAreInserted() {
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

  public void testPsvmWithString() {
    IdeaTestUtil.withLevel(
      getModule(),
      JavaFeature.IMPLICIT_CLASSES.getMinimumLevel(),
      () -> {
        myFixture.configureByText(
          "a.java",
            """
            <caret>
            """);
        final TemplateImpl template =
          TemplateSettings.getInstance().getTemplate("psvma", "Java//Instance 'main' methods for implicitly declared classes");
        startTemplate(template);
        myFixture.checkResult(
            """
            void main(String[] args) {
                <caret>
            }
            """);
      }
    );
  }

  public void testPsvmWithoutString() {
    IdeaTestUtil.withLevel(
      getModule(),
      JavaFeature.IMPLICIT_CLASSES.getMinimumLevel(),
      () -> {
        myFixture.configureByText(
          "a.java",
            """
            class A{
            <caret>
            }
            """);
        final TemplateImpl template = TemplateSettings.getInstance().getTemplate("main", "Java//Instance 'main' methods for normal classes");
        startTemplate(template);
        myFixture.checkResult(
            """
            class A{
                static void main() {
                    <caret>
                }
            }
            """);
      }
    );
  }

  @Override
  public final String getBasePath() {
    return basePath;
  }

  private final String basePath = JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/template/";
}
