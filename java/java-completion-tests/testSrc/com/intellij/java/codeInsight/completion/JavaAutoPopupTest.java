// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.completion;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionContributorEP;
import com.intellij.codeInsight.completion.CompletionInitializationContext;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionPhase;
import com.intellij.codeInsight.completion.CompletionProgressIndicator;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.JavaCompletionAutoPopupFixture;
import com.intellij.codeInsight.completion.impl.CompletionServiceImpl;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.codeInsight.lookup.LookupFocusDegree;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.codeInsight.template.JavaCodeContextType;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.impl.LiveTemplateCompletionContributor;
import com.intellij.codeInsight.template.impl.LiveTemplateDocumentationProvider;
import com.intellij.codeInsight.template.impl.LiveTemplateLookupElement;
import com.intellij.codeInsight.template.impl.TemplateContextTypes;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.ide.DataManager;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.impl.UndoManagerImpl;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.actionSystem.TypedAction;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.extensions.DefaultPluginDescriptor;
import com.intellij.openapi.extensions.LoadingOrder;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.impl.CurrentEditorProvider;
import com.intellij.openapi.options.advanced.AdvancedSettings;
import com.intellij.openapi.options.advanced.AdvancedSettingsImpl;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.NavigatablePsiElement;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.statistics.StatisticsManager;
import com.intellij.psi.statistics.impl.StatisticsManagerImpl;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LoggedErrorProcessor;
import com.intellij.testFramework.NeedsIndex;
import com.intellij.testFramework.common.ThreadUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestUtil;
import com.intellij.testFramework.fixtures.CompletionAutoPopupTester;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

