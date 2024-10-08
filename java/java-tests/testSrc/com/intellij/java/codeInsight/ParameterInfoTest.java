// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl;
import com.intellij.codeInsight.hint.ParameterInfoComponent;
import com.intellij.codeInsight.hint.api.impls.AnnotationParameterInfoHandler;
import com.intellij.codeInsight.hint.api.impls.MethodParameterInfoHandler;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.java.codeInspection.DataFlowInspectionTestCase;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lang.parameterInfo.*;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.*;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.NeedsIndex;
import com.intellij.testFramework.fixtures.EditorHintFixture;
import com.intellij.testFramework.utils.parameterInfo.MockCreateParameterInfoContext;
import com.intellij.testFramework.utils.parameterInfo.MockParameterInfoUIContext;
import com.intellij.testFramework.utils.parameterInfo.MockUpdateParameterInfoContext;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.indexing.DumbModeAccessType;
import com.intellij.util.indexing.FileBasedIndex;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

import static com.intellij.testFramework.fixtures.EditorHintFixture.removeCurrentParameterColor;

public class ParameterInfoTest extends AbstractParameterInfoTestCase {
  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/parameterInfo/";
  }

  public void testPrivateMethodOfEnclosingClass() { doTest(); }
  public void testNotAccessible() { doTest(); }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }

  private void doTest() {
    myFixture.configureByFile(getTestName(false) + ".java");

    MethodParameterInfoHandler handler = new MethodParameterInfoHandler();
    CreateParameterInfoContext context = new MockCreateParameterInfoContext(getEditor(), getFile());
    PsiExpressionList list = handler.findElementForParameterInfo(context);
    assertNotNull(list);
    Object[] itemsToShow = context.getItemsToShow();
    assertNotNull(itemsToShow);
    assertTrue(itemsToShow.length > 0);
  }

  public void testParameterInfoDoesNotShowInternalJetBrainsAnnotations() {
    myFixture.configureByText("x.java", "class X { void f(@org.intellij.lang.annotations.Flow int i) { f(<caret>0); }}");

    CreateParameterInfoContext context = new MockCreateParameterInfoContext(getEditor(), getFile());
    PsiMethod method = PsiTreeUtil.getParentOfType(getFile().findElementAt(context.getOffset()), PsiMethod.class);
    assertNotNull(method);
    MockParameterInfoUIContext<PsiMethod> uiContext = new MockParameterInfoUIContext<>(method);
    String list = MethodParameterInfoHandler.updateMethodPresentation(method, PsiSubstitutor.EMPTY, uiContext);
    assertEquals("int i", list);
    PsiAnnotation[] annotations = AnnotationUtil.getAllAnnotations(method.getParameterList().getParameters()[0], false, null);
    assertEquals(1, annotations.length);
  }

  @NeedsIndex.ForStandardLibrary
  public void testWhenInferenceIsBoundedByEqualsBound() {
    EditorHintFixture hintFixture = new EditorHintFixture(getTestRootDisposable());
    myFixture.configureByText("x.java",
                              """
                                import java.util.function.Function;
                                import java.util.function.Supplier;
                                class X {
                                    public <K> void foo(Supplier<K> extractKey, Function<String, K> right) {}
                                    public void bar(Function<String, Integer> right) {
                                        foo(<caret>() -> 1, right);
                                    }
                                }
                                """);

    showParameterInfo();
    assertEquals("<html><b>Supplier&lt;Integer&gt; extractKey</b>, Function&lt;String, Integer&gt; right</html>", hintFixture.getCurrentHintText());
  }

  public void testSelectionWithGenerics() {
    doTest2CandidatesWithPreselection();
  }

  public void testOverloadWithVarargs() {
    doTest2CandidatesWithPreselection();
  }

  public void testOverloadWithVarargsMultipleArgs() {
    doTest2CandidatesWithPreselection();
  }

  public void testOverloadWithVarargsSingleArg() {
    doTest2CandidatesWithPreselection();
  }

  @NeedsIndex.ForStandardLibrary
  public void testOverloadWithOneIncompatibleVarargs() {
    assertNotNull(doTest2CandidatesWithPreselection().getHighlightedParameter());
  }

  @NeedsIndex.ForStandardLibrary
  public void testOverloadWithErrorOnTheTopLevel() {
    doTest2CandidatesWithPreselection();
    PsiElement elementAtCaret = myFixture.getFile().findElementAt(myFixture.getEditor().getCaretModel().getOffset());
    PsiCall call = LambdaUtil.treeWalkUp(elementAtCaret);
    assertNotNull(call);
    //cache the type of first argument: if type is calculated by cached session of first (wrong) overload, then applicability check would fail
    //applicability check itself takes into account only child constraints and thus won't see cached elements on top level, thus explicit type calculation
    PsiType type = call.getArgumentList().getExpressions()[1].getType();
    assertNotNull(type);
    JavaResolveResult result = call.resolveMethodGenerics();
    assertTrue(result instanceof MethodCandidateInfo);
    assertTrue(((MethodCandidateInfo)result).isApplicable());
  }

  public void testOverloadWithVarargsArray() {
    doTest2CandidatesWithPreselection();
  }

  public void testSuperConstructorCalls() {
    EditorHintFixture hintFixture = new EditorHintFixture(getTestRootDisposable());
    myFixture.configureByText("x.java",
                              """
                                class A {
                                       public A(String s, int... p) {}
                                   }
                                   class B extends A {
                                       public B() {
                                           super(<caret>"a", 1);
                                       }
                                   }""");
    showParameterInfo();
    assertEquals("<html><b>String s</b>, int... p</html>", hintFixture.getCurrentHintText());
  }

  @NeedsIndex.ForStandardLibrary
  public void testCompletionPolicyWithLowerBounds() {
    EditorHintFixture hintFixture = new EditorHintFixture(getTestRootDisposable());
    myFixture.configureByText("x.java",
                              """
                                class B {
                                  static <T> T[] foo(T[] args, int l) {
                                    return null;
                                  }
                                  void f(String[] args) {
                                    String[] a = foo(args, args.len<caret>gth);
                                  }
                                }""");
    showParameterInfo();
    assertEquals("<html>String[] args, <b>int l</b></html>", hintFixture.getCurrentHintText());
  }

  @NeedsIndex.ForStandardLibrary
  public void testPreselectionOfCandidatesInNestedMethod() {
    myFixture.configureByFile(getTestName(false) + ".java");

    MethodParameterInfoHandler handler = new MethodParameterInfoHandler();
    CreateParameterInfoContext context = new MockCreateParameterInfoContext(getEditor(), getFile());
    PsiExpressionList list = handler.findElementForParameterInfo(context);
    assertNotNull(list);
    Object[] itemsToShow = context.getItemsToShow();
    assertNotNull(itemsToShow);
    assertEquals(3, itemsToShow.length);
    ParameterInfoComponent.createContext(itemsToShow, getEditor(), handler, -1);
    MockUpdateParameterInfoContext updateParameterInfoContext = updateParameterInfo(handler, list, itemsToShow);
    assertTrue(updateParameterInfoContext.isUIComponentEnabled(0) ||
                updateParameterInfoContext.isUIComponentEnabled(1) ||
                updateParameterInfoContext.isUIComponentEnabled(2));
  }

  private MockUpdateParameterInfoContext doTest2CandidatesWithPreselection() {
    myFixture.configureByFile(getTestName(false) + ".java");

    MethodParameterInfoHandler handler = new MethodParameterInfoHandler();
    CreateParameterInfoContext context = new MockCreateParameterInfoContext(getEditor(), getFile());
    PsiExpressionList list = handler.findElementForParameterInfo(context);
    assertNotNull(list);
    Object[] itemsToShow = context.getItemsToShow();
    assertNotNull(itemsToShow);
    assertEquals(2, itemsToShow.length);
    MethodParameterInfoHandler.getMethodFromCandidate(itemsToShow[0]);
    ParameterInfoComponent.createContext(itemsToShow, getEditor(), handler, -1);
    MockUpdateParameterInfoContext updateParameterInfoContext = updateParameterInfo(handler, list, itemsToShow);
    assertTrue(updateParameterInfoContext.isUIComponentEnabled(0) || updateParameterInfoContext.isUIComponentEnabled(1));
    return updateParameterInfoContext;
  }

  @NotNull
  private MockUpdateParameterInfoContext updateParameterInfo(MethodParameterInfoHandler handler,
                                                             PsiExpressionList list,
                                                             Object[] itemsToShow) {
    MockUpdateParameterInfoContext updateParameterInfoContext = new MockUpdateParameterInfoContext(getEditor(), getFile(), itemsToShow);
    updateParameterInfoContext.setParameterOwner(list);
    handler.updateParameterInfo(list, updateParameterInfoContext);
    return updateParameterInfoContext;
  }

  public void testStopAtAccessibleStaticCorrectCandidate() {
    myFixture.configureByFile(getTestName(false) + ".java");

    MethodParameterInfoHandler handler = new MethodParameterInfoHandler();
    CreateParameterInfoContext context = new MockCreateParameterInfoContext(getEditor(), getFile());
    PsiExpressionList list = handler.findElementForParameterInfo(context);
    assertNotNull(list);
    Object[] itemsToShow = context.getItemsToShow();
    assertNotNull(itemsToShow);
    assertEquals(1, itemsToShow.length);
    assertEquals(0, MethodParameterInfoHandler.getMethodFromCandidate(itemsToShow[0]).getParameterList().getParametersCount());
  }

  public void testAfterGenericsInsideCall() {
    myFixture.configureByFile(getTestName(false) + ".java");

    MethodParameterInfoHandler handler = new MethodParameterInfoHandler();
    CreateParameterInfoContext context = new MockCreateParameterInfoContext(getEditor(), getFile());
    PsiExpressionList list = handler.findElementForParameterInfo(context);
    assertNotNull(list);
    Object[] itemsToShow = context.getItemsToShow();
    assertNotNull(itemsToShow);
    assertEquals(2, itemsToShow.length);
    ParameterInfoUIContextEx parameterContext = ParameterInfoComponent.createContext(itemsToShow, getEditor(), handler, 1);
    parameterContext.setUIComponentEnabled(true);
    String presentation = MethodParameterInfoHandler.updateMethodPresentationFromCandidate(parameterContext, itemsToShow[0]);
    assertEquals("<html>Class&lt;T&gt; type, <b>boolean tags</b></html>", removeCurrentParameterColor(presentation));
  }

  public void testNoParams() { doTestPresentation("<html>&lt;no parameters&gt;</html>", -1); }
  public void testGenericsInsideCall() { doTestPresentation("<html>List&lt;String&gt; param</html>", -1); }
  public void testGenericsOutsideCall() { doTestPresentation("<html>List&lt;String&gt; param</html>", -1); }
  public void testIgnoreVarargs() { doTestPresentation("<html>Class&lt;T&gt; a, <b>Class&lt;? extends CharSequence&gt;... stopAt</b></html>", 1); }

  private void doTestPresentation(String expectedString, int parameterIndex) {
    myFixture.configureByFile(getTestName(false) + ".java");
    String presentation = parameterPresentation(parameterIndex);
    assertEquals(expectedString, removeCurrentParameterColor(presentation));
  }

  private String parameterPresentation(int parameterIndex) {
    return parameterPresentation(0, parameterIndex);
  }

  private String parameterPresentation(int lineIndex, int parameterIndex) {
    MethodParameterInfoHandler handler = new MethodParameterInfoHandler();
    CreateParameterInfoContext context = createContext();
    PsiExpressionList list = handler.findElementForParameterInfo(context);
    assertNotNull(list);
    Object[] itemsToShow = context.getItemsToShow();
    assertNotNull(itemsToShow);
    ParameterInfoUIContextEx parameterContext = ParameterInfoComponent.createContext(itemsToShow, getEditor(), handler, parameterIndex);
    return MethodParameterInfoHandler.updateMethodPresentationFromCandidate(parameterContext, itemsToShow[lineIndex]);
  }

  private CreateParameterInfoContext createContext() {
    int caretOffset = getEditor().getCaretModel().getOffset();
    PsiExpressionList argList = PsiTreeUtil.findElementOfClassAtOffset(getFile(), caretOffset, PsiExpressionList.class, false);
    return new MockCreateParameterInfoContext(getEditor(), getFile()) {
      @Override
      public int getParameterListStart() {
        return argList == null ? caretOffset : argList.getTextRange().getStartOffset();
      }
    };
  }

  public void testAnnotationWithGenerics() {
    myFixture.configureByFile(getTestName(false) + ".java");
    String text = annoParameterPresentation();
    assertEquals("<html>Class&lt;List&lt;String[]&gt;&gt; <b>value</b>()</html>", removeCurrentParameterColor(text));
  }

  private String annoParameterPresentation() {
    AnnotationParameterInfoHandler handler = new AnnotationParameterInfoHandler();
    CreateParameterInfoContext context = new MockCreateParameterInfoContext(getEditor(), getFile());
    PsiAnnotationParameterList list = handler.findElementForParameterInfo(context);
    assertNotNull(list);
    Object[] itemsToShow = context.getItemsToShow();
    assertNotNull(itemsToShow);
    assertEquals(1, itemsToShow.length);
    assertTrue(itemsToShow[0] instanceof PsiAnnotationMethod);
    PsiAnnotationMethod method = (PsiAnnotationMethod)itemsToShow[0];
    ParameterInfoUIContextEx parameterContext = ParameterInfoComponent.createContext(itemsToShow, getEditor(), handler, -1);
    return AnnotationParameterInfoHandler.updateUIText(method, parameterContext);
  }

  public void testParameterAnnotation() {
    myFixture.addClass("import java.lang.annotation.*;\n@Documented @Target({ElementType.PARAMETER}) @interface TA { }");
    myFixture.configureByText("a.java", "class C {\n void m(@TA String s) { }\n void t() { m(<caret>\"test\"); }\n}");
    assertEquals(removeAnnotationsIfDumb("<html>@TA String s</html>"), parameterPresentation(-1));
  }

  public void testParameterUndocumentedAnnotation() {
    myFixture.addClass("import java.lang.annotation.*;\n@Target({ElementType.PARAMETER}) @interface TA { }");
    myFixture.configureByText("a.java", "class C {\n void m(@TA String s) { }\n void t() { m(<caret>\"test\"); }\n}");
    assertEquals("<html>String s</html>", parameterPresentation(-1));
  }

  public void testParameterTypeAnnotation() {
    myFixture.addClass("import java.lang.annotation.*;\n@Documented @Target({ElementType.PARAMETER, ElementType.TYPE_USE}) @interface TA { }");
    myFixture.configureByText("a.java", "class C {\n void m(@TA String s) { }\n void t() { m(<caret>\"test\"); }\n}");
    assertEquals(removeAnnotationsIfDumb("<html>@TA String s</html>"), parameterPresentation(-1));
  }

  @NeedsIndex.ForStandardLibrary
  public void testInferredParametersInNestedCallsNoOverloads() {
    myFixture.configureByText("a.java", """
      import java.util.function.Consumer;
      class A {
              interface Context<T> {
              }
              static <A> Context<A> withProvider(Consumer<A> consumer) {
                  return null;
              }
              static <T> Context<T> withContext(Class<T> clazz, Context<T> context) {
                  return null;
              }
              public static void testInference() {
                  withContext(String.class, withProvider(<caret>));
              }
      }""");
    assertEquals("<html>Consumer&lt;String&gt; consumer</html>", parameterPresentation(-1));
  }

  public void testParameterUndocumentedTypeAnnotation() {
    myFixture.addClass("import java.lang.annotation.*;\n@Target({ElementType.PARAMETER, ElementType.TYPE_USE}) @interface TA { }");
    myFixture.configureByText("a.java", "class C {\n void m(@TA String s) { }\n void t() { m(<caret>\"test\"); }\n}");
    assertEquals(removeAnnotationsIfDumb("<html>@TA String s</html>"), parameterPresentation(-1));
  }

  public void testHighlightMethodJustChosenInCompletion() {
    myFixture.configureByText("a.java", "class Foo {" +
                                        "{ bar<caret> }" +
                                        "void bar(boolean a);" +
                                        "void bar(String a);" +
                                        "void bar(int a);" +
                                        "void bar2(int a);" +
                                        "}");
    LookupElement[] elements = myFixture.completeBasic();
    assertEquals("(String a)", LookupElementPresentation.renderElement(elements[1]).getTailText());
    myFixture.getLookup().setCurrentItem(elements[1]);
    myFixture.type('\n');

    assertEquals("<html>boolean a</html>", parameterPresentation(0, -1));
    assertEquals("<html>String a</html>", parameterPresentation(1, -1));
    assertEquals("<html>int a</html>", parameterPresentation(2, -1));

    checkHighlighted(1);
  }

  public void testHighlightConstructorJustChosenInCompletion() {
    Registry.get("java.completion.show.constructors").setValue(true);
    Disposer.register(myFixture.getTestRootDisposable(), () -> Registry.get("java.completion.show.constructors").setValue(false));

    myFixture.addClass("class Bar {" +
                       "Bar(boolean a);" +
                       "Bar(String a);" +
                       "Bar(int a);" +
                       "} " +
                       "class Bar2 {}");
    myFixture.configureByText("a.java", "class Foo {{ new Bar<caret> }}");
    LookupElement[] elements = myFixture.completeBasic();
    assertEquals("(boolean a) default package", LookupElementPresentation.renderElement(elements[2]).getTailText());
    myFixture.getLookup().setCurrentItem(elements[2]);
    myFixture.type('\n');
    myFixture.checkResult("class Foo {{ new Bar(<caret>) }}");

    assertEquals("<html>boolean a</html>", parameterPresentation(0, -1));
    assertEquals("<html>String a</html>", parameterPresentation(1, -1));
    assertEquals("<html>int a</html>", parameterPresentation(2, -1));

    checkHighlighted(0);
  }

  @NeedsIndex.ForStandardLibrary
  public void testNoStrikeoutForSingleDeprecatedMethod() {
    myFixture.configureByText(JavaFileType.INSTANCE, "class C { void m() { System.runFinalizersOnExit(true<caret>); } }");
    assertEquals("<html>boolean b</html>", parameterPresentation(-1));
  }

  @NeedsIndex.ForStandardLibrary
  public void testInferredWithVarargs() {
    @Language("JAVA")
    String text =
      "import java.util.*;" +
      "class C { " +
      "  static <T> boolean addAll(Collection<? super T> c, T... elements) {" +
      "    return false;" +
      "  }" +
      "  static void m(Object objects[], List<Object> list) { " +
      "    addAll(/*caret*/list, objects);" +
      "  } " +
      "}";

    myFixture.configureByText(JavaFileType.INSTANCE, text.replace("/*caret*/", "<caret>"));
    assertEmpty(myFixture.doHighlighting(HighlightSeverity.ERROR));
    assertEquals("<html>Collection&lt;? super Object&gt; c, Object... elements</html>", parameterPresentation(-1));
  }

  private void checkHighlighted(int lineIndex) {
    MethodParameterInfoHandler handler = new MethodParameterInfoHandler();
    CreateParameterInfoContext context = createContext();
    PsiExpressionList list = handler.findElementForParameterInfo(context);
    Object[] itemsToShow = context.getItemsToShow();
    assertEquals(itemsToShow[lineIndex], updateParameterInfo(handler, list, itemsToShow).getHighlightedParameter());
  }

  @NeedsIndex.ForStandardLibrary
  public void testTypeInvalidationByCompletion() {
    myFixture.configureByFile(getTestName(false) + ".java");

    MethodParameterInfoHandler handler = new MethodParameterInfoHandler();
    CreateParameterInfoContext context = new MockCreateParameterInfoContext(getEditor(), getFile());
    PsiExpressionList argList = handler.findElementForParameterInfo(context);
    assertNotNull(argList);
    Object[] items = context.getItemsToShow();
    assertSize(2, items);
    updateParameterInfo(handler, argList, items);

    myFixture.completeBasic();
    myFixture.type('\n');

    assertTrue(argList.isValid());
    // items now contain references to invalid PSI
    updateParameterInfo(handler, argList, items);
    assertSize(2, context.getItemsToShow());

    myFixture.checkResultByFile(getTestName(false) + "_after.java");
  }

  public void testHighlightCurrentParameterAfterTypingFirstArgumentOfThree() {
    configureJava("""
                    class A {
                        void foo() {}
                        void foo(int a, int b, int c) {}
                        {
                            foo(<caret>)
                        }
                    }""");
    showParameterInfo();
    checkHintContents("""
                        [<html>&lt;no parameters&gt;</html>]
                        -
                        <html><b>int a</b>, int b, int c</html>""");
    type("1, ");
    waitForAllAsyncStuff();
    checkHintContents("""
                        <html><font color=a8a8a8>&lt;no parameters&gt;</font></html>
                        -
                        <html>int a, <b>int b</b>, int c</html>""");
  }

  private String removeAnnotationsIfDumb(String s) {
    return DumbService.isDumb(getProject()) ? s.replaceAll("@\\p{Alnum}* ", "") : s;
  }

  @NeedsIndex.ForStandardLibrary
  public void testOverloadIsChangedAfterCompletion() {
    configureJava("class C { void m() { System.out.pr<caret> } }");
    complete("print(int i)");
    type("'a");
    checkResult("class C { void m() { System.out.print('a<caret>'); } }");
    showParameterInfo();
    checkHintContents(removeAnnotationsIfDumb(
      """
        <html><b>boolean b</b></html>
        -
        [<html><b>char c</b></html>]
        -
        <html><b>int i</b></html>
        -
        <html><b>long l</b></html>
        -
        <html><b>float v</b></html>
        -
        <html><b>double v</b></html>
        -
        <html><b>@NotNull char[] chars</b></html>
        -
        <html><b>@Nullable String s</b></html>
        -
        <html><b>@Nullable Object o</b></html>"""
    ));
  }

  @NeedsIndex.ForStandardLibrary
  public void testParameterInfoIsAvailableAtMethodName() {
    configureJava("class C { void m() { System.ex<caret>it(0); } }");
    showParameterInfo();
    checkHintContents("<html>int i</html>");
  }

  public void testVarargWithArrayArgument() {
    configureJava("""
                    class C {
                      void some(int a) {}
                      void some(String... b) {}
                      void m(String[] c) {
                        some(c<caret>);
                      }
                    }""");
    showParameterInfo();
    checkHintContents("""
                        <html><b>int a</b></html>
                        -
                        [<html><b>String... b</b></html>]""");
  }

  public void testDoNotShowUnrelatedInfoOnTyping() {
    configureJava("class C { void m(String a, String b) { String s = <caret>a + b).trim(); } }");
    type('(');
    waitForAllAsyncStuff();
    checkHintContents(null);
  }

  @NeedsIndex.SmartMode(reason = "MethodParameterInfoHandler.appendModifierList doesn't work in dumb mode")
  public void testInferredAnnotation() {
    DataFlowInspectionTestCase.setupTypeUseAnnotations("tu", myFixture);
    NullableNotNullManager nnnManager = NullableNotNullManager.getInstance(getProject());
    String defaultNotNull = nnnManager.getDefaultNotNull();
    nnnManager.setDefaultNotNull("tu.NotNull");
    try {
      configureJava("""
                      class X {
                          public static void main(String[] args) {
                              System.out.println(getSomething(<caret>));
                          }
                                          
                          public static String getSomething(Something l) {
                              return l.toString();
                          }
                      }
                      """);
      showParameterInfo();
      checkHintContents("<html><b>@NotNull Something l</b></html>");
    }
    finally {
      nnnManager.setDefaultNotNull(defaultNotNull);
    }
  }

  @NeedsIndex.SmartMode(reason = "MethodParameterInfoHandler.appendModifierList doesn't work in dumb mode")
  public void testQualifierTypeUse() {
    configureJava("""
                    import java.lang.annotation.*;
                    import java.util.Map;
                    class X {
                        void foo(@NotNull Map.Entry p, @NotNull Map m, Map.Entry p1, Map.@NotNull Entry p2) {
                    
                            foo(<caret>);
                        }
                    }
                    
                    @Documented
                    @Target({ElementType.TYPE_USE, ElementType.PARAMETER})
                    @interface NotNull {}
                    """);
    showParameterInfo();
    checkHintContents("<html><b>@NotNull Map.Entry p</b>, @NotNull Map m, Entry p1, @NotNull Entry p2</html>");
  }

  public void testCustomHandlerHighlighterWithEscaping() {
    myFixture.configureByText(PlainTextFileType.INSTANCE, " ");

    class CustomHandler implements ParameterInfoHandler<PsiElement, Object>, DumbAware {

      @NotNull
      @Override
      public PsiElement findElementForParameterInfo(@NotNull CreateParameterInfoContext context) {
        context.setItemsToShow(new Object[]{this});
        return context.getFile();
      }

      @Override
      public void showParameterInfo(@NotNull PsiElement element, @NotNull CreateParameterInfoContext context) {
        context.showHint(context.getFile(), context.getOffset(), this);
      }

      @NotNull
      @Override
      public PsiElement findElementForUpdatingParameterInfo(@NotNull UpdateParameterInfoContext context) {
        return context.getFile();
      }

      @Override
      public void updateParameterInfo(@NotNull PsiElement o, @NotNull UpdateParameterInfoContext context) {}

      @Override
      public void updateUI(Object p, @NotNull ParameterInfoUIContext context) {
        context.setupUIComponentPresentation("<ABC>, DEF", 7, 10, true, true, false, context.getDefaultParameterColor());
      }
    }

    LanguageParameterInfo.INSTANCE.addExplicitExtension(PlainTextLanguage.INSTANCE, new CustomHandler(), getTestRootDisposable());
    showParameterInfo();
    checkHintContents("<html><strike>&lt;ABC&gt;, <b>DEF</b></strike></html>");
  }

  @Override
  protected void runTestRunnable(@NotNull ThrowableRunnable<Throwable> testRunnable) throws Throwable {
    if (getIndexingMode() != IndexingMode.SMART) {
      ((DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(getProject())).mustWaitForSmartMode(false, getTestRootDisposable());
      FileBasedIndex.getInstance().ignoreDumbMode(DumbModeAccessType.RELIABLE_DATA_ONLY, () -> {
        super.runTestRunnable(testRunnable);
        return null;
      });
    } else {
      super.runTestRunnable(testRunnable);
    }
  }

}