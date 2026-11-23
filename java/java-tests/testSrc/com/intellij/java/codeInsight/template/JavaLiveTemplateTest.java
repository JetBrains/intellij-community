// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.template;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.daemon.impl.quickfix.EmptyExpression;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.template.JavaCodeContextType;
import com.intellij.codeInsight.template.JavaStringContextType;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateActionContext;
import com.intellij.codeInsight.template.TemplateContextType;
import com.intellij.codeInsight.template.actions.SaveAsTemplateAction;
import com.intellij.codeInsight.template.impl.ConstantNode;
import com.intellij.codeInsight.template.impl.EmptyNode;
import com.intellij.codeInsight.template.impl.MacroCallNode;
import com.intellij.codeInsight.template.impl.TemplateContextTypes;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateSettings;
import com.intellij.codeInsight.template.impl.TextExpression;
import com.intellij.codeInsight.template.macro.ArrayVariableMacro;
import com.intellij.codeInsight.template.macro.BaseCompleteMacro;
import com.intellij.codeInsight.template.macro.CompleteMacro;
import com.intellij.codeInsight.template.macro.CompleteSmartMacro;
import com.intellij.codeInsight.template.macro.MethodNameMacro;
import com.intellij.codeInsight.template.macro.MethodReturnTypeMacro;
import com.intellij.codeInsight.template.macro.TypeOfVariableMacro;
import com.intellij.codeInsight.template.macro.VariableOfTypeMacro;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.pom.java.JavaFeature;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.openapi.application.ReadAction;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.containers.ContainerUtil;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.intellij.java.codeInsight.template.LiveTemplateFixtureKt.liveTemplateFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("removal")
@TestApplication
public class JavaLiveTemplateTest {
  private static final TestFixture<LightJavaProject> projectFixture =
    lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_LATEST_WITH_LATEST_JDK);
  private final TestFixture<LiveTemplateFixture> lt =
    liveTemplateFixture(projectFixture, JavaTestUtil.getJavaTestDataPath() + "/codeInsight/template/");

  @Test
  public void testNotToGoToNextTabAfterInsertIfElementIsPsiPackage() {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
    myFixture.configureByText("a.java", "\n<caret>\n");
    Template template = lt.get().getTemplateManager().createTemplate("imp", "user", "import $MODIFIER$ java.$NAME$;");
    template.addVariable("NAME", new MacroCallNode(new CompleteMacro(true)), new EmptyNode(), true);
    template.addVariable("MODIFIER", new EmptyExpression(), true);
    EdtTestUtil.runInEdtAndWait(() -> {
      lt.get().startTemplate(template);
      myFixture.type("uti\n");
      myFixture.checkResult("\nimport  java.util.<caret>;\n");
      assertFalse(lt.get().getState().isFinished());
    });
  }

  @Test
  public void testNotToGoToNextTabAfterInsertIfElementHasCallArguments() {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
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
      lt.get().getTemplateManager().createTemplate("for", "user", """
        for ($ELEMENT_TYPE$ $VAR$ : $ITERABLE_TYPE$) {
        $END$;
        }""");
    template.addVariable("ITERABLE_TYPE", new MacroCallNode(new CompleteSmartMacro()), new EmptyNode(), true);
    template.addVariable("VAR", new TextExpression("item"), true);
    template.addVariable("ELEMENT_TYPE", new TextExpression("String"), true);
    template.setToReformat(true);
    EdtTestUtil.runInEdtAndWait(() -> {
      lt.get().startTemplate(template);
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
      assertFalse(lt.get().getState().isFinished());
    });
  }

  @Test
  public void testGoToNextTabAfterInsertIfElementDoesNotHaveCallArguments() {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
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
      lt.get().getTemplateManager().createTemplate("for", "user", """
        for ($ELEMENT_TYPE$ $VAR$ : $ITERABLE_TYPE$) {
        $END$;
        }""");
    template.addVariable("ITERABLE_TYPE", new MacroCallNode(new CompleteSmartMacro()), new EmptyNode(), true);
    template.addVariable("VAR", new TextExpression("item"), true);
    template.addVariable("ELEMENT_TYPE", new TextExpression("String"), true);
    template.setToReformat(true);
    EdtTestUtil.runInEdtAndWait(() -> {
      lt.get().startTemplate(template);
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
      assertFalse(lt.get().getState().isFinished());
    });
  }

  @Test
  public void testVariableOfTypeSuggestsInnerStaticClasses() {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
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

    Template template = lt.get().getTemplateManager().createTemplate("myCbDo", "user", "MyUtils.doSomethingWithCallback($CB$)");

    MacroCallNode call = new MacroCallNode(new VariableOfTypeMacro());
    call.addParameter(new ConstantNode("MyCallback"));
    template.addVariable("CB", call, new EmptyNode(), false);
    EdtTestUtil.runInEdtAndWait(() -> {
      lt.get().startTemplate(template);

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
    });
  }

  @Test
  public void testToar() {
    configure();
    EdtTestUtil.runInEdtAndWait(() -> {
      lt.get().startTemplate("toar", "Java");
      lt.get().getState().gotoEnd(false);
      checkResult();
    });
  }

  @Test
  public void testElseIf() {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
    configure();
    EdtTestUtil.runInEdtAndWait(() -> {
      lt.get().startTemplate("else-if", "Java");
      WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> lt.get().getState().gotoEnd(false));
      checkResult();
    });
  }

  @Test
  public void testElseIf2() {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
    configure();
    EdtTestUtil.runInEdtAndWait(() -> {
      lt.get().startTemplate("else-if", "Java");
      WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> lt.get().getState().gotoEnd(false));
      checkResult();
    });
  }

  @Test
  public void testElseIf3() {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
    configure();
    EdtTestUtil.runInEdtAndWait(() -> {
      lt.get().startTemplate("else-if", "Java");
      WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> lt.get().getState().gotoEnd(false));
      checkResult();
    });
  }

  @Test
  public void testIter() {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
    configure();
    EdtTestUtil.runInEdtAndWait(() -> {
      lt.get().startTemplate("iter", "Java");
      WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> lt.get().getState().nextTab());
      myFixture.finishLookup(Lookup.AUTO_INSERT_SELECT_CHAR);
      checkResult();
    });
  }

  @Test
  public void testIter1() {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
    configure();
    EdtTestUtil.runInEdtAndWait(() -> {
      lt.get().startTemplate("iter", "Java");
      myFixture.performEditorAction("NextTemplateVariable");
      checkResult();
    });
  }

  @Test
  public void testIterTypingKeepsDependentVariablesUntilNextTab() {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
    myFixture.configureByText("a.java", """
      import java.util.List;
      class A {
        void foo(List<String> names, List<Integer> numbers) {
          <caret>
        }
      }""");
    EdtTestUtil.runInEdtAndWait(() -> {
      lt.get().startTemplate("iter", "Java");
      myFixture.checkResult("""
        import java.util.List;
        class A {
          void foo(List<String> names, List<Integer> numbers) {
              for (String name : names) {
                 \s
              }
          }
        }""");
      myFixture.type("numbers");
      myFixture.checkResult("""
        import java.util.List;
        class A {
          void foo(List<String> names, List<Integer> numbers) {
              for (String name : numbers) {
                 \s
              }
          }
        }""");
      WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> lt.get().getState().nextTab());
      myFixture.checkResult("""
        import java.util.List;
        class A {
          void foo(List<String> names, List<Integer> numbers) {
              for (Integer number : numbers) {
                 \s
              }
          }
        }""");
    });
  }

  @Test
  public void testIterParameterizedInner() {
    configure();
    EdtTestUtil.runInEdtAndWait(() -> {
      lt.get().startTemplate("iter", "Java");
      stripTrailingSpaces();
      checkResult();
    });
  }

  @Test
  public void testIterParameterizedInnerInMethod() {
    configure();
    EdtTestUtil.runInEdtAndWait(() -> {
      lt.get().startTemplate("iter", "Java");
      stripTrailingSpaces();
      checkResult();
    });
  }

  @Test
  public void testThrInSwitch() {
    configure();
    EdtTestUtil.runInEdtAndWait(() -> {
      lt.get().startTemplate("thr", "Java");
      stripTrailingSpaces();
      checkResult();
    });
  }

  private void stripTrailingSpaces() {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
    DocumentImpl document = (DocumentImpl)myFixture.getEditor().getDocument();
    PsiDocumentManager manager = PsiDocumentManager.getInstance(myFixture.getProject());
    manager.commitDocument(document);
    document.setStripTrailingSpacesEnabled(true);
    document.stripTrailingSpaces(myFixture.getProject());
    manager.commitAllDocuments();
  }

  @Test
  public void testAsListToar() {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
    configure();
    EdtTestUtil.runInEdtAndWait(() -> {
      lt.get().startTemplate("toar", "Java");
      myFixture.type("\n\t");
      checkResult();
    });
  }

  @Test
  public void testVarargToar() {
    configure();
    EdtTestUtil.runInEdtAndWait(() -> {
      lt.get().startTemplate("toar", "Java");
      checkResult();
    });
  }

  @Test
  public void testSoutp() {
    configure();
    EdtTestUtil.runInEdtAndWait(() -> {
      lt.get().startTemplate("soutp", "Java");
      checkResult();
    });
  }

  @Test
  public void testSoutpLambda() {
    configure();
    EdtTestUtil.runInEdtAndWait(() -> {
      lt.get().startTemplate("soutp", "Java");
      checkResult();
    });
  }

  @Test
  public void testItm() {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
    configure();
    EdtTestUtil.runInEdtAndWait(() -> {
      lt.get().startTemplate("itm", "Java");
      WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> lt.get().getState().gotoEnd(false));
      checkResult();
    });
  }

  @Test
  public void testInst() {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
    configure();
    EdtTestUtil.runInEdtAndWait(() -> {
      lt.get().startTemplate("inst", "Java");
      myFixture.type("\n");
      myFixture.type("\n");
      myFixture.type("\n");
      checkResult();
    });
  }

  @Test
  public void testSoutConsumerApplicability() {
    for (String name : new ArrayList<>(Arrays.asList("soutc", "serrc"))) {
      TemplateImpl template = TemplateSettings.getInstance().getTemplate(name, "Java");
      assertFalse(isApplicable("class Foo {void x(){ <caret>JUNK }}", template));
      assertFalse(isApplicable("class Foo {void x(java.util.stream.IntStream is){ is.map(<caret>JUNK) }}", template));
      assertTrue(isApplicable("class Foo {void x(java.util.stream.IntStream is){ is.peek(<caret>JUNK) }}", template));
    }
  }

  @Test
  public void testSoutConsumer() {
    configure();
    EdtTestUtil.runInEdtAndWait(() -> {
      lt.get().startTemplate("soutc", "Java");
      checkResult();
    });
  }

  @Test
  public void testSerrConsumerConflict() {
    configure();
    EdtTestUtil.runInEdtAndWait(() -> {
      lt.get().startTemplate("serrc", "Java");
      checkResult();
    });
  }

  private boolean isApplicable(String text, TemplateImpl inst) {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
    myFixture.configureByText("a.java", text);
    return ReadAction.computeBlocking(
      () -> TemplateManagerImpl.isApplicable(myFixture.getFile(), myFixture.getEditor().getCaretModel().getOffset(), inst));
  }

  @Test
  public void testGenericTypeArgumentIsDeclarationContext() {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
    myFixture.configureByText("a.java", "class Foo {{ List<Pair<X, <caret>Y>> l; }}");
    ReadAction.runBlocking(() -> {
      Set<TemplateContextType> contextTypeSet = TemplateManagerImpl
        .getApplicableContextTypes(TemplateActionContext.expanding(myFixture.getFile(), myFixture.getEditor()));
      List<Class<? extends TemplateContextType>> applicableContextTypesClasses = ContainerUtil.map(contextTypeSet, TemplateContextType::getClass);
      List<Class<? extends JavaCodeContextType>> declarationTypes = Arrays.asList(JavaCodeContextType.Declaration.class);

      assertEquals(declarationTypes, applicableContextTypesClasses);
    });
  }

  @Test
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

  @Test
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

  @Test
  public void testJavaStringContext() {
    TemplateImpl template = (TemplateImpl)lt.get().getTemplateManager().createTemplate("a", "b");
    template.getTemplateContext().setEnabled(TemplateContextTypes.getByClass(JavaStringContextType.class), true);
    assertFalse(isApplicable("class Foo {{ <caret> }}", template));
    assertFalse(isApplicable("class Foo {{ <caret>1 }}", template));
    assertTrue(isApplicable("class Foo {{ \"<caret>\" }}", template));
    assertTrue(isApplicable("class Foo {{ \"\"\"<caret>\"\"\" }}", template));
  }

  @Test
  public void testJavaNormalClassDeclarationContextWithInstanceMethod() {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
    final TemplateImpl template = TemplateSettings.getInstance().getTemplate("psvm", "Java//Instance 'main' methods for normal classes");
    IdeaTestUtil.withLevel(myFixture.getModule(), JavaFeature.IMPLICIT_CLASSES.getStandardLevel(), () -> {
      assertTrue(isApplicable("class Foo { <caret>xxx }", template));
      assertTrue(isApplicable("class Foo { <caret>xxx String[] foo(String[] bar) {} }", template));
      assertFalse(isApplicable("<caret>", template));
      assertFalse(isApplicable("int a = 1; <caret>", template));
      assertFalse(isApplicable("class Foo { void test(<caret>xxx) {} }", template));
      assertFalse(isApplicable("record Foo(<caret>xxx) {}", template));
    });
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_1_8, () -> {
      assertFalse(isApplicable("class Foo { <caret>xxx }", template));
    });
  }

  @Test
  public void testJavaNormalClassDeclarationContextWithoutInstanceMethod() {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
    final TemplateImpl template = TemplateSettings.getInstance().getTemplate("psvm", "Java");
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_1_8, () -> {
      assertTrue(isApplicable("class Foo { <caret>xxx }", template));
      assertTrue(isApplicable("class Foo { <caret>xxx String[] foo(String[] bar) {} }", template));
      assertFalse(isApplicable("<caret>", template));
      assertFalse(isApplicable("int a = 1; <caret>", template));
      assertFalse(isApplicable("class Foo { void test(<caret>xxx) {} }", template));
      assertFalse(isApplicable("record Foo(<caret>xxx) {}", template));
    });
    IdeaTestUtil.withLevel(myFixture.getModule(), JavaFeature.IMPLICIT_CLASSES.getStandardLevel(), () -> {
      assertFalse(isApplicable("class Foo { <caret>xxx }", template));
    });
  }

  @Test
  public void testImplicitClassDeclarationContext() {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
    final TemplateImpl template = TemplateSettings.getInstance().getTemplate("psvm", "Java//Instance 'main' methods for implicitly declared classes");
    IdeaTestUtil.withLevel(myFixture.getModule(), JavaFeature.IMPLICIT_CLASSES.getStandardLevel(), ()->{
      assertFalse(isApplicable("class Foo { <caret>xxx }", template));
      assertFalse(isApplicable("class Foo { <caret>xxx String[] foo(String[] bar) {} }", template));
      assertTrue(isApplicable("<caret>xxx", template));
      assertTrue(isApplicable("int a = 1; <caret>xxx", template));
    });
  }

  @Test
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

  @Test
  public void testInnerClassName() {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
    myFixture.configureByText("a.java",
                              """
                                class Outer {
                                    class Inner {
                                        void foo() {
                                            soutm<caret>
                                        }
                                    }
                                }""");
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.type("\t");
      assertTrue(myFixture.getEditor().getDocument().getText().contains("\"Inner.foo"));
    });
  }

  @Test
  public void testDoNotStripTypeArgumentContainingClass() {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
    myFixture.configureByText("a.java",
                              """
                                import java.util.*;
                                class Foo {
                                  List<Map.Entry<String, Integer>> foo() {
                                    <caret>
                                  }
                                }
                                """);

    Template template = lt.get().getTemplateManager().createTemplate("result", "user", "$T$ result;");
    template.addVariable("T", new MacroCallNode(new MethodReturnTypeMacro()), new EmptyNode(), false);
    template.setToReformat(true);

    EdtTestUtil.runInEdtAndWait(() -> {
      lt.get().startTemplate(template);
      assertTrue(myFixture.getEditor().getDocument().getText().contains("List<Map.Entry<String, Integer>> result;"));
    });
  }

  @Test
  public void testTypeOfVariableOfNestedArrayVariableCallGivesArrayType() {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
    myFixture.configureByText("a.java", """
      class A {
        void foo(String[] names) {
          <caret>
        }
      }""");
    Template template = lt.get().getTemplateManager().createTemplate("t", "user", "$T$ x = null;");
    MacroCallNode macro = new MacroCallNode(new TypeOfVariableMacro());
    macro.addParameter(new MacroCallNode(new ArrayVariableMacro()));
    template.addVariable("T", macro, new EmptyNode(), true);

    EdtTestUtil.runInEdtAndWait(() -> {
      lt.get().startTemplate(template);
      String line = ContainerUtil.find(myFixture.getEditor().getDocument().getText().lines().toList(), it -> it.contains("x = null;"));
      assertEquals("String[] x = null;", line.trim());
    });
  }

  @Test
  public void testMethodNameInAnnotation() {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
    myFixture.configureByText("a.java", """
      class Foo {
        <caret>
        void foo() {}
      }
      """);

    Template template = lt.get().getTemplateManager().createTemplate("result", "user", "@SuppressWarnings(\"$T$\")");
    template.addVariable("T", new MacroCallNode(new MethodNameMacro()), new EmptyNode(), false);
    template.setToReformat(true);

    EdtTestUtil.runInEdtAndWait(() -> {
      lt.get().startTemplate(template);
      assertTrue(myFixture.getEditor().getDocument().getText().contains("@SuppressWarnings(\"foo\")"));
    });
  }

  @Test
  public void testNameShadowing() {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
    myFixture.configureByText("a.java",
                              """
                                class LiveTemplateVarSuggestion {
                                    private Object value;
                                    public void setValue(Object value, Object value1){
                                      inn<caret>
                                    }
                                }""");
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.type("\t");
      assertEquals(myFixture.getLookupElementStrings(), List.of("value", "value1"));
    });
  }

  @Test
  public void testEscapeStringCharactersInSoutv() {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
    myFixture.configureByText("a.java", """
      class Foo {
        {
          soutv<caret>
        }
      }
      """);
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.type("\t\"a\"");
      myFixture.checkResult("""
                              class Foo {
                                {
                                    System.out.println("\\"a\\" = " + "a"<caret>);
                                }
                              }
                              """);
    });
  }

  @Test
  public void testReuseStaticImport() {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
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
    Template template = lt.get().getTemplateManager().createTemplate("xxx", "user", "foo.Bar.someMethod($END$)");
    template.setValue(TemplateImpl.Property.USE_STATIC_IMPORT_IF_POSSIBLE, true);

    EdtTestUtil.runInEdtAndWait(() -> {
      lt.get().startTemplate(template);
      myFixture.checkResult("""
                              import static foo.Bar.someMethod;

                              class Foo {
                                {
                                  someMethod(<caret>)
                                }
                              }
                              """);
    });
  }

  @Test
  public void testUseSingleMemberStaticImportFirst() {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
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
    Template template = lt.get().getTemplateManager().createTemplate("xxx", "user", "foo.Bar.someMethod($END$)");
    template.setValue(TemplateImpl.Property.USE_STATIC_IMPORT_IF_POSSIBLE, true);

    EdtTestUtil.runInEdtAndWait(() -> {
      lt.get().startTemplate(template);
      myFixture.checkResult("""
                              import static foo.Bar.someMethod;

                              class Foo {
                                {
                                  someMethod(<caret>)
                                }
                              }
                              """);
    });
  }

  @Test
  public void testTwoStaticImports() {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
    myFixture.configureByText("a.java", """
      class Foo {
        {
          <caret>
        }
      }
      """);
    Template template = lt.get().getTemplateManager().createTemplate("xxx", "user", "java.lang.Math.abs(java.lang.Math.PI);");
    template.setValue(TemplateImpl.Property.USE_STATIC_IMPORT_IF_POSSIBLE, true);

    EdtTestUtil.runInEdtAndWait(() -> {
      lt.get().startTemplate(template);
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
    });
  }

  @Test
  public void testSoutTemplateInExpressionLambda() {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
    myFixture.configureByText("a.java", """
      class Foo {{
        strings.stream().forEach(o -> sout<caret>);
      }}
      """);
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.type("\t");
      myFixture.checkResult("""
                              class Foo {{
                                strings.stream().forEach(o -> System.out.println(<caret>));
                              }}
                              """);
    });
  }

  @Test
  public void testItarTemplateInExpressionLambda() {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
    myFixture.configureByText("a.java", """
      class Foo {
        void test(int[] arr) {
          Runnable r = () -> itar<caret>
        }
      }
      """);
    EdtTestUtil.runInEdtAndWait(() -> {
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
    });
  }

  @Test
  public void testIterateOverListWithWildcardComponentType() {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
    myFixture.configureByText("a.java", """
      class C {{
      java.util.List<? extends Integer> list;
      <caret>
      }}""");
    EdtTestUtil.runInEdtAndWait(() -> {
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
    });
  }

  private void configure() {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
    myFixture.configureByFile(myFixture.getTestName(false) + ".java");
  }

  private void checkResult() {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
    myFixture.checkResultByFile(myFixture.getTestName(false) + "-out.java");
  }

  @Test
  public void testSuggestForeachParameterNameBasedOnTheCalledMethodName() {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
    myFixture.configureByText("a.java", """
      class A { Iterable<String> getCreatedTags() { }
      {
        iter<caret>
      }}""");
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.type("\tgetCreatedTags()\n");
      myFixture.checkResult(
        """
          class A { Iterable<String> getCreatedTags() { }
          {
              for (String createdTag : getCreatedTags()) {
                 \s
              }
          }}""");
    });
  }

  @Test
  public void testOvertypingSuggestionWithQuote() {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
    CodeInsightSettings.getInstance().setSelectAutopopupSuggestionsByChars(true);

    myFixture.configureByText("a.java", """
      class A {
        {
          String s;
          <caret>s.toString();
        }
      }""");
    EdtTestUtil.runInEdtAndWait(() -> {
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
    });
  }

  @Test
  public void testSaveAsLiveTemplateForAnnotationValues() {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
    myFixture.addClass("package foo; public @interface Anno { String value(); }");
    myFixture.configureByText("a.java", "import foo.*; <selection>@Anno(\"\")</selection> class T {}");
    assert EdtTestUtil.runInEdtAndGet(
      () -> SaveAsTemplateAction.suggestTemplateText(myFixture.getEditor(), myFixture.getFile(), myFixture.getProject()))
      .equals("@foo.Anno(\"\")");

    myFixture.configureByText("b.java", "import foo.*; <selection>@Anno(value=\"\")</selection> class T {}");
    assert EdtTestUtil.runInEdtAndGet(
      () -> SaveAsTemplateAction.suggestTemplateText(myFixture.getEditor(), myFixture.getFile(), myFixture.getProject()))
      .equals("@foo.Anno(value=\"\")");
  }

  @Test
  public void testReformatWithVirtualSpace() {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
    myFixture.configureByText("a.java", """
      class C {
          public static void main(String ...args) {
              <caret>
          }
      }""");
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.getEditor().getSettings().setVirtualSpace(true);
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
    });
  }

  @Test
  public void testSubtypesMacroWorksWithTextArgument() {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
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
    Template template = lt.get().getTemplateManager().createTemplate("xxx", "user", "$T$ var = new $ST$();");
    template.addVariable("T", new EmptyNode(), true);
    template.addVariable("ST", "subtypes(T)", "", true);

    EdtTestUtil.runInEdtAndWait(() -> {
      lt.get().startTemplate(template);

      myFixture.type("Foo");
      lt.get().getState().nextTab();

      assertTrue(myFixture.getEditor().getDocument().getText().contains("Foo var = new Foo();"));
      assertThat(myFixture.getLookupElementStrings()).containsExactlyInAnyOrder("Foo", "Bar1", "Bar2");
    });
  }

  @Test
  public void testMethodParameterTypes() {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
    myFixture.configureByText("a.java", """
      class X {
        void test(int a, String b, double[] c) {
          <caret>
        }
      }
      """);
    Template template = lt.get().getTemplateManager().createTemplate("xxx", "user", "System.out.println(\"$TYPES$\");");
    template.addVariable("TYPES", "methodParameterTypes()", "", true);

    EdtTestUtil.runInEdtAndWait(() -> {
      lt.get().startTemplate(template);

      myFixture.checkResult(
        """
          class X {
            void test(int a, String b, double[] c) {
              System.out.println("[int, java.lang.String, double[]]");
            }
          }
          """);
    });
  }

  @Test
  public void testAtEqualsToken() {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
    myFixture.configureByText("a.java", """
      class X {
        void test() {
          int i <selection>=</selection> 5;
        }
      }
      """);
    ReadAction.runBlocking(() -> {
      TemplateActionContext templateActionContext = TemplateActionContext.surrounding(myFixture.getFile(), myFixture.getEditor());
      List<TemplateImpl> templates = TemplateManagerImpl.listApplicableTemplates(templateActionContext);
      assertEquals(templates, List.of());
    });
  }

  @Test
  public void testWholeLineSelected() {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
    myFixture.configureByText("a.java", """
      class X {
        int test() {
      <selection>    return 5;
      </selection>  }
      }
      """);
    ReadAction.runBlocking(() -> {
      TemplateActionContext templateActionContext = TemplateActionContext.surrounding(myFixture.getFile(), myFixture.getEditor());
      List<TemplateImpl> templates = TemplateManagerImpl.listApplicableTemplates(templateActionContext);
      String result = templates.stream()
        .map(Object::toString)
        .collect(Collectors.joining(", "));

      assertEquals("Java/C, Java/RL, Java/WL, Java/I", result);
    });
  }

  @Test
  public void testGenericArgumentsAreInserted() {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
    myFixture.configureByText("a.java",
                              """
                                import java.util.*;
                                public class Main {
                                  List<String> getList(ArrayList<String> list) {
                                    <caret>
                                  }
                                }
                                """);
    Template template = lt.get().getTemplateManager().createTemplate("rlazy", "user", "return $VAR$ == null ? $VAR$ = new $TYPE$($END$) : $VAR$;");
    template.addVariable("VAR", "methodParameterTypes()", "", true);
    template.addVariable("TYPE", "subtypes(typeOfVariable(VAR))", "", true);
    template.setToReformat(true);
    EdtTestUtil.runInEdtAndWait(() -> {
      lt.get().startTemplate(template);
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
    });
  }

  @Test
  public void testPsvmWithString() {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
    IdeaTestUtil.withLevel(
      myFixture.getModule(),
      JavaFeature.IMPLICIT_CLASSES.getStandardLevel(),
      () -> {
        myFixture.configureByText(
          "a.java",
            """
            <caret>
            """);
        final TemplateImpl template =
          TemplateSettings.getInstance().getTemplate("psvma", "Java//Instance 'main' methods for implicitly declared classes");
        EdtTestUtil.runInEdtAndWait(() -> {
          lt.get().startTemplate(template);
          myFixture.checkResult(
              """
              void main(String[] args) {
                  <caret>
              }
              """);
        });
      }
    );
  }

  @Test
  public void testPsvmWithoutString() {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
    IdeaTestUtil.withLevel(
      myFixture.getModule(),
      JavaFeature.IMPLICIT_CLASSES.getStandardLevel(),
      () -> {
        myFixture.configureByText(
          "a.java",
            """
            class A{
            <caret>
            }
            """);
        final TemplateImpl template = TemplateSettings.getInstance().getTemplate("main", "Java//Instance 'main' methods for normal classes");
        EdtTestUtil.runInEdtAndWait(() -> {
          lt.get().startTemplate(template);
          myFixture.checkResult(
              """
              class A{
                  static void main() {
                      <caret>
                  }
              }
              """);
        });
      }
    );
  }

  @Test
  public void testIOP() {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
    IdeaTestUtil.withLevel(
      myFixture.getModule(),
      JavaFeature.JAVA_LANG_IO.getStandardLevel(),
      () -> {
        myFixture.configureByText(
          "a.java",
            """
            class A{
              public static void main(String[] args) {
                <caret>
              }
            }
            """);
        final TemplateImpl template = TemplateSettings.getInstance().getTemplate("iop", "Java");
        EdtTestUtil.runInEdtAndWait(() -> {
          lt.get().startTemplate(template);
          myFixture.checkResult(
              """
              class A{
                public static void main(String[] args) {
                    IO.println(<caret>);
                }
              }
              """);
        });
      }
    );
  }

  @Test
  public void testIOR() {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
    IdeaTestUtil.withLevel(
      myFixture.getModule(),
      JavaFeature.JAVA_LANG_IO.getStandardLevel(),
      () -> {
        myFixture.configureByText(
          "a.java",
            """
            class A{
              public static void main(String[] args) {
                  <caret>
              }
            }""");
        final TemplateImpl template = TemplateSettings.getInstance().getTemplate("ior", "Java");
        EdtTestUtil.runInEdtAndWait(() -> {
          lt.get().startTemplate(template);
          myFixture.checkResult(
              """
              class A{
                public static void main(String[] args) {
                    IO.readln(<caret>);
                }
              }""");
        });
      }
    );
  }

  @Test
  public void testSTSAvailable() {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
    TemplateImpl template = TemplateSettings.getInstance().getTemplate("sts", "Java");
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_26_PREVIEW, () -> {
      assertFalse(isApplicable("class Foo {void x(){ <caret>JUNK }", template));
    });
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_27_PREVIEW, () -> {
      assertTrue(isApplicable("class Foo {void x(){ <caret>JUNK }", template));
    });
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_28, () -> {
      assertTrue(isApplicable("class Foo {void x(){ <caret>JUNK }", template));
    });

  }

  @Test
  public void testIOPAvailable() {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
    final TemplateImpl template =
      TemplateSettings.getInstance().getTemplate("iop", "Java");
    IdeaTestUtil.withLevel(myFixture.getModule(), JavaFeature.JAVA_LANG_IO.getStandardLevel(), () -> {
      assertTrue(isApplicable("class Foo {void x(){ <caret>JUNK }", template));
    });
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_21, () -> {
      assertFalse(isApplicable("class Foo {void x(){ <caret>JUNK }", template));
    });
  }

  @Test
  public void testIORAvailable() {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
    final TemplateImpl template =
      TemplateSettings.getInstance().getTemplate("ior", "Java");
    IdeaTestUtil.withLevel(myFixture.getModule(), JavaFeature.JAVA_LANG_IO.getStandardLevel(), () -> {
      assertTrue(isApplicable("class Foo {void x(){ <caret>JUNK }", template));
    });
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_21, () -> {
      assertFalse(isApplicable("class Foo {void x(){ <caret>JUNK }", template));
    });
  }

  @Test
  public void testSoutNotAvailableAfterDot() {
    final TemplateImpl template =
      TemplateSettings.getInstance().getTemplate("sout", "Java");
      assertFalse(isApplicable("""
                                 class A{
                                  public static void main(){
                                    .<caret>
                                  }
                                 }""", template));
  }

  @Test
  public void testNamedMacroCallInExpressionGivesFullResultOfMacro() {
    LightJavaCodeInsightTestFixture myFixture = lt.get().getFixture();
    myFixture.configureByText("a.java", "class A {\n  void foo(String[] names) {\n    <caret>\n  }\n}");
    Template template = lt.get().getTemplateManager().createTemplate("t", "user", "$T$.length;");
    template.addVariable("T", "V=arrayVariable()", "", true);
    EdtTestUtil.runInEdtAndWait(() -> {
      lt.get().startTemplate(template);
      String line = myFixture.getEditor().getDocument().getText().lines()
        .filter(it -> it.contains(".length;"))
        .findFirst()
        .orElseThrow()
        .trim();
      assertEquals("names.length;", line);
    });
  }

}