import static com.intellij.codeInsight.completion.JavaCompletionAutoPopupFixtureKt.javaCompletionAutoPopupFixture;
import static com.intellij.testFramework.EdtTestUtil.runInEdtAndWait;
import static com.intellij.testFramework.UsefulTestCase.assertSize;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@NeedsIndex.SmartMode(reason = "AutoPopup shouldn't work in dumb mode")
@TestApplication
public class JavaAutoPopupTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<JavaCompletionAutoPopupFixture> autoPopupFixture = javaCompletionAutoPopupFixture(projectFixture);
  private LightJavaCodeInsightTestFixture myFixture;
  private CompletionAutoPopupTester myTester;

  private void type(String s) {
    autoPopupFixture.get().type(s);
  }

  private LookupImpl getLookup() {
    return autoPopupFixture.get().getLookup();
  }

  @Test
  public void testNewItemsOnLongerPrefix() {
    myFixture.configureByText("a.java", """
      class Foo {
        void foo(String iterable) {
          <caret>
        }
      }""");
    type("i");
    List<String> les = myFixture.getLookupElementStrings();
    assertTrue(les.contains("iterable"));
    assertTrue(les.contains("if"));
    assertTrue(les.contains("int"));

    type("t");
    assertContains("iterable");
    assertEquals("iterable", getLookup().getCurrentItem().getLookupString());

    type("er");
    les = myFixture.getLookupElementStrings();
    assertTrue(les.contains("iterable"));
    assertTrue(les.contains("iter"));
    assertEquals("iter", getLookup().getCurrentItem().getLookupString());
    assertTrue(getLookup().isFocused());

    type("a");
    assertTrue(getLookup().isFocused());
  }

  @Test
  public void testAfterDblColon() {
    myFixture.configureByText("a.java", """
      class Foo {
        void foo() {
          Runnable::<caret>
        }
      }""");
    type("r");
    List<String> les = myFixture.getLookupElementStrings();
    assertTrue(les.contains("run"));
    assertTrue(getLookup().isFocused());
  }

  public void assertContains(String... items) {
    assertTrue(myFixture.getLookupElementStrings().containsAll(Arrays.asList(items)));
  }

  @Test
  public void testRecalculateItemsOnBackspace() {
    myFixture.configureByText("a.java", """
      class Foo {
        void foo(String iterable) {
          int itaa;
          ite<caret>
        }
      }""");
    type("r");
    myFixture.assertPreferredCompletionItems(0, "iter", "iterable");

    type("\b");
    assertContains("iterable");

    type("\b");
    assertContains("itaa", "iterable");
    type("a");
    assertContains("itaa");
    type("\b");
    assertContains("itaa", "iterable");
    type("e");
    assertContains("iterable");

    type("r");
    myFixture.assertPreferredCompletionItems(0, "iter", "iterable");
  }

  @Test
  public void testExplicitSelectionShouldSurvive() {
    myFixture.configureByText("a.java", """
      class Foo {
        void foo(String iterable) {
          int iterable2;
          it<caret>
        }
      }""");
    type("e");
    assertContains("iterable", "iterable2");

    assertEquals("iterable", getLookup().getCurrentItem().getLookupString());
    runInEdtAndWait(() -> myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN));
    assertEquals("iterable2", getLookup().getCurrentItem().getLookupString());

    type("r");
    assertEquals("iter", getLookup().getItems().getFirst().getLookupString());
    assertEquals("iterable2", getLookup().getCurrentItem().getLookupString());
  }

  @Test
  public void testExplicitMouseSelectionShouldSurvive() {
    myFixture.configureByText("a.java", """
      class Foo {
        void foo(String iterable) {
          int iterable2;
          it<caret>
        }
      }""");
    type("e");
    assertContains("iterable", "iterable2");

    assertEquals("iterable", getLookup().getCurrentItem().getLookupString());
    runInEdtAndWait(() -> setCurrentItem(getLookup(), getLookup().getItems().get(1)));
    assertEquals("iterable2", getLookup().getCurrentItem().getLookupString());

    type("r");
    myFixture.assertPreferredCompletionItems(2, "iter", "iterable", "iterable2");
  }

  @Test
  public void testGenerallyFocusLookupInJavaMethod() {
    myFixture.configureByText("a.java", """
      class Foo {
        String foo(String iterable) {
          return it<caret>;
        }
      }""");
    type("e");
    assertTrue(getLookup().isFocused());
  }

  @Test
  public void testNoStupidNameSuggestions() {
    myFixture.configureByText("a.java", """
      class Foo {
        String foo(String <caret>) {
        }
      }""");
    type("x");
    assertNull(myFixture.getLookupElementStrings());
  }

  @Test
  public void testExplicitSelectionShouldBeHonoredFocused() {
    myFixture.configureByText("a.java", """
      class Foo {
        String foo() {
          int abcd;
          int abce;
          a<caret>
        }
      }""");
    type("b");
    assertTrue(getLookup().isFocused());
    type("c");

    assertContains("abcd", "abce");
    assertEquals("abcd", getLookup().getCurrentItem().getLookupString());
    runInEdtAndWait(() -> setCurrentItem(getLookup(), getLookup().getItems().get(1)));
    assertEquals("abce", getLookup().getCurrentItem().getLookupString());

    type("\t");
    myFixture.checkResult("""
                               class Foo {
                                 String foo() {
                                   int abcd;
                                   int abce;
                                   abce<caret>
                                 }
                               }""");
  }

  @Test
  public void test_popup_in_javadoc_reference() {
    myFixture.configureByText("a.java", """
      /**
       * {@link AIO<caret>}
       */
      class Foo {}""");
    type("O");
    assertNotNull(getLookup());
  }

  @Test
  public void test_popup_after_hash_in_javadoc() {
    myFixture.configureByText("a.java", """
      /**
       * {@link String<caret>}
       */
      class Foo {}""");
    type("#");
    assertNotNull(getLookup());
  }

  @Test
  public void test_popup_in_javadoc_local_reference() {
    myFixture.configureByText("a.java", """
      /**
       * {@link #<caret>}
       */
      class Foo {
        void foo() {}
      }""");
    type("f");
    assertNotNull(getLookup());
  }

  @Test
  public void test_autopopup_in_javadoc_tag_name() {
    myFixture.configureByText("a.java", """
      /**
       * @a<caret>
       */
      class Foo {}""");
    type("u");
    assertNotNull(getLookup());
  }

  @Test
  public void test_no_autopopup_in_javadoc_parameter_descriptions() {
    myFixture.configureByText("a.java", """
      class Foo {
        /**
         * @param o some sentence
         */
        void foo(Object o) {}
      
        /**
         * @param o s<caret>
         */
        void foo2(Object o) {}
      }""");
    type("o");
    assertNull(getLookup());
  }

  @Test
  public void test_autopopup_in_javadoc_parameter_name() {
    myFixture.configureByText("a.java", """
      class Foo {
        /**
         * @param <caret>
         */
        void foo2(Object oooooooo) {}
      }""");
    type("o");
    assertNotNull(getLookup());
  }

  @Test
  public void test_no_autopopup_at_the_end_of_line_comment() {
    LiveTemplateCompletionContributor.setShowTemplatesInTests(true, myFixture.getTestRootDisposable());
    myFixture.configureByText("a.java", """
      class Foo {
        // foo bar <caret>
      }""");
    type("t");
    assertNull(getLookup());
  }

  @Test
  public void testPrefixLengthDependentSorting() {
    myFixture.addClass("package foo; public class PsiJavaCodeReferenceElement {}");
    myFixture.configureByText("a.java", """
      import foo.PsiJavaCodeReferenceElement

      class PsiJavaCodeReferenceElementImpl
        { <caret>
      """);
    type("PJCR");
    assertContains("PsiJavaCodeReferenceElement", "PsiJavaCodeReferenceElementImpl");
  }

  @Test
  public void testQuickSelectAfterReuse() {
    myFixture.configureByText("a.java", """
      class A { Iterable<?> iterable;
        { <caret> }
      }""");
    type("ite");
    runInEdtAndWait(() -> {
      myFixture.type("r");
      getLookup().markReused();
      getLookup().setCurrentItem(getLookup().getItems().getFirst());
      CommandProcessor.getInstance().executeCommand(myFixture.getProject(), () -> getLookup().finishLookup(Lookup.NORMAL_SELECT_CHAR), null, null);
    });
    myFixture.checkResult("""
                           class A { Iterable<?> iterable;
                             {
                                 for (Object o : iterable) {
                                    \s
                                 }
                             }
                           }""");
  }

  @Test
  public void testQuickSelectAfterReuseAndBackspace() {
    myFixture.configureByText("a.java", """
        class A { Iterable<?> iterable;
          { <caret> }
        }""");
    type("ite");
    runInEdtAndWait(() -> {
      myFixture.type("r");
      getLookup().markReused();
      myFixture.type("\b\b");
      getLookup().setCurrentItem(getLookup().getItems().getFirst());
      CommandProcessor.getInstance()
        .executeCommand(myFixture.getProject(), () -> getLookup().finishLookup(Lookup.NORMAL_SELECT_CHAR), null, null);
    });
    myFixture.checkResult("""
                             class A { Iterable<?> iterable;
                               {
                                   for (Object o : iterable) {
                                      \s
                                   }
                               }
                             }""");
  }

  @Test
  public void testQuickSelectLiveTemplate() {
    myFixture.configureByText("a.java", """
        class A {
          { <caret> }
        }""");
    type("th");
    runInEdtAndWait(() -> myFixture.type("r\t"));
    myFixture.checkResult("""
                             class A {
                               { throw new <caret> }
                             }""");
  }

  @Test
  public void testTwoQuickRestartsAfterHiding() {
    for (int i = 0; i <= 10; i++) {
      myFixture.configureByText("a" + i + ".java", """
          class A {
            { <caret> }
          }""");
      runInEdtAndWait(() -> myFixture.type("A"));
      joinAutopopup();// completion started
      final Ref<Boolean> tooQuick = Ref.create(false);
      runInEdtAndWait(() -> {
        tooQuick.set(getLookup() == null);
        myFixture.type("IO");
      });
      joinAutopopup();//I
      joinAutopopup();//O
      joinCompletion();
      assertNotNull(getLookup());
      assertTrue(myFixture.getLookupElementStrings().contains("ArrayIndexOutOfBoundsException"));
      if (!tooQuick.get()) {
        return;
      }

      runInEdtAndWait(() -> {
        LookupManager.getInstance(myFixture.getProject()).hideActiveLookup();
        CompletionProgressIndicator.cleanupForNextTest();
      });
    }

    fail("too many too quick attempts");
  }

  private void joinSomething(int degree) {
    if (degree == 0) return;
    joinCommit();
    if (degree == 1) return;
    joinCommit();
    if (degree == 2) return;
    runInEdtAndWait(() -> {});
    if (degree == 3) return;
    joinCompletion();
  }

  public void joinCompletion() {
    myTester.joinCompletion();
  }

  protected void joinCommit(Runnable c1) {
    myTester.joinCommit(c1);
  }

  protected void joinCommit() {
    joinCommit(() -> {});
  }

  protected void joinAutopopup() {
    myTester.joinAutopopup();
  }

  private static <Value extends LookupElement> Value setCurrentItem(LookupImpl propOwner, Value item) {
    propOwner.setCurrentItem(item);
    return item;
  }

  @Test
  public void testTypingDuringExplicitCompletion() {
    myFixture.configureByText("a.java", """
          class A {
            static { Runnable r = new <caret> }
          }""");
    myFixture.complete(CompletionType.SMART);
    runInEdtAndWait(() -> myFixture.type("Thr"));
    joinCompletion();
    assertNotNull(getLookup());
    assertTrue(myFixture.getLookupElementStrings().contains("Thread"));
  }

  @Test
  public void testDotAfterVariable() {
    myFixture.configureByText("a.java", """
          class A {
            { Object ooo; <caret> }
          }""");
    type("o.");
    assertTrue(myFixture.getEditor().getDocument().getText().contains("ooo."));
    assertNotNull(getLookup());
  }

  @Test
  public void testDotAfterCall() {
    myFixture.configureByText("a.java", """
        class A {
          { <caret> }
        }""");
    type("tos.");
    assertTrue(myFixture.getEditor().getDocument().getText().contains("toString()."));
    assertNotNull(getLookup());
  }

  @Test
  public void testDotAfterClassName() {
    myFixture.configureByText("a.java", """
      class A {
        { <caret> }
      }""");
    type("AIOO.");
    assertTrue(myFixture.getEditor().getDocument().getText().contains("ArrayIndexOutOfBoundsException."));
    assertNotNull(getLookup());
  }

  @Test
  public void testDotAfterClassNameInParameter() {
    myFixture.configureByText("a.java", """
      class A {
        void foo(<caret>) {}
      }""");
    type("AIOO...");
    assertTrue(myFixture.getEditor().getDocument().getText().contains("ArrayIndexOutOfBoundsException..."));
    assertNull(getLookup());
  }

  public void doTestArrows(final String toType, final LookupFocusDegree focusDegree, int indexDown, int indexUp) {
    BiConsumer<String, Integer> checkArrow = (action, expectedIndex) -> {
      myFixture.configureByText("a.java", """
          class A {
            void foo() {}
            void farObject() {}
            void fzrObject() {}
            { <caret> }
          }""");

      type(toType);
      assertNotNull(getLookup());
      getLookup().setLookupFocusDegree(focusDegree);

      runInEdtAndWait(() -> myFixture.performEditorAction(action));
      if (getLookup() != null) {
        assertTrue(getLookup().isFocused());
        assertTrue(expectedIndex >= 0);
        assertEquals(getLookup().getItems().get(expectedIndex), getLookup().getCurrentItem());
        runInEdtAndWait(() -> getLookup().hide());
      }
      else {
        assertEquals(-1, (int)expectedIndex);
      }

      type("\b");
    };

    checkArrow.accept(IdeActions.ACTION_EDITOR_MOVE_CARET_UP, indexUp);
    checkArrow.accept(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN, indexDown);
  }

  @Test
  public void test_vertical_arrows_in_non_focused_lookup() {
    String toType = "ArrayIndexOutOfBoundsException ind";
    doTestArrows(toType, LookupFocusDegree.UNFOCUSED, 0, 2);

    ((AdvancedSettingsImpl)AdvancedSettings.getInstance()).setSetting("ide.cycle.scrolling", false, myFixture.getTestRootDisposable());
    doTestArrows(toType, LookupFocusDegree.UNFOCUSED, 0, -1);
  }

  @Test
  public void test_vertical_arrows_in_semi_focused_lookup() {
    CodeInsightSettings.getInstance().setSelectAutopopupSuggestionsByChars(false);
    UISettings.getInstance().setSortLookupElementsLexicographically(true);

    String toType = "fo";
    doTestArrows(toType, LookupFocusDegree.SEMI_FOCUSED, 2, 0);

    ((AdvancedSettingsImpl)AdvancedSettings.getInstance()).setSetting("ide.cycle.scrolling", false, myFixture.getTestRootDisposable());
    doTestArrows(toType, LookupFocusDegree.SEMI_FOCUSED, 2, 0);
  }

  @Test
  public void testHideOnOnePrefixVariant() {
    myFixture.configureByText("a.java", """
      class A {
        Object foo() { return nu<caret> }
      }""");
    type("ll");
    assertNull(getLookup());
  }

  @Test
  public void testResumeAfterBackspace() {
    myFixture.configureByText("a.java", """
      class A {
        Object foo() { this<caret> }
      }""");
    type(".");
    assertNotNull(getLookup());
    type("x");
    assertNull(getLookup());
    type("\b");
    assertNull(getLookup());
    type("c");
    assertNotNull(getLookup());
  }

  @Test
  public void testHideOnInvalidSymbolAfterBackspace() {
    myFixture.configureByText("a.java", """
      class A {
        Object foo() { this<caret> }
      }""");
    type(".");
    assertNotNull(getLookup());
    type("c");
    assertNotNull(getLookup());
    type("\b");
    assertNotNull(getLookup());
    type("x");
    assertNull(getLookup());
  }

  @Test
  public void testDoubleLiteralInField() {
    myFixture.configureByText("a.java", """
      public interface Test {
        double FULL = 1.0<caret>
      }""");
    type("d");
    assertNull(getLookup());
  }

  @Test
  public void testCancellingDuringCalculation() {
    myFixture.configureByText("a.java", """
      class Aaaaaaa {}
      public interface Test {
        <caret>
      }""");
    runInEdtAndWait(() -> myFixture.type("A"));
    joinAutopopup();
    final LookupImpl first = getLookup();
    assertNotNull(first);
    runInEdtAndWait(() -> {
      assertEquals(first, getLookup());
      getLookup().hide();
      myFixture.type("a");
    });
    joinAutopopup();
    joinAutopopup();
    joinAutopopup();
    assertNotEquals(getLookup(), first);
  }

  @Test
  public void testDuringCompletionMustFinish() throws InterruptedException {
    registerContributor(LongReplacementOffsetContributor.class);

    runInEdtAndWait(() -> myFixture.addFileToProject("directory/foo.txt", ""));
    myFixture.configureByText("a.java", "public interface Test { RuntiExce<caret>xxx }");
    myFixture.completeBasic();
    while (getLookup().getItems().isEmpty()) {
      Thread.sleep(10);
      runInEdtAndWait(() -> getLookup().refreshUi(false, false));
    }

    runInEdtAndWait(() -> myFixture.type("\t"));
    myFixture.checkResult("public interface Test { RuntimeException<caret>x }");
  }

  private void registerContributor(final Class<?> contributor, LoadingOrder order) {
    registerCompletionContributor(contributor, myFixture.getTestRootDisposable(), order);
  }

  private void registerContributor(final Class<?> contributor) {
    registerContributor(contributor, LoadingOrder.LAST);
  }

  public static void registerCompletionContributor(Class<?> contributor, Disposable parentDisposable, LoadingOrder order) {
    DefaultPluginDescriptor pluginDescriptor = new DefaultPluginDescriptor("registerCompletionContributor");
    CompletionContributorEP extension = new CompletionContributorEP("any", contributor.getName(), pluginDescriptor);
    CompletionContributor.EP.getPoint().registerExtension(extension, order, parentDisposable);
  }

  @Test
  public void testLeftRightMovements() {
    myFixture.configureByText("a.java", """
      class Foo {
        void foo(String iterable) {
          <caret>ter   x
        }
      }""");
    type("i");
    int offset = getCaretOffset();
    assertContains("iterable", "if", "int");

    runInEdtAndWait(() -> myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT));
    assertEquals(getCaretOffset(), offset + 1);
    joinAutopopup();
    joinCompletion();
    assertFalse(getLookup().isCalculating());
    assertContains("iterable");
    assertEquals("iterable", getLookup().getCurrentItem().getLookupString());

    runInEdtAndWait(() -> myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_CARET_LEFT));
    assertEquals(getCaretOffset(), offset);
    joinAutopopup();
    joinCompletion();
    assertFalse(getLookup().isCalculating());
    assertContains("iterable", "if", "int");
    assertEquals("iterable", getLookup().getCurrentItem().getLookupString());

    runInEdtAndWait(() -> myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_CARET_LEFT));
    joinAutopopup();
    joinCompletion();
    assertFalse(getLookup().isCalculating());
    assertTrue(getLookup().getItems().size() > 3);

    for (int i = 0; i <= "iter".length(); i++) {
      runInEdtAndWait(() -> myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT));
    }

    assertNull(getLookup());
  }

  private int getCaretOffset() {
    final JavaCodeInsightTestFixture fixture = myFixture;
    return ReadAction.compute(() -> fixture.getEditor().getCaretModel().getOffset());
  }

  @Test
  public void testMulticaretLeftRightMovements() {
    myFixture.configureByText("a.java", """
      class Foo {
        void foo(String iterable) {
          <caret>ter   x
          <caret>ter   x
        }
      }""");
    type("i");
    assertNotNull(getLookup());

    runInEdtAndWait(() -> myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT));
    myFixture.checkResult("""
                               class Foo {
                                 void foo(String iterable) {
                                   it<caret>er   x
                                   it<caret>er   x
                                 }
                               }""");
    joinAutopopup();
    joinCompletion();
    assertNotNull(getLookup());
    assertFalse(getLookup().isCalculating());

    runInEdtAndWait(() -> myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_CARET_LEFT));
    myFixture.checkResult("""
                               class Foo {
                                 void foo(String iterable) {
                                   i<caret>ter   x
                                   i<caret>ter   x
                                 }
                               }""");
    joinAutopopup();
    joinCompletion();
    assertNotNull(getLookup());
    assertFalse(getLookup().isCalculating());
  }

  @Test
  public void testMulticaretRightMovementWithOneCaretAtDocumentEnd() {
    myFixture.configureByText("a.java",
                              """
                                  class Foo {
                                    void foo(String iterable) {
                                      ter   x
                                    }
                                  }
                                  <caret>""");
    runInEdtAndWait(() -> {
      int primaryCaretOffset = myFixture.getEditor().getDocument().getText().indexOf("ter   x");
      myFixture.getEditor().getCaretModel().addCaret(myFixture.getEditor().offsetToVisualPosition(primaryCaretOffset));
    });

    type("i");
    assertNotNull(getLookup());

    runInEdtAndWait(() -> myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT));
    myFixture.checkResult(
      """
              class Foo {
                void foo(String iterable) {
                  it<caret>er   x
                }
              }
              i<caret>""");
  }

  @Test
  public void testTypingInAnotherEditor() {
    myFixture.configureByText("a.java", "");
    type("c");
    assertNotNull(getLookup());

    final Ref<Editor> another = Ref.create();
    final Runnable wca = () -> {
      WriteCommandAction.writeCommandAction(myFixture.getProject())
        .run(() -> TypedAction.getInstance().getHandler().execute(another.get(), 'x', DataManager.getInstance().getDataContext()));
    };

    try {
      runInEdtAndWait(() -> {
        assertFalse(getLookup().isCalculating());
        getLookup().hide();
        PsiFile file = myFixture.addFileToProject("b.java", "");
        another.set(EditorFactory.getInstance().createEditor(file.getViewProvider().getDocument(), myFixture.getProject()));
        wca.run();
        assertEquals("x", another.get().getDocument().getText());
      });
      joinAutopopup();
      joinCompletion();
      LookupImpl l1 = (LookupImpl)LookupManager.getActiveLookup(another.get());
      if (l1 != null) {
        ThreadUtil.printThreadDump();
        System.out.println(l1.getItems());
        System.out.println(l1.isCalculating());
        System.out.println(myFixture.getEditor());
        System.out.println(another.get());
        System.out.println(CompletionServiceImpl.getCompletionPhase());
        fail(String.valueOf(l1.getItems()));
      }

      type("l");
      assertNotNull(getLookup());
    }
    finally {
      runInEdtAndWait(() -> EditorFactory.getInstance().releaseEditor(another.get()));
    }
  }

  @Test
  public void testEmptyAutoPopupReleasesDisposedEditor() {
    // IJPL-249705: an EmptyAutoPopup phase must not keep referencing (and thus leak) its editor
    // once the editor is disposed.
    PsiFile file = myFixture.addFileToProject("b.java", "class B {}");
    Ref<Editor> editor = Ref.create();
    Ref<Editor> unrelated = Ref.create();
    try {
      runInEdtAndWait(() -> {
        editor.set(EditorFactory.getInstance().createEditor(file.getViewProvider().getDocument(), myFixture.getProject()));
        CompletionServiceImpl.setCompletionPhase(new CompletionPhase.EmptyAutoPopup(editor.get(), Collections.emptySet()));
        assertInstanceOf(CompletionPhase.EmptyAutoPopup.class, CompletionServiceImpl.getCompletionPhase());

        // releasing an unrelated editor must not reset the phase
        unrelated.set(EditorFactory.getInstance().createEditor(file.getViewProvider().getDocument(), myFixture.getProject()));
        EditorFactory.getInstance().releaseEditor(unrelated.get());
        unrelated.set(null);
        assertInstanceOf(CompletionPhase.EmptyAutoPopup.class, CompletionServiceImpl.getCompletionPhase());

        // releasing the editor backing the phase must reset it to NoCompletion, dropping the reference
        EditorFactory.getInstance().releaseEditor(editor.get());
        editor.set(null);
        assertSame(CompletionPhase.NoCompletion, CompletionServiceImpl.getCompletionPhase());
      });
    }
    finally {
      runInEdtAndWait(() -> {
        if (!editor.isNull()) EditorFactory.getInstance().releaseEditor(editor.get());
        if (!unrelated.isNull()) EditorFactory.getInstance().releaseEditor(unrelated.get());
        CompletionServiceImpl.setCompletionPhase(CompletionPhase.NoCompletion);
      });
    }
  }

  @Test
  public void testHidingLookupAfterExternalPhaseResetKeepsCompletionUsable() {
    // IJPL-192597: the phase can be moved away from a live indicator without closing it (handleEmptyLookup,
    // performance-testing commands, ...), which leaves the lookup on screen. Hiding it afterwards finishes that
    // orphaned completion: the platform reports the inconsistency, but must not reset a phase it doesn't own anymore.
    myFixture.configureByText("a.java", """
      class Foo {
        void foo(String iterable) {
          <caret>
        }
      }""");
    type("i");
    LookupImpl lookup = getLookup();
    assertNotNull(lookup);

    List<String> loggedErrors = new ArrayList<>();
    LoggedErrorProcessor.executeWith(new LoggedErrorProcessor() {
      @Override
      public @NotNull Set<Action> processError(@NotNull String category,
                                              @NotNull String message,
                                              String @NotNull [] details,
                                              @Nullable Throwable t) {
        loggedErrors.add(message);
        return Action.NONE;
      }
    }, () -> runInEdtAndWait(() -> {
      CompletionServiceImpl.setCompletionPhase(CompletionPhase.NoCompletion);
      lookup.hideLookup(false);
      assertTrue(lookup.isLookupDisposed());
      assertSame(CompletionPhase.NoCompletion, CompletionServiceImpl.getCompletionPhase());
    }));

    assertSize(1, loggedErrors);
    assertTrue(loggedErrors.getFirst().contains("null!=CompletionProgressIndicator"), loggedErrors.getFirst());

    // the completion machinery is still usable afterwards
    type("t");
    assertContains("iterable");
  }

  public static class LongReplacementOffsetContributor extends CompletionContributor {
    @Override
    public void duringCompletion(@NotNull CompletionInitializationContext cxt) {
      try {
        Thread.sleep(500);
      }
      catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
      ProgressManager.checkCanceled();
      cxt.setReplacementOffset(cxt.getReplacementOffset() - 1);
    }
  }

  public static class LongContributor extends CompletionContributor {
    @Override
    public void fillCompletionVariants(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
      ApplicationManager.getApplication().assertIsNonDispatchThread();
      result.runRemainingContributors(parameters, true);
      try {
        Thread.sleep(500);
      }
      catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }
  }

  @Test
  public void testExplicitCompletionOnEmptyAutopopup() {
    myFixture.configureByText("a.java", "<caret>");
    type("cccccc");
    myFixture.completeBasic();
    joinCompletion();
    assertNull(getLookup());
  }

  @Test
  public void testLookupWithTemplates() {
    myFixture.configureByText("a.java", "class Foo { psv<caret> }");
    type("m");
    assertEquals(List.of("psvm", "psvma"), myFixture.getLookupElementStrings());
  }

  @Test
  public void testTemplatesWithNonImportedClasses() {
    CodeInsightSettings.getInstance().setCompletionCaseSensitive(CodeInsightSettings.NONE);
    myFixture.addClass("package foo.bar; public class ToArray {}");
    try {
      myFixture.configureByText("a.java", "class Foo {{ foo(<caret>) }}");
      type("toar");
      assertNotNull(getLookup());
      assertTrue(myFixture.getLookupElementStrings().contains("toar"));
      assertTrue(myFixture.getLookupElementStrings().contains("ToArray"));
    }
    finally {
      CodeInsightSettings.getInstance().setCompletionCaseSensitive(CodeInsightSettings.FIRST_LETTER);
    }
  }

  @Test
  public void testTemplateSelectionBySpace() {
    myFixture.configureByText("a.java", """
      class Foo {
          int ITER = 2;
          int itea = 2;
      
          static {
              it<caret>
          }
      }""");
    type("er ");
    assertTrue(myFixture.getEditor().getDocument().getText().contains("for "));
  }

  @Test
  public void testNewClassParenthesis() {
    CodeInsightSettings.getInstance().setCompletionCaseSensitive(CodeInsightSettings.NONE);
    try{
      myFixture.configureByText("a.java", " class Foo { static { new <caret> } } ");
      type("aioo(");
      assertTrue(myFixture.getEditor().getDocument().getText().contains("new ArrayIndexOutOfBoundsException()"));
    }
    finally {
      CodeInsightSettings.getInstance().setCompletionCaseSensitive(CodeInsightSettings.FIRST_LETTER);
    }
  }

  @Test
  public void testUnknownMethodParenthesis() {
    myFixture.configureByText("a.java", " class Foo { { <caret> } } ");
    type("filinpstr(");
    assertTrue(myFixture.getEditor().getDocument().getText().contains("filinpstr()"));
  }

  @Test
  public void testNoAutopopupAfterSpace() {
    myFixture.configureByText("a.java", " class Foo { { int newa; <caret> } } ");
    runInEdtAndWait(() -> myFixture.type("new "));
    joinAutopopup();
    joinCompletion();
    assertNull(getLookup());
  }

  @Test
  public void testRestartAndTypingDuringCopyCommit() {
    registerContributor(JavaAutoPopupTest.LongReplacementOffsetContributor.class);

    myFixture.configureByText("a.java", " class Foo { { int newa; <caret> } } ");
    myFixture.type("n");
    joinAutopopup();
    myFixture.type("e");
    joinCommit();// original commit
    myFixture.type("w");
    joinAutopopup();
    joinCompletion();
    myFixture.type("\n");
    myFixture.checkResult(" class Foo { { int newa; new <caret> } } ");
    assertNull(getLookup());
  }

  @Test
  public void testAutoRestartAndTypingDuringCopyCommit() {
    registerContributor(JavaAutoPopupTest.LongReplacementOffsetContributor.class);

    myFixture.configureByText("a.java", " class Foo { { int iteraaa; <caret> } } ");
    type("ite");
    assertTrue(myFixture.getLookupElementStrings().contains("iter"));
    myFixture.type("r");
    joinCommit();
    myFixture.type("a");
    joinAutopopup();
    joinCompletion();
    myFixture.type("\n");
    myFixture.checkResult(" class Foo { { int iteraaa; iteraaa<caret> } } ");
    assertNull(getLookup());
  }

  @Test
  public void testChoosingItemDuringCopyCommit() {
    registerContributor(JavaAutoPopupTest.LongReplacementOffsetContributor.class);

    myFixture.configureByText("a.java", " class Foo { { int iteraaa; <caret> } } ");
    type("ite");
    assertTrue(myFixture.getLookupElementStrings().contains("iter"));
    assertTrue(myFixture.getLookupElementStrings().contains("iteraaa"));
    myFixture.type("r");
    joinCommit();
    myFixture.type("a.");
    myFixture.checkResult(" class Foo { { int iteraaa; iteraaa.<caret> } } ");
  }

  @Test
  public void testRestartWithInvisibleLookup() {
    registerContributor(JavaAutoPopupTest.LongReplacementOffsetContributor.class);

    myFixture.configureByText("a.java", " class Foo { { int abcdef; <caret> } } ");
    myFixture.type("a");
    joinAutopopup();
    assertNotNull(getLookup());
    runInEdtAndWait(() -> myFixture.type("bc"));
    joinAutopopup();
    joinAutopopup();
    joinCompletion();
    assertNotNull(getLookup());
    assertTrue(getLookup().isShown());
  }

  @Test
  public void testRestartWithVisibleLookup() throws InterruptedException {
    registerContributor(JavaAutoPopupTest.LongContributor.class, LoadingOrder.FIRST);

    myFixture.configureByText("a.java", " class Foo { static { int abcdef, abcdefg; ab<caret> } } ");
    myFixture.completeBasic();
    while (!getLookup().isShown()) {
      Thread.sleep(1);
    }

    LookupImpl l = getLookup();
    runInEdtAndWait(() -> {
      if (!getLookup().isCalculating()) {
        System.out.println("testRestartWithVisibleLookup couldn't be faster than LongContributor");
      }
      myFixture.type("c");
    });
    joinCommit(() -> myFixture.type("d"));
    joinAutopopup();
    joinCompletion();
    assertEquals(getLookup(), l);
    assertFalse(getLookup().isCalculating());
    assertTrue(getLookup().isShown());
  }

  @Test
  public void testEveryPossibleWayToTypeIf() {
    String src = "class Foo { { int ifa; <caret> } }";
    String result = "class Foo { { int ifa; if <caret> } }";
    String result2 = "class Foo { { int ifa; if ( <caret>)} }";
    int actions = 4;

    for (int a1 = 0; a1 <= actions; a1++) {
      for (int a2 = 0; a2 <= actions; a2++) {
        myFixture.configureByText(a1 + " " + a2 + " .java", src);
        myFixture.getEditor().getDocument().addDocumentListener(new DocumentListener() {
          @Override
          public void documentChanged(@NotNull DocumentEvent e) {
            if (e.getNewFragment().toString().contains("a")) {
              fail(e.toString());
            }
          }
        });
        myFixture.type("i");
        joinSomething(a1);
        myFixture.type("f");
        joinSomething(a2);
        myFixture.type(" ");

        joinAutopopup();
        joinCompletion();
        try {
          myFixture.checkResult(a1 == 4 || a2 == 4 ? result2 : result);
        }
        catch (Exception e) {
          throw new RuntimeException("actions: " + a1 + " " + a2, e);
        }

        assertNull(getLookup());
      }
    }


    for (int a1 = 0; a1 <= actions; a1++) {
      myFixture.configureByText(a1 + " if .java", src);
      runInEdtAndWait(() -> myFixture.type("if"));
      joinSomething(a1);
      myFixture.type(" ");
      joinAutopopup();
      joinCompletion();
      try {
        myFixture.checkResult(a1 == 4 ? result2 : result);
      }
      catch (Exception e) {
        throw new RuntimeException("actions: " + a1, e);
      }

      assertNull(getLookup());
    }


    for (int a1 = 0; a1 <= actions; a1++) {
      myFixture.configureByText(a1 + " if .java", src);
      myFixture.type("i");
      joinSomething(a1);

      runInEdtAndWait(() -> myFixture.type("f "));

      joinAutopopup();
      joinCompletion();
      try {
        myFixture.checkResult(a1 == 4 ? result2 : result);
      }
      catch (Exception e) {
        throw new RuntimeException("actions: " + a1, e);
      }

      assertNull(getLookup());
    }
  }

  @Test
  public void testNonFinishedParameterComma() {
    myFixture.configureByText("a.java", """
      class Foo {
        void foo(int aaa, int aaaaa) { }
        void bar(int aaa, int aaaaa) { foo(<caret>) }
      }""");
    type("a");
    type("a");
    type(",");
    assertTrue(myFixture.getEditor().getDocument().getText().contains("foo(aaa, )"));
  }

  @Test
  public void testFinishedParameterComma() {
    myFixture.configureByText("a.java", " class Foo { void foo(int aaa, int aaaaa) { foo(<caret>) } } ");
    type("aaa,");
    assertTrue(myFixture.getEditor().getDocument().getText().contains("foo(aaa,)"));
  }

  @Test
  public void testNonFinishedVariableEq() {
    myFixture.configureByText("a.java", " class Foo { void foo(int aaa, int aaaaa) { <caret> } } ");
    type("a=");
    assertTrue(myFixture.getEditor().getDocument().getText().contains("aaa = "));
  }

  @Test
  public void testFinishedVariableEq() {
    myFixture.configureByText("a.java", " class Foo { void foo(int aaa, int aaaaa) { <caret> } } ");
    type("aaa=");
    assertTrue(myFixture.getEditor().getDocument().getText().contains("aaa="));
  }

  @Test
  public void testCompletionWhenLiveTemplateAreNotSufficient() {
    TemplateManagerImpl.setTemplateTesting(myFixture.getTestRootDisposable());
    myFixture.configureByText("a.java", """
      class Foo {
          static {
              Iterable<String> l1 = null;
              Iterable<String> l2 = null;
              Object asdf = null;
              iter<caret>
          }
      }""");
    type("\t");
    assertEquals(List.of("l2", "l1"), myFixture.getLookupElementStrings());
    type("as");
    assertNotNull(getLookup());
    assertContains("asdf", "assert");
    type("\n.");
    assertNotNull(getLookup());
    assertTrue(myFixture.getLookupElementStrings().contains("hashCode"));
    assertTrue(myFixture.getEditor().getDocument().getText().contains("asdf."));
  }

  @Test
  public void testNoWordCompletionAutoPopup() {
    myFixture.configureByText("a.java", "class Bar { void foo() { \"f<caret>\" }}");
    type("o");
    assertNull(getLookup());
  }

  @Test
  public void testMethodNameRestart() {
    myFixture.configureByText("a.java", """
      public class Bar {
          private static List<java.io.File> getS<caret>
      }
      """);
    type("ta");
    assertNull(getLookup());
  }

  @Test
  public void testTargetElementInLookup() {
    myFixture.configureByText("a.java", """
      class Foo {
        void x__foo() {}
        void bar() {
          <caret>
        }
        void x__goo() {}
      }
      """);
    final PsiClass cls = ReadAction.compute(() -> ((PsiJavaFile)myFixture.getFile()).getClasses()[0]);

    PsiMethod[] methods = ReadAction.compute(() -> cls.getMethods());
    final PsiMethod foo = methods[0];
    final PsiMethod goo = methods[2];
    type("x");
    assertContains("x__foo", "x__goo");
    runInEdtAndWait(() -> {
      assertEquals(foo, TargetElementUtil.findTargetElement(myFixture.getEditor(), TargetElementUtil.LOOKUP_ITEM_ACCEPTED));
      myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN);
      assertEquals(goo, TargetElementUtil.findTargetElement(myFixture.getEditor(), TargetElementUtil.LOOKUP_ITEM_ACCEPTED));
    });

    type("_");
    myFixture.assertPreferredCompletionItems(1, "x__foo", "x__goo");
    runInEdtAndWait(() -> {
      assertEquals(goo, TargetElementUtil.findTargetElement(myFixture.getEditor(), TargetElementUtil.LOOKUP_ITEM_ACCEPTED));
      myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_CARET_UP);
      assertEquals(foo, TargetElementUtil.findTargetElement(myFixture.getEditor(), TargetElementUtil.LOOKUP_ITEM_ACCEPTED));
    });
  }

  @Test
  public void testExplicitAutocompletionAfterAutoPopup() {
    myFixture.configureByText("a.java", "class Foo <caret>");
    type("ext");

    runInEdtAndWait(() -> myFixture.completeBasic());
    assertNull(getLookup(), String.valueOf(myFixture.getLookupElementStrings()));
    myFixture.checkResult("class Foo extends <caret>");
  }

  @Test
  public void testExplicitMultipleVariantCompletionAfterAutoPopup() {
    myFixture.configureByText("a.java", "class Foo {<caret>}");
    type("pr");

    runInEdtAndWait(() -> myFixture.completeBasic());
    myFixture.checkResult("class Foo {pr<caret>}");

    ReadAction.run(() -> {
      JList<LookupElement> list = getLookup().getList();
      for (LookupElement item : getLookup().getItems()) {
        list.getCellRenderer().getListCellRendererComponent(list, item, 0, false, false);
      }
    });
    assertTrue(myFixture.getLookupElementStrings().containsAll(List.of("private", "protected")));
  }

  @Test
  public void testExactMatchesFirst() {
    myFixture.configureByText("a.java", """
      public class UTest {
          void nextWord() {}
      
          void foo() {
              n<caret>
          }
      }""");
    type("ew");
    assertEquals(myFixture.getLookupElementStrings(), List.of("new", "nextWord"));
  }

  @Test
  public void testExactMatchesTemplateFirst() {
    LiveTemplateCompletionContributor.setShowTemplatesInTests(true, myFixture.getTestRootDisposable());
    myFixture.configureByText("a.java", """
      public class Test {
          void itar() {}
      
          void foo() {
              ita<caret>
          }
      }""");
    type("r");
    assertEquals(myFixture.getLookupElementStrings(), List.of("itar", "itar"));
    assertTrue(myFixture.getLookup().getCurrentItem() instanceof LiveTemplateLookupElement);
  }

  @Test
  public void testMoreRecentExactMatchesTemplateFirst() {
    TemplateManager manager = TemplateManager.getInstance(myFixture.getProject());
    Template template = manager.createTemplate("itar", "myGroup", null);
    JavaCodeContextType contextType = TemplateContextTypes.getByClass(JavaCodeContextType.Statement.class);
    ((TemplateImpl)template).getTemplateContext().setEnabled(contextType, true);
    CodeInsightTestUtil.addTemplate(template, myFixture.getTestRootDisposable());

    LiveTemplateCompletionContributor.setShowTemplatesInTests(true, myFixture.getTestRootDisposable());
    myFixture.configureByText("a.java", """
      public class Test {
          void foo() {
              ita<caret>
          }
      }""");
    type("r");
    myFixture.assertPreferredCompletionItems(0, "itar", "itar");
  }

  @Test
  public void testUpdatePrefixMatchingOnTyping() {
    myFixture.addClass("class CertificateEncodingException extends Exception {}");
    myFixture.addClass("class CertificateException extends Exception {}");
    myFixture.configureByText("a.java", "class Foo {<caret>}");
    type("CertificateExce");
    assertEquals(myFixture.getLookupElementStrings(), List.of("CertificateException", "CertificateEncodingException"));
  }

  @Test
  public void testNoClassesInUnqualifiedImports() {
    myFixture.addClass("package xxxxx; public class Xxxxxxxxx {}");
    myFixture.configureByText("a.java", "package foo; import <caret>");
    type("xxx");
    assertNull(getLookup());
  }

  @Test
  public void testPopupAfterDotAfterPackage() {
    myFixture.configureByText("a.java", "<caret>");
    type("import jav");
    assertNotNull(getLookup());
    type(".");
    assertNotNull(getLookup());
  }

  @Test
  public void testPopupInShebang() {
    myFixture.configureByText("app", """
      #! /usr/bin/java --source 16
      record App() {
          public  static void main(String[] args) {
            args<caret>
          }
      }""");
    type(".");
    assertNotNull(getLookup());
  }

  @Test
  public void testSamePrefixIgnoreCase() {
    myFixture.addClass("package xxxxx; public class SYSTEM_EXCEPTION {}");
    myFixture.configureByText("a.java", "import xxxxx.*; class Foo { S<caret> }");
    type("Ystem");
    myFixture.assertPreferredCompletionItems(1, "System", "SYSTEM_EXCEPTION");
  }

  @Test
  public void testSamePrefixIgnoreCase2() {
    myFixture.addClass("package xxxxx; public class SYSTEM_EXCEPTION {}");
    myFixture.addClass("package xxxxx; public class SYstem {}");
    myFixture.configureByText("a.java", "import xxxxx.*; class Foo { S<caret> }");
    type("Ystem");
    myFixture.assertPreferredCompletionItems(0, "SYstem", "System", "SYSTEM_EXCEPTION");
  }

  private FileEditor openEditorForUndo() {
    final Ref<FileEditor> editor = Ref.create();
    runInEdtAndWait(() -> editor.set(FileEditorManager.getInstance(myFixture.getProject()).openFile(myFixture.getFile().getVirtualFile(), false)[0]));
    final UndoManagerImpl manager = (UndoManagerImpl)UndoManager.getInstance(myFixture.getProject());
    manager.setOverriddenEditorProvider(new CurrentEditorProvider() {
      @Override
      public FileEditor getCurrentEditor(@Nullable Project project) {
        return editor.get();
      }
    });
    Disposer.register(myFixture.getTestRootDisposable(), () -> manager.setOverriddenEditorProvider(null));
    return editor.get();
  }

  @Test
  public void testAutopopupTypingUndo() {
    myFixture.configureByText("a.java", "class Foo {{ <caret> }}");
    final FileEditor editor = openEditorForUndo();
    type("aioobeeee");
    runInEdtAndWait(() -> UndoManager.getInstance(myFixture.getProject()).undo(editor));
    assertFalse(myFixture.getEditor().getDocument().getText().contains("aioo"));
  }

  @Test
  public void testNoLiveTemplatesAfterDot() {
    myFixture.configureByText("a.java", "import java.util.List; class Foo { static { List<?> t; t.<caret> }}");
    type("toar");
    assertEquals(myFixture.getLookupElementStrings(), List.of("toArray", "toArray"));
  }

  @Test
  public void testTypingFirstVarargDot() {
    myFixture.configureByText("a.java", "class Foo { void foo(Foo<caret>[] a) { } class Bar {}}");
    type(".");
    assertNull(getLookup());
  }

  @Test
  public void testMulticaret() {
    doTestMulticaret("""
                       class Foo {{
                         <selection>t<caret></selection>x;
                         <selection>t<caret></selection>x;
                       }}""", "\n", """
                       class Foo {{
                         toString()<caret>x;
                         toString()<caret>x;
                       }}""");
  }

  @Test
  public void testMulticaretTab() {
    doTestMulticaret("""
                       class Foo {{
                         <selection>t<caret></selection>x;
                         <selection>t<caret></selection>x;
                       }}""", "\t", """
                       class Foo {{
                         toString()<caret>;
                         toString()<caret>;
                       }}""");
  }

  @Test
  public void testMulticaretBackspace() {
    doTestMulticaret("""
                       class Foo {{
                         <selection>t<caret></selection>;
                         <selection>t<caret></selection>;
                       }}""", "\b\t", """
                       class Foo {{
                         toString()<caret>;
                         toString()<caret>;
                       }}""");
  }

  private void doTestMulticaret(final String textBefore, final String toType, final String textAfter) {
    myFixture.configureByText("a.java", textBefore);
    type("toStr");
    assertNotNull(getLookup());
    type(toType);
    myFixture.checkResult(textAfter);
  }

  @Test
  public void test_two_non_imported_classes_when_space_selects_first_autopopup_item() {
    myFixture.addClass("package foo; public class Abcdefg {}");
    myFixture.configureByText("a.java", "class Foo extends <caret>");
    type("Abcde ");
    myFixture.checkResult("import foo.Abcdefg;\n\nclass Foo extends Abcdefg <caret>");
  }

  @Test
  public void test_two_non_imported_classes_when_space_does_not_select_first_autopopup_item() {
    CodeInsightSettings.getInstance().setSelectAutopopupSuggestionsByChars(false);

    myFixture.addClass("package foo; public class Abcdefg {}");
    myFixture.addClass("package bar; public class Abcdefg {}");
    myFixture.configureByText("a.java", "class Foo extends <caret>");
    type("Abcde");
    assertEquals(2, getLookup().getItems().size());
    runInEdtAndWait(() -> myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN));
    type(" ");
    myFixture.checkResult("""
                            import foo.Abcdefg;

                            class Foo extends Abcdefg <caret>""");
  }

  @Test
  public void testTwoNonImportedClasses_() {
    myFixture.addClass("package foo; public class Abcdefg {}");
    myFixture.addClass("package bar; public class Abcdefg {}");
    myFixture.configureByText("a.java", "class Foo extends <caret>");
    type("Abcde");
    assertEquals(2, getLookup().getItems().size());
  }

  @Test
  public void testClassNameInProperties() {
    myFixture.addClass("package java.langa; public class Abcdefg {}");
    myFixture.configureByText("a.properties", "key.11=java<caret>");
    type(".");
    assertNotNull(getLookup());
    type("lang");
    assertTrue(myFixture.getLookupElementStrings().size() >= 2);
    type(".");
    assertNotNull(getLookup());
    runInEdtAndWait(() -> myFixture.getEditor().getCaretModel().moveToOffset(myFixture.getEditor().getDocument().getText().indexOf("lang")));
    assertNull(getLookup());
    type("i");
    assertTrue(myFixture.getLookupElementStrings().contains("io"));
  }

  @Test
  public void testEnteringLabel() {
    myFixture.configureByText("a.java", """
      class Foo {{
        <caret>
      }}
      """);
    type("FIS:");
    assertTrue(myFixture.getEditor().getDocument().getText().contains("FIS:"));
  }

  @Test
  public void testSoutvTemplate() {
    TemplateManagerImpl.setTemplateTesting(myFixture.getTestRootDisposable());
    myFixture.configureByText("a.java", "class Foo {{ <caret> }}");
    type("soutv\tgetcl");
    myFixture.completeBasic();
    type(".");
    myFixture.checkResult("class Foo {{\n    System.out.println(\"getClass(). = \" + getClass().<caret>);\n}}");
  }

  @Test
  public void testReturnLParen() {
    myFixture.configureByText("a.java", "class Foo { int foo() { <caret> }}");
    type("re(");
    myFixture.checkResult("class Foo { int foo() { re(<caret>) }}");
  }

  @Test
  public void testAmbiguousClassQualifier() {
    myFixture.addClass("package foo; public final class Util<T> { public static void foo() {} public static final int CONSTANT = 2; }");
    myFixture.addClass("package bar; public final class Util { public static void bar() {} }");
    myFixture.configureByText("a.java", "class Foo { static { Util<caret> }}");
    type(".");
    assertEquals(Set.of(".new", ".lambda", "Util.bar", "Util.CONSTANT", "Util.foo"), Set.copyOf(myFixture.getLookupElementStrings()));

    LookupElement constant = ContainerUtil.find(myFixture.getLookupElements(), it -> it.getLookupString().equals("Util.CONSTANT"));
    LookupElementPresentation p = NormalCompletionFixture.renderElement(constant);
    assertEquals("Util.CONSTANT", p.getItemText());
    assertEquals(" ( = 2) foo", p.getTailText());
    assertEquals("int", p.getTypeText());

    type("fo\n");
    myFixture.checkResult("""
                            import foo.Util;

                            class Foo { static {
                                Util.foo();<caret> }}""");
  }

  @Test
  public void testPackageQualifier() {
    CodeInsightSettings.getInstance().setSelectAutopopupSuggestionsByChars(false);

    myFixture.addClass("package com.too; public class Util {}");
    myFixture.configureByText("a.java", "class Foo { void foo(Object command) { <caret> }}");
    type("com.t");
    assertTrue(myFixture.getLookupElementStrings().containsAll(List.of("too", "command.toString")));
  }

  @Test
  public void testUnfinishedString() {
    myFixture.configureByText("a.java", """
      // Date
      class Foo {
        String s = "<caret>
        String s2 = s;
      }
      """);
    type("D");
    assertNull(getLookup());
  }

  @Test
  public void testVarargParenthesis() {
    myFixture.configureByText("a.java", """
      class Foo {
        void foo(File... files) { }
        { foo(new <caret>) }
      }
      """);
    type("File(");
    assertTrue(myFixture.getEditor().getDocument().getText().contains("new File()"));
  }

  @Test
  public void test_inaccessible_class_in_another_package_shouldn_t_prevent_choosing_by_space() {
    myFixture.addClass("package foo; class b {}");
    myFixture.configureByText("a.java", "class Foo {{ <caret> }}");
    type("b");
    final LookupImpl impl = getLookup();
    final LookupElement item = (impl == null ? null : impl.getCurrentItem());
    assertEquals("boolean", (item == null ? null : item.getLookupString()));
    type(" ");
    myFixture.checkResult("class Foo {{ boolean <caret> }}");
  }

  @BeforeEach
  void setUp() {
    myFixture = autoPopupFixture.get().getFixture();
    myTester = autoPopupFixture.get().getTester();
    CodeInsightSettings.getInstance().setSelectAutopopupSuggestionsByChars(true);
    LiveTemplateCompletionContributor.setShowTemplatesInTests(true, myFixture.getTestRootDisposable());
  }

  @AfterEach
  void tearDown() {
    CodeInsightSettings.getInstance().setSelectAutopopupSuggestionsByChars(false);
    CodeInsightSettings.getInstance().setCompletionCaseSensitive(CodeInsightSettings.FIRST_LETTER);
    UISettings.getInstance().setSortLookupElementsLexicographically(false);
  }

  @Test
  public void testBackspaceShouldShowPreviousVariants() {
    CodeInsightSettings.getInstance().setCompletionCaseSensitive(CodeInsightSettings.NONE);
    myFixture.configureByText("a.java", "class Foo{ void foo(int itera, int itex) { it<caret> }}");
    type("e");
    myFixture.assertPreferredCompletionItems(0, "itera", "itex");
    type("r");
    myFixture.assertPreferredCompletionItems(0, "iter", "itera");
    type("\b");
    myFixture.assertPreferredCompletionItems(0, "itera", "itex");
  }

  @Test
  public void testBackspaceUntilDot() {
    myFixture.configureByText("a.java", "class Foo{ void foo(String s) { s<caret> }}");
    type(".sub");
    assertNotNull(myFixture.getLookupElementStrings());
    type("\b\b\b");
    assertNotNull(getLookup());
    type("\b");
    assertNull(getLookup());
  }

  @Test
  public void testQuickBackspaceEnter() {
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_21, () -> {
      myFixture.configureByText("a.java", "<caret>");
      type("cl");
      assertEquals(myFixture.getLookupElementStrings(), List.of("class"));
      myFixture.type("\b\n");
      myFixture.checkResult("class <caret>");
    });
  }

  @Test
  public void test_new_primitive_array_in_Object_variable() {
    CodeInsightSettings.getInstance().setCompletionCaseSensitive(CodeInsightSettings.NONE);
    myFixture.configureByText("a.java", "\nclass Foo {\n  void foo() {\n    Object o = new <caret>\n  }\n}\n");
    type("int");
    myFixture.assertPreferredCompletionItems(0, "int", "Integer");
    assertEquals(PsiTypes.intType().createArrayType(), myFixture.getLookupElements()[0].getObject());
    type("[");
    myFixture.checkResult("\nclass Foo {\n  void foo() {\n    Object o = new int[<caret>]\n  }\n}\n");
  }

  @Test
  public void test_middle_matching_and_overwrite() {
    myFixture.configureByText("a.java", "\nclass ListConfigKey {\n  void foo() {\n    <caret>\n  }\n}\n");
    type("CK\t");
    myFixture.checkResult("\nclass ListConfigKey {\n  void foo() {\n    ListConfigKey<caret>\n  }\n}\n");

  }

  @Test
  public void testPreselectMostRelevantInTheMiddleAlpha() {
    UISettings.getInstance().setSortLookupElementsLexicographically(true);
    CodeInsightSettings.getInstance().setSelectAutopopupSuggestionsByChars(false);

    myFixture.configureByText("a.java", """
      class Foo {
        void setText() {}
        void setHorizontalText() {}
        void foo() {
          <caret>
        }
      
      }
      """);
    type("sette");
    myFixture.assertPreferredCompletionItems(1, "setHorizontalText", "setText");
    runInEdtAndWait(() -> myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_CARET_UP));
    myFixture.assertPreferredCompletionItems(0, "setHorizontalText", "setText");
  }

  @Test
  public void test_pressing_enter_while_autopopup_is_calculating_variants_should_cancel_autopopup() {
    registerContributor(JavaAutoPopupTest.LongContributor.class, LoadingOrder.FIRST);
    myFixture.configureByText("a.java", "class Foo {{ <caret> }}");
    myFixture.type("a");
    joinAutopopup();
    type("\n");
    assertNull(getLookup());
  }

  @Test
  public void test_pressing_enter_and_a_letter_while_autopopup_is_calculating_variants_should_restart_autopopup() {
    registerContributor(JavaAutoPopupTest.LongContributor.class, LoadingOrder.FIRST);
    myFixture.configureByText("a.java", "class Foo {{ <caret> }}");
    myFixture.type("a");
    joinAutopopup();
    myFixture.type("\nf");
    joinCompletion();
    assertNotNull(getLookup());
  }

  @Test
  public void test_a_random_write_action_shouldn_t_cancel_autopopup() {
    registerContributor(JavaAutoPopupTest.LongContributor.class, LoadingOrder.FIRST);
    myFixture.configureByText("a.java", "class Foo {{ <caret> }}");
    myFixture.type("a");
    joinAutopopup();
    runInEdtAndWait(() -> ApplicationManager.getApplication().runWriteAction(() -> {}));
    joinCompletion();
    assertNotNull(getLookup());
  }

  @Test
  public void test_typing_during_restart_commit_document() {
    String longText = "\nfoo(); bar();".repeat(100);
    myFixture.configureByText("a.java", "class Foo { void foo(int xb, int xbde) { <caret>; " + longText + " }}");
    myFixture.type("x");
    joinAutopopup();
    myFixture.type("b");
    myTester.joinCommit();
    myFixture.type("c");
    joinCompletion();
    assertNull(getLookup());
  }

  @Test
  public void test_no_name_autopopup_in_live_template() {
    TemplateManagerImpl.setTemplateTesting(myFixture.getTestRootDisposable());
    myFixture.configureByText("a.java", """
      class F {
        String nameContainingIdentifier;
      <caret>
      }""");

    final TemplateManager manager = TemplateManager.getInstance(myFixture.getProject());
    final Template template = manager.createTemplate("m", "user", "void foo(String $V1$) {}");
    template.addVariable("V1", "", "\"s\"", true);

    runInEdtAndWait(() -> CommandProcessor.getInstance().executeCommand(
      myFixture.getProject(), () -> manager.startTemplate(myFixture.getEditor(), template), null, null));

    type("name");
    assertNull(myFixture.getLookupElementStrings());
    assertNull(getLookup());
  }

  @Test
  public void test_template_prefix_is_better_than_middle_matches() {
    myFixture.configureByText("a.java", """
      class Cls {
        void foo() {
          <caret>
        }
        void mySout() {}
      }
      """);
    type("sout");
    myFixture.assertPreferredCompletionItems(0, "sout", "mySout");
  }

  @Test
  public void test_single_overriding_getter() {
    myFixture.configureByText("a.java", """
      public class Foo {
          public int getField() {}
      }
      
      class X extends Foo {
          int field;
      
          <caret>
      }
      """);
    type("getf");
    assertEquals(myFixture.getLookupElementStrings(), List.of("public int getField"));
  }

  @Test
  public void test_live_template_quick_doc() {
    myFixture.configureByText("a.java", """
      class Cls {
        void foo() {
          <caret>
        }
        void mySout() {}
      }
      """);
    type("sout");
    assertNotNull(getLookup());
    assertTrue(myFixture.getLookupElementStrings().contains("sout"));

    LiveTemplateDocumentationProvider docProvider = new LiveTemplateDocumentationProvider();
    PsiElement docElement = docProvider.getDocumentationElementForLookupItem(myFixture.getPsiManager(), getLookup().getCurrentItem(), null);
    assertEquals("sout", ((NavigatablePsiElement)docElement).getPresentation().getPresentableText());
    assertTrue(docProvider.generateDoc(docElement, docElement).contains("System.out"));
  }

  @Test
  public void test_finishing_class_reference_property_value_completion_with_dot_opens_autopopup() {
    myFixture.configureByText("a.properties", "myprop=ja<caret>");
    type("v");
    myFixture.assertPreferredCompletionItems(0, "java");
    getLookup().setLookupFocusDegree(LookupFocusDegree.FOCUSED);
    type(".");
    myFixture.checkResult("myprop=java.<caret>");
    assertNotNull(getLookup());
  }

  @Test
  public void test_live_template_without_description() {
    final TemplateManager manager = TemplateManager.getInstance(myFixture.getProject());
    final Template template = manager.createTemplate("tpl", "user", null);
    JavaCodeContextType contextType = TemplateContextTypes.getByClass(JavaCodeContextType.Statement.class);
    ((TemplateImpl)template).getTemplateContext().setEnabled(contextType, true);
    CodeInsightTestUtil.addTemplate(template, myFixture.getTestRootDisposable());

    myFixture.configureByText("a.java", """
      class Foo {
       int tplMn;
      \s
       { <caret> }
      }
      """);
    type("tpl");
    myFixture.assertPreferredCompletionItems(0, "tpl", "tplMn");

    LookupElementPresentation p = NormalCompletionFixture.renderElement(myFixture.getLookupElements()[0]);
    assertEquals("tpl", p.getItemText());
    assertNull(p.getTailText());
    assertTrue(p.getTypeText().isEmpty());
  }

  @Test
  public void test_autopopup_after_package_completion() {
    myFixture.addClass("package foo.bar.goo; class Foo {}");
    myFixture.configureByText("a.java", "class Foo { { foo.<caret> } }");
    type("b");
    assertEquals(myFixture.getLookupElementStrings(), List.of("bar"));
    assertEquals("bar.", NormalCompletionFixture.renderElement(myFixture.getLookupElements()[0]).getItemText());
    myFixture.type("\n");
    assertTrue(myFixture.getEditor().getDocument().getText().contains("foo.bar. "));
    joinAutopopup();
    joinCompletion();
    assertNotNull(getLookup());
    assertEquals(myFixture.getLookupElementStrings(), List.of("goo", ".lambda"));
  }

  @Test
  public void test_in_column_selection_mode() {
    myFixture.configureByText("a.java", """
      class Foo {{
        <caret>
      }}""");
    runInEdtAndWait(() -> ((EditorEx)myFixture.getEditor()).setColumnMode(true));
    type("toStr");
    assertNotNull(getLookup());
  }

  @Test
  public void test_show_popup_with_single_live_template_if_show_live_tempate_in_completion_option_is_enabled() {
    LiveTemplateCompletionContributor.setShowTemplatesInTests(false, myFixture.getTestRootDisposable());
    myFixture.configureByText("a.java", """
      class Foo {
        static {
          ita<caret>
        }
      }""");
    type("r");
    assertNull(getLookup());

    LiveTemplateCompletionContributor.setShowTemplatesInTests(true, myFixture.getTestRootDisposable());
    type("\br");
    assertNotNull(getLookup());
    assertEquals(myFixture.getLookupElementStrings(), List.of("itar"));
  }

  @Test
  public void test_expand_class_list_when_typing_more_or_moving_caret() {
    myFixture.addClass("package foo; public class KimeFamilyRange {}");
    myFixture.addClass("package foo; public class FamiliesRangesMetaData {}");
    myFixture.addClass("public class KSomethingInCurrentPackage {}");
    myFixture.configureByText("a.java", "class Foo { <caret> }");

    type("F");
    assertFalse(myFixture.getLookupElementStrings().contains("KimeFamilyRange"));

    type("aRa");
    myFixture.assertPreferredCompletionItems(0, "FamiliesRangesMetaData", "KimeFamilyRange");

    for (int i = 0; i < 4; i++) {
      runInEdtAndWait(() -> myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_CARET_LEFT));
      joinCompletion();
    }
    assertFalse(myFixture.getLookupElementStrings().contains("KimeFamilyRange"));

    type("K");

    for (int i = 0; i < 4; i++) {
      runInEdtAndWait(() -> myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT));
      joinCompletion();
    }

    myFixture.assertPreferredCompletionItems(0, "KimeFamilyRange");
  }

  @Test
  public void test_show_autopopup_when_typing_digit_after_letter() {
    myFixture.configureByText("a.java", "class Foo { static { int a42; a<caret> }}");
    type("4");
    assertNotNull(getLookup());
  }

  @Test
  public void test_autopopup_after_new() {
    myFixture.configureByText("a.java", "class Foo { static { java.util.List<String> l = new<caret> }}");
    type(" ");
    assertNotNull(getLookup());
    List<LookupElement> firstItems = Arrays.asList(myFixture.getLookupElements()).subList(0, 4);
    boolean inheritors = ReadAction.compute(() -> ContainerUtil.all(
      firstItems, it -> InheritanceUtil.isInheritor((PsiClass)it.getObject(), CommonClassNames.JAVA_UTIL_LIST)));
    assertTrue(inheritors);
  }

  @Test
  public void test_prefer_previously_selected_despite_many_namesakes() {
    ((StatisticsManagerImpl)StatisticsManager.getInstance()).enableStatistics(myFixture.getTestRootDisposable());

    int count = 400;
    final int toSelect = 390;
    for (int i = 0; i <= count; i++) {
      myFixture.addClass("package p" + i + "; public class MyClass {}");
    }

    myFixture.configureByText("a.java", "class C extends <caret>");
    type("MyCla");
    myFixture.assertPreferredCompletionItems(0, ArrayUtil.toStringArray(Collections.nCopies(count, "MyClass")));

    runInEdtAndWait(() -> {
      assertEquals(" p" + toSelect, NormalCompletionFixture.renderElement(myFixture.getLookup().getItems().get(toSelect)).getTailText());
      CompletionSortingFixture.imitateItemSelection(myFixture.getLookup(), toSelect);
      myFixture.getLookup().hideLookup(true);
    });

    type("s");
    myFixture.assertPreferredCompletionItems(0, ArrayUtil.toStringArray(Collections.nCopies(count, "MyClass")));
    runInEdtAndWait(() -> {
      assertEquals(" p" + toSelect, NormalCompletionFixture.renderElement(myFixture.getLookup().getItems().getFirst()).getTailText());
    });
  }

  @Test
  public void test_show_popup_when_completing_property_key() {
    myFixture.createFile("PropertyKey.properties", "foo.bar = 1");
    myFixture.configureByText("a.java", """
      import org.jetbrains.annotations.PropertyKey;

      public class Foo {
          public static void message(@PropertyKey(resourceBundle = "PropertyKey") String key) {}

          void test() {
              message("fo<caret>");
          }
      }
      """);
    type("o");
    assertNotNull(getLookup());
  }
}
